package at.marcelr.mradmin.branding;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The start pages (since 1.5): one for visitors and one for signed-in players, each made of widgets in a 12-column
 * grid that the team arranges right on the page (edit mode for mradmin.appearance) - move, resize, insert, set up,
 * remove. Each widget has a type, a width (1-12 columns), a height (1-8 rows) and its own settings. Personal widgets
 * only exist on the page of signed-in players, the call to log in only on the visitors' one. A new server gets
 * templates. Kept as JSON in the storage ("landing"); everything is checked here, the
 * page only gets what passed. Older layouts of 1.5 (header + blocks) are turned into widgets once. Thread-safe.
 */
public final class Landing {

    /** type -> whether it is personal (then only signed-in players see it). */
    public static final Map<String, Boolean> TYPES = Map.ofEntries(
            Map.entry("banner", false), Map.entry("hints", false), Map.entry("address", false), Map.entry("online", false),
            Map.entry("greeting", true), Map.entry("tiles", true), Map.entry("stats", true),
            Map.entry("news", false), Map.entry("text", false), Map.entry("image", false), Map.entry("links", false),
            Map.entry("discord", false), Map.entry("login", false), Map.entry("apply", false));
    /** The two pages. */
    public static final Set<String> PAGES = Set.of("guests", "members");
    private static final int MAX_WIDGETS = 40;
    private static final String STORE = "landing";

    private final MRAdmin plugin;
    private JsonArray guests = template("guests");
    private JsonArray members = template("members");

    public Landing(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** The widgets of a page (guests / members) - a copy. */
    public synchronized JsonArray widgets(String page) {
        return ("guests".equals(page) ? guests : members).deepCopy();
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
            String g = y.getString("guests", "");
            String m = y.getString("members", "");
            if (!g.isBlank() || !m.isBlank()) {
                guests = g.isBlank() ? template("guests") : check(JsonParser.parseString(g).getAsJsonArray(), "guests");
                members = m.isBlank() ? template("members") : check(JsonParser.parseString(m).getAsJsonArray(), "members");
                return;
            }
            // One page of widgets with "audience" (earlier 1.5): split into the two pages once.
            String json = y.getString("widgets", "");
            if (!json.isBlank()) {
                split(JsonParser.parseString(json).getAsJsonArray());
                plugin.getLogger().info("Start page: split into a page for visitors and one for signed-in players.");
                return;
            }
            // Older layout of 1.5: header + blocks in two columns - turned into widgets once.
            String blocks = y.getString("blocks", "");
            if (!blocks.isBlank()) {
                split(fromBlocks(y, JsonParser.parseString(blocks).getAsJsonArray()));
                plugin.getLogger().info("Start page: turned the earlier layout into widgets.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the start page (landing), using the template: " + e.getMessage());
        }
    }

    /** Widgets that had an audience: everyone + visitors -> visitors' page, everyone + signed-in -> players' page. */
    private void split(JsonArray all) {
        JsonArray g = new JsonArray();
        JsonArray m = new JsonArray();
        for (JsonElement e : all) {
            JsonObject w = e.getAsJsonObject();
            String audience = w.has("audience") ? w.get("audience").getAsString() : "all";
            if (!"members".equals(audience)) {
                JsonObject copy = w.deepCopy();
                copy.addProperty("id", newId());
                g.add(copy);
            }
            if (!"guests".equals(audience)) {
                m.add(w.deepCopy());
            }
        }
        guests = check(g, "guests");
        members = check(m, "members");
        persist();
    }

    /** Saves both pages after checking each widget. @return null or an error key */
    public synchronized String save(JsonArray rawGuests, JsonArray rawMembers) {
        JsonArray g;
        JsonArray m;
        try {
            g = check(rawGuests, "guests");
            m = check(rawMembers, "members");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        guests = g;
        members = m;
        persist();
        return null;
    }

    private void persist() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("guests", guests.toString());
        y.set("members", members.toString());
        plugin.storage().saveState(STORE, y.saveToString());
    }

    // ------------------------------------------------------------------ template

    /** The start pages of a new server. */
    public static JsonArray template(String page) {
        JsonArray a = new JsonArray();
        a.add(widget("banner", 12, 3, "all", d -> {
            d.addProperty("image", "");
            d.addProperty("address", "");
            d.addProperty("online", true);
        }));
        a.add(widget("hints", 12, 2, "all", d -> { }));
        if ("members".equals(page)) {
            a.add(widget("tiles", 12, 3, "all", d -> { }));
        } else {
            a.add(widget("news", 8, 4, "all", d -> {
                d.addProperty("title", "");
                d.addProperty("count", 4);
            }));
            a.add(widget("login", 4, 2, "all", d -> {
                d.addProperty("title", "");
                d.addProperty("text", "");
            }));
            a.add(widget("apply", 4, 2, "all", d -> {
                d.addProperty("title", "");
                d.addProperty("text", "");
            }));
        }
        return a;
    }

    /** A widget; audience only for the split of older layouts. */
    private static JsonObject widget(String type, int w, int h, String audience, Consumer<JsonObject> data) {
        JsonObject o = new JsonObject();
        o.addProperty("id", newId());
        o.addProperty("type", type);
        o.addProperty("w", w);
        o.addProperty("h", h);
        o.addProperty("audience", audience);
        JsonObject d = new JsonObject();
        data.accept(d);
        o.add("data", d);
        return o;
    }

    private static String newId() {
        return Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 10);
    }

