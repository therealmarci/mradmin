package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.hook.Bans;
import at.marcelr.mradmin.mail.Mailer;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.ticket.WebRequest;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static at.marcelr.mradmin.Messages.tag;

/**
 * The public forms of the web interface: unban requests and the contact form below the imprint. Both end up as
 * tickets ({@link WebRequest} carries the extra data); the person follows them through a secret status link and, if
 * mail is set up, by mail.
 * <p>
 * <b>Unban code:</b> a banned player cannot sign in anywhere, so the proof that a request really comes from them is
 * a code MRadmin adds to LibertyBans' ban message: {@code <ban id>-<6 characters>}, the characters being an HMAC of
 * player and ban with a secret only the server knows. Nothing has to be stored for it, and a code only fits one
 * player and one ban.
 * <p>
 * <b>Spam protection</b> (no third parties): a signed challenge with a minimum fill-in time and a small proof of work
 * the browser solves, a honeypot field, per-IP limits per minute and hour, a cap of open contact requests.
 */
public final class PublicRequests implements Listener {

    private static final String STATE = "web-requests";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern CODE = Pattern.compile("^(\\d{1,18})-([A-Z2-9]{6})$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final long CHALLENGE_MIN_MS = 3_000;
    private static final long CHALLENGE_MAX_MS = 2 * 3_600_000L;

    private final MRAdmin plugin;
    private final WebServer server;
    private final Mailer mailer;
    private final SecureRandom random = new SecureRandom();
    private byte[] secret;
    /** Bans that already had a request - kept apart from the tickets, which are deleted after tickets.keep-closed. */
    private final java.util.Set<Long> usedBans = ConcurrentHashMap.newKeySet();
    /** used proof-of-work challenges (replay), with when they expire */
    private final Map<String, Long> usedChallenges = new ConcurrentHashMap<>();
    /** ip -> times of requests within the last hour */
    private final Map<String, Deque<Long>> hourly = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean warnedNoUrl = new java.util.concurrent.atomic.AtomicBoolean();

    public PublicRequests(MRAdmin plugin, WebServer server) {
        this.plugin = plugin;
        this.server = server;
        this.mailer = new Mailer(() -> plugin.settings().mail(), plugin.getLogger());
    }

    /** Loads the secret (made on first start). */
    public void load() {
        YamlConfiguration y = plugin.storage().loadYaml(STATE, plugin.getLogger());
        String stored = y.getString("secret");
        if (stored == null || stored.length() < 40) {
            byte[] fresh = new byte[32];
            random.nextBytes(fresh);
            stored = Base64.getEncoder().encodeToString(fresh);
            y.set("secret", stored);
            y.options().setHeader(List.of("MRadmin - public web forms (managed by the plugin, do not edit).",
                    "Changing the secret makes all unban codes in ban messages invalid."));
            plugin.storage().saveYaml(STATE, y);
        }
        secret = Base64.getDecoder().decode(stored);
        usedBans.clear();
        y.getLongList("used-bans").forEach(usedBans::add);
        if (settings().unban() && settings().kickMessage() && plugin.settings().web().enabled()
                && plugin.settings().web().publicUrl().isEmpty()) {
            plugin.getLogger().warning("Unban requests are on, but web.public-url is not set - ban messages get no"
                    + " unban link and code until it is.");
        }
    }

    /** Whether this ban already had its one request. */
    public boolean used(long banId) {
        return usedBans.contains(banId);
    }

    synchronized void markUsed(long banId) {
        if (usedBans.add(banId)) {
            YamlConfiguration y = plugin.storage().loadYaml(STATE, plugin.getLogger());
            y.set("used-bans", new java.util.ArrayList<>(usedBans));
            plugin.storage().saveYaml(STATE, y);
        }
    }

    public Mailer mailer() {
        return mailer;
    }

    private Settings.RequestSettings settings() {
        return plugin.settings().requests();
    }

    /** Unban requests are possible: switched on, tickets on and LibertyBans there. */
    public boolean unbanAvailable() {
        return settings().unban() && plugin.feature(at.marcelr.mradmin.Feature.TICKETS) && Bans.get(plugin) != null;
    }

    public boolean contactAvailable() {
        return settings().contact() && plugin.feature(at.marcelr.mradmin.Feature.TICKETS);
    }

    // ------------------------------------------------------------ unban code

    /** "57-K7F2QX" for this player and ban. */
    public String code(UUID player, long banId) {
        return banId + "-" + codeTag(player, banId);
    }

    /** The ban id if the code belongs to this player, otherwise -1. */
    public long verify(UUID player, String code) {
        Matcher m = CODE.matcher(code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replace(" ", ""));
        if (!m.matches()) {
            return -1;
        }
        long banId;
        try {
            banId = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
        byte[] expected = codeTag(player, banId).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, m.group(2).getBytes(StandardCharsets.US_ASCII)) ? banId : -1;
    }

    private String codeTag(UUID player, long banId) {
        byte[] mac = hmac("unban:" + player + ":" + banId);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            out.append(CODE_ALPHABET.charAt((mac[i] & 0xff) % CODE_ALPHABET.length()));
        }
        return out.toString();
    }

