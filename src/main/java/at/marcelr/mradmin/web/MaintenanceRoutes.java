package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.announce.AnnounceTemplates;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.util.DurationUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Maintenance and announcements in the web interface - the same as /mradmin maintenance and /mradmin announce:
 * looking needs mradmin.maintenance.view, changing mradmin.maintenance.manage, announcing mradmin.announce.
 * <pre>
 *  GET  /api/maintenance                      phase, current window, all windows (Uptime Kuma and manual), Kuma state
 *  POST /api/maintenance/start    {duration?, title?}
 *  POST /api/maintenance/schedule {in, duration?, title?}
 *  POST /api/maintenance/extend   {by}        e.g. "30m" or "-15m"
 *  POST /api/maintenance/stop                 ends the current one or cancels the planned one
 *  POST /api/maintenance/refresh              asks Uptime Kuma again
 *  GET  /api/announce                         own templates to send (the built-in presets only by command),
 *                                             with mradmin.announce.templates also everything to edit them
 *  POST /api/announce   {preset, args?} | {text}, discord
 *  POST /api/announce/template                {name, original?, texts: {part: {de, en}}, discord, fadeIn, stay,
 *                                             fadeOut, sound, volume, pitch, audience}   (mradmin.announce.templates)
 *  POST /api/announce/template/{name}/delete
 *  GET  /api/announce/sounds                  all sound keys
 * </pre>
 * Durations as in the commands ("30m", "2h", "1h30m"). Everything goes into the web log.
 */
public final class MaintenanceRoutes {

    private static final int MAX_TITLE = 100;
    private static final int MAX_TEXT = 300;

    private final WebServer server;
    private final MRAdmin plugin;