    /** The earlier layout: header -> banner, then hints and tiles, then the blocks (main 8 wide, side 4 wide). */
    private static JsonArray fromBlocks(YamlConfiguration y, JsonArray blocks) {
        JsonArray a = new JsonArray();
        String address = y.getString("header.address", "");
        boolean online = y.getBoolean("header.online", true);
        for (JsonElement e : blocks) {
            JsonObject b = e.getAsJsonObject();
            if ("hero".equals(b.get("type").getAsString()) && address.isEmpty()) {
                JsonObject d = b.getAsJsonObject("data");
                address = d.has("address") ? d.get("address").getAsString() : "";
                online = !d.has("online") || d.get("online").getAsBoolean();
            }
        }
        String image = y.getString("header.image", "");
        String addr = address;
        boolean on = online;
        a.add(widget("banner", 12, 3, "all", d -> {
            d.addProperty("image", image);
            d.addProperty("address", addr);
            d.addProperty("online", on);
        }));
        a.add(widget("hints", 12, 2, "all", d -> { }));
        a.add(widget("tiles", 12, 3, "members", d -> { }));
        for (JsonElement e : blocks) {
            JsonObject b = e.getAsJsonObject();
            if (b.has("enabled") && !b.get("enabled").getAsBoolean()) {
                continue;
            }
            String type = b.get("type").getAsString();
            JsonObject d = b.has("data") ? b.getAsJsonObject("data") : new JsonObject();
            int w = b.has("column") && "side".equals(b.get("column").getAsString()) ? 4 : 8;
            switch (type) {
                case "news" -> a.add(widget("news", w, 4, "all", n -> n.add("count", d.get("count"))));
                case "text" -> a.add(widget("text", w, 3, "all", n -> {
                    n.add("title", d.get("title"));
                    n.add("text", d.get("text"));
                }));
                case "image" -> a.add(widget("image", w, 3, "all", n -> {
                    n.add("image", d.get("image"));
                    n.add("alt", d.get("alt"));
                }));
                case "buttons" -> a.add(widget("links", w, 2, "all", n -> {
                    n.add("title", d.get("title"));
                    n.add("items", d.get("items"));
                }));
                case "columns" -> {
                    if (d.has("items") && d.get("items").isJsonArray()) {
                        for (JsonElement c : d.getAsJsonArray("items")) {
                            JsonObject col = c.getAsJsonObject();
                            a.add(widget("text", 4, 3, "all", n -> {
                                n.add("title", col.get("title"));
                                n.add("text", col.get("text"));
                            }));
                        }
                    }
                }
                case "signin" -> a.add(widget("login", w, 2, "guests", n -> {
                    n.add("title", d.get("title"));
                    n.add("text", d.get("text"));
                }));
                default -> {
                    // the old head block lives on in the banner
                }
            }
        }
        return a;
    }

