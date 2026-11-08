package at.marcelr.mradmin.branding;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Set;

/**
 * The start page (since 1.5, the same for everyone): a header (background picture, server address, players online) and
 * a list of blocks in two columns (main and side) the team arranges under Server -> Appearance. A new server gets a
 * ready template (news, about, rules, links, sign-in); every block can be switched off, moved to the other column,
 * filled - and own ones added (text, picture, buttons, columns). Kept as JSON in the storage
 * ("landing"); everything is checked here, the page only gets what passed. Empty titles mean "the default title in
 * the visitor's language" (the page fills it in). Thread-safe.
 */
public final class Landing {

    public static final Set<String> TYPES = Set.of("news", "text", "image", "buttons", "columns", "signin");
    /** The header: background picture (id of a start page picture, "" = gradient in the accent colour), address, online. */
    public record Header(String image, String address, boolean online) {
    }
    private static final int MAX_BLOCKS = 30;
    private static final String STORE = "landing";

    private final MRAdmin plugin;
    private boolean enabled = true;
    private JsonArray blocks = template();
    private Header header = new Header("", "", true);

    public Landing(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized Header header() {
        return header;
    }

    /** All blocks (also switched-off ones) - a copy. */
    public synchronized JsonArray blocks() {
        return blocks.deepCopy();
    }

    /** Loads the saved page (main thread, while enabling), otherwise the template. */
    public synchronized void load() {
        try {
            String text = plugin.storage().loadState(STORE);
            if (text == null || text.isBlank()) {
                return;
            }
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(text);
            enabled = y.getBoolean("enabled", true);
            header = new Header(clean(y.getString("header.image", "")), y.getString("header.address", ""),
                    y.getBoolean("header.online", true));
            String json = y.getString("blocks", "");
            if (!json.isBlank()) {
                JsonArray raw = JsonParser.parseString(json).getAsJsonArray();
                // First layout of 1.5: the head was a block - its address and "online" go into the header.
                for (JsonElement e : raw) {
                    JsonObject b = e.getAsJsonObject();
                    if ("hero".equals(b.get("type").getAsString()) && !y.contains("header.address")) {
                        JsonObject d = b.getAsJsonObject("data");
                        header = new Header("", str(d, "address", 100), bool(d, "online", true));
                    }
                }
                blocks = check(raw);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the start page (landing), using the template: " + e.getMessage());
        }
    }

    /**
     * Saves a new page after checking every block. @return null or an error key
     */
    public synchronized String save(boolean on, Header head, JsonArray raw) {
        JsonArray checked;
        try {
            checked = check(raw);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        enabled = on;
        blocks = checked;
        header = new Header(clean(head.image()), cut(head.address(), 100), head.online());
        YamlConfiguration y = new YamlConfiguration();
        y.set("enabled", enabled);
        y.set("header.image", header.image());
        y.set("header.address", header.address());
        y.set("header.online", header.online());
        y.set("blocks", blocks.toString());
        plugin.storage().saveState(STORE, y.saveToString());
        return null;
    }

    /** The template of a new server. */
    public static JsonArray template() {
        JsonArray a = new JsonArray();
        a.add(block("news", "main", o -> {
            o.addProperty("title", "");
            o.addProperty("count", 4);
        }));
        a.add(block("text", "main", o -> {
            o.addProperty("title", "");
            o.addProperty("text", "");
            o.addProperty("role", "about");
        }));
        a.add(block("signin", "side", o -> {
            o.addProperty("title", "");
            o.addProperty("text", "");
        }));
        a.add(block("buttons", "side", o -> {
            o.addProperty("title", "");
            o.add("items", new JsonArray());
        }));
        a.add(block("text", "side", o -> {
            o.addProperty("title", "");
            o.addProperty("text", "");
            o.addProperty("role", "rules");
        }));
        return a;
    }

    private static JsonObject block(String type, String column, java.util.function.Consumer<JsonObject> data) {
        JsonObject b = new JsonObject();
        b.addProperty("id", Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 10));
        b.addProperty("type", type);
        b.addProperty("enabled", true);
        b.addProperty("column", column);
        JsonObject d = new JsonObject();
        data.accept(d);
        b.add("data", d);
        return b;
    }

    // ------------------------------------------------------------------ checking

    /** Only known blocks with known fields, lengths and addresses within limits. */
    static JsonArray check(JsonArray raw) {
        if (raw.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("landing-too-many");
        }
        JsonArray out = new JsonArray();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonElement e : raw) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("landing-invalid");
            }
            JsonObject in = e.getAsJsonObject();
            String type = str(in, "type", 20);
            if (type.equals("hero")) {
                continue;
            }
            if (!TYPES.contains(type)) {
                throw new IllegalArgumentException("landing-invalid");
            }
            String id = str(in, "id", 20).replaceAll("[^a-zA-Z0-9_-]", "");
            if (id.isEmpty() || !ids.add(id)) {
                id = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 10);
                ids.add(id);
            }
            JsonObject data = in.has("data") && in.get("data").isJsonObject() ? in.getAsJsonObject("data") : new JsonObject();
            JsonObject d = new JsonObject();
            d.addProperty("title", str(data, "title", 80));
            switch (type) {
                case "news" -> d.addProperty("count", Math.clamp(num(data, "count", 3), 1, 6));
                case "text" -> {
                    d.addProperty("text", str(data, "text", 4000));
                    String role = str(data, "role", 10);
                    d.addProperty("role", List.of("about", "rules").contains(role) ? role : "");
                }
                case "image" -> {
                    d.addProperty("image", str(data, "image", 40).replaceAll("[^a-f0-9]", ""));
                    d.addProperty("alt", str(data, "alt", 120));
                    d.addProperty("wide", bool(data, "wide", false));
                }
                case "buttons" -> {
                    JsonArray items = new JsonArray();
                    for (JsonObject item : objects(data, "items", 6)) {
                        String label = str(item, "label", 40);
                        String url = str(item, "url", 300);
                        if (label.isEmpty() && url.isEmpty()) {
                            continue;
                        }
                        if (!url.matches("https?://\\S+")) {
                            throw new IllegalArgumentException("branding-link-invalid");
                        }
                        JsonObject o = new JsonObject();
                        o.addProperty("label", label);
                        o.addProperty("url", url);
                        items.add(o);
                    }
                    d.add("items", items);
                }
                case "columns" -> {
                    JsonArray items = new JsonArray();
                    for (JsonObject item : objects(data, "items", 3)) {
                        JsonObject o = new JsonObject();
                        o.addProperty("title", str(item, "title", 80));
                        o.addProperty("text", str(item, "text", 1500));
                        items.add(o);
                    }
                    d.add("items", items);
                }
                case "signin" -> d.addProperty("text", str(data, "text", 300));
                default -> throw new IllegalArgumentException("landing-invalid");
            }
            JsonObject b = new JsonObject();
            b.addProperty("id", id);
            b.addProperty("type", type);
            b.addProperty("enabled", bool(in, "enabled", true));
            String column = str(in, "column", 4);
            boolean sideByDefault = type.equals("buttons") || type.equals("signin")
                    || (type.equals("text") && "rules".equals(d.get("role").getAsString()));
            b.addProperty("column", column.equals("main") || column.equals("side") ? column : sideByDefault ? "side" : "main");
            b.add("data", d);
            out.add(b);
        }
        return out;
    }

    /** A picture id (hex) or "". */
    private static String clean(String id) {
        return id == null ? "" : id.replaceAll("[^a-f0-9]", "");
    }

    private static String cut(String text, int max) {
        String t = text == null ? "" : text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String str(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return "";
        }
        String s = e.getAsString().replace("\r\n", "\n").strip();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean bool(JsonObject o, String key, boolean otherwise) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? otherwise : e.getAsBoolean();
    }

    private static int num(JsonObject o, String key, int otherwise) {
        JsonElement e = o.get(key);
        try {
            return e == null || !e.isJsonPrimitive() ? otherwise : e.getAsInt();
        } catch (NumberFormatException ex) {
            return otherwise;
        }
    }

    private static List<JsonObject> objects(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            return List.of();
        }
        List<JsonObject> list = new java.util.ArrayList<>();
        for (JsonElement item : e.getAsJsonArray()) {
            if (item.isJsonObject() && list.size() < max) {
                list.add(item.getAsJsonObject());
            }
        }
        return list;
    }
}
