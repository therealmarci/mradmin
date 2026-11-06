package at.marcelr.mradmin.starter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * {@code mrserver run}: starts Paper, passes console input on and starts it again when MRadmin asks for a restart or
 * the server crashed or hangs. Before each start it installs a new STABLE Paper build of the same Minecraft version
 * (if {@code server.paper-update: auto}); if the new build does not come up, the old one is used again. No Bukkit
 * types - this runs before Paper.
 */
public final class Supervisor {

    /** What MRadmin writes to launch.properties, with the defaults for a server that has not run MRadmin yet. */
    public record Launch(String memoryMin, String memoryMax, List<String> flags, String paperUpdate, String minecraft,
                         int paperBuild, int crashMax, int crashWindowMinutes, int hangSeconds, int stopTimeoutSeconds) {

        static Launch read(StarterFiles files) {
            Properties p = StarterFiles.read(files.launch());
            String min = JavaFlags.memory(p.getProperty("memory-min"));
            String max = JavaFlags.memory(p.getProperty("memory-max"));
            String update = p.getProperty("paper-update", "auto").trim().toLowerCase(Locale.ROOT);
            return new Launch(min == null ? "1G" : min, max == null ? "4G" : max,
                    JavaFlags.allowed(JavaFlags.split(p.getProperty("java-flags", ""))),
                    update.matches("off|notify|auto") ? update : "auto", p.getProperty("minecraft", "").trim(),
                    integer(p, "paper-build", -1), Math.clamp(integer(p, "crash-max", 3), 1, 100),
                    Math.clamp(integer(p, "crash-window-minutes", 15), 1, 1440),
                    Math.clamp(integer(p, "hang-seconds", 180), 0, 3600),
                    Math.clamp(integer(p, "stop-timeout-seconds", 180), 30, 3600));
        }
    }

    /** Lines in the server log that mean a crash, even if Paper still shut down by itself afterwards. */
    static final Pattern CRASH_LINE = Pattern.compile(
            "(?i)this crash report has been saved to|minecraft crash report ----|encountered an unexpected exception"
                    + "|the server has stopped responding|failed to start the minecraft server");
    /** A new Paper build counts as working once MRadmin ticks or the server ran this long. */
    private static final long PROVEN_AFTER_MS = 5 * 60_000L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StarterFiles files;
    private final Deque<Long> crashes = new ArrayDeque<>();
    private volatile Process process;
    private volatile boolean stopping;
    private final Object stdinLock = new Object();

    public Supervisor(StarterFiles files) {
        this.files = files;
    }

