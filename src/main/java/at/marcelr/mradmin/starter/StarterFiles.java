package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/**
 * How the starter and MRadmin talk to each other: small files in {@code plugins/MRadmin/starter/} of the server
 * folder. No Bukkit types - the starter uses this class too.
 *
 * <ul>
 *   <li>{@code launch.properties} - MRadmin writes: memory, Java options, Paper update mode, crash rules</li>
 *   <li>{@code action} - MRadmin (or {@code mrserver stop}) writes before the server goes down: restart | stop</li>
 *   <li>{@code request} - {@code mrserver stop/restart/cancel} asks MRadmin for a countdown</li>
 *   <li>{@code heartbeat} - MRadmin writes every few seconds while the server ticks</li>
 *   <li>{@code console.in} - {@code mrserver console} appends commands, the starter passes them on</li>
 *   <li>{@code state.properties} - the starter writes: state, process id, Paper build</li>
 *   <li>{@code events.log} - the starter appends what happened (crash, hang, Paper update); MRadmin reports it</li>
 * </ul>
 */
public final class StarterFiles {

    /** Set by the starter for the server process - MRadmin sees from it that something brings the server back. */
    public static final String ENV = "MRADMIN_STARTER";
    /** The starter process asks its wrapper to swap in a new starter JAR and run it again. */
    public static final int EXIT_UPDATE_STARTER = 3;
    /** The starter gave up after too many crashes - a service manager must not start it again. */
    public static final int EXIT_GAVE_UP = 4;

    public static final String ACTION_RESTART = "restart";
    public static final String ACTION_STOP = "stop";

    private final Path server;
    private final Path dir;

    public StarterFiles(Path server) {
        this.server = server.toAbsolutePath().normalize();
        this.dir = this.server.resolve("plugins").resolve("MRadmin").resolve("starter");
    }

    public Path server() {
        return server;
    }

    public Path dir() {
        return dir;
    }

    public Path launch() {
        return dir.resolve("launch.properties");
    }

    public Path action() {
        return dir.resolve("action");
    }

    public Path request() {
        return dir.resolve("request");
    }

    public Path heartbeat() {
        return dir.resolve("heartbeat");
    }

    public Path consoleIn() {
        return dir.resolve("console.in");
    }

    public Path state() {
        return dir.resolve("state.properties");
    }

    public Path events() {
        return dir.resolve("events.log");
    }

    public Path lock() {
        return dir.resolve("starter.lock");
    }

    /** The starter itself: a copy of the MRadmin JAR (the original may be replaced by an update meanwhile). */
    public Path starterJar() {
        return dir.resolve("starter.jar");
    }

    public Path starterJarNew() {
        return dir.resolve("starter-new.jar");
    }

    /** The Paper JAR before the last update - used again if the new build does not start. */
    public Path paperPrevious() {
        return dir.resolve("paper-previous.jar");
    }

    /** Settings only the server owner changes on the machine itself (Java, mode) - not from the web. */
    public Path local() {
        return server.resolve("mrserver.properties");
    }

    // ------------------------------------------------------------------ helpers

    public static Properties read(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException ignored) {
                // Broken or half written - treated like missing.
            }
        }
        return p;
    }

    /** Writes sorted, without the date line, atomically (a reader never sees half a file). */
    public static void write(Path file, Properties p, String comment) throws IOException {
        StringBuilder out = new StringBuilder();
        if (comment != null) {
            for (String line : comment.split("\n")) {
                out.append("# ").append(line).append('\n');
            }
        }
        for (var e : new TreeMap<>(p).entrySet()) {
            Properties one = new Properties();
            one.setProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            StringWriter w = new StringWriter();
            one.store(w, null);
            w.toString().lines().filter(l -> !l.startsWith("#")).forEach(l -> out.append(l).append('\n'));
        }
        writeText(file, out.toString());
    }

    public static void writeText(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String readText(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** The file as it is (not trimmed), null if missing. */
    public static String readRaw(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void appendLine(Path file, String line) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, line.replace('\n', ' ') + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** One line of events.log: time, kind, details. */
    public record Event(long time, String kind, String detail) {

        public String line() {
            return time + " " + kind + (detail == null || detail.isBlank() ? "" : " " + detail);
        }

        public static Event parse(String line) {
            String[] parts = line.trim().split(" ", 3);
            if (parts.length < 2) {
                return null;
            }
            try {
                return new Event(Long.parseLong(parts[0]), parts[1], parts.length > 2 ? parts[2] : "");
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public List<Event> readEvents() {
        List<Event> list = new ArrayList<>();
        try {
            if (Files.isRegularFile(events())) {
                for (String line : Files.readAllLines(events(), StandardCharsets.UTF_8)) {
                    Event e = Event.parse(line);
                    if (e != null) {
                        list.add(e);
                    }
                }
            }
        } catch (IOException ignored) {
            // Nothing to report then.
        }
        return list;
    }

    public void addEvent(String kind, String detail) {
        try {
            appendLine(events(), new Event(System.currentTimeMillis(), kind, detail).line());
        } catch (IOException ignored) {
            // Only a report - the starter keeps going.
        }
    }
}
