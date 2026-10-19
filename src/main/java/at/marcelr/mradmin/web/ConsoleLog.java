package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The server console for the web interface: follows logs/latest.log (everything the console shows, Minecraft's own
 * output included) in a background thread and keeps the newest lines. Links and IP addresses are hidden when a line
 * is read (web.console.hide-links / hide-ips) - the raw line is never kept. Starts with the end of the current file.
 */
final class ConsoleLog {

    /** One line with its running number (the browser asks for "everything after n"). */
    record Line(long seq, String text) {
    }

    private static final long POLL_MILLIS = 500;
    /** At the start, read at most this much of the end of the file. */
    private static final long START_BYTES = 256 * 1024;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?|ftp)://\\S+");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
    // Full IPv6 addresses and shortened ones with "::" - times like 12:34:56 do not match.
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\\b|(?:[0-9a-f]{1,4}:){0,6}:(?::[0-9a-f]{1,4}){1,7}|\\b(?:[0-9a-f]{1,4}:){1,7}:(?=[\\s\\]/%]|$)");

    private final MRAdmin plugin;
    private final Path file;
    private final Deque<Line> lines = new ArrayDeque<>();
    private long seq;
    private long position = -1;
    private final StringBuilder partial = new StringBuilder();
    private volatile Thread thread;

    ConsoleLog(MRAdmin plugin) {
        this.plugin = plugin;
        this.file = plugin.getServer().getWorldContainer().toPath().resolve("logs").resolve("latest.log");
    }

    void start() {
        stop();
        Thread t = Thread.ofVirtual().name("MRadmin-ConsoleLog").unstarted(this::run);
        thread = t;
        t.start();
    }

    void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    /** Lines after {@code after} (at most {@code max}, the newest), oldest first. */
    synchronized List<Line> after(long after, int max) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            if (line.seq() > after) {
                result.add(line);
            }
        }
        return result.size() > max ? result.subList(result.size() - max, result.size()) : result;
    }

    synchronized long last() {
        return seq;
    }

    // ---------------------------------------------------------------- reading

    private void run() {
        while (thread == Thread.currentThread()) {
            try {
                read();
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                return;
            } catch (IOException | RuntimeException e) {
                // File rotated or briefly not there - try again next time.
                position = -1;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
    }

    private void read() throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        long size = Files.size(file);
        if (position < 0) {
            position = Math.max(0, size - START_BYTES);
            partial.setLength(0);
            if (position > 0) {
                partial.append('\u0000'); // the first (cut) line is dropped
            }
        } else if (size < position) {
            // New file after a restart of the log (rotation).
            position = 0;
            partial.setLength(0);
        }
        if (size == position) {
            return;
        }
        byte[] data;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(position);
            data = new byte[(int) Math.min(size - position, 4 * 1024 * 1024)];
            raf.readFully(data);
        }
        position += data.length;
        partial.append(new String(data, StandardCharsets.UTF_8));
        int newline;
        List<String> complete = new ArrayList<>();
        while ((newline = partial.indexOf("\n")) >= 0) {
            String line = partial.substring(0, newline);
            partial.delete(0, newline + 1);
            if (!line.startsWith("\u0000")) {
                complete.add(line);
            }
        }
        if (!complete.isEmpty()) {
            add(complete);
        }
    }

    private synchronized void add(List<String> raw) {
        boolean links = plugin.getConfig().getBoolean("web.console.hide-links", true);
        boolean ips = plugin.getConfig().getBoolean("web.console.hide-ips", true);
        int keep = Math.clamp(plugin.getConfig().getInt("web.console.lines", 1000), 100, 10_000);
        for (String line : raw) {
            String text = ANSI.matcher(line.replace("\r", "")).replaceAll("");
            if (links) {
                text = LINK.matcher(text).replaceAll("[Link]");
            }
            if (ips) {
                text = IPV4.matcher(text).replaceAll("[IP]");
                text = IPV6.matcher(text).replaceAll("[IP]");
            }
            lines.addLast(new Line(++seq, text));
        }
        while (lines.size() > keep) {
            lines.removeFirst();
        }
    }
}
