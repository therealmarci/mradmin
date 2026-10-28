package at.marcelr.mradmin.news;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Incidents ("warnings") pinned on Uptime Kuma status pages - e.g. during an outage. Read from the public status page
 * address ({@code <url>/api/status-page/<slug>}, no login needed - uptime-kuma.url is enough) every
 * {@code uptime-kuma.refresh-seconds} in the
 * background, for the slugs in {@code uptime-kuma.status-pages}. They are shown like the warnings written in MRadmin
 * (player page, in game); a new or changed one is announced once ({@code onNew}, main thread).
 */
public final class KumaIncidents {

    /** One incident. {@code key} changes when Kuma's text changes (announced again then). */
    public record Incident(String key, String slug, String title, String content, String style, long created) {

        /** Kuma's "danger" (red) counts as critical, everything else as a plain warning. */
        public boolean critical() {
            return "danger".equals(style);
        }
    }

    private static final DateTimeFormatter KUMA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MRAdmin plugin;
    private final Consumer<Incident> onNew;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile List<Incident> current = List.of();
    private final Set<String> announced = new HashSet<>();
    private boolean first = true;
    private BukkitTask task;

    public KumaIncidents(MRAdmin plugin, Consumer<Incident> onNew) {
        this.plugin = plugin;
        this.onNew = onNew;
    }

    public void start() {
        stop();
        Settings.KumaSettings s = plugin.settings().kuma();
        current = List.of();
        // Only the public status page is read - no login, so uptime-kuma.enabled is not needed for this.
        if (!s.incidents() || s.url().isEmpty() || s.statusPages().isEmpty()) {
            return;
        }
        long ticks = s.refreshSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::poll, 40L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Pinned incidents right now (empty without Kuma or status pages). */
    public List<Incident> current() {
        return current;
    }

    private void poll() {
        Settings.KumaSettings s = plugin.settings().kuma();
        List<Incident> found = new ArrayList<>();
        for (String slug : s.statusPages()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(s.url() + "/api/status-page/" + slug))
                        .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    plugin.getLogger().fine("Uptime Kuma status page '" + slug + "': HTTP " + response.statusCode());
                    continue;
                }
                JsonObject page = JsonParser.parseString(response.body()).getAsJsonObject();
                // Newer Kuma: "incidents" (a list), older: "incident" (one or null). Only pinned, active ones.
                List<JsonElement> list = new ArrayList<>();
                if (page.get("incidents") instanceof com.google.gson.JsonArray array) {
                    array.forEach(list::add);
                }
                if (page.get("incident") != null && page.get("incident").isJsonObject()) {
                    list.add(page.get("incident"));
                }
                for (JsonElement e : list) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject o = e.getAsJsonObject();
                    if (flag(o, "pin", true) && flag(o, "active", true)) {
                        found.add(parse(slug, o));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                plugin.getLogger().fine("Uptime Kuma status page '" + slug + "' not readable: " + e.getMessage());
            }
        }
        current = List.copyOf(found);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Incident i : found) {
                // Whatever is already there when the server starts is not announced again (only shown).
                if (announced.add(i.key()) && !first) {
                    onNew.accept(i);
                }
            }
            first = false;
        });
    }

    private static Incident parse(String slug, JsonObject o) {
        String id = string(o, "id");
        String updated = string(o, "lastUpdatedDate");
        String created = string(o, "createdDate");
        return new Incident(slug + ":" + id + ":" + (updated.isEmpty() ? created : updated), slug, string(o, "title"),
                string(o, "content"), string(o, "style"), time(created));
    }

    /** A true/false of Kuma (also 1/0), {@code missing} if it is not there. */
    private static boolean flag(JsonObject o, String key, boolean missing) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return missing;
        }
        String v = e.getAsString();
        return v.equals("true") || v.equals("1");
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** Kuma gives "2026-10-28 20:15:00" in UTC. */
    private static long time(String raw) {
        try {
            return LocalDateTime.parse(raw.length() > 19 ? raw.substring(0, 19) : raw, KUMA_TIME)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }
}
