package at.marcelr.mradmin.starter;

import at.marcelr.mradmin.util.Versions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Objects;
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
        copyStarter(files, ownJar);
        writeWrapper(files.server().resolve("mrserver.sh"), "/starter/mrserver.sh", true);
        // Started from a running mrserver.cmd (first setup on Windows): it must not be replaced while cmd reads it.
        Path cmd = files.server().resolve("mrserver.cmd");
        if (System.getenv("MRSERVER_SETUP_FROM_CMD") == null || !Files.isRegularFile(cmd)) {
            writeWrapper(cmd, "/starter/mrserver.cmd", false);
        }

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

    /** The starter becomes a copy of {@code jar} (on Windows as starter-new.jar if the running starter holds it). */
    private static void copyStarter(StarterFiles files, Path jar) throws IOException {
        if (jar.toAbsolutePath().normalize().equals(files.starterJar().toAbsolutePath().normalize())) {
            return;
        }
        Path tmp = files.starterJar().resolveSibling("starter.jar.tmp");
        Files.copy(jar, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, files.starterJar(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Windows: a running starter holds starter.jar - swapped on its next restart.
            Files.move(tmp, files.starterJarNew(), StandardCopyOption.REPLACE_EXISTING);
        }
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
        return Files.isDirectory(plugins) && installedMradmin(plugins) != null;
    }

    /** The MRadmin JAR in plugins/, null if there is none. */
    private static Path installedMradmin(Path plugins) throws IOException {
        try (var list = Files.list(plugins)) {
            return list.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches("mradmin.*\\.jar"))
                    .sorted().reduce((a, b) -> b).orElse(null);
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

    private static String option(List<String> args, String name) {
        int i = args.indexOf(name);
        return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : null;
    }

    /** null if the memory options are fine (each optional; minimum not above maximum), otherwise why not. */
    static String memoryProblem(String min, String max) {
        if (min != null && JavaFlags.memory(min) == null) {
            return "'" + min + "' is not a memory size like 1G or 2048M.";
        }
        if (max != null && JavaFlags.memory(max) == null) {
            return "'" + max + "' is not a memory size like 4G or 4096M.";
        }
        if (min != null && max != null && megabytes(JavaFlags.memory(min)) > megabytes(JavaFlags.memory(max))) {
            return "The minimum memory (" + min + ") is larger than the maximum (" + max + ").";
        }
        return null;
    }

    /** "4G" -> 4096, "512M" -> 512, "1048576K" -> 1024. */
    static long megabytes(String memory) {
        long n = Long.parseLong(memory.substring(0, memory.length() - 1));
        return switch (memory.charAt(memory.length() - 1)) {
            case 'G' -> n * 1024;
            case 'K' -> n / 1024;
            default -> n;
        };
    }

    /** The only config.yml keys the installer may set (seed.properties, applied once by MRadmin). */
    public static final List<String> SEED_KEYS = List.of("server.memory-min", "server.memory-max", "web.enabled",
            "web.public-url", "storage.type", "versions.channel", "versions.dev-token");

    /** seed.properties: channel of single plugins on the page Plugins, e.g. {@code plugin-channel.MRadmin=dev}. */
    public static final String SEED_PLUGIN_CHANNEL = "plugin-channel.";

    /** Environment variable with the release token of the dev channel (not an argument - those show in process lists). */
    public static final String ENV_DEV_TOKEN = "MRSERVER_DEV_TOKEN";

    /** null if fine (empty = none), otherwise why not. */
    static String webUrlProblem(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        return url.matches("https?://[A-Za-z0-9.\\-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~\\-/]*)?")
                ? null : "'" + url + "' is not an address like https://admin.example.com";
    }

    /**
     * Answers of the installer. Memory goes into launch.properties (the starter uses it on the first start) and, like
     * the web settings, into seed.properties, which MRadmin takes over into config.yml on its first start.
     *
     * @param web    switch the web interface on (listens only locally, storage SQLite)
     * @param webUrl  public address, null/empty = none yet
     * @param channel stable/beta/dev or null; beta = versions.channel beta, dev = beta for all plus MRadmin itself on
     *                dev with the release token
     */
    static void seed(StarterFiles files, String min, String max, boolean web, String webUrl, String channel,
                     String devToken) throws IOException {
        Properties launch = StarterFiles.read(files.launch());
        Properties seed = StarterFiles.read(files.seed());
        if (min != null) {
            launch.setProperty("memory-min", min);
            seed.setProperty("server.memory-min", min);
        }
        if (max != null) {
            launch.setProperty("memory-max", max);
            seed.setProperty("server.memory-max", max);
        }
        if (web) {
            seed.setProperty("web.enabled", "true");
            seed.setProperty("storage.type", "sqlite");
            if (webUrl != null && !webUrl.isEmpty()) {
                seed.setProperty("web.public-url", webUrl);
            }
        }
        if ("beta".equals(channel) || "dev".equals(channel)) {
            seed.setProperty("versions.channel", "beta");
        }
        if ("dev".equals(channel) && devToken != null && !devToken.isBlank()) {
            seed.setProperty("versions.dev-token", devToken.trim());
            seed.setProperty(SEED_PLUGIN_CHANNEL + "MRadmin", "dev");
        }
        StarterFiles.write(files.launch(), launch, "Written by MRadmin from config.yml (section server) - do not edit.");
        if (!seed.isEmpty()) {
            StarterFiles.write(files.seed(), seed, "Answers of the installer - MRadmin takes them over into config.yml on its first start.");
        }
    }

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
        String memoryMin = option(args, "--memory-min");
        String memoryMax = option(args, "--memory-max");
        String memoryProblem = memoryProblem(memoryMin, memoryMax);
        if (memoryProblem != null) {
            System.out.println(memoryProblem);
            return 2;
        }
        String webUrl = option(args, "--web-url");
        boolean web = args.contains("--web") || webUrl != null;
        String urlProblem = webUrlProblem(webUrl);
        if (urlProblem != null) {
            System.out.println(urlProblem);
            return 2;
        }
        String channel = option(args, "--channel");
        if (channel != null && Channels.normalize(channel) == null) {
            System.out.println("Unknown channel '" + channel + "' - stable, beta or dev.");
            return 2;
        }
        channel = Channels.normalize(channel);
        String devToken = System.getenv(ENV_DEV_TOKEN);
        // The channel picks the MRadmin only on a new server - an existing one changes it on the page Server -> Plugins
        // (with the protection against steps back).
        boolean fresh = !Files.exists(files.server().resolve("plugins/MRadmin/config.yml"));
        if (channel != null && !fresh) {
            System.out.println("This server already ran MRadmin - choose the channel in the web interface: Server -> Plugins.");
            channel = null;
        }
        Path own = ownJar();
        if (downloaded && !verifyDownload(own)) {
            Files.deleteIfExists(own);
            return 1;
        }
        Channels.Pick pick;
        try {
            // Before anything changes: a wrong release token stops here.
            pick = Channels.resolve(channel, devToken);
        } catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
            return 1;
        }
        System.out.println("Setting up the MRadmin starter in " + files.server());
        Result result = prepare(files, own, mode);
        if (result.copiedPlugin()) {
            System.out.println("  MRadmin copied to plugins/" + own.getFileName());
        }
        if (pick != null) {
            // Paper swaps plugins/update/<name> in on the first start - the running JAR cannot be replaced on Windows.
            Path plugin = installedMradmin(files.server().resolve("plugins"));
            Path update = files.server().resolve("plugins/update").resolve(plugin.getFileName());
            System.out.println("  Downloading MRadmin " + pick.label() + " ...");
            try {
                Channels.download(pick, devToken, update);
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                return 1;
            }
            copyStarter(files, update);
            System.out.println("  MRadmin " + pick.label() + " ready (checksum ok) - it replaces " + Main.version()
                    + " on the first start.");
        } else if ("beta".equals(channel)) {
            System.out.println("  No beta newer than MRadmin " + Main.version() + " - betas come as updates later.");
        }
        if (channel != null) {
            System.out.println("  Update channel: " + ("dev".equals(channel) ? "beta, MRadmin itself dev" : channel));
        }
        if (memoryMin != null || memoryMax != null || web || channel != null) {
            seed(files, JavaFlags.memory(memoryMin), JavaFlags.memory(memoryMax), web, webUrl, channel, devToken);
        }
        if (memoryMin != null || memoryMax != null) {
            System.out.println("  Memory: " + Objects.requireNonNullElse(JavaFlags.memory(memoryMin), "1G") + " - "
                    + Objects.requireNonNullElse(JavaFlags.memory(memoryMax), "4G"));
        }
        if (web) {
            System.out.println("  Web interface: on (http://127.0.0.1:8125" + (webUrl == null || webUrl.isEmpty()
                    ? ", no public address yet)" : ", public address " + webUrl + ")"));
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
        String version = Main.version();
        String tag = "mc" + Main.minecraft() + (Versions.isScheme(version) ? "-" : "-v") + version;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = Channels.request("https://api.github.com/repos/" + repo + "/releases/tags/" + tag)
                .timeout(Duration.ofSeconds(20)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.out.println("ERROR: could not check the download (" + Channels.github(response.statusCode()) + ").");
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
