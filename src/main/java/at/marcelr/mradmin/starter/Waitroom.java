package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * {@code mrserver setup --role waitroom}: sets up a waiting room server - Paper and MRwaitroom (instead of MRadmin),
 * started by this starter. MRwaitroom talks to the starter itself (heartbeat, action, requests) and writes its settings
 * (memory, Paper updates) from its own config.yml (section server); MRadmin's JAR is only the starter here.
 * <p>
 * Options besides those of the normal setup ({@code --mode}, {@code --memory-min/-max}, {@code --accept-eula}):
 * {@code --main-server <name in velocity.toml>}, {@code --main-address <host:port>}, {@code --link-url <MRadmin's
 * waiting-room.link>}; the key comes from the environment variable {@code MRSERVER_LINK_KEY} (not as argument, so it
 * does not show up in the process list or the shell history).
 */
final class Waitroom {

    static final String REPO = "therealmarci/mrwaitroom";
    static final String NAME = "MRwaitroom";
    static final String ENV_LINK_KEY = "MRSERVER_LINK_KEY";

    private Waitroom() {
    }

    static int setup(StarterFiles files, List<String> args) throws IOException, InterruptedException {
        boolean acceptEula = args.contains("--accept-eula");
        String mode = Setup.option(args, "--mode");
        if (mode != null && !mode.toLowerCase(Locale.ROOT).matches("screen|systemd|window|foreground")) {
            System.out.println("Unknown mode '" + mode + "' - screen, systemd, window or foreground.");
            return 2;
        }
        String memoryMin = Setup.option(args, "--memory-min");
        String memoryMax = Setup.option(args, "--memory-max");
        String memoryProblem = Setup.memoryProblem(memoryMin, memoryMax);
        if (memoryProblem != null) {
            System.out.println(memoryProblem);
            return 2;
        }
        String mainServer = Setup.option(args, "--main-server");
        String mainAddress = Setup.option(args, "--main-address");
        String linkUrl = Setup.option(args, "--link-url");
        String linkKey = System.getenv(ENV_LINK_KEY);
        if (linkKey != null && linkKey.isBlank()) {
            linkKey = null; // the installer always sets the variable - empty = no key yet
        }
        if (mainServer != null && !mainServer.matches("[A-Za-z0-9_.-]{1,64}")) {
            System.out.println("'" + mainServer + "' is not a server name like main.");
            return 2;
        }
        if (mainAddress != null && !mainAddress.matches("[A-Za-z0-9.\\-\\[\\]:]{1,100}")) {
            System.out.println("'" + mainAddress + "' is not an address like 10.0.0.5:25565.");
            return 2;
        }
        if (linkUrl != null && !linkUrl.matches("https?://[A-Za-z0-9.\\-\\[\\]]+(:[0-9]{1,5})?/?")) {
            System.out.println("'" + linkUrl + "' is not an address like http://10.0.0.5:8126.");
            return 2;
        }
        if (linkKey != null && !linkKey.trim().matches("[A-Za-z0-9]{16,200}")) {
            System.out.println("The key in " + ENV_LINK_KEY + " does not look like waiting-room.link.key of MRadmin.");
            return 2;
        }

        System.out.println("Setting up a waiting room server (MRwaitroom) in " + files.server());
        Path own = Setup.ownJar();
        Setup.Result result = Setup.prepare(files, own, mode == null ? null : mode.toLowerCase(Locale.ROOT), false);
        StarterFiles.writeText(files.role(), "waitroom");
        // The first setup ran from plugins/MRadmin*.jar - not a plugin here (Windows keeps it locked while this runs;
        // then the starter removes it before the first start).
        Path plugins = files.server().resolve("plugins");
        if (own.toAbsolutePath().normalize().startsWith(plugins.toAbsolutePath().normalize())) {
            try {
                Files.deleteIfExists(own);
                System.out.println("  MRadmin removed from plugins/ (it is only the starter here).");
            } catch (IOException e) {
                System.out.println("  MRadmin in plugins/ is removed on the first start (it is only the starter here).");
            }
        }

        Properties launch = StarterFiles.read(files.launch());
        launch.setProperty("memory-min", Objects.requireNonNullElse(JavaFlags.memory(memoryMin), launch.getProperty("memory-min", "1G")));
        launch.setProperty("memory-max", Objects.requireNonNullElse(JavaFlags.memory(memoryMax), launch.getProperty("memory-max", "2G")));
        StarterFiles.write(files.launch(), launch, "Written by MRwaitroom from config.yml (section server) - do not edit.");
        System.out.println("  Memory: " + launch.getProperty("memory-min") + " - " + launch.getProperty("memory-max"));

        Files.createDirectories(plugins);
        if (installed(plugins) == null) {
            Channels.Pick pick;
            try {
                pick = Channels.newestPlugin(REPO, NAME);
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage() + " Put the MRwaitroom JAR into plugins/ and run this again.");
                return 1;
            }
            Path target = plugins.resolve(pick.url().substring(pick.url().lastIndexOf('/') + 1));
            System.out.println("  Downloading MRwaitroom " + pick.label() + " ...");
            try {
                Channels.download(pick, null, target, NAME);
            } catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                return 1;
            }
            System.out.println("  MRwaitroom " + pick.label() + " ready (checksum ok).");
        } else {
            System.out.println("  MRwaitroom is already in plugins/.");
        }

        Path config = plugins.resolve("MRwaitroom/config.yml");
        if (mainServer != null || mainAddress != null || linkUrl != null || linkKey != null) {
            if (Files.exists(config)) {
                System.out.println("  plugins/MRwaitroom/config.yml exists already - set the connection there (section main).");
            } else {
                // Only these values - MRwaitroom fills in everything else (with comments) on its first start.
                StringBuilder yaml = new StringBuilder("# Answers of mrserver setup - MRwaitroom completes this file on its first start.\nmain:\n");
                if (mainServer != null) {
                    yaml.append("  server: \"").append(mainServer).append("\"\n");
                }
                if (mainAddress != null) {
                    yaml.append("  address: \"").append(mainAddress).append("\"\n");
                }
                if (linkUrl != null) {
                    yaml.append("  link-url: \"").append(linkUrl.replaceAll("/$", "")).append("\"\n");
                }
                if (linkKey != null) {
                    yaml.append("  link-key: \"").append(linkKey.trim()).append("\"\n");
                }
                Files.createDirectories(config.getParent());
                Files.writeString(config, yaml.toString(), StandardCharsets.UTF_8);
                System.out.println("  Connection to the main server written to plugins/MRwaitroom/config.yml.");
            }
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
        // A waiting room has no whitelist of its own (whoever the main server turns away is sent away by MRwaitroom).
        Path properties = files.server().resolve("server.properties");
        if (!Files.exists(properties)) {
            Files.writeString(properties, "# Written by mrserver setup --role waitroom - Paper adds the rest on its first start.\n"
                    + "white-list=false\nonline-mode=false\nview-distance=4\nsimulation-distance=2\nspawn-protection=0\n",
                    StandardCharsets.UTF_8);
            System.out.println("  server.properties prepared (no whitelist, small view distance, online-mode=false for Velocity).");
        }

        if (!Setup.eula(files.server().resolve("eula.txt"), acceptEula)) {
            System.out.println("Setup done, but the server only starts after you accept the Minecraft EULA:");
            System.out.println("  https://aka.ms/MinecraftEULA  ->  mrserver setup --accept-eula  (or eula=true in eula.txt)");
            return 0;
        }
        System.out.println("Done. Still to do:");
        System.out.println("  - Velocity forwarding in config/paper-global.yml (proxies.velocity) after the first start");
        if (linkUrl == null || linkKey == null) {
            System.out.println("  - main.link-url and main.link-key in plugins/MRwaitroom/config.yml (MRadmin: waiting-room.link)");
        }
        if (!result.local().mode().equals("foreground")) {
            System.out.println("  mrserver install   start automatically with the machine");
            System.out.println("  mrserver start     start now");
        } else {
            System.out.println("  mrserver run       start in this terminal");
        }
        return 0;
    }

    /** The MRwaitroom JAR in plugins/, null if there is none. */
    private static Path installed(Path plugins) throws IOException {
        try (var list = Files.list(plugins)) {
            return list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches("mrwaitroom.*\\.jar"))
                    .findFirst().orElse(null);
        }
    }
}
