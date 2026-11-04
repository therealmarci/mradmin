package at.marcelr.mradmin.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * The older server log for the page Log: reads Paper's files in {@code logs/} ({@code latest.log} = today,
 * {@code <date>-<n>.log.gz} = earlier, oldest number first) and finds lines by time and text, newest first.
 * Nothing is kept - Paper keeps the files itself. Links and IP addresses are hidden like in the web console.
 * <p>
 * Every line gets a key that orders it: its time (date from the file name, time from {@code [HH:mm:ss]} at the start;
 * lines without one, e.g. stack traces, take the one before) plus a running number within that second. The page
 * asks for "older than key" to load more.
 */
final class ServerLogFiles {

    /** One line: {@code key} for ordering and "load older", {@code time} in epoch ms. */
    record Line(long key, long time, String text) {
    }

    record Result(List<Line> lines, boolean more) {
    }

    public static final int MAX_DAYS = 14;
    private static final Pattern GZ = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d+)\\.log\\.gz");
    private static final Pattern TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})");

    private final Path folder;
    private final ZoneId zone;

    ServerLogFiles(Path folder, ZoneId zone) {
        this.folder = folder;
        this.zone = zone;
    }

    /**
     * @param since  only from this time (epoch ms; at most {@link #MAX_DAYS} days back)
     * @param before only keys below this (0 = up to now)
     * @param query  text that must appear (case does not matter), empty = all
     */
    Result find(LocalDate today, long since, long before, String query, int limit, boolean links, boolean ips) throws IOException {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        LocalDate first = today.minusDays(MAX_DAYS - 1L);
        LocalDate from = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(Math.max(0, since)), zone);
        if (from.isBefore(first)) {
            from = first;
        }
        List<Line> found = new ArrayList<>();
        for (LocalDate day = today; !day.isBefore(from); day = day.minusDays(1)) {
            if (before > 0 && start(day) > before) {
                continue;
            }
            List<Line> lines = read(day, today, links, ips);
            for (int i = lines.size() - 1; i >= 0; i--) {
                Line line = lines.get(i);
                if (line.time() < since) {
                    return new Result(found, false);
                }
                if ((before > 0 && line.key() >= before) || (!q.isEmpty() && !line.text().toLowerCase(Locale.ROOT).contains(q))) {
                    continue;
                }
                if (found.size() == limit) {
                    return new Result(found, true);
                }
                found.add(line);
            }
        }
        return new Result(found, false);
    }

    /** All lines of one day in order: its .gz files by number, today also latest.log. */
    private List<Line> read(LocalDate day, LocalDate today, boolean links, boolean ips) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> list = Files.list(folder)) {
                list.filter(f -> {
                    Matcher m = GZ.matcher(f.getFileName().toString());
                    return m.matches() && m.group(1).equals(day.toString());
                }).sorted(Comparator.comparingInt(f -> number(f.getFileName().toString()))).forEach(files::add);
            }
        }
        Path latest = folder.resolve("latest.log");
        if (day.equals(today) && Files.isRegularFile(latest)) {
            files.add(latest);
        }
        long dayStart = start(day);
        List<Line> out = new ArrayList<>();
        long second = dayStart;
        int index = 0;
        for (Path file : files) {
            try (InputStream raw = Files.newInputStream(file);
                 InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = TIME.matcher(line);
                    if (m.find()) {
                        long t = dayStart + (Integer.parseInt(m.group(1)) * 3600L + Integer.parseInt(m.group(2)) * 60L
                                + Integer.parseInt(m.group(3))) * 1000L;
                        if (t != second) {
                            second = t;
                            index = 0;
                        }
                    }
                    out.add(new Line(second + Math.min(index++, 999), second, ConsoleLog.clean(line, links, ips)));
                }
            } catch (IOException e) {
                // a broken or still written .gz - skip the rest of it
            }
        }
        return out;
    }

    private long start(LocalDate day) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static int number(String name) {
        Matcher m = GZ.matcher(name);
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    }
}