    public int run() throws IOException, InterruptedException {
        Files.createDirectories(files.dir());
        try (RandomAccessFile raf = new RandomAccessFile(files.lock().toFile(), "rw");
             FileChannel channel = raf.getChannel()) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                say("The starter is already running for this server.");
                return 1;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "MRadmin starter shutdown"));
            startInputThreads();
            return loop();
        }
    }

    private int loop() throws IOException, InterruptedException {
        boolean wrapped = System.getenv("MRSERVER_WRAPPER") != null;
        while (!stopping) {
            if (wrapped && Files.isRegularFile(files.starterJarNew())) {
                say("A new starter version is there - restarting the starter.");
                return StarterFiles.EXIT_UPDATE_STARTER;
            }
            LocalSettings local = LocalSettings.load(files);
            Launch launch = Launch.read(files);
            Path jar = files.server().resolve(local.jar());
            boolean updated = updatePaper(launch, jar);
            if (!Files.isRegularFile(jar)) {
                say("ERROR: " + jar + " not found. Run: mrserver setup");
                state("stopped", null);
                return 1;
            }
            Files.deleteIfExists(files.action());
            Files.deleteIfExists(files.heartbeat());

            List<String> command = new ArrayList<>();
            command.add(local.java());
            command.add("-Xms" + launch.memoryMin());
            command.add("-Xmx" + launch.memoryMax());
            command.addAll(launch.flags());
            command.addAll(JavaFlags.split(local.extraFlags()));
            command.add("-jar");
            command.add(jar.toString());
            command.add("--nogui");
            ProcessBuilder builder = new ProcessBuilder(command).directory(files.server().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
            builder.environment().put(StarterFiles.ENV, files.server().toString());
            builder.environment().put("MRADMIN_STARTER_MODE", local.mode());
            long started = System.currentTimeMillis();
            say("Starting the server (" + local.jar() + ", " + launch.memoryMin() + "-" + launch.memoryMax() + ").");
            process = builder.start();
            state("running", process);

            String outcome = watch(process, launch, started);
            int exit = process.waitFor();
            process = null;
            String action = StarterFiles.readText(files.action());
            // MRadmin always writes the action when it shuts down. Without it (MRadmin missing or not loaded) a
            // clean "Stopping server" in the log counts as a stop.
            boolean crashed = outcome != null || exit != 0 || crashedInLog(started)
                    || (action == null && !stoppedInLog(started));
            if (stopping) {
                state("stopped", null);
                say("The server is down (starter stopped).");
                return 0;
            }
            if (!crashed && StarterFiles.ACTION_RESTART.equals(action)) {
                say("Restart requested - starting again.");
                continue;
            }
            if (!crashed) {
                state("stopped", null);
                say("The server was stopped.");
                return 0;
            }

            String reason = outcome != null ? outcome : action == null ? "exit code " + exit : "crash in the log";
            boolean proven = Files.isRegularFile(files.heartbeat())
                    || System.currentTimeMillis() - started >= PROVEN_AFTER_MS;
            if (updated && !proven && Files.isRegularFile(files.paperPrevious())) {
                rollBack(jar);
            }
            files.addEvent("crash", reason);
            long now = System.currentTimeMillis();
            crashes.addLast(now);
            while (!crashes.isEmpty() && crashes.peekFirst() < now - launch.crashWindowMinutes() * 60_000L) {
                crashes.removeFirst();
            }
            if (crashes.size() >= launch.crashMax()) {
                files.addEvent("gave-up", crashes.size() + " " + launch.crashWindowMinutes());
                state("gave-up", null);
                say("The server crashed " + crashes.size() + " times within " + launch.crashWindowMinutes()
                        + " minutes - giving up. Check it, then: mrserver start");
                return StarterFiles.EXIT_GAVE_UP;
            }
            state("waiting", null);
            say("The server crashed (" + reason + ") - starting again in 10 seconds.");
            Thread.sleep(10_000L);
        }
        return 0;
    }

    /**
     * Waits while the server runs. Kills it if it hangs (no heartbeat from MRadmin) or does not finish stopping.
     *
     * @return null if the server ended by itself, otherwise why it was killed
     */
    private String watch(Process p, Launch launch, long started) throws InterruptedException {
        long actionSeen = 0;
        while (p.isAlive()) {
            Thread.sleep(1000L);
            long now = System.currentTimeMillis();
            if (actionSeen == 0 && Files.isRegularFile(files.action())) {
                actionSeen = now;
            }
            if (actionSeen > 0 || stopping) {
                long since = actionSeen > 0 ? actionSeen : now;
                if (now - since > launch.stopTimeoutSeconds() * 1000L) {
                    say("The server did not stop within " + launch.stopTimeoutSeconds() + " seconds - ending it.");
                    kill(p);
                    files.addEvent("stop-timeout", String.valueOf(launch.stopTimeoutSeconds()));
                    return stopping ? null : "did not stop";
                }
                continue;
            }
            if (launch.hangSeconds() > 0) {
                long beat = beat();
                if (beat > 0 && beat >= started && now - beat > launch.hangSeconds() * 1000L) {
                    say("The server has not ticked for " + (now - beat) / 1000 + " seconds - ending it.");
                    files.addEvent("hang", String.valueOf((now - beat) / 1000));
                    kill(p);
                    return "hang";
                }
            }
        }
        return null;
    }

    private long beat() {
        String text = StarterFiles.readText(files.heartbeat());
        try {
            return text == null ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void kill(Process p) throws InterruptedException {
        p.destroy();
        if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private boolean stoppedInLog(long started) {
        return tail(started).stream().anyMatch(l -> l.contains("Stopping server"));
    }

    private List<String> tail(long started) {
        Path log = files.server().resolve("logs").resolve("latest.log");
        try {
            if (!Files.isRegularFile(log) || log.toFile().lastModified() < started) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            return lines.subList(Math.max(0, lines.size() - 200), lines.size());
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** A crash report written since the start, or a crash line at the end of the server log. */
    private boolean crashedInLog(long started) {
        Path reports = files.server().resolve("crash-reports");
        try (var list = Files.isDirectory(reports) ? Files.list(reports) : java.util.stream.Stream.<Path>empty()) {
            if (list.anyMatch(f -> f.toFile().lastModified() >= started)) {
                return true;
            }
        } catch (IOException ignored) {
            // Then only the log counts.
        }
        return tail(started).stream().anyMatch(Supervisor::crashLine);
    }

    /** A crash line of Paper or Minecraft - not of a plugin (MRadmin itself reports earlier crashes in the log). */
    static boolean crashLine(String line) {
        return CRASH_LINE.matcher(line).find() && !line.matches(".*\\]: \\[[A-Za-z0-9_.-]+\\] .*");
    }

    // ------------------------------------------------------------------ Paper updates

    /** @return true if a new build was put in place just now */
    private boolean updatePaper(Launch launch, Path jar) {
        if (!launch.paperUpdate().equals("auto") || launch.minecraft().isEmpty() || !Files.isRegularFile(jar)) {
            return false;
        }
        Properties state = StarterFiles.read(files.state());
        int current = Math.max(launch.paperBuild(), integer(state, "paper-build", -1));
        int skip = integer(state, "paper-skip-build", -1);
        try {
            Paper.Build build = new Paper(Main.userAgent()).latestStable(launch.minecraft());
            if (build == null || build.id() <= current || build.id() == skip) {
                return false;
            }
            Path download = jar.resolveSibling(jar.getFileName() + ".new");
            say("Downloading Paper " + build.minecraft() + " build " + build.id() + " ...");
            new Paper(Main.userAgent()).download(build, download);
            Files.copy(jar, files.paperPrevious(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(download, jar, StandardCopyOption.REPLACE_EXISTING);
            files.addEvent("paper-update", (current < 0 ? "?" : current) + " " + build.id());
            putState("paper-build", String.valueOf(build.id()));
            putState("paper-previous-build", String.valueOf(current));
            say("Paper updated" + (current < 0 ? "" : " from build " + current) + " to build " + build.id() + ".");
            return true;
        } catch (IOException | RuntimeException e) {
            say("Could not update Paper: " + e.getMessage() + " - starting the installed build.");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void rollBack(Path jar) {
        try {
            Properties state = StarterFiles.read(files.state());
            String bad = state.getProperty("paper-build", "?");
            String old = state.getProperty("paper-previous-build", "?");
            Files.copy(files.paperPrevious(), jar, StandardCopyOption.REPLACE_EXISTING);
            putState("paper-skip-build", bad);
            putState("paper-build", old);
            files.addEvent("paper-rollback", bad + " " + old);
            say("The new Paper build " + bad + " did not start - using build " + old + " again.");
        } catch (IOException e) {
            say("Could not go back to the previous Paper build: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ console input

    private void startInputThreads() {
        Thread stdin = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    send(line);
                }
            } catch (IOException ignored) {
                // No terminal (systemd) - commands come through console.in.
            }
        }, "MRadmin starter stdin");
        stdin.setDaemon(true);
        stdin.start();

        Thread file = new Thread(() -> {
            Path reading = files.consoleIn().resolveSibling("console.in.reading");
            while (true) {
                try {
                    Thread.sleep(300L);
                    if (Files.isRegularFile(files.consoleIn()) && Files.size(files.consoleIn()) > 0) {
                        Files.move(files.consoleIn(), reading, StandardCopyOption.REPLACE_EXISTING);
                        for (String line : Files.readAllLines(reading, StandardCharsets.UTF_8)) {
                            if (!line.isBlank()) {
                                send(line);
                            }
                        }
                        Files.deleteIfExists(reading);
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (IOException ignored) {
                    // Next round.
                }
            }
        }, "MRadmin starter console.in");
        file.setDaemon(true);
        file.start();
    }

    private void send(String line) {
        Process p = process;
        if (p == null || !p.isAlive()) {
            return;
        }
        synchronized (stdinLock) {
            try {
                OutputStream out = p.getOutputStream();
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // The server is going down.
            }
        }
    }

    /** Ctrl+C, systemd stop, logout: stop the server properly, then end. */
    private void shutdown() {
        stopping = true;
        Process p = process;
        if (p == null || !p.isAlive()) {
            return;
        }
        say("Stopping the server ...");
        try {
            if (!Files.isRegularFile(files.action())) {
                StarterFiles.writeText(files.action(), StarterFiles.ACTION_STOP);
            }
        } catch (IOException ignored) {
            // The stop command is enough.
        }
        send("stop");
        try {
            int timeout = Launch.read(files).stopTimeoutSeconds();
            if (!p.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS)) {
                kill(p);
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
        }
        state("stopped", null);
    }

    // ------------------------------------------------------------------ state

    private void state(String state, Process server) {
        Properties p = StarterFiles.read(files.state());
        p.setProperty("state", state);
        p.setProperty("since", String.valueOf(System.currentTimeMillis()));
        p.setProperty("starter-pid", String.valueOf(ProcessHandle.current().pid()));
        if (server != null) {
            p.setProperty("server-pid", String.valueOf(server.pid()));
        } else {
            p.remove("server-pid");
        }
        try {
            StarterFiles.write(files.state(), p, "Written by the MRadmin starter - do not edit.");
        } catch (IOException ignored) {
            // Only for status display.
        }
    }

    private void putState(String key, String value) throws IOException {
        Properties p = StarterFiles.read(files.state());
        p.setProperty(key, value);
        StarterFiles.write(files.state(), p, "Written by the MRadmin starter - do not edit.");
    }

    static int integer(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    void say(String text) {
        String line = "[MRadmin starter] " + text;
        System.out.println(line);
        try {
            StarterFiles.appendLine(files.dir().resolve("starter.log"), LocalDateTime.now().format(TIME) + " " + text);
        } catch (IOException ignored) {
            // Console output is enough.
        }
    }
}
