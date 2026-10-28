package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.api.SelfBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the building blocks of the modules ({@link SelfBlock}) into plain JSON for the player area - each with a
 * "type" the web interface draws (stats, text, table, buttons, ranking, map). Colours of map areas are checked (only
 * "#RRGGBB"), everything else is shown as text, never as HTML.
 */
final class SelfJson {

    private SelfJson() {
    }

    static List<Map<String, Object>> blocks(List<SelfBlock> blocks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SelfBlock block : blocks == null ? List.<SelfBlock>of() : blocks) {
            Map<String, Object> m = block(block);
            if (m != null) {
                result.add(m);
            }
        }
        return result;
    }

    private static Map<String, Object> block(SelfBlock block) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (block) {
            case SelfBlock.Stats s -> {
                m.put("type", "stats");
                List<Map<String, Object>> stats = new ArrayList<>();
                s.stats().forEach(x -> stats.add(Map.of("label", text(x.label()), "value", text(x.value()), "tone", tone(x.tone()))));
                m.put("stats", stats);
            }
            case SelfBlock.Text t -> {
                m.put("type", "text");
                m.put("text", text(t.text()));
                m.put("tone", tone(t.tone()));
            }
            case SelfBlock.Table t -> {
                m.put("type", "table");
                m.put("title", text(t.title()));
                m.put("columns", t.columns().stream().map(SelfJson::text).toList());
                List<Map<String, Object>> rows = new ArrayList<>();
                t.rows().forEach(r -> rows.add(Map.of("cells", r.cells().stream().map(SelfJson::text).toList(),
                        "tone", tone(r.tone()), "buttons", buttons(r.buttons()))));
                m.put("rows", rows);
                m.put("empty", text(t.empty()));
            }
            case SelfBlock.Buttons b -> {
                m.put("type", "buttons");
                m.put("buttons", buttons(b.buttons()));
            }
            case SelfBlock.Ranking r -> {
                m.put("type", "ranking");
                m.put("title", text(r.title()));
                List<Map<String, Object>> places = new ArrayList<>();
                r.places().forEach(p -> places.add(Map.of("rank", p.rank(), "uuid", p.player().toString(),
                        "name", text(p.name()), "value", text(p.value()), "self", p.self())));
                m.put("places", places);
                m.put("empty", text(r.empty()));
            }
            case SelfBlock.AreaMap a -> {
                m.put("type", "map");
                m.put("title", text(a.title()));
                List<Map<String, Object>> rects = new ArrayList<>();
                a.rects().forEach(r -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("world", text(r.world()));
                    x.put("minX", r.minX());
                    x.put("minZ", r.minZ());
                    x.put("maxX", r.maxX());
                    x.put("maxZ", r.maxZ());
                    x.put("label", text(r.label()));
                    x.put("color", r.color() != null && r.color().matches("#[0-9A-Fa-f]{6}") ? r.color() : "#2ECC71");
                    x.put("link", link(r.link()));
                    rects.add(x);
                });
                m.put("rects", rects);
                m.put("link", link(a.link()));
                m.put("linkLabel", text(a.linkLabel()));
            }
        }
        return m;
    }

    private static List<Map<String, Object>> buttons(List<SelfBlock.Button> buttons) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SelfBlock.Button b : buttons == null ? List.<SelfBlock.Button>of() : buttons) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", text(b.label()));
            m.put("action", text(b.action()));
            m.put("args", b.args() == null ? Map.of() : b.args());
            m.put("tone", tone(b.tone()));
            m.put("confirm", b.confirm());
            if (b.input() != null) {
                m.put("input", Map.of("label", text(b.input().label()), "value", text(b.input().value()),
                        "maxLength", Math.clamp(b.input().maxLength(), 1, 200)));
            }
            result.add(m);
        }
        return result;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String tone(String tone) {
        return tone != null && List.of("good", "info", "warn", "bad").contains(tone) ? tone : "";
    }

    /** Only http(s) links - nothing a page could abuse (javascript: and the like). */
    private static String link(String link) {
        return link != null && (link.startsWith("https://") || link.startsWith("http://")) ? link : null;
    }
}
