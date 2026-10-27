package at.marcelr.mradmin.items;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Item pictures and item names for the web interface, from the game itself: once per Minecraft version MRadmin
 * fetches the official client from Mojang (checked with its SHA-1), draws every item with {@link IconRenderer} into
 * one picture (atlas) and keeps the item names in English and German. Only these results are stored, under
 * plugins/MRadmin/items/&lt;version&gt;/ - the client itself is never written to disk, and nothing of Mojang's is
 * in the plugin JAR.
 * <p>
 * Runs only while the web interface is on; the download happens in the background. Without it (no internet,
 * web.item-icons false) the web shows short labels instead of pictures.
 */
public final class ItemAssets {

    public enum State { OFF, LOADING, READY, FAILED }

    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Set<String> HOSTS = Set.of("piston-meta.mojang.com", "piston-data.mojang.com",
            "launchermeta.mojang.com", "launcher.mojang.com", "resources.download.minecraft.net");
    /** Parts of the client the renderer needs. */
    private static final List<String> KEEP = List.of("assets/minecraft/items/", "assets/minecraft/models/",
            "assets/minecraft/textures/item/", "assets/minecraft/textures/block/", "assets/minecraft/textures/entity/",
            "assets/minecraft/textures/colormap/");
    /** Game language -> language of the web interface. */
    private static final Map<String, String> LANGUAGES = Map.of("en_us", "en", "de_de", "de");
    /** Translations the item display and editor use. */
    private static final List<String> LANG_KEYS = List.of("item.minecraft.", "block.minecraft.",
            "enchantment.minecraft.", "enchantment.level.", "effect.minecraft.", "effect.duration.infinite",
            "trim_material.minecraft.", "trim_pattern.minecraft.", "potion.potency.", "potion.withAmplifier",
            "potion.withDuration", "item.unbreakable", "item.durability", "item.dyed", "item.color",
            "color.minecraft.", "upgrade.minecraft.");
    private static final int COLUMNS = 32;

    private final MRAdmin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile State state = State.OFF;
    private volatile byte[] atlas;
    private volatile Map<String, Object> info = Map.of();
    private final Map<String, byte[]> names = new HashMap<>();

    public ItemAssets(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Loads the stored pictures, or starts the download if they are missing. Server thread. */
    public void start() {
        if (!plugin.settings().web().enabled() || state == State.READY || state == State.LOADING) {
            return;
        }
        String version = Bukkit.getMinecraftVersion();
        File folder = folder(version);
        try {
            if (load(folder)) {
                return;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Stored item pictures are unreadable, fetching them again: " + e.getMessage());
        }
        if (!plugin.settings().web().itemIcons()) {
            state = State.OFF;
            return;
        }
        state = State.LOADING;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetch(version, folder));
    }

    public State state() {
        return state;
    }

    /** The atlas as PNG, or null. */
    public byte[] atlas() {
        return atlas;
    }

    /** {state, version, size, columns, items: {id: position}} - empty apart from the state until ready. */
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>(info);
        result.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        return result;
    }

    /** Item names etc. as JSON for "de" or "en", or null. */
    public byte[] names(String language) {
        synchronized (names) {
            return names.get(language);
        }
    }

    private File folder(String version) {
        return new File(new File(plugin.getDataFolder(), "items"), version.replaceAll("[^0-9A-Za-z._-]", "_"));
    }

    // ------------------------------------------------------------------ stored files

