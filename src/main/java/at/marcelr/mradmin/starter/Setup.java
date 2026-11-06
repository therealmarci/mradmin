package at.marcelr.mradmin.starter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Sets up a server folder for the starter: wrapper scripts, mrserver.properties, the starter JAR and - on a fresh
 * folder - MRadmin and Paper. Works on a new and on an existing server; nothing that is there gets overwritten
 * except the wrappers and the starter JAR. Used by {@code mrserver setup} and {@code /mradmin starter setup}.
 */
public final class Setup {

    /** What the setup did (for the messages of the caller). */
    public record Result(LocalSettings local, boolean newLocal, boolean copiedPlugin) {
    }

    private Setup() {
    }

    /**
     * The part that also runs inside MRadmin: files only, no downloads.
     *
     * @param ownJar the MRadmin JAR this code runs from
     * @param mode   null = keep / default
     */
    public static Result prepare(StarterFiles files, Path ownJar, String mode) throws IOException {
        Files.createDirectories(files.dir());
        boolean copiedPlugin = false;
        Path plugins = files.server().resolve("plugins");
        if (!ownJar.toAbsolutePath().normalize().startsWith(plugins.toAbsolutePath().normalize()) && !hasMradmin(plugins)) {
            Files.createDirectories(plugins);
            Files.copy(ownJar, plugins.resolve(ownJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            copiedPlugin = true;
        }
        if (!ownJar.toAbsolutePath().normalize().equals(files.starterJar().toAbsolutePath().normalize())) {
            Path tmp = files.starterJar().resolveSibling("starter.jar.tmp");
            Files.copy(ownJar, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, files.starterJar(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Windows: a running starter holds starter.jar - swapped on its next restart.
                Files.move(tmp, files.starterJarNew(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        writeWrapper(files.server().resolve("mrserver.sh"), "/starter/mrserver.sh", true);
        writeWrapper(files.server().resolve("mrserver.cmd"), "/starter/mrserver.cmd", false);

        boolean newLocal = !Files.isRegularFile(files.local());
        LocalSettings local = LocalSettings.load(files);
        if (mode != null) {
            local = local.withMode(mode);
        }
        local.save(files);

        if (!Files.isRegularFile(files.launch())) {
            // Until MRadmin has run once: the defaults plus the Minecraft version for Paper updates.
            Properties launch = new Properties();
            launch.setProperty("minecraft", Main.minecraft());
            launch.setProperty("paper-update", "auto");
            StarterFiles.write(files.launch(), launch, "Written by MRadmin from config.yml (section server) - do not edit.");
        }
        return new Result(local, newLocal, copiedPlugin);
    }

    /**
     * After an MRadmin update (runs inside MRadmin): new wrappers, and the new JAR as starter-new.jar - the wrapper
     * swaps it in before the starter runs next time.
     *
     * @return true if the starter gets a new version
     */
    public static boolean refresh(StarterFiles files, Path ownJar) throws IOException {
        writeWrapper(files.server().resolve("mrserver.sh"), "/starter/mrserver.sh", true);
        // A running mrserver.cmd is read line by line while it runs - Windows would mix old and new. Only setup writes it.
        if (!LocalSettings.windows()) {
            writeWrapper(files.server().resolve("mrserver.cmd"), "/starter/mrserver.cmd", false);
        }
        if (Files.isRegularFile(files.starterJar()) && sameContent(ownJar, files.starterJar())) {
            Files.deleteIfExists(files.starterJarNew());
            return false;
        }
        if (Files.isRegularFile(files.starterJarNew()) && sameContent(ownJar, files.starterJarNew())) {
            return true;
        }
        Path tmp = files.starterJarNew().resolveSibling("starter-new.jar.tmp");
        Files.copy(ownJar, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, files.starterJarNew(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static boolean sameContent(Path a, Path b) throws IOException {
        return Files.size(a) == Files.size(b) && Files.mismatch(a, b) == -1;
    }

    private static boolean hasMradmin(Path plugins) throws IOException {
        if (!Files.isDirectory(plugins)) {
            return false;
        }
        try (var list = Files.list(plugins)) {
            return list.anyMatch(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches("mradmin.*\\.jar"));
        }
    }

    private static void writeWrapper(Path target, String resource, boolean executable) throws IOException {
        try (InputStream in = Setup.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing " + resource + " in the JAR");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Shell needs LF, cmd needs CRLF - whatever the JAR was built with.
            text = text.replace("\r\n", "\n");
            if (!executable) {
                text = text.replace("\n", "\r\n");
            }
            if (text.equals(StarterFiles.readRaw(target))) {
                return;
            }
            StarterFiles.writeText(target, text);
        }
        if (executable) {
            target.toFile().setExecutable(true, false);
        }
    }

    /** The JAR this class was loaded from. */
    public static Path ownJar() throws IOException {
        try {
            return Path.of(Setup.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | SecurityException e) {
            throw new IOException("Cannot find the own JAR", e);
        }
    }

    // ------------------------------------------------------------------ mrserver setup

    static int fromCommandLine(StarterFiles files, List<String> args) throws IOException, InterruptedException {
        boolean acceptEula = args.contains("--accept-eula");
        boolean downloaded = args.contains("--downloaded");
        String mode = null;
        int i = args.indexOf("--mode");
        if (i >= 0 && i + 1 < args.size()) {
            mode = args.get(i + 1).toLowerCase(Locale.ROOT);
            if (!mode.matches("screen|systemd|window|foreground")) {
                System.out.println("Unknown mode '" + mode + "' - screen, systemd, window or foreground.");
                return 2;
            }
        }
        Path own = ownJar();
        if (downloaded && !verifyDownload(own)) {
            Files.deleteIfExists(own);
            return 1;
        }
        System.out.println("Setting up the MRadmin starter in " + files.server());
        Result result = prepare(files, own, mode);
        if (result.copiedPlugin()) {
            System.out.println("  MRadmin copied to plugins/" + own.getFileName());
        }
        System.out.println("  mrserver.sh / mrserver.cmd written, mode: " + result.local().mode()
                + (result.newLocal() ? " (change it in mrserver.properties)" : ""));

        Path jar = files.server().resolve(result.local().jar());
        if (!Files.isRegularFile(jar)) {
            String mc = Main.minecraft();
            Paper paper = new Paper(Main.userAgent());
            Paper.Build build = paper.latestStable(mc);
            if (build == null) {
                System.out.println("ERROR: PaperMC has no stable build for Minecraft " + mc + " yet.");
                return 1;
            }
            System.out.println("  Downloading Paper " + mc + " build " + build.id() + " ...");
            paper.download(build, jar);
            Properties state = StarterFiles.read(files.state());
            state.setProperty("paper-build", String.valueOf(build.id()));
            StarterFiles.write(files.state(), state, "Written by the MRadmin starter - do not edit.");
            System.out.println("  Paper saved as " + jar.getFileName() + " (checksum ok).");
        }

        if (!eula(files.server().resolve("eula.txt"), acceptEula)) {
            System.out.println("Setup done, but the server only starts after you accept the Minecraft EULA:");
            System.out.println("  https://aka.ms/MinecraftEULA  ->  mrserver setup --accept-eula  (or eula=true in eula.txt)");
            return 0;
        }
        System.out.println("Done. Next:");
        if (!result.local().mode().equals("foreground")) {
            System.out.println("  mrserver install   start automatically with the machine");
            System.out.println("  mrserver start     start now");
        } else {
            System.out.println("  mrserver run       start in this terminal");
        }
        System.out.println("Already using another start script? Stop the server with it once and switch it off first.");
        return 0;
    }

    private static boolean eula(Path file, boolean accept) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).lines().anyMatch(l -> l.trim().equalsIgnoreCase("eula=true"))) {
            return true;
        }
        if (!accept && System.console() != null) {
            System.out.print("Do you accept the Minecraft EULA (https://aka.ms/MinecraftEULA)? [y/N] ");
            String answer = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            accept = answer != null && answer.trim().toLowerCase(Locale.ROOT).matches("y|yes|j|ja");
        }
        if (!accept) {
            return false;
        }
        Files.writeString(file, "# Accepted with mrserver setup (https://aka.ms/MinecraftEULA)\neula=true\n");
        return true;
    }

    /**
     * After the wrapper downloaded MRadmin: the JAR must match the SHA-256 GitHub keeps for this release asset.
     */
    private static boolean verifyDownload(Path own) throws IOException, InterruptedException {
        Properties info = Main.buildInfo();
        String repo = info.getProperty("githubRepository", "therealmarci/mradmin");
        String tag = "mc" + Main.minecraft() + "-v" + Main.version();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/tags/" + tag))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", Main.userAgent())
                .header("Accept", "application/vnd.github+json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.out.println("ERROR: could not check the download (GitHub answered " + response.statusCode() + ").");
            return false;
        }
        String expected = null;
        for (Object a : Json.array(Json.object(Json.parse(response.body())).get("assets"))) {
            Map<String, Object> asset = Json.object(a);
            if (own.getFileName().toString().equals(Json.string(asset.get("name")))) {
                String digest = Json.string(asset.get("digest"));
                if (digest != null && digest.startsWith("sha256:")) {
                    expected = digest.substring(7).toLowerCase(Locale.ROOT);
                }
            }
        }
        if (expected == null) {
            System.out.println("ERROR: GitHub has no checksum for " + own.getFileName() + " - not using it.");
            return false;
        }
        MessageDigest digest = Paper.sha256();
        try (InputStream in = Files.newInputStream(own)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) {
            System.out.println("ERROR: the downloaded MRadmin does not match its checksum - deleted.");
            return false;
        }
        System.out.println("MRadmin " + Main.version() + " downloaded (checksum ok).");
        return true;
    }
}