    // ------------------------------------------------------------------ checking

    /**
     * Only known widgets with known fields, sizes, lengths and addresses within limits; personal widgets only on the
     * players' page, the call to log in only on the visitors' page (others are left out).
     */
    static JsonArray check(JsonArray raw, String page) {
        boolean visitors = "guests".equals(page);
        if (raw.size() > MAX_WIDGETS) {
            throw new IllegalArgumentException("landing-too-many");
        }
        JsonArray out = new JsonArray();
        Set<String> ids = new HashSet<>();
        for (JsonElement e : raw) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("landing-invalid");
            }
            JsonObject in = e.getAsJsonObject();
            String type = str(in, "type", 20);
            if (!TYPES.containsKey(type)) {
                throw new IllegalArgumentException("landing-invalid");
            }
            if ((visitors && TYPES.get(type)) || (!visitors && (type.equals("login") || type.equals("apply")))) {
                continue;
            }
            String id = str(in, "id", 20).replaceAll("[^a-zA-Z0-9_-]", "");
            if (id.isEmpty() || !ids.add(id)) {
                id = newId();
                ids.add(id);
            }
            JsonObject data = in.has("data") && in.get("data").isJsonObject() ? in.getAsJsonObject("data") : new JsonObject();
            JsonObject d = new JsonObject();
            switch (type) {
                case "banner" -> {
                    d.addProperty("image", str(data, "image", 40).replaceAll("[^a-f0-9]", ""));
                    d.addProperty("address", str(data, "address", 100));
                    d.addProperty("online", bool(data, "online", true));
                }
                case "address" -> {
                    d.addProperty("title", str(data, "title", 80));
                    d.addProperty("address", str(data, "address", 100));
                }
                case "online", "greeting", "tiles", "stats", "hints" -> d.addProperty("title", str(data, "title", 80));
                case "news" -> {
                    d.addProperty("title", str(data, "title", 80));
                    d.addProperty("count", Math.clamp(num(data, "count", 4), 1, 8));
                }
                case "text" -> {
                    d.addProperty("title", str(data, "title", 80));
                    d.addProperty("text", str(data, "text", 4000));
                }
                case "image" -> {
                    d.addProperty("image", str(data, "image", 40).replaceAll("[^a-f0-9]", ""));
                    d.addProperty("alt", str(data, "alt", 120));
                    d.addProperty("fit", "contain".equals(str(data, "fit", 8)) ? "contain" : "cover");
                }
                case "links" -> {
                    d.addProperty("title", str(data, "title", 80));
                    JsonArray items = new JsonArray();
                    for (JsonObject item : objects(data, "items", 8)) {
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
                case "discord" -> {
                    String url = str(data, "url", 300);
                    if (!url.isEmpty() && !url.matches("https?://\\S+")) {
                        throw new IllegalArgumentException("branding-link-invalid");
                    }
                    d.addProperty("url", url);
                    d.addProperty("text", str(data, "text", 300));
                }
                case "login", "apply" -> {
                    d.addProperty("title", str(data, "title", 80));
                    d.addProperty("text", str(data, "text", 300));
                }
                default -> throw new IllegalArgumentException("landing-invalid");
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("type", type);
            o.addProperty("w", Math.clamp(num(in, "w", 6), 1, 12));
            o.addProperty("h", Math.clamp(num(in, "h", 2), 1, 8));
            o.add("data", d);
            out.add(o);
        }
        return out;
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
