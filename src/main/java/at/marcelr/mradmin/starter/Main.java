package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * The MRadmin starter: {@code java -jar MRadmin.jar <command>} - usually through {@code mrserver.sh} /
 * {@code mrserver.cmd} in the server folder. Starts Paper, brings it back after a restart or crash and keeps it up to
 * date. Countdowns, schedule and settings come from MRadmin (web interface, config.yml section {@code server}).
 * No Bukkit types - this runs without Paper. Console output English.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            code = 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            code = 130;
        }
        System.exit(code);
    }

    static int run(String[] args) throws IOException, InterruptedException {
        StarterFiles files = new StarterFiles(Path.of("").toAbsolutePath());
        String command = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> rest = Arrays.asList(args).subList(Math.min(1, args.length), args.length);
        return switch (command) {
            case "setup" -> Setup.fromCommandLine(files, rest);
            case "run" -> new Supervisor(files).run();
            case "start" -> Service.start(files);
            case "stop" -> Control.request(files, "stop", rest);
            case "restart" -> Control.request(files, "restart", rest);
            case "cancel" -> Control.request(files, "cancel", rest);
            case "status" -> status(files);
            case "console" -> Control.console(files, rest);
            case "attach", "log" -> Service.attach(files);
            case "install" -> Service.install(files);
            case "uninstall" -> Service.uninstall(files);
            default -> help();
        };
    }

    private static int help() {
        System.out.println("""
                MRadmin starter %s - starts your Paper server and brings it back after restarts and crashes.
                  mrserver setup [--accept-eula] [--mode screen|systemd|window|foreground]
                         [--channel stable|beta|dev] [--web] [--web-url <address>]
                         [--proxy here|docker|<address>] [--memory-min 2G] [--memory-max 4G]
                                              set up this folder (downloads Paper if missing); beta = newest
                                              MRadmin beta, dev = dev build (token in MRSERVER_DEV_TOKEN);
                                              --proxy: where the reverse proxy runs (this machine,
                                              Docker on it, or the address / range of another machine)
                  mrserver setup --role waitroom [--main-server main] [--main-address 10.0.0.5:25565]
                         [--link-url http://10.0.0.5:8126] [--memory-max 2G]
                                              set up a waiting room server (Paper + MRwaitroom instead of
                                              MRadmin); key of MRadmin waiting-room.link in MRSERVER_LINK_KEY
                  mrserver install            start the server automatically when the machine starts
                  mrserver uninstall          no autostart any more
                  mrserver start              start the server (in the background)
                  mrserver run                run the server in this terminal
                  mrserver stop [time|now] [reason]     stop with a countdown (e.g. 5m Hardware)
                  mrserver restart [time|now] [reason]  restart with a countdown
                  mrserver cancel             cancel a countdown
                  mrserver status             is the server running?
                  mrserver console <command>  send a command to the server console
                  mrserver attach             show the console (screen) or the log (systemd)
                Settings: mrserver.properties (this machine) and plugins/MRadmin/config.yml, section server.
                Guide: https://mrtechnology.marcelr.at/wiki/mradmin/starter/""".formatted(version()));
        return 0;
    }

    private static int status(StarterFiles files) {
        Properties state = StarterFiles.read(files.state());
        boolean running = supervisorRunning(files);
        LocalSettings local = LocalSettings.load(files);
        String s = running ? state.getProperty("state", "running") : "stopped";
        if (!running && "gave-up".equals(state.getProperty("state"))) {
            s = "gave up after crashes (check the server, then: mrserver start)";
        }
        System.out.println("Server:  " + s);
        System.out.println("Mode:    " + local.mode() + " (" + local.name() + ")");
        System.out.println("Paper:   " + local.jar() + ", build " + state.getProperty("paper-build", "?"));
        System.out.println("Folder:  " + files.server());
        return running ? 0 : 3;
    }

    /** Whether a starter holds the lock of this server folder. */
    static boolean supervisorRunning(StarterFiles files) {
        if (!Files.isRegularFile(files.lock())) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(files.lock().toFile(), "rw");
             FileChannel channel = raf.getChannel()) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
            return false;
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            return true;
        }
    }

    /** Version of this MRadmin JAR (build-info.properties), e.g. "1.3.0". */
    public static String version() {
        return buildInfo().getProperty("pluginVersion", "?");
    }

    /** Minecraft version this MRadmin JAR is made for, e.g. "26.3". */
    public static String minecraft() {
        return buildInfo().getProperty("mcVersion", "");
    }

    static Properties buildInfo() {
        Properties p = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
            // Unknown version then.
        }
        return p;
    }

    public static String userAgent() {
        return "MRadmin-starter/" + version() + " (+https://mrtechnology.marcelr.at)";
    }
}
