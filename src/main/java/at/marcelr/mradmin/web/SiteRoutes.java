package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Imprint, privacy policy and player heads.
 * <pre>
 *  GET /api/legal               open   links (own website) or whether the texts are here; contact/unban forms on
 *  GET /api/legal/{page}        open   imprint | privacy: text (Markdown) from plugins/MRadmin/web/
 *  GET /api/head/{uuid}                player head as PNG (made by MRadmin, see Heads)
 * </pre>
 * The texts are plain files the server owner fills in; MRadmin creates templates on the first start.
 */
public final class SiteRoutes {

    /** page -> file in plugins/MRadmin/web/ */
    private static final Map<String, String> PAGES = Map.of("imprint", "impressum.md", "privacy", "datenschutz.md");

    private final MRAdmin plugin;
    private final Heads heads = new Heads();

    private SiteRoutes(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public static void register(WebServer server) {
        SiteRoutes r = new SiteRoutes(server.plugin());
        r.templates();
        server.open("GET", "/api/legal", r::links);
        server.open("GET", "/api/legal/{page}", r::page);
        server.route("GET", "/api/head/{uuid}", r::head);
    }

    /** Creates impressum.md and datenschutz.md from the templates (in the server's language) if they are missing. */
    private void templates() {
        File folder = new File(plugin.getDataFolder(), "web");
        String lang = plugin.getConfig().getString("language", "de").toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
        for (String file : PAGES.values()) {
            File target = new File(folder, file);
            if (target.exists()) {
                continue;
            }
            try (InputStream in = plugin.getResource("legal/" + file.replace(".md", "-" + lang + ".md"))) {
                if (in != null) {
                    folder.mkdirs();
                    Files.write(target.toPath(), in.readAllBytes());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create web/" + file + ": " + e.getMessage());
            }
        }
    }

    private void links(Http http) throws IOException {
        Settings.WebSettings s = plugin.settings().web();
        var requests = plugin.web().requests();
        http.json(200, Map.of("imprint", s.imprintUrl(), "privacy", s.privacyUrl(),
                "contact", requests.contactAvailable(), "unban", requests.unbanAvailable()));
    }

    private void page(Http http) throws IOException {
        String file = PAGES.get(http.param("page"));
        if (file == null) {
            throw new Http.WebException(404, "not-found");
        }
        File target = new File(new File(plugin.getDataFolder(), "web"), file);
        String text = target.exists() ? read(target) : "";
        http.json(200, Map.of("text", text));
    }

    /** UTF-8 (with or without BOM); files saved as ANSI by Windows editors fall back to Windows-1252. */
    private static String read(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
            return !text.isEmpty() && text.charAt(0) == 0xFEFF ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("windows-1252"));
        }
    }

    private void head(Http http) throws IOException {
        UUID uuid;
        try {
            uuid = UUID.fromString(http.param("uuid"));
        } catch (IllegalArgumentException e) {
            throw new Http.WebException(404, "not-found");
        }
        http.send(200, "image/png", heads.head(uuid), "private, max-age=3600");
    }
}
