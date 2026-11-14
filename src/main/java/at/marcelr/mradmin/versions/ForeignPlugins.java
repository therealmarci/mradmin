package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugins that are not from MRtechnology, per server (main server and waiting room): only watched, never updated.
 * <p>
 * Where a plugin comes from: MRadmin asks Modrinth with the SHA-1 of its JAR (that finds the project and exactly the
 * installed version); the team can also choose a Modrinth project (search) or a GitHub repository by hand, or switch
 * the lookup off. The newest version is the one that fits the server: on Modrinth for its Minecraft version and a
 * Paper-compatible loader, on GitHub the newest release that names the Minecraft version (without any such release the
 * newest one, marked "Minecraft version not stated"). Looked up in the background, at most every 6 hours.
 * <p>
 * Stored in the storage document "foreign-plugins": server -> plugin -> "modrinth:&lt;project&gt;" | "github:owner/repo" |
 * "off" (older files: just "owner/repo" = GitHub).
 */
public final class ForeignPlugins {

    /** What MRadmin knows about one plugin of a server. */
    public record Info(String version, boolean enabled, String sha1) {
    }

    /** A chosen source: type modrinth | github | off, id = project id/slug or owner/repo. */
    public record Source(String type, String id) {
    }

    private record Hit(String project, String versionId, String number, long published, long checked) {
    }

    private record Project(String id, String slug, String title, String icon, long checked) {
    }

    /** The newest fitting version of a source; error: no-version (nothing for this Minecraft version) or a failure. */
    private record Latest(String version, String versionId, String url, long published, String channel, boolean mcUnknown,
                          String error, long checked) {
    }

    private static final String DOC = "foreign-plugins";
    private static final long MAX_AGE = 6 * 3_600_000L;
    private static final long RETRY = 15 * 60_000L;
    private static final String MODRINTH = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "MRadmin (github.com/therealmarci/mradmin)";
    /** Loaders whose plugins run on Paper. */
    private static final List<String> LOADERS = List.of("paper", "spigot", "bukkit", "purpur", "folia");
    private static final Pattern REPO = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern GITHUB_URL = Pattern.compile("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?$");
    private static final Pattern MODRINTH_URL = Pattern.compile("^(?:https?://)?(?:www\\.)?modrinth\\.com/(?:plugin|mod|project|datapack)/([A-Za-z0-9_.-]+)(?:/.*)?$");
    private static final Pattern SLUG = Pattern.compile("^[A-Za-z0-9_.-]{2,64}$");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Gson GSON = new Gson();

    private final MRAdmin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final JarHashes hashes = new JarHashes();
    private final Map<String, Map<String, String>> links = new ConcurrentHashMap<>();
    private final Map<String, Hit> hits = new ConcurrentHashMap<>();
    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final Map<String, Latest> latest = new ConcurrentHashMap<>();
    private final Set<String> fetching = ConcurrentHashMap.newKeySet();
    private boolean loaded;

