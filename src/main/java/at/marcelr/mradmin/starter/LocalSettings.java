package at.marcelr.mradmin.starter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * {@code mrserver.properties} in the server folder: what only the owner on the machine itself decides - which Java,
 * which Paper JAR, how the starter runs (mode) and its name. The web interface cannot change these.
 *
 * @param mode       screen (Linux, screen session), systemd (Linux, user service), window (Windows, own console
 *                   window) or foreground (in the current terminal, e.g. for Docker or a panel)
 * @param name       screen session, systemd service or Windows task name
 * @param extraFlags Java options without checks (only the owner can set them here)
 */
public record LocalSettings(String java, String jar, String mode, String name, String extraFlags) {

    public static final String COMMENT = """
            Settings of the MRadmin starter on this machine - see the MRadmin wiki, page "Starter".
            They cannot be changed from the web interface. Memory, Java options and Paper updates are in
            plugins/MRadmin/config.yml (section server).
              java        Java to start the server with
              jar         the Paper JAR in this folder
              mode        screen | systemd (Linux), window (Windows), foreground (current terminal, Docker, panels)
              name        name of the screen session / systemd service / Windows task
              java-flags  extra Java options (not checked - only set them here on the machine)
            After a change of mode or name: mrserver uninstall, then mrserver install.""";

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The Java this program runs on - Paper gets the same one unless set otherwise. */
    public static String currentJava() {
        return ProcessHandle.current().info().command().orElse(windows() ? "java.exe" : "java");
    }

    public static String defaultMode() {
        if (windows()) {
            return "window";
        }
        if (onPath("systemctl") && Files.isDirectory(Path.of("/run/systemd/system"))) {
            return "systemd";
        }
        return onPath("screen") ? "screen" : "foreground";
    }

    public static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, program)) || Files.isExecutable(Path.of(dir, program + ".exe"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plain "key=value" lines, no escaping: the wrapper scripts read the file too, and people write Windows paths
     * like C:\Program Files\... as they are.
     */
    static Properties readPlain(Path file) {
        Properties p = new Properties();
        String text = StarterFiles.readRaw(file);
        if (text != null) {
            for (String line : text.lines().toList()) {
                String l = line.strip();
                int eq = l.indexOf('=');
                if (l.isEmpty() || l.startsWith("#") || eq <= 0) {
                    continue;
                }
                p.setProperty(l.substring(0, eq).strip(), l.substring(eq + 1).strip());
            }
        }
        return p;
    }

    public static LocalSettings load(StarterFiles files) {
        Properties p = readPlain(files.local());
        String mode = p.getProperty("mode", defaultMode()).trim().toLowerCase(Locale.ROOT);
        if (!mode.matches("screen|systemd|window|foreground")) {
            mode = defaultMode();
        }
        String name = p.getProperty("name", "").trim();
        if (!name.matches("[A-Za-z0-9_.-]{1,40}")) {
            name = defaultName(files.server());
        }
        String jar = p.getProperty("jar", "").trim();
        if (jar.isEmpty() || jar.contains("/") || jar.contains("\\")) {
            jar = findPaper(files.server());
        }
        String java = p.getProperty("java", "").trim();
        return new LocalSettings(java.isEmpty() ? currentJava() : java, jar, mode, name,
                p.getProperty("java-flags", "").trim());
    }

    public void save(StarterFiles files) throws IOException {
        StringBuilder out = new StringBuilder();
        COMMENT.lines().forEach(l -> out.append("# ").append(l).append('\n'));
        out.append("java=").append(java).append('\n');
        out.append("jar=").append(jar).append('\n');
        out.append("mode=").append(mode).append('\n');
        out.append("name=").append(name).append('\n');
        out.append("java-flags=").append(extraFlags).append('\n');
        String text = out.toString();
        StarterFiles.writeText(files.local(), LocalSettings.windows() ? text.replace("\n", "\r\n") : text);
    }

    public LocalSettings withMode(String mode) {
        return new LocalSettings(java, jar, mode, name, extraFlags);
    }

    /** "minecraft" plus the folder name, e.g. "minecraft-server". */
    public static String defaultName(Path server) {
        String folder = server.getFileName() == null ? "server" : server.getFileName().toString();
        String clean = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "-").replaceAll("^-+|-+$", "");
        String name = "minecraft-" + (clean.isEmpty() ? "server" : clean);
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    /** The Paper JAR in the folder: paper.jar, else paper-*.jar, else "paper.jar" (to be downloaded). */
    public static String findPaper(Path server) {
        if (Files.isRegularFile(server.resolve("paper.jar"))) {
            return "paper.jar";
        }
        try (var list = Files.list(server)) {
            return list.map(f -> f.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).matches("paper.*\\.jar"))
                    .sorted().reduce((a, b) -> b).orElse("paper.jar");
        } catch (IOException e) {
            return "paper.jar";
        }
    }
}
