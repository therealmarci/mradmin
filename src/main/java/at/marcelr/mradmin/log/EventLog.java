package at.marcelr.mradmin.log;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * MRadmin's log: the events of MRadmin and of every module (the same ones that go to their Discord logs), kept up to
 * {@code logging.keep-days} (at most 14). One file per day in {@code plugins/MRadmin/logs/} (one JSON object per line,
 * written on a background thread), the entries of the kept days stay in memory for the web interface (at most
 * {@link #MAX_ENTRIES}, the oldest go first). Old files are deleted on start and at midnight.
 */
public final class EventLog {

    public static final int MAX_DAYS = 14;
    static final int MAX_ENTRIES = 200_000;
    private static final Gson GSON = new Gson();

    /** One event. {@code source}: "mradmin", "web" or the id of a module. */
    public record Entry(long time, String source, String event, String text, int color) {
    }

    private final Path folder;
    private final Logger logger;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-Log");
        thread.setDaemon(true);
        return thread;
    });
    private volatile int keepDays = MAX_DAYS;

    public EventLog(Path folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    /** Reads the kept days (call once on start, any thread). */
    public void load(int days) {
        keepDays = Math.clamp(days, 1, MAX_DAYS);
        prune();
        List<Entry> loaded = new ArrayList<>();
        try (Stream<Path> files = Files.isDirectory(folder) ? Files.list(folder) : Stream.empty()) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("events-\\d{4}-\\d{2}-\\d{2}\\.log")).sorted().toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    try {
                        Entry e = GSON.fromJson(line, Entry.class);
                        if (e != null && e.source() != null) {
                            loaded.add(e);
                        }
                    } catch (RuntimeException ignored) {
                        // a broken line (e.g. cut off by a crash) is skipped
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Could not read the log: " + e.getMessage());
        }
        synchronized (entries) {
            entries.clear();
            loaded.stream().skip(Math.max(0, loaded.size() - MAX_ENTRIES)).forEach(entries::addLast);
        }
    }

    public void keepDays(int days) {
        keepDays = Math.clamp(days, 1, MAX_DAYS);
    }

    /** Adds an event (any thread). Texts longer than 4000 characters are cut. */
    public void add(String source, String event, String text, int color) {
        String t = text == null ? "" : text.length() > 4000 ? text.substring(0, 4000) + " …" : text;
        Entry entry = new Entry(System.currentTimeMillis(), source, event == null ? "" : event, t, color & 0xFFFFFF);
        synchronized (entries) {
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        writer.execute(() -> write(entry));
    }

    private void write(Entry entry) {
        try {
            Files.createDirectories(folder);
            Path file = folder.resolve("events-" + LocalDate.now(ZoneId.systemDefault()) + ".log");
            Files.writeString(file, GSON.toJson(entry) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Could not write the log: " + e.getMessage());
        }
    }

    /** Deletes files and entries older than the kept days (any thread). */
    public void prune() {
        LocalDate oldest = LocalDate.now(ZoneId.systemDefault()).minusDays(keepDays - 1L);
        long cutoff = oldest.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        synchronized (entries) {
            while (!entries.isEmpty() && entries.peekFirst().time() < cutoff) {
                entries.removeFirst();
            }
        }
        try (Stream<Path> files = Files.isDirectory(folder) ? Files.list(folder) : Stream.empty()) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("events-\\d{4}-\\d{2}-\\d{2}\\.log") && LocalDate.parse(name.substring(7, 17)).isBefore(oldest)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.warning("Could not clean up the log: " + e.getMessage());
        }
    }

    /**
     * Entries that match, newest first.
     *
     * @param before only older than this (0 = all), for loading more
     */
    public List<Entry> find(String source, String query, long since, long before, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Predicate<Entry> match = e -> (source == null || source.isEmpty() || source.equals(e.source()))
                && e.time() >= since && (before <= 0 || e.time() < before)
                && (q.isEmpty() || e.text().toLowerCase(Locale.ROOT).contains(q) || e.event().toLowerCase(Locale.ROOT).contains(q));
        List<Entry> result = new ArrayList<>();
        synchronized (entries) {
            var it = entries.descendingIterator();
            while (it.hasNext() && result.size() < limit) {
                Entry e = it.next();
                if (match.test(e)) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    /** The sources that have entries. */
    public List<String> sources() {
        synchronized (entries) {
            return entries.stream().map(Entry::source).distinct().sorted().toList();
        }
    }

    public void shutdown() {
        writer.shutdown();
    }
}
