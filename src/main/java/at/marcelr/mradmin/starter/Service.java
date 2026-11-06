package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the starter in the background and sets up the autostart, depending on {@code mode} in mrserver.properties:
 * screen (session + {@code @reboot} line in the crontab), systemd (user service), window (Windows: own console window,
 * autostart as task at logon), foreground (nothing in the background - for Docker and panels).
 */
final class Service {

    private Service() {
    }

    static int start(StarterFiles files) throws IOException, InterruptedException {
        if (Main.supervisorRunning(files)) {
            System.out.println("The server is already running.");
            return 0;
        }
        LocalSettings local = LocalSettings.load(files);
        Path sh = files.server().resolve("mrserver.sh");
        Path cmd = files.server().resolve("mrserver.cmd");
        return switch (local.mode()) {
            case "screen" -> {
                if (screenRunning(local.name())) {
                    System.out.println("The screen session " + local.name() + " already exists - mrserver attach.");
                    yield 1;
                }
                int code = exec(List.of("screen", "-dmS", local.name(), "sh", sh.toString(), "run"));
                System.out.println(code == 0 ? "Started in the screen session " + local.name() + " (mrserver attach)."
                        : "Could not start screen.");
                yield code;
            }
            case "systemd" -> {
                if (!Files.isRegularFile(unitFile(local))) {
                    System.out.println("The service is not installed yet - run: mrserver install");
                    yield 1;
                }
                int code = exec(List.of("systemctl", "--user", "start", local.name() + ".service"));
                System.out.println(code == 0 ? "Started the service " + local.name() + " (mrserver attach shows the log)."
                        : "Could not start the service - systemctl --user status " + local.name());
                yield code;
            }
            case "window" -> {
                int code = exec(List.of("cmd", "/c", "start", "\"" + local.name() + "\"", "/D", files.server().toString(),
                        cmd.toString(), "run"));
                System.out.println(code == 0 ? "Started in its own window." : "Could not open the window.");
                yield code;
            }
            default -> {
                System.out.println("Mode foreground: run the server in this terminal with  mrserver run");
                yield 1;
            }
        };
    }

    static int attach(StarterFiles files) throws IOException, InterruptedException {
        LocalSettings local = LocalSettings.load(files);
        return switch (local.mode()) {
            case "screen" -> {
                System.out.println("Leave the console with Ctrl+A, then D (the server keeps running).");
                yield inherit(List.of("screen", "-r", local.name()));
            }
            case "systemd" -> {
                System.out.println("Server output (Ctrl+C ends the view, not the server). Commands: mrserver console <command>");
                yield inherit(List.of("journalctl", "--user", "-u", local.name() + ".service", "-f", "-n", "50"));
            }
            default -> {
                System.out.println("The console is in the server's own window or terminal. Commands: mrserver console <command>");
                yield 0;
            }
        };
    }

    static int install(StarterFiles files) throws IOException, InterruptedException {
        LocalSettings local = LocalSettings.load(files);
        Path sh = files.server().resolve("mrserver.sh");
        switch (local.mode()) {
            case "systemd" -> {
                Path unit = unitFile(local);
                Files.createDirectories(unit.getParent());
                Files.writeString(unit, unit(files, local, sh), StandardCharsets.UTF_8);
                exec(List.of("systemctl", "--user", "daemon-reload"));
                int code = exec(List.of("systemctl", "--user", "enable", local.name() + ".service"));
                if (code != 0) {
                    System.out.println("Could not enable the service (systemctl --user). Is systemd running for your user?");
                    return code;
                }
                // Without "linger" the user's services only run while the user is logged in.
                String user = System.getProperty("user.name");
                if (exec(List.of("loginctl", "enable-linger", user)) != 0) {
                    System.out.println("Note: run once as root:  loginctl enable-linger " + user
                            + "  - otherwise the server stops when you log out.");
                }
                System.out.println("Autostart is on (systemd user service " + local.name() + "). Start now: mrserver start");
            }
            case "screen" -> {
                String line = "@reboot sh '" + sh + "' start >/dev/null 2>&1 " + cronMark(local);
                List<String> lines = crontab();
                lines.removeIf(l -> l.endsWith(cronMark(local)));
                lines.add(line);
                if (writeCrontab(lines) != 0) {
                    System.out.println("Could not write the crontab.");
                    return 1;
                }
                System.out.println("Autostart is on (crontab @reboot, screen session " + local.name() + "). Start now: mrserver start");
            }
            case "window" -> {
                Path cmd = files.server().resolve("mrserver.cmd");
                int code = exec(List.of("schtasks", "/Create", "/F", "/TN", local.name(), "/SC", "ONLOGON",
                        "/TR", (cmd.toString().contains(" ") ? "\"" + cmd + "\"" : cmd.toString()) + " start"));
                System.out.println(code == 0 ? "Autostart is on (Windows task " + local.name() + " at logon). Start now: mrserver start"
                        : "Could not create the Windows task.");
                return code;
            }
            default -> {
                System.out.println("Mode foreground has no autostart - your panel or container starts  mrserver run.");
                return 1;
            }
        }
        return 0;
    }