    private MaintenanceRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        MaintenanceRoutes r = new MaintenanceRoutes(server);
        server.route("GET", "/api/maintenance", r::state);
        server.route("POST", "/api/maintenance/{action}", r::action);
        server.route("POST", "/api/maintenance/kuma/create", r::kumaCreate);
        server.route("POST", "/api/maintenance/kuma/{id}/{action}", r::kumaAction);
        server.route("GET", "/api/announce", r::presets);
        server.route("POST", "/api/announce", r::announce);
        server.route("POST", "/api/announce/template", r::templateSave);
        server.route("POST", "/api/announce/template/{name}/delete", r::templateDelete);
        server.route("GET", "/api/announce/sounds", r::sounds);
    }

    private boolean may(Http http, String permission) {
        return server.permissions().has(http.account().id(), permission);
    }

    private void require(Http http, Feature feature, String permission) throws Http.WebException {
        if (!plugin.feature(feature)) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!may(http, permission)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    // ------------------------------------------------------------ maintenance

    private void state(Http http) throws Exception {
        require(http, Feature.MAINTENANCE, Perms.MAINTENANCE_VIEW);
        boolean manage = may(http, Perms.MAINTENANCE_MANAGE);
        Map<String, Object> result = server.sync(() -> {
            MaintenanceManager m = plugin.maintenance();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("phase", m.phase().name().toLowerCase(Locale.ROOT));
            r.put("current", window(m.current()));
            r.put("manual", window(m.manual()));
            List<Map<String, Object>> windows = new ArrayList<>();
            for (Window w : m.windows()) {
                windows.add(window(w));
            }
            windows.sort((a, b) -> Long.compare((Long) a.get("start"), (Long) b.get("start")));
            r.put("windows", windows);
            // Maintenances that are over (Kuma and manual), newest first; the page mixes in Kuma's ended ones.
            List<Map<String, Object>> history = new ArrayList<>();
            for (MaintenanceManager.Past past : m.history()) {
                Map<String, Object> h = window(past.window());
                h.put("endedAt", past.at().toEpochMilli());
                h.put("cancelled", past.cancelled());
                history.add(h);
            }
            r.put("history", history);
            r.put("kuma", m.kuma().state().name().toLowerCase(Locale.ROOT));
            Instant last = m.kuma().lastUpdate();
            r.put("kumaUpdate", last == null ? 0 : last.toEpochMilli());
            r.put("manage", manage);
            // Uptime Kuma set up -> maintenance is managed there; otherwise MRadmin's own (local) engine.
            r.put("mode", kumaConfigured() ? "kuma" : "local");
            return r;
        });
        if (kumaConfigured()) {
            result.put("kumaInfo", kumaInfo());
        }
        http.json(200, result);
    }

    // ----------------------------------------------------------- Uptime Kuma

    private boolean kumaConfigured() {
        var k = plugin.settings().kuma();
        return k.enabled() && !k.url().isEmpty() && k.hasLogin();
    }

    /** Kuma's state and this server's maintenances in Kuma (also paused and ended ones). */
    private Map<String, Object> kumaInfo() {
        var kuma = plugin.maintenance().kuma();
        java.time.ZoneId zone = plugin.settings().zone();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("state", kuma.state().name().toLowerCase(Locale.ROOT));
        info.put("version", kuma.version());
        info.put("problem", kuma.problem());
        info.put("url", plugin.settings().kuma().url());
        info.put("canCreate", kuma.canCreate());
        List<Map<String, Object>> list = new ArrayList<>();
        for (com.google.gson.JsonObject m : kuma.maintenances()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", m.get("id").getAsInt());
            e.put("title", text(m, "title"));
            e.put("description", text(m, "description"));
            e.put("strategy", text(m, "strategy"));
            e.put("status", text(m, "status"));
            Instant[] range = range(m, zone);
            e.put("start", range[0] == null ? 0L : range[0].toEpochMilli());
            e.put("end", range[1] == null ? 0L : range[1].toEpochMilli());
            list.add(e);
        }
        list.sort((a, b) -> Long.compare((Long) a.get("start"), (Long) b.get("start")));
        info.put("maintenances", list);
        return info;
    }

    private static String text(com.google.gson.JsonObject m, String key) {
        return m.has(key) && !m.get(key).isJsonNull() ? m.get(key).getAsString() : "";
    }

    /** Start and end: the first time slot, otherwise the date range (single maintenances), in their timezone. */
    private static Instant[] range(com.google.gson.JsonObject m, java.time.ZoneId fallback) {
        java.time.ZoneId zone = at.marcelr.mradmin.maintenance.KumaParser.zone(m, fallback);
        String start = null;
        String end = null;
        if (m.has("timeslotList") && m.get("timeslotList").isJsonArray() && !m.getAsJsonArray("timeslotList").isEmpty()
                && m.getAsJsonArray("timeslotList").get(0).isJsonObject()) {
            var slot = m.getAsJsonArray("timeslotList").get(0).getAsJsonObject();
            start = text(slot, "startDate");
            end = text(slot, "endDate");
        } else if (m.has("dateRange") && m.get("dateRange").isJsonArray()) {
            var r = m.getAsJsonArray("dateRange");
            start = r.size() > 0 && !r.get(0).isJsonNull() ? r.get(0).getAsString() : null;
            end = r.size() > 1 && !r.get(1).isJsonNull() ? r.get(1).getAsString() : null;
        }
        return new Instant[] {at.marcelr.mradmin.maintenance.KumaParser.time(start, zone),
                at.marcelr.mradmin.maintenance.KumaParser.time(end, zone)};
    }

    private at.marcelr.mradmin.maintenance.KumaClient requireKuma(Http http) throws Http.WebException {
        require(http, Feature.MAINTENANCE, Perms.MAINTENANCE_MANAGE);
        if (!kumaConfigured()) {
            throw new Http.WebException(404, "kuma-off");
        }
        var kuma = plugin.maintenance().kuma();
        if (kuma.state() != at.marcelr.mradmin.maintenance.KumaClient.State.CONNECTED) {
            throw new Http.WebException(503, "kuma-offline");
        }
        return kuma;
    }

    /**
     * New maintenance in Kuma: now without duration = "manual" (until paused/deleted), now with duration or planned
     * = a single window ({in} from now, {duration} long).
     */
    private void kumaCreate(Http http) throws Exception {
        var kuma = requireKuma(http);
        if (!kuma.canCreate()) {
            throw new Http.WebException(400, "kuma-cannot-create");
        }
        String title = clean(http.string("title"), MAX_TITLE);
        if (title.isEmpty()) {
            title = plugin.messages().plain("maintenance-default-title");
            if (title.isEmpty()) {
                title = "Maintenance";
            }
        }
        String description = clean(http.string("description"), MAX_TEXT);
        boolean plan = "plan".equals(http.string("mode"));
        Duration duration = duration(http.string("duration"), false);
        Instant start = null;
        Instant end = null;
        if (plan) {
            Duration in = duration(http.string("in"), false);
            if (in == null) {
                throw new Http.WebException(400, "invalid-duration");
            }
            if (duration == null) {
                throw new Http.WebException(400, "kuma-needs-duration");
            }
            start = Instant.now().plus(in);
            end = start.plus(duration);
        } else if (duration != null) {
            start = Instant.now();
            end = start.plus(duration);
        }
        String error = kuma.add(title, description, start, end, plugin.settings().zone());
        if (error != null) {
            throw new Http.WebException(400, "kuma:" + error);
        }
        audit(http, "kuma-maintenance-add", title + (start == null ? " (manual)" : ""));
        http.json(200, Map.of());
    }

    /** pause | resume | delete | extend {by} | edit {title, description} - only this server's maintenances. */
    private void kumaAction(Http http) throws Exception {
        var kuma = requireKuma(http);
        int id;
        try {
            id = Integer.parseInt(http.param("id"));
        } catch (NumberFormatException e) {
            throw new Http.WebException(404, "not-found");
        }
        if (!kuma.owns(id)) {
            throw new Http.WebException(404, "not-found");
        }
        String action = http.param("action");
        java.time.ZoneId zone = plugin.settings().zone();
        String detail = "#" + id;
        String error = switch (action) {
            case "pause" -> kuma.simple("pauseMaintenance", id);
            case "resume" -> kuma.simple("resumeMaintenance", id);
            case "delete" -> kuma.simple("deleteMaintenance", id);
            case "edit" -> {
                String title = http.string("title") == null ? null : clean(http.string("title"), MAX_TITLE);
                String description = http.string("description") == null ? null : clean(http.string("description"), MAX_TEXT);
                yield kuma.edit(id, title, description, null, null, zone);
            }
            case "extend" -> {
                Duration by = duration(http.string("by"), true);
                if (by == null) {
                    throw new Http.WebException(400, "invalid-duration");
                }
                Instant end = kuma.maintenances().stream().filter(m -> m.get("id").getAsInt() == id)
                        .map(m -> range(m, zone)[1]).findFirst().orElse(null);
                if (end == null) {
                    throw new Http.WebException(400, "maintenance-extend-failed");
                }
                detail += " " + http.string("by");
                yield kuma.edit(id, null, null, null, end.plus(by), zone);
            }
            default -> throw new Http.WebException(404, "not-found");
        };
        if (error != null) {
            throw new Http.WebException(400, "kuma:" + error);
        }
        audit(http, "kuma-maintenance-" + action, detail);
        http.json(200, Map.of());
    }

    private static Map<String, Object> window(Window w) {
        if (w == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", w.source());
        // Maintenance id in Uptime Kuma (0 for manual ones) - the page finds the Kuma actions for it.
        m.put("id", w.id());
        m.put("title", w.title() == null ? "" : w.title());
        m.put("start", w.start() == null ? 0L : w.start().toEpochMilli());
        m.put("end", w.end() == null ? 0L : w.end().toEpochMilli());
        m.put("active", w.activeAt(Instant.now()));
        return m;
    }

    private void action(Http http) throws Exception {
        require(http, Feature.MAINTENANCE, Perms.MAINTENANCE_MANAGE);
        String action = http.param("action");
        String by = http.account().name() + " (web)";
        String title = clean(http.string("title"), MAX_TITLE);
        String detail = "";
        String error = switch (action) {
            case "start" -> {
                Duration duration = duration(http.string("duration"), false);
                server.sync(() -> {
                    plugin.maintenance().startNow(duration, title, by);
                    return null;
                });
                detail = (duration == null ? "open end" : DurationUtil.format(duration.toMillis())) + " " + title;
                yield null;
            }
            case "schedule" -> {
                Duration in = duration(http.string("in"), false);
                if (in == null) {
                    yield "invalid-duration";
                }
                Duration duration = duration(http.string("duration"), false);
                server.sync(() -> {
                    plugin.maintenance().schedule(in, duration, title, by);
                    return null;
                });
                detail = "in " + DurationUtil.format(in.toMillis()) + " " + title;
                yield null;
            }
            case "extend" -> {
                Duration change = duration(http.string("by"), true);
                if (change == null) {
                    yield "invalid-duration";
                }
                boolean ok = server.sync(() -> plugin.maintenance().extendManual(change));
                detail = http.string("by");
                yield ok ? null : "maintenance-extend-failed";
            }
            case "stop" -> server.sync(() -> plugin.maintenance().stopCurrent(by)) ? null : "maintenance-none";
            case "refresh" -> {
                server.sync(() -> {
                    plugin.maintenance().refreshKuma();
                    return null;
                });
                yield null;
            }
            default -> throw new Http.WebException(404, "not-found");
        };
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        audit(http, "maintenance-" + action, detail.strip());
        http.json(200, Map.of());
    }

    /** "30m", "2h", "1h30m" (minus allowed for extend); null = none or invalid. */
    private static Duration duration(String raw, boolean signed) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip();
        boolean negative = signed && s.startsWith("-");
        long ms = DurationUtil.parse(negative ? s.substring(1) : s);
        if (ms <= 0) {
            return null;
        }
        return Duration.ofMillis(negative ? -ms : ms);
    }

    // ---------------------------------------------------------- announcements

    private void presets(Http http) throws Exception {
        require(http, Feature.ANNOUNCE, Perms.ANNOUNCE);
        // Only the own templates - the built-in presets of the language file stay for commands and scripts.
        List<Map<String, Object>> presets = server.sync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            var announcer = plugin.announcer();
            for (String p : announcer.menuPresets()) {
                list.add(Map.of("name", p, "preview", announcer.preview(p), "values", announcer.needsValues(p),
                        "discord", announcer.postsToDiscord(p), "audience", announcer.templates().get(p).audience()));
            }
            return list;
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("presets", presets);
        if (may(http, Perms.ANNOUNCE_TEMPLATES)) {
            result.put("templates", templates());
            result.put("builtIn", server.sync(() -> plugin.announcer().builtIn()));
        }
        http.json(200, result);
    }

    private void announce(Http http) throws Exception {
        require(http, Feature.ANNOUNCE, Perms.ANNOUNCE);
        String preset = http.string("preset");
        String text = clean(http.string("text"), MAX_TEXT);
        String rawArgs = clean(http.string("args"), MAX_TITLE);
        boolean discord = !"false".equals(http.string("discord"));
        String by = http.account().name();
        String error = server.sync(() -> {
            var announcer = plugin.announcer();
            if (preset != null && !preset.isBlank()) {
                var found = announcer.preset(preset);
                if (found.isEmpty()) {
                    return "not-found";
                }
                List<String> args = rawArgs.isEmpty() ? List.of() : List.of(rawArgs.split("\\s+"));
                announcer.announcePreset(found.get(), args, by, discord);
                return null;
            }
            if (text.isEmpty()) {
                return "announce-empty";
            }
            announcer.announceText(text, by, discord);
            return null;
        });
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        audit(http, "announce", (preset != null && !preset.isBlank() ? preset + " " + rawArgs : text).strip()
                + (discord ? "" : " (no Discord)"));
        http.json(200, Map.of());
    }

    // ---------------------------------------------------------------- helpers

    /** One line, no control characters, at most {@code max} characters. */

    // ------------------------------------------------------------ announcement templates

    /** All own templates with everything (for editing). */
    private List<Map<String, Object>> templates() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AnnounceTemplates.Template t : plugin.announcer().templates().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            Map<String, Object> texts = new LinkedHashMap<>();
            for (String part : AnnounceTemplates.PARTS) {
                Map<String, String> byLang = new LinkedHashMap<>();
                for (String lang : AnnounceTemplates.LANGS) {
                    byLang.put(lang, t.texts().getOrDefault(part, Map.of()).getOrDefault(lang, ""));
                }
                texts.put(part, byLang);
            }
            m.put("texts", texts);
            m.put("discord", t.discord());
            m.put("fadeIn", t.fadeIn());
            m.put("stay", t.stay());
            m.put("fadeOut", t.fadeOut());
            m.put("sound", t.sound());
            m.put("volume", t.volume());
            m.put("pitch", t.pitch());
            m.put("audience", t.audience());
            m.put("updated", t.updated());
            m.put("author", t.author());
            list.add(m);
        }
        return list;
    }

    /** Creates or changes a template. Names of the built-in presets are taken. */
    private void templateSave(Http http) throws Exception {
        require(http, Feature.ANNOUNCE, Perms.ANNOUNCE_TEMPLATES);
        Map<String, Object> body = http.bodyTree();
        String name = body.get("name") instanceof String n ? n.strip().toLowerCase(Locale.ROOT) : "";
        String original = body.get("original") instanceof String o && !o.isBlank() ? o : null;
        if (!AnnounceTemplates.NAME.matcher(name).matches()) {
            throw new Http.WebException(400, "template-name");
        }
        var announcer = plugin.announcer();
        boolean builtIn = server.sync(() -> announcer.builtIn().stream().anyMatch(b -> b.equalsIgnoreCase(name)));
        if (builtIn || (!name.equals(original) && announcer.templates().get(name) != null)) {
            throw new Http.WebException(409, "template-taken");
        }
        if (original != null && announcer.templates().get(original) == null) {
            throw new Http.WebException(404, "not-found");
        }
        Map<String, Map<String, String>> texts = new LinkedHashMap<>();
        if (body.get("texts") instanceof Map<?, ?> raw) {
            for (String part : AnnounceTemplates.PARTS) {
                if (raw.get(part) instanceof Map<?, ?> byLang) {
                    Map<String, String> clean = new LinkedHashMap<>();
                    for (String lang : AnnounceTemplates.LANGS) {
                        if (byLang.get(lang) instanceof String value && !value.isBlank()) {
                            clean.put(lang, clean(value, AnnounceTemplates.MAX_TEXT));
                        }
                    }
                    if (!clean.isEmpty()) {
                        texts.put(part, Map.copyOf(clean));
                    }
                }
            }
        }
        if (Stream.of("title", "subtitle", "chat", "actionbar").noneMatch(texts::containsKey)) {
            throw new Http.WebException(400, "template-empty");
        }
        String sound = body.get("sound") instanceof String s && !s.isBlank() ? s.strip().toLowerCase(Locale.ROOT)
                : AnnounceTemplates.SOUND_DEFAULT;
        if (!sound.equals(AnnounceTemplates.SOUND_DEFAULT) && !sound.equals(AnnounceTemplates.SOUND_NONE)
                && Settings.sound(sound, "sound", plugin.getLogger()) == null) {
            throw new Http.WebException(400, "template-sound");
        }
        String audience = AnnounceTemplates.AUDIENCES.contains(body.get("audience")) ? (String) body.get("audience") : "player";
        AnnounceTemplates.Template template = new AnnounceTemplates.Template(name, Map.copyOf(texts),
                Boolean.TRUE.equals(body.get("discord")), seconds(body.get("fadeIn"), 0.5), seconds(body.get("stay"), 3.5),
                seconds(body.get("fadeOut"), 1), sound, (float) range(body.get("volume"), 1, 0, 2),
                (float) range(body.get("pitch"), 1, 0.5, 2), audience, System.currentTimeMillis(), http.account().name());
        announcer.templates().save(template, original);
        audit(http, original == null ? "announce-template-create" : "announce-template-change", name);
        http.json(200, Map.of("name", name));
    }

    private void templateDelete(Http http) throws Exception {
        require(http, Feature.ANNOUNCE, Perms.ANNOUNCE_TEMPLATES);
        String name = http.param("name");
        if (!plugin.announcer().templates().delete(name)) {
            throw new Http.WebException(404, "not-found");
        }
        audit(http, "announce-template-delete", name);
        http.json(200, Map.of());
    }

    /** All sound keys of the game, for picking a template's sound. */
    private void sounds(Http http) throws Exception {
        require(http, Feature.ANNOUNCE, Perms.ANNOUNCE_TEMPLATES);
        List<String> keys = new ArrayList<>();
        for (org.bukkit.Sound s : org.bukkit.Registry.SOUND_EVENT) {
            keys.add(org.bukkit.Registry.SOUND_EVENT.getKey(s).asString());
        }
        keys.sort(null);
        http.json(200, Map.of("sounds", keys));
    }

    /** Seconds of a title time (0-60). */
    private static double seconds(Object raw, double fallback) {
        return range(raw, fallback, 0, 60);
    }

    private static double range(Object raw, double fallback, double min, double max) {
        double value = raw instanceof Number n ? n.doubleValue() : fallback;
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    private static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("[\\p{Cntrl}]", " ").strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}