    private byte[] hmac(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * LibertyBans refuses banned players in this event (priority LOW/HIGH); afterwards the address of the unban page
     * and the player's code are added to its message. Runs on Paper's login thread, so the ban lookup may block.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.KICK_BANNED || !settings().kickMessage()
                || !plugin.settings().web().enabled() || !unbanAvailable()) {
            return;
        }
        if (plugin.settings().web().publicUrl().isEmpty()) {
            // Without an address the player would get a code but no place to use it.
            if (!warnedNoUrl.getAndSet(true)) {
                plugin.getLogger().warning("Banned player " + event.getName() + " got no unban link: set web.public-url"
                        + " (the address of the web interface) so ban messages show where to request an unban.");
            }
            return;
        }
        Bans bans = Bans.get(plugin);
        if (bans == null) {
            return;
        }
        try {
            Bans.Entry ban = bans.applicableBan(event.getUniqueId(), event.getAddress());
            if (ban == null) {
                return;
            }
            String url = plugin.settings().web().publicUrl() + "/entbannung";
            event.kickMessage(event.kickMessage().append(plugin.messages().get("unban-kick",
                    tag("url", url), tag("code", code(event.getUniqueId(), ban.id())))));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not add the unban code to the ban message of " + event.getName() + ": " + e);
        }
    }

    // --------------------------------------------------------- spam protection

    /** A challenge for the browser: "time.random.signature", plus how many zero bits the proof of work needs. */
    public Map<String, Object> challenge() {
        byte[] r = new byte[12];
        random.nextBytes(r);
        String body = System.currentTimeMillis() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(r);
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac("challenge:" + body)).substring(0, 22);
        return Map.of("challenge", body + "." + sig, "bits", settings().powBits());
    }

    /**
     * Checks everything a public form sends against spam; throws with a key the page shows.
     * @param honeypot the invisible field - people leave it empty
     */
    void checkSpam(Http http, String challenge, String nonce, String honeypot) throws Http.WebException {
        if (honeypot != null && !honeypot.isEmpty()) {
            throw new Http.WebException(400, "spam");
        }
        String[] parts = challenge == null ? new String[0] : challenge.split("\\.");
        if (parts.length != 3) {
            throw new Http.WebException(400, "challenge");
        }
        String body = parts[0] + "." + parts[1];
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac("challenge:" + body)).substring(0, 22);
        if (!MessageDigest.isEqual(sig.getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) {
            throw new Http.WebException(400, "challenge");
        }
        long issued;
        try {
            issued = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new Http.WebException(400, "challenge");
        }
        long age = System.currentTimeMillis() - issued;
        if (age < CHALLENGE_MIN_MS) {
            throw new Http.WebException(400, "too-fast");
        }
        if (age > CHALLENGE_MAX_MS) {
            throw new Http.WebException(400, "challenge-expired");
        }
        if (!proofOk(challenge, nonce, settings().powBits())) {
            throw new Http.WebException(400, "challenge");
        }
        long now = System.currentTimeMillis();
        usedChallenges.values().removeIf(until -> until < now);
        if (usedChallenges.putIfAbsent(challenge, now + CHALLENGE_MAX_MS) != null) {
            throw new Http.WebException(400, "challenge");
        }
    }

    /** SHA-256(challenge + ":" + nonce) starts with {@code bits} zero bits. */
    static boolean proofOk(String challenge, String nonce, int bits) {
        if (bits <= 0) {
            return true;
        }
        if (nonce == null || nonce.length() > 20) {
            return false;
        }
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest((challenge + ":" + nonce).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
        for (int i = 0; i < bits; i++) {
            if ((hash[i / 8] >> (7 - i % 8) & 1) != 0) {
                return false;
            }
        }
        return true;
    }

    /** At most per-ip-per-hour requests per address (counted when a request is really made). */
    void checkHourly(Http http) throws Http.WebException {
        long now = System.currentTimeMillis();
        Deque<Long> times = hourly.computeIfAbsent(http.ip(), k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < now - 3_600_000L) {
                times.pollFirst();
            }
            if (times.size() >= settings().perIpPerHour()) {
                throw new Http.WebException(429, "rate-limit-hour");
            }
            times.addLast(now);
        }
        if (hourly.size() > 10_000) {
            hourly.clear();
        }
    }

    // ----------------------------------------------------------------- tokens

    /** A new secret for a status link (32 characters, 192 bits). */
    String newToken() {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public String statusUrl(String token) {
        return plugin.settings().web().publicUrl() + "/anfrage/" + token;
    }

    // ------------------------------------------------------------------ names

    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** UUID of a player name: from LibertyBans (knows everyone who tried to join), else Paper's cache; or null. */
    UUID lookup(Bans bans, String name) throws Exception {
        UUID id = bans.lookup(name);
        if (id != null) {
            return id;
        }
        OfflinePlayer cached = server.sync(() -> Bukkit.getOfflinePlayerIfCached(name));
        return cached == null ? null : cached.getUniqueId();
    }

    // ------------------------------------------------------------------ mails

    /** Right after a request was made: the status link by mail (if an address was given). */
    void sendReceived(Ticket ticket) {
        WebRequest r = ticket.request();
        if (!r.hasEmail() || !settings().notifyByMail()) {
            return;
        }
        mail(r.email(), ticket, r.unban() ? "mail-unban-received" : "mail-contact-received", "");
    }

    /** Called by {@link TicketManager} after a team answer or closing (server thread): mail to the person. */
    public void changed(Ticket ticket, String answer) {
        WebRequest r = ticket.request();
        if (r == null || !r.hasEmail() || !settings().notifyByMail()) {
            return;
        }
        String key;
        if (ticket.open()) {
            key = "mail-request-answer";
        } else if (r.unban()) {
            key = WebRequest.ACCEPTED.equals(r.decision()) ? "mail-unban-accepted"
                    : r.appeal() ? "mail-unban-rejected-final" : "mail-unban-rejected";
        } else {
            key = "mail-contact-closed";
        }
        mail(r.email(), ticket, key, answer == null ? "" : answer);
    }

    private void mail(String to, Ticket ticket, String key, String answer) {
        var m = plugin.messages();
        String server = m.plain("mail-server-name");
        Object[] values = {"id", ticket.id(), "name", ticket.creatorName(), "subject", ticket.subject(),
                "answer", answer.isEmpty() ? m.plain("mail-no-text") : answer, "link", statusUrl(ticket.request().token()),
                "server", server, "time", Texts.at(plugin, Instant.now())};
        mailer.send(to, m.plain(key + "-subject", values), m.plain(key + "-body", values));
    }
}