    public ForeignPlugins(MRAdmin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ links

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        YamlConfiguration yaml = plugin.storage().loadYaml(DOC, plugin.getLogger());
        for (String server : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(server);
            if (section == null) {
                continue;
            }
            Map<String, String> map = new ConcurrentHashMap<>();
            for (String name : section.getKeys(false)) {
                String value = section.getString(name, "");
                // older files: just "owner/repo"
                map.put(name, value.contains(":") || value.equals("off") ? value : "github:" + value);
            }
            links.put(server, map);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        links.forEach((server, map) -> map.forEach((name, value) -> yaml.set(server + "." + name, value)));
        plugin.storage().saveYaml(DOC, yaml);
    }

    /**
     * Reads what the team typed: a Modrinth or GitHub address, "owner/repo" (GitHub), a Modrinth project id/slug with
     * type modrinth, or type off. Null if it is neither.
     */
    public static Source parse(String type, String text) {
        String raw = text == null ? "" : text.strip();
        if ("off".equals(type)) {
            return new Source("off", "");
        }
        Matcher m = MODRINTH_URL.matcher(raw);
        if (m.matches()) {
            return new Source("modrinth", m.group(1));
        }
        m = GITHUB_URL.matcher(raw);
        if (m.matches()) {
            return new Source("github", m.group(1));
        }
        if ("modrinth".equals(type) && SLUG.matcher(raw).matches()) {
            return new Source("modrinth", raw);
        }
        if (!"modrinth".equals(type) && REPO.matcher(raw).matches()) {
            return new Source("github", raw);
        }
        return null;
    }

    /** Stores the source of a plugin on a server; null = back to the automatic lookup. */
    public void link(String server, String name, Source source) {
        load();
        Map<String, String> map = links.computeIfAbsent(server, k -> new ConcurrentHashMap<>());
        if (source == null) {
            map.remove(name);
        } else {
            map.put(name, source.type().equals("off") ? "off" : source.type() + ":" + source.id());
        }
        save();
    }

    private Source chosen(String server, String name) {
        String value = links.getOrDefault(server, Map.of()).get(name);
        if (value == null) {
            return null;
        }
        if (value.equals("off")) {
            return new Source("off", "");
        }
        int i = value.indexOf(':');
        return new Source(value.substring(0, i), value.substring(i + 1));
    }

    // ------------------------------------------------------------------- view

    /**
     * The plugins of a server ("main" or "room") with their source and state (none | checking | current | newer |
     * no-version | unknown). Asks Modrinth/GitHub in the background for what is missing or old.
     */
    public List<Map<String, Object>> view(String server, Map<String, Info> plugins, String minecraft) {
        load();
        lookUpHashes(plugins.values().stream().map(Info::sha1).filter(h -> h != null).toList());
        List<Map<String, Object>> list = new ArrayList<>();
        for (var e : plugins.entrySet()) {
            Info info = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("version", info.version());
            m.put("enabled", info.enabled());
            Source manual = chosen(server, e.getKey());
            Hit hit = info.sha1() == null ? null : hits.get(info.sha1());
            Source source = manual != null ? manual : hit != null && hit.project() != null ? new Source("modrinth", hit.project()) : null;
            m.put("manual", manual != null);
            if (source == null || source.type().equals("off")) {
                m.put("source", source == null ? null : Map.of("type", "off"));
                m.put("state", info.sha1() != null && hit == null && manual == null ? "checking" : "none");
                list.add(m);
                continue;
            }
            m.put("source", sourceView(source));
            Latest l = latest(source, minecraft);
            if (l == null) {
                m.put("state", "checking");
            } else if (l.error() != null) {
                m.put("state", l.error().equals("no-version") ? "no-version" : "unknown");
                m.put("error", l.error());
            } else {
                m.put("latest", latestView(l));
                // the exact installed version from Modrinth (same project) - else compare the version numbers
                boolean same = hit != null && hit.project() != null && source.type().equals("modrinth")
                        && (hit.project().equals(source.id()) || hit.project().equals(projectId(source.id())));
                m.put("state", same ? (hit.versionId().equals(l.versionId()) || l.published() <= hit.published() ? "current" : "newer")
                        : compare(info.version(), l.version()));
            }
            list.add(m);
        }
        list.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return list;
    }

    /** The newest fitting version of a source, looked up now if needed (for the preview in the dialog). */
    public Map<String, Object> preview(Source source, String minecraft) {
        Latest l = latest(source, minecraft);
        String key = key(source, minecraft);
        for (int i = 0; l == null && i < 40; i++) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            l = latest.get(key);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", sourceView(source));
        if (l == null) {
            m.put("state", "checking");
        } else if (l.error() != null) {
            m.put("state", l.error().equals("no-version") ? "no-version" : "unknown");
            m.put("error", l.error());
        } else {
            m.put("state", "found");
            m.put("latest", latestView(l));
        }
        return m;
    }

    private Map<String, Object> sourceView(Source source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", source.type());
        m.put("id", source.id());
        if (source.type().equals("modrinth")) {
            Project p = project(source.id());
            m.put("title", p == null ? source.id() : p.title());
            m.put("icon", p == null ? null : p.icon());
            m.put("url", "https://modrinth.com/plugin/" + (p == null ? source.id() : p.slug()));
        } else {
            m.put("title", source.id());
            m.put("url", "https://github.com/" + source.id());
        }
        return m;
    }

    private static Map<String, Object> latestView(Latest l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", l.version());
        m.put("url", l.url());
        m.put("published", l.published());
        m.put("channel", l.channel());
        m.put("mcUnknown", l.mcUnknown());
        return m;
    }

    // ------------------------------------------------------------- the plugins

    /** The plugins of this server that MRadmin does not manage, with their JAR. Server thread. */
    public Map<String, Object[]> local() {
        Set<String> managed = Set.copyOf(plugin.versions().list().stream().filter(e -> e.isInstalled() && !e.remote)
                .map(e -> e.name).toList());
        Map<String, Object[]> map = new LinkedHashMap<>();
        for (var p : Bukkit.getPluginManager().getPlugins()) {
            if (!managed.contains(p.getName())) {
                map.put(p.getName(), new Object[]{p.getPluginMeta().getVersion(), p.isEnabled(), p});
            }
        }
        return map;
    }

    /** {@link #local()} with the SHA-1 of every JAR (reads the files - not on the server thread). */
    public Map<String, Info> withHashes(Map<String, Object[]> local) {
        Map<String, Info> map = new LinkedHashMap<>();
        local.forEach((name, v) -> map.put(name, new Info(String.valueOf(v[0]), Boolean.TRUE.equals(v[1]),
                hashes.sha1((org.bukkit.plugin.Plugin) v[2]))));
        return map;
    }

    // ---------------------------------------------------------------- Modrinth

    /** Asks Modrinth once for all hashes not known yet (misses again after 6 hours). */
    private void lookUpHashes(List<String> all) {
        long now = System.currentTimeMillis();
        List<String> missing = all.stream().filter(h -> {
            Hit hit = hits.get(h);
            return hit == null || (hit.project() == null && now - hit.checked() > MAX_AGE);
        }).distinct().toList();
        if (missing.isEmpty() || !fetching.add("hashes")) {
            return;
        }
        Thread.ofVirtual().name("MRadmin-ModrinthHashes").start(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray array = new JsonArray();
                missing.forEach(array::add);
                body.add("hashes", array);
                body.addProperty("algorithm", "sha1");
                HttpResponse<String> response = http.send(request(MODRINTH + "/version_files")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }
                JsonObject found = JsonParser.parseString(response.body()).getAsJsonObject();
                long checked = System.currentTimeMillis();
                for (String h : missing) {
                    JsonElement v = found.get(h);
                    if (v != null && v.isJsonObject()) {
                        JsonObject o = v.getAsJsonObject();
                        hits.put(h, new Hit(o.get("project_id").getAsString(), o.get("id").getAsString(),
                                o.get("version_number").getAsString(), time(o, "date_published"), checked));
                    } else {
                        hits.put(h, new Hit(null, null, null, 0, checked));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                plugin.getLogger().fine("Modrinth hash lookup failed: " + e);
            } finally {
                fetching.remove("hashes");
            }
        });
    }

    /** Title, slug and icon of a Modrinth project (in the background; null until known). */
    private Project project(String idOrSlug) {
        Project p = projects.get(idOrSlug);
        if ((p == null || System.currentTimeMillis() - p.checked() > MAX_AGE) && fetching.add("project:" + idOrSlug)) {
            Thread.ofVirtual().name("MRadmin-ModrinthProject").start(() -> {
                try {
                    HttpResponse<String> r = http.send(request(MODRINTH + "/project/" + enc(idOrSlug)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 200) {
                        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                        Project found = new Project(o.get("id").getAsString(), o.get("slug").getAsString(),
                                o.get("title").getAsString(), string(o, "icon_url"), System.currentTimeMillis());
                        projects.put(idOrSlug, found);
                        projects.put(found.id(), found);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // shown with its id until it works
                } finally {
                    fetching.remove("project:" + idOrSlug);
                }
            });
        }
        return p;
    }

    private String projectId(String idOrSlug) {
        Project p = projects.get(idOrSlug);
        return p == null ? idOrSlug : p.id();
    }

    /** Modrinth search for the dialog: plugin projects, each with whether it has versions for this Minecraft version. */
    public List<Map<String, Object>> search(String query, String minecraft) throws Exception {
        String facets = "[[\"project_type:plugin\"]]";
        HttpResponse<String> r = http.send(request(MODRINTH + "/search?limit=8&query=" + enc(query) + "&facets=" + enc(facets)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new java.io.IOException("HTTP " + r.statusCode());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("hits")) {
            JsonObject o = e.getAsJsonObject();
            boolean fits = false;
            if (o.has("versions")) {
                for (JsonElement v : o.getAsJsonArray("versions")) {
                    fits |= v.getAsString().equals(minecraft);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.get("project_id").getAsString());
            m.put("slug", string(o, "slug"));
            m.put("title", string(o, "title"));
            m.put("author", string(o, "author"));
            m.put("description", string(o, "description"));
            m.put("icon", string(o, "icon_url"));
            m.put("downloads", o.has("downloads") ? o.get("downloads").getAsLong() : 0);
            m.put("fits", fits);
            list.add(m);
            projects.putIfAbsent(o.get("project_id").getAsString(), new Project(o.get("project_id").getAsString(),
                    string(o, "slug"), string(o, "title"), string(o, "icon_url"), System.currentTimeMillis()));
        }
        return list;
    }

    // ------------------------------------------------------------------ latest

    private static String key(Source source, String minecraft) {
        return source.type() + ":" + source.id() + "@" + minecraft;
    }

    private Latest latest(Source source, String minecraft) {
        String key = key(source, minecraft);
        Latest l = latest.get(key);
        long now = System.currentTimeMillis();
        boolean stale = l == null || now - l.checked() > (l.error() == null || l.error().equals("no-version") ? MAX_AGE : RETRY);
        if (stale && fetching.add(key)) {
            Thread.ofVirtual().name("MRadmin-ForeignRelease").start(() -> {
                try {
                    latest.put(key, source.type().equals("modrinth") ? modrinth(source.id(), minecraft) : github(source.id(), minecraft));
                } finally {
                    fetching.remove(key);
                }
            });
        }
        return l;
    }

    /** Newest version of a Modrinth project for this Minecraft version and a Paper loader - a release if there is one. */
    private Latest modrinth(String project, String minecraft) {
        long now = System.currentTimeMillis();
        try {
            String url = MODRINTH + "/project/" + enc(project) + "/version?loaders=" + enc(GSON.toJson(LOADERS))
                    + "&game_versions=" + enc(GSON.toJson(List.of(minecraft)));
            HttpResponse<String> r = http.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) {
                return failed("not-found", now);
            }
            if (r.statusCode() != 200) {
                return failed("HTTP " + r.statusCode(), now);
            }
            JsonObject pick = null;
            for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if (pick == null || ("release".equals(string(o, "version_type")) && !"release".equals(string(pick, "version_type")))
                        || (string(o, "version_type").equals(string(pick, "version_type")) && time(o, "date_published") > time(pick, "date_published"))) {
                    pick = o;
                }
            }
            if (pick == null) {
                return failed("no-version", now);
            }
            Project p = projects.get(project);
            String slug = p == null ? project : p.slug();
            return new Latest(pick.get("version_number").getAsString(), pick.get("id").getAsString(),
                    "https://modrinth.com/plugin/" + slug + "/version/" + pick.get("id").getAsString(),
                    time(pick, "date_published"), string(pick, "version_type"), false, null, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("interrupted", now);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), now);
        }
    }

    /**
     * Newest GitHub release that names this Minecraft version (tag, title, text or file names). Without any such
     * release the newest one, marked as "Minecraft version not stated".
     */
    private Latest github(String repo, String minecraft) {
        long now = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases?per_page=30"))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MRadmin").GET().build();
            HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 404) {
                return failed("not-found", now);
            }
            if (r.statusCode() != 200) {
                return failed("HTTP " + r.statusCode(), now);
            }
            Pattern mc = Pattern.compile("(?<![\\d.])" + Pattern.quote(minecraft) + "(?!\\d)");
            JsonObject newest = null;
            for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if (bool(o, "draft") || bool(o, "prerelease")) {
                    continue;
                }
                if (newest == null) {
                    newest = o;
                }
                if (mc.matcher(releaseText(o)).find()) {
                    return githubLatest(o, false, now);
                }
            }
            return newest == null ? failed("no-release", now) : githubLatest(newest, true, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("interrupted", now);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), now);
        }
    }

    private static String releaseText(JsonObject o) {
        StringBuilder text = new StringBuilder(string(o, "tag_name")).append('\n').append(string(o, "name")).append('\n').append(string(o, "body"));
        if (o.has("assets")) {
            for (JsonElement a : o.getAsJsonArray("assets")) {
                text.append('\n').append(string(a.getAsJsonObject(), "name"));
            }
        }
        return text.toString();
    }

    private static Latest githubLatest(JsonObject o, boolean mcUnknown, long now) {
        return new Latest(string(o, "tag_name"), string(o, "tag_name"), string(o, "html_url"), time(o, "published_at"),
                "release", mcUnknown, null, now);
    }

    private static Latest failed(String error, long now) {
        return new Latest(null, null, null, 0, null, false, error, now);
    }

    // ----------------------------------------------------------------- helpers

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", USER_AGENT);
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static long time(JsonObject o, String key) {
        String s = string(o, key);
        try {
            return s.isEmpty() ? 0 : Instant.parse(s).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** current if the installed version is at least the other one (by their numbers), newer if the other is higher. */
    static String compare(String installed, String tag) {
        int[] a = numbers(installed);
        int[] b = numbers(tag);
        if (a == null || b == null) {
            return "unknown";
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return y > x ? "newer" : "current";
            }
        }
        return "current";
    }

    private static int[] numbers(String text) {
        Matcher m = NUMBER.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return null;
        }
        String[] parts = m.group().split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = Integer.MAX_VALUE;
            }
        }
        return result;
    }
}
