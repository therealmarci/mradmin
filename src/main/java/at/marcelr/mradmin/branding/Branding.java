package at.marcelr.mradmin.branding;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The look of the web interface for players (since 1.5): the server's name instead of "MRadmin", logo and browser icon,
 * accent colour, footer. Texts and colour are in config.yml (section branding), the pictures are files in
 * plugins/MRadmin/branding/ (logo.* and icon.*, PNG, WebP or SVG). Used for the page itself (index.html, the app
 * manifest), the 2FA app and passkeys, and the HTML mails. Any thread.
 */
public final class Branding {

    /** Biggest picture that can be uploaded. */
    public static final int MAX_IMAGE = 512 * 1024;
    public static final List<String> KINDS = List.of("logo", "icon");
    private static final List<String> EXTENSIONS = List.of("png", "webp", "svg");
    /** Things an SVG must not contain - it is served from the same origin as the interface. */
    private static final Pattern SVG_DANGER = Pattern.compile(
            "<\\s*script|javascript:|\\son[a-z]+\\s*=|<\\s*foreignobject|<\\s*iframe|<\\s*embed|<\\s*object|<!entity|<!doctype",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+");

    /** An uploaded picture: its bytes, media type and when it was saved (for cache busting). */
    public record Image(byte[] data, String type, long stamp) {
    }

    private final MRAdmin plugin;

    public Branding(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Settings.BrandingSettings settings() {
        return plugin.settings().branding();
    }

    /** The server's name for players: branding.name, otherwise the text mail-server-name (server language). */
    public String name() {
        String name = settings().name();
        if (!name.isBlank()) {
            return name;
        }
        return plugin.messages().server(() -> plugin.messages().plain("mail-server-name"));
    }

    public String accent() {
        return settings().accent();
    }

    /** Black or white text on the accent colour, whichever is easier to read. */
    public static String textOn(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.62 ? "#111111" : "#ffffff";
    }

    // ------------------------------------------------------------------ pictures

    private Path dir() {
        return plugin.getDataFolder().toPath().resolve("branding");
    }

    /** The uploaded picture of this kind (logo, icon), or null. */
    public Image image(String kind) {
        if (!KINDS.contains(kind)) {
            return null;
        }
        for (String ext : EXTENSIONS) {
            Path file = dir().resolve(kind + "." + ext);
            if (Files.isRegularFile(file)) {
                try {
                    return new Image(Files.readAllBytes(file), type(ext), Files.getLastModifiedTime(file).toMillis());
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** When the picture was saved, 0 if there is none (the icon falls back to the logo). */
    public long stamp(String kind) {
        Image image = image(kind);
        return image == null ? 0 : image.stamp();
    }

    /**
     * Saves a picture (PNG, WebP or SVG - recognised by its content, not the name). SVGs with scripts, event handlers
     * or embedded documents are refused. @return null or an error key
     */
    public String save(String kind, byte[] data) throws IOException {
        if (!KINDS.contains(kind)) {
            return "unknown-action";
        }
        if (data == null || data.length == 0 || data.length > MAX_IMAGE) {
            return "image-too-large";
        }
        String ext = sniff(data);
        if (ext == null) {
            return "image-type";
        }
        if (ext.equals("svg") && SVG_DANGER.matcher(new String(data, StandardCharsets.UTF_8)).find()) {
            return "image-unsafe";
        }
        Files.createDirectories(dir());
        remove(kind);
        Path temp = dir().resolve(kind + ".upload");
        Files.write(temp, data);
        Files.move(temp, dir().resolve(kind + "." + ext), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return null;
    }

    public void remove(String kind) throws IOException {
        for (String ext : EXTENSIONS) {
            Files.deleteIfExists(dir().resolve(kind + "." + ext));
        }
    }

    private static String sniff(byte[] d) {
        if (d.length > 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E'
                && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        String start = new String(d, 0, Math.min(d.length, 1024), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (start.startsWith("<svg") || (start.startsWith("<?xml") && start.contains("<svg"))) {
            return "svg";
        }
        return null;
    }

    private static String type(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/svg+xml";
        };
    }

    // ------------------------------------------------------------------ the page

    /** index.html with the name as title, the own browser icon and the accent colour - before the first paint. */
    public String index(String html) {
        String result = html.replace("<title>MRadmin</title>", "<title>" + escape(name()) + "</title>");
        long icon = stamp("icon") > 0 ? stamp("icon") : stamp("logo");
        if (icon > 0) {
            result = result.replace("href=\"/icon.svg\" type=\"image/svg+xml\"", "href=\"/branding/icon?v=" + icon + "\"");
        }
        String accent = accent();
        String style = "<style>:root:root{--accent:" + accent + ";--accent-text:" + textOn(accent) + "}</style>";
        return result.replace("</head>", style + "</head>");
    }

    /** The app manifest ("add to home screen") with the name, colour and icon. */
    public String manifest() {
        String name = name();
        long icon = stamp("icon") > 0 ? stamp("icon") : stamp("logo");
        Image image = icon > 0 ? (stamp("icon") > 0 ? image("icon") : image("logo")) : null;
        String iconJson = image == null
                ? "{\"src\":\"/icon.svg\",\"sizes\":\"any\",\"type\":\"image/svg+xml\"}"
                : "{\"src\":\"/branding/icon?v=" + icon + "\",\"sizes\":\"any\",\"type\":\"" + image.type() + "\"}";
        return "{\"name\":\"" + json(name) + "\",\"short_name\":\"" + json(name.length() > 12 ? name.substring(0, 12) : name)
                + "\",\"start_url\":\"/\",\"display\":\"standalone\",\"background_color\":\"#0f1115\",\"theme_color\":\""
                + accent() + "\",\"icons\":[" + iconJson + "]}";
    }

    // ------------------------------------------------------------------ mails

    /**
     * The HTML version of a mail: header with logo and name in the accent colour, the text (paragraphs; a line that is
     * only an address becomes a button), footer with the footer text, the links and imprint / privacy.
     */
    public String mail(String subject, String text) {
        String accent = accent();
        String on = textOn(accent);
        String base = plugin.settings().web().publicUrl().replaceAll("/+$", "");
        String button = plugin.messages().server(() -> plugin.messages().plain("mail-button"));
        StringBuilder body = new StringBuilder();
        for (String paragraph : text.replace("\r\n", "\n").split("\n\\s*\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (URL.matcher(p).matches()) {
                body.append("<p style=\"margin:22px 0;text-align:center\"><a href=\"").append(attr(p))
                        .append("\" style=\"display:inline-block;padding:12px 22px;border-radius:8px;background:").append(accent)
                        .append(";color:").append(on).append(";text-decoration:none;font-weight:600\">").append(escape(button))
                        .append("</a></p><p style=\"margin:0 0 16px;font-size:12px;color:#6b7280;word-break:break-all\">")
                        .append(links(p)).append("</p>");
            } else {
                body.append("<p style=\"margin:0 0 16px\">").append(links(p).replace("\n", "<br>")).append("</p>");
            }
        }
        StringBuilder head = new StringBuilder();
        Image logo = image("logo");
        if (logo != null && !base.isEmpty()) {
            head.append("<img src=\"").append(attr(base + "/branding/logo?v=" + logo.stamp()))
                    .append("\" alt=\"\" height=\"32\" style=\"height:32px;vertical-align:middle;margin-right:10px;border:0\">");
        }
        head.append("<span style=\"vertical-align:middle\">").append(escape(name())).append("</span>");
        List<String> foot = new ArrayList<>();
        Settings.BrandingSettings b = settings();
        for (Settings.BrandingSettings.Link link : b.links()) {
            foot.add("<a href=\"" + attr(link.url()) + "\" style=\"color:#6b7280\">" + escape(link.label()) + "</a>");
        }
        var web = plugin.settings().web();
        String imprint = !web.imprintUrl().isEmpty() ? web.imprintUrl() : base.isEmpty() ? "" : base + "/impressum";
        String privacy = !web.privacyUrl().isEmpty() ? web.privacyUrl() : base.isEmpty() ? "" : base + "/datenschutz";
        var m = plugin.messages();
        if (!imprint.isEmpty()) {
            foot.add("<a href=\"" + attr(imprint) + "\" style=\"color:#6b7280\">" + escape(m.server(() -> m.plain("mail-imprint"))) + "</a>");
        }
        if (!privacy.isEmpty()) {
            foot.add("<a href=\"" + attr(privacy) + "\" style=\"color:#6b7280\">" + escape(m.server(() -> m.plain("mail-privacy"))) + "</a>");
        }
        String footer = (b.footerText().isEmpty() ? "" : "<div style=\"margin-bottom:6px\">" + escape(b.footerText()) + "</div>")
                + String.join(" &middot; ", foot);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + escape(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f4f6\"><tr>"
                + "<td align=\"center\" style=\"padding:24px 12px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;"
                + "background:#ffffff;border-radius:12px;overflow:hidden\">"
                + "<tr><td style=\"background:" + accent + ";color:" + on + ";padding:18px 24px;font-size:18px;font-weight:700\">"
                + head + "</td></tr>"
                + "<tr><td style=\"padding:24px;color:#1f2937;font-size:15px;line-height:1.55\">" + body + "</td></tr>"
                + (footer.isEmpty() ? "" : "<tr><td style=\"padding:14px 24px;background:#f9fafb;color:#6b7280;font-size:12px;"
                        + "line-height:1.5\">" + footer + "</td></tr>")
                + "</table></td></tr></table></body></html>";
    }

    /** Escapes the text and turns addresses into links. */
    private static String links(String text) {
        Matcher matcher = URL.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(escape(text.substring(last, matcher.start())));
            String url = matcher.group();
            out.append("<a href=\"").append(attr(url)).append("\" style=\"color:#2563eb\">").append(escape(url)).append("</a>");
            last = matcher.end();
        }
        return out.append(escape(text.substring(last))).toString();
    }

    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String attr(String text) {
        return escape(text).replace("'", "&#39;");
    }

    private static String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\x00-\\x1f]", " ");
    }
}
