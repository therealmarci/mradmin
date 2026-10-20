package at.marcelr.mradmin.mail;

import at.marcelr.mradmin.Settings;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Sends plain text mails over SMTP - only what MRadmin needs (answers to unban and contact requests), without a mail
 * library: SSL/TLS from the start (port 465), STARTTLS (587) or plain (only for local relays), AUTH PLAIN or LOGIN.
 * Always on a virtual thread, never on the server thread.
 */
public final class Mailer {

    /** Simple check of an address: something@domain.tld, no spaces or line breaks (header injection). */
    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s<>\"(),;:]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,24}$");
    private static final int TIMEOUT_MS = 15_000;

    private final Supplier<Settings.MailSettings> settings;
    private final Logger log;

    public Mailer(Supplier<Settings.MailSettings> settings, Logger log) {
        this.settings = settings;
        this.log = log;
    }

    public static boolean validAddress(String address) {
        return address != null && address.length() <= 254 && ADDRESS.matcher(address).matches();
    }

    /** Mails are switched on and the SMTP server is filled in. */
    public boolean enabled() {
        Settings.MailSettings s = settings.get();
        return s.enabled() && !s.host().isEmpty() && validAddress(s.from());
    }

    /** Sends in the background; failures are logged (without the password). */
    public void send(String to, String subject, String body) {
        if (!enabled() || !validAddress(to)) {
            return;
        }
        Settings.MailSettings s = settings.get();
        Thread.ofVirtual().name("MRadmin-Mail").start(() -> {
            try {
                deliver(s, to, subject, body);
            } catch (IOException | RuntimeException e) {
                log.warning("Could not send a mail to " + mask(to) + " via " + s.host() + ":" + s.port() + ": " + e.getMessage());
            }
        });
    }

    /** Sends right away and throws on errors - for /mradmin mail test. */
    public void sendNow(String to, String subject, String body) throws IOException {
        if (!validAddress(to)) {
            throw new IOException("invalid address");
        }
        deliver(settings.get(), to, subject, body);
    }

    /** a***@example.com - addresses are personal data, the console shows only the domain. */
    public static String mask(String address) {
        int at = address == null ? -1 : address.indexOf('@');
        return at <= 0 ? "?" : address.charAt(0) + "***" + address.substring(at);
    }

    // ------------------------------------------------------------------ SMTP

    private void deliver(Settings.MailSettings s, String to, String subject, String body) throws IOException {
        Socket socket;
        if (s.security().equals("ssl")) {
            socket = SSLSocketFactory.getDefault().createSocket();
        } else {
            socket = new Socket();
        }
        socket.connect(new InetSocketAddress(s.host(), s.port()), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        Connection c = new Connection(socket);
        try {
            c.expect(220);
            String helo = helloName(s);
            c.command("EHLO " + helo, 250);
            if (s.security().equals("starttls")) {
                c.command("STARTTLS", 220);
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(socket, s.host(), s.port(), true);
                tls.startHandshake();
                c = new Connection(tls);
                c.command("EHLO " + helo, 250);
            }
            if (!s.username().isEmpty()) {
                String ehlo = c.lastReply.toUpperCase(Locale.ROOT);
                if (ehlo.contains("PLAIN")) {
                    String token = Base64.getEncoder().encodeToString(("\0" + s.username() + "\0" + s.password())
                            .getBytes(StandardCharsets.UTF_8));
                    c.command("AUTH PLAIN " + token, 235);
                } else {
                    c.command("AUTH LOGIN", 334);
                    c.command(Base64.getEncoder().encodeToString(s.username().getBytes(StandardCharsets.UTF_8)), 334);
                    c.command(Base64.getEncoder().encodeToString(s.password().getBytes(StandardCharsets.UTF_8)), 235);
                }
            }
            c.command("MAIL FROM:<" + s.from() + ">", 250);
            c.command("RCPT TO:<" + to + ">", 250, 251);
            c.command("DATA", 354);
            c.writeRaw(message(s, to, subject, body));
            c.command(".", 250);
            c.command("QUIT", 221);
        } finally {
            socket.close();
        }
    }

    private static String helloName(Settings.MailSettings s) {
        int at = s.from().indexOf('@');
        return at > 0 ? s.from().substring(at + 1) : "localhost";
    }

    /** Headers + base64 body (UTF-8, any line length); dot-stuffing is not needed for base64. */
    private static String message(Settings.MailSettings s, String to, String subject, String body) {
        String domain = helloName(s);
        StringBuilder m = new StringBuilder();
        String from = s.fromName().isEmpty() ? s.from() : encode(s.fromName()) + " <" + s.from() + ">";
        m.append("From: ").append(from).append("\r\n");
        m.append("To: <").append(to).append(">\r\n");
        if (validAddress(s.replyTo())) {
            m.append("Reply-To: <").append(s.replyTo()).append(">\r\n");
        }
        m.append("Subject: ").append(encode(subject.replaceAll("[\\r\\n]+", " "))).append("\r\n");
        m.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
        m.append("Message-ID: <").append(UUID.randomUUID()).append("@").append(domain).append(">\r\n");
        m.append("Auto-Submitted: auto-generated\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        String encoded = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        m.append(encoded).append("\r\n");
        return m.toString();
    }

    /** RFC 2047 for non-ASCII header text. */
    private static String encode(String text) {
        boolean ascii = text.chars().allMatch(ch -> ch >= 32 && ch < 127);
        return ascii ? text : "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /** One SMTP connection: commands with expected reply codes; multi-line replies are read completely. */
    private static final class Connection {
        private final BufferedReader in;
        private final OutputStream out;
        private String lastReply = "";

        Connection(Socket socket) throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            out = socket.getOutputStream();
        }

        void command(String line, int... expected) throws IOException {
            writeRaw(line + "\r\n");
            expect(expected);
        }

        void writeRaw(String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        void expect(int... expected) throws IOException {
            StringBuilder reply = new StringBuilder();
            String line;
            int code;
            do {
                line = in.readLine();
                if (line == null || line.length() < 3) {
                    throw new IOException("connection closed by the SMTP server");
                }
                reply.append(line).append('\n');
                code = Integer.parseInt(line.substring(0, 3));
            } while (line.length() > 3 && line.charAt(3) == '-');
            lastReply = reply.toString();
            for (int e : expected) {
                if (code == e) {
                    return;
                }
            }
            throw new IOException("SMTP server answered: " + line);
        }
    }
}