    /** @return false if the files are not (all) there */
    private boolean load(File folder) throws IOException {
        File index = new File(folder, "atlas.json");
        File picture = new File(folder, "atlas.png");
        if (!index.isFile() || !picture.isFile()) {
            return false;
        }
        JsonObject json = JsonParser.parseString(Files.readString(index.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Integer> positions = new LinkedHashMap<>();
        JsonArray items = json.getAsJsonArray("items");
        for (int i = 0; i < items.size(); i++) {
            positions.put(items.get(i).getAsString(), i);
        }
        byte[] png = Files.readAllBytes(picture.toPath());
        Map<String, byte[]> loaded = new HashMap<>();
        for (String language : LANGUAGES.values()) {
            File file = new File(folder, "lang/" + language + ".json");
            if (file.isFile()) {
                loaded.put(language, Files.readAllBytes(file.toPath()));
            }
        }
        synchronized (names) {
            names.clear();
            names.putAll(loaded);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", sha1(png).substring(0, 12));
        result.put("size", json.get("size").getAsInt());
        result.put("columns", json.get("columns").getAsInt());
        result.put("items", positions);
        atlas = png;
        info = result;
        state = State.READY;
        return true;
    }

    // ------------------------------------------------------------------ download (async)

    private void fetch(String version, File folder) {
        try {
            plugin.getLogger().info("Fetching item pictures for Minecraft " + version + " from Mojang (once, about 40 MB) ...");
            JsonObject manifest = json(get(MANIFEST, null, 5 << 20));
            String url = null;
            for (JsonElement v : manifest.getAsJsonArray("versions")) {
                if (v.getAsJsonObject().get("id").getAsString().equals(version)) {
                    url = v.getAsJsonObject().get("url").getAsString();
                }
            }
            if (url == null) {
                throw new IOException("Mojang does not list version " + version);
            }
            JsonObject meta = json(get(url, null, 5 << 20));
            JsonObject client = meta.getAsJsonObject("downloads").getAsJsonObject("client");
            Map<String, byte[]> files = client(client.get("url").getAsString(), client.get("sha1").getAsString());

            JsonObject assetIndex = meta.getAsJsonObject("assetIndex");
            JsonObject objects = json(get(assetIndex.get("url").getAsString(), assetIndex.get("sha1").getAsString(), 50 << 20))
                    .getAsJsonObject("objects");
            Map<String, byte[]> languages = new LinkedHashMap<>();
            for (Map.Entry<String, String> language : LANGUAGES.entrySet()) {
                byte[] data = files.get("assets/minecraft/lang/" + language.getKey() + ".json");
                JsonObject object = objects.getAsJsonObject("minecraft/lang/" + language.getKey() + ".json");
                if (data == null && object != null) {
                    String hash = object.get("hash").getAsString();
                    data = get("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, hash, 10 << 20);
                }
                if (data != null) {
                    languages.put(language.getValue(), filter(data));
                }
            }

            IconRenderer renderer = new IconRenderer(files);
            List<String> ids = new ArrayList<>();
            List<BufferedImage> icons = new ArrayList<>();
            for (String id : renderer.items()) {
                BufferedImage icon;
                try {
                    icon = renderer.render(id);
                } catch (RuntimeException e) {
                    icon = null;
                }
                if (icon != null) {
                    ids.add(id);
                    icons.add(icon);
                }
            }
            int size = IconRenderer.SIZE;
            int rows = (icons.size() + COLUMNS - 1) / COLUMNS;
            BufferedImage sheet = new BufferedImage(COLUMNS * size, Math.max(1, rows) * size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            for (int i = 0; i < icons.size(); i++) {
                g.drawImage(icons.get(i), (i % COLUMNS) * size, (i / COLUMNS) * size, null);
            }
            g.dispose();

            File langFolder = new File(folder, "lang");
            if (!langFolder.isDirectory() && !langFolder.mkdirs()) {
                throw new IOException("cannot create " + langFolder);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(sheet, "png", png);
            Files.write(new File(folder, "atlas.png").toPath(), png.toByteArray());
            for (Map.Entry<String, byte[]> language : languages.entrySet()) {
                Files.write(new File(langFolder, language.getKey() + ".json").toPath(), language.getValue());
            }
            JsonObject index = new JsonObject();
            index.addProperty("size", size);
            index.addProperty("columns", COLUMNS);
            JsonArray list = new JsonArray();
            ids.forEach(list::add);
            index.add("items", list);
            // Written last: its presence means everything is there.
            Files.writeString(new File(folder, "atlas.json").toPath(), index.toString(), StandardCharsets.UTF_8);
            load(folder);
            plugin.getLogger().info("Item pictures ready (" + ids.size() + " items, " + png.size() / 1024 + " KB).");
        } catch (Exception e) {
            state = State.FAILED;
            plugin.getLogger().warning("Could not fetch item pictures - the web shows labels instead: " + e.getMessage());
        }
    }

    /** Reads the client JAR while it downloads and keeps only what the renderer needs; checks the SHA-1 at the end. */
    private Map<String, byte[]> client(String url, String sha1) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("client download failed: HTTP " + response.statusCode());
        }
        Map<String, byte[]> files = new HashMap<>();
        MessageDigest digest = digest();
        try (DigestInputStream in = new DigestInputStream(response.body(), digest)) {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && (KEEP.stream().anyMatch(name::startsWith)
                        || name.equals("assets/minecraft/lang/en_us.json"))) {
                    files.put(name, zip.readAllBytes());
                }
            }
            // The rest of the file (central directory) counts for the checksum too.
            in.transferTo(OutputStream.nullOutputStream());
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha1)) {
            throw new IOException("client checksum does not match");
        }
        return files;
    }

    /** Only the translations the web needs, sorted. */
    private static byte[] filter(byte[] data) {
        JsonObject all = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        TreeMap<String, String> kept = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            if (LANG_KEYS.stream().anyMatch(e.getKey()::startsWith) && e.getValue().isJsonPrimitive()) {
                kept.put(e.getKey(), e.getValue().getAsString());
            }
        }
        JsonObject result = new JsonObject();
        kept.forEach(result::addProperty);
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] get(String url, String sha1, int limit) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        byte[] body = response.body();
        if (body.length > limit) {
            throw new IOException("unexpectedly large answer from " + url);
        }
        if (sha1 != null && !sha1(body).equalsIgnoreCase(sha1)) {
            throw new IOException("checksum does not match for " + url);
        }
        return body;
    }

    /** Only Mojang's own servers - never an address someone else could choose. */
    private HttpRequest request(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new IOException("unexpected address " + url);
        }
        return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "MRadmin/" + plugin.getPluginMeta().getVersion()).build();
    }

    private static JsonObject json(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha1(byte[] data) {
        return HexFormat.of().formatHex(digest().digest(data));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
