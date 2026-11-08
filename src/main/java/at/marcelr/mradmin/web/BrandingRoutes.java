package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.branding.Branding;
import at.marcelr.mradmin.config.ConfigEditor;
import at.marcelr.mradmin.config.ConfigForm;
import at.marcelr.mradmin.storage.Storage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The look of the web interface (page Server -> Appearance, since 1.5).
 * <pre>
 *  GET    /api/branding                                   open: name, tagline, colour, footer, pictures
 *  POST   /api/branding        {name, tagline, accent, footer, links: [{label, url}]}   mradmin.appearance
 *  POST   /api/branding/{kind} raw picture (logo | icon), PNG / WebP / SVG, at most 512 KB   mradmin.appearance
 *  DELETE /api/branding/{kind}                                                             mradmin.appearance
 * </pre>
 * Texts and colour are written into config.yml (section branding, comments stay) like the config editor does, then
 * MRadmin reloads. The pictures themselves are served by WebServer (/branding/logo, /branding/icon).
 */
public final class BrandingRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private BrandingRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        BrandingRoutes r = new BrandingRoutes(server);
        server.open("GET", "/api/branding", r::state);
        server.route("POST", "/api/branding", r::save);
        server.route("POST", "/api/branding/{kind}", r::upload);
        server.route("DELETE", "/api/branding/{kind}", r::removeImage);
    }

    private Branding branding() {
        return plugin.branding();
    }

    private void state(Http http) throws Exception {
        Settings.BrandingSettings b = plugin.settings().branding();
        List<Map<String, String>> links = new ArrayList<>();
        for (Settings.BrandingSettings.Link link : b.links()) {
            links.add(Map.of("label", link.label(), "url", link.url()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", branding().name());
        result.put("ownName", b.name());
        result.put("tagline", b.tagline());
        result.put("accent", b.accent());
        result.put("accentText", Branding.textOn(b.accent()));
        result.put("footer", b.footerText());
        result.put("links", links);
        result.put("logo", branding().stamp("logo"));
        result.put("icon", branding().stamp("icon"));
        http.json(200, result);
    }

    private void require(Http http) throws Http.WebException {
        if (!server.permissions().has(http.account().id(), Perms.APPEARANCE)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    /** Texts, colour and links into config.yml, then reload. */
    private void save(Http http) throws Exception {
        require(http);
        JsonObject body = http.body();
        String accent = str(body, "accent", 7);
        if (!accent.matches("#[0-9a-fA-F]{6}")) {
            throw new Http.WebException(400, "config-invalid");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("branding.name", str(body, "name", 40));
        changes.put("branding.tagline", str(body, "tagline", 120));
        changes.put("branding.accent", accent.toLowerCase(java.util.Locale.ROOT));
        changes.put("branding.footer-text", str(body, "footer", 300));
        Map<String, Object> rows = new LinkedHashMap<>();
        List<JsonObject> links = new ArrayList<>();
        if (body.has("links") && body.get("links").isJsonArray()) {
            for (JsonElement e : body.getAsJsonArray("links")) {
                if (e.isJsonObject()) {
                    links.add(e.getAsJsonObject());
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            String label = i < links.size() ? str(links.get(i), "label", 40) : "";
            String url = i < links.size() ? str(links.get(i), "url", 300) : "";
            if (!url.isEmpty() && !url.matches("https?://\\S+")) {
                throw new Http.WebException(400, "branding-link-invalid");
            }
            rows.put("link-" + (i + 1), Map.of("label", label, "url", url));
        }
        changes.put("branding.links", rows);
        var target = server.sync(() -> new ConfigEditor(plugin).targets().get(plugin.getName()));
        if (target == null) {
            throw new Http.WebException(404, "not-found");
        }
        List<String> changed;
        try {
            changed = ConfigEditor.write(target, ConfigEditor.load(target).version(), changes);
        } catch (ConfigForm.Invalid e) {
            throw new Http.WebException(400, "config-invalid");
        } catch (ConfigEditor.Stale e) {
            throw new Http.WebException(409, "config-changed");
        }
        String by = http.account().name() + " (web)";
        if (!changed.isEmpty()) {
            server.sync(() -> {
                target.reload().accept(by);
                return null;
            });
        }
        audit(http, "branding", String.join(", ", changed));
        http.json(200, Map.of("changed", changed));
    }

    private void upload(Http http) throws Exception {
        require(http);
        String kind = http.param("kind");
        String error = branding().save(kind, http.raw(Branding.MAX_IMAGE));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        audit(http, "branding-" + kind, "uploaded");
        http.json(200, Map.of("stamp", branding().stamp(kind)));
    }

    private void removeImage(Http http) throws Exception {
        require(http);
        String kind = http.param("kind");
        if (!Branding.KINDS.contains(kind)) {
            throw new Http.WebException(404, "not-found");
        }
        branding().remove(kind);
        audit(http, "branding-" + kind, "removed");
        http.json(200, Map.of());
    }

    private static String str(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        String s = e == null || e.isJsonNull() ? "" : e.getAsString().replaceAll("[\\r\\n\\t]+", " ").strip();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}