    static int uninstall(StarterFiles files) throws IOException, InterruptedException {
        LocalSettings local = LocalSettings.load(files);
        switch (local.mode()) {
            case "systemd" -> {
                exec(List.of("systemctl", "--user", "disable", local.name() + ".service"));
                Files.deleteIfExists(unitFile(local));
                exec(List.of("systemctl", "--user", "daemon-reload"));
            }
            case "screen" -> {
                List<String> lines = crontab();
                lines.removeIf(l -> l.endsWith(cronMark(local)));
                writeCrontab(lines);
            }
            case "window" -> exec(List.of("schtasks", "/Delete", "/F", "/TN", local.name()));
            default -> {
                // Nothing was installed.
            }
        }
        System.out.println("Autostart is off. A running server keeps running.");
        return 0;
    }

    // ------------------------------------------------------------------ systemd

    static Path unitFile(LocalSettings local) {
        return Path.of(System.getProperty("user.home"), ".config", "systemd", "user", local.name() + ".service");
    }

    static String unit(StarterFiles files, LocalSettings local, Path sh) {
        return """
                # Written by the MRadmin starter (mrserver install). Remove with: mrserver uninstall
                [Unit]
                Description=Minecraft server %s (MRadmin starter)
                After=network-online.target
                Wants=network-online.target

                [Service]
                Type=simple
                WorkingDirectory=%s
                ExecStart=/bin/sh %s run
                # Only the starter gets the stop signal - it stops the server properly.
                KillMode=mixed
                TimeoutStopSec=300
                # The starter restarts the server itself; systemd only steps in if the starter itself fails.
                Restart=on-failure
                RestartSec=15
                # 4 = gave up after too many crashes: stay down until someone looks.
                RestartPreventExitStatus=4

                [Install]
                WantedBy=default.target
                """.formatted(local.name(), quote(files.server()), quote(sh));
    }

    private static String quote(Path path) {
        String s = path.toString();
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    // ------------------------------------------------------------------ screen + crontab

    private static boolean screenRunning(String name) throws InterruptedException {
        try {
            Process p = new ProcessBuilder("screen", "-ls").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out.lines().anyMatch(l -> l.matches("\\s*[0-9]+\\." + java.util.regex.Pattern.quote(name) + "\\s.*")
                    && !l.contains("Dead"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String cronMark(LocalSettings local) {
        return "# mrserver:" + local.name();
    }

    private static List<String> crontab() throws InterruptedException {
        try {
            Process p = new ProcessBuilder("crontab", "-l").redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return p.waitFor() == 0 ? new ArrayList<>(out.lines().toList()) : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static int writeCrontab(List<String> lines) throws InterruptedException {
        try {
            Process p = new ProcessBuilder("crontab", "-").redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            p.getOutputStream().write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            return p.waitFor();
        } catch (IOException e) {
            return 1;
        }
    }

    // ------------------------------------------------------------------ processes

    private static int exec(List<String> command) throws InterruptedException {
        try {
            return new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start().waitFor();
        } catch (IOException e) {
            System.out.println("Could not run " + command.getFirst() + ": " + e.getMessage());
            return 127;
        }
    }

    private static int inherit(List<String> command) throws InterruptedException {
        try {
            return new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (IOException e) {
            System.out.println("Could not run " + command.getFirst() + ": " + e.getMessage());
            return 127;
        }
    }
}
