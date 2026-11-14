package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugins that are not from MRtechnology, per server (main server and waiting room): only watched, never updated. The
 * team can store a GitHub repository per plugin; MRadmin then reads its newest release (at most every 6 hours, in the
 * background) and says whether the installed version is current. The links are kept in the storage document
 * "foreign-plugins" (server -> plugin -> owner/repo).
 */
public final class ForeignPlugins {

    /** One plugin with what is known about its newest release. state: none (no link) | checking | current | newer | unknown. */
    public record View(String name, String version, boolean enabled, String repo, String latest, String url, long published,
                       String state, String error) {
    }

    private record Release(String tag, String url, long published, String error, long checked) {
    }

    private static final String DOC = "foreign-plugins";
    private static final long MAX_AGE = 6 * 3_600_000L;
    private static final long RETRY = 15 * 60_000L;
    private static final Pattern REPO = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern URL = Pattern.compile("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?$");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)*");

    private final MRAdmin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Map<String, String>> links = new ConcurrentHashMap<>();
    private final Map<String, Release> releases = new ConcurrentHashMap<>();
    private final Set<String> fetching = ConcurrentHashMap.newKeySet();
    private boolean loaded;

    public ForeignPlugins(MRAdmin plugin) {
        this.plugin = plugin;
    }

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
                map.put(name, section.getString(name, ""));
            }
            links.put(server, map);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        links.forEach((server, map) -> map.forEach((name, repo) -> yaml.set(server + "." + name, repo)));
        plugin.storage().saveYaml(DOC, yaml);
    }

    /**
     * Stores (or with an empty text removes) the GitHub repository of a plugin on a server. Accepts "owner/repo" or a
     * github.com address.
     *
     * @return the stored "owner/repo", "" when removed, null if the text is no GitHub repository
     */
    public String link(String server, String name, String text) {
        load();
        String raw = text == null ? "" : text.strip();
        Map<String, String> map = links.computeIfAbsent(server, k -> new ConcurrentHashMap<>());
        if (raw.isEmpty()) {
            map.remove(name);
            save();
            return "";
        }
        Matcher m = URL.matcher(raw);
        String repo = m.matches() ? m.group(1) : raw;
        if (!REPO.matcher(repo).matches()) {
            return null;
        }
        map.put(name, repo);
        save();
        releases.remove(repo);
        return repo;
    }

    /** The plugins of a server ("main" or "room") with their state; plugins: name -> [version, enabled]. */
    public List<View> view(String server, Map<String, Object[]> plugins) {
        load();
        Map<String, String> map = links.getOrDefault(server, Map.of());
        List<View> list = new ArrayList<>();
        for (var e : plugins.entrySet()) {
            String version = String.valueOf(e.getValue()[0]);
            boolean enabled = Boolean.TRUE.equals(e.getValue()[1]);
            String repo = map.get(e.getKey());
            if (repo == null) {
                list.add(new View(e.getKey(), version, enabled, null, null, null, 0, "none", null));
                continue;
            }
            Release r = release(repo);
            if (r == null) {
                list.add(new View(e.getKey(), version, enabled, repo, null, null, 0, "checking", null));
            } else if (r.error() != null) {
                list.add(new View(e.getKey(), version, enabled, repo, null, null, 0, "unknown", r.error()));
            } else {
                list.add(new View(e.getKey(), version, enabled, repo, r.tag(), r.url(), r.published(), compare(version, r.tag()), null));
            }
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }

    /** The plugins of this server that MRadmin does not manage: name -> [version, enabled]. Server thread. */
    public Map<String, Object[]> local() {
        Set<String> managed = Set.copyOf(plugin.versions().list().stream().filter(e -> e.isInstalled() && !e.remote)
                .map(e -> e.name).toList());
        Map<String, Object[]> map = new LinkedHashMap<>();
        for (var p : Bukkit.getPluginManager().getPlugins()) {
            if (!managed.contains(p.getName())) {
                map.put(p.getName(), new Object[]{p.getPluginMeta().getVersion(), p.isEnabled()});
            }
        }
        return map;
    }

    /** The newest release of a repository from the cache; asks GitHub in the background when old or missing. */
    private Release release(String repo) {
        Release r = releases.get(repo);
        long now = System.currentTimeMillis();
        boolean stale = r == null || now - r.checked() > (r.error() == null ? MAX_AGE : RETRY);
        if (stale && fetching.add(repo)) {
            Thread.ofVirtual().name("MRadmin-ForeignRelease").start(() -> {
                try {
                    releases.put(repo, fetch(repo));
                } finally {
                    fetching.remove(repo);
                }
            });
        }
        return r;
    }

    private Release fetch(String repo) {
        long now = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MRadmin").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return new Release(null, null, 0, "no-release", now);
            }
            if (response.statusCode() != 200) {
                return new Release(null, null, 0, "HTTP " + response.statusCode(), now);
            }
            JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
            long published = o.has("published_at") && !o.get("published_at").isJsonNull()
                    ? Instant.parse(o.get("published_at").getAsString()).toEpochMilli() : 0;
            return new Release(o.get("tag_name").getAsString(), o.get("html_url").getAsString(), published, null, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Release(null, null, 0, "interrupted", now);
        } catch (Exception e) {
            return new Release(null, null, 0, e.getClass().getSimpleName(), now);
        }
    }

    /** current if the installed version is at least the release (by their numbers), newer if the release is higher. */
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
        Matcher m = NUMBER.matcher(text == null ? "" : text);
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
