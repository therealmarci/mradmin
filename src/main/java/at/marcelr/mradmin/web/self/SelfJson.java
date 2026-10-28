package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.api.SelfBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the building blocks of the modules ({@link SelfBlock}) into plain JSON for the player area - each with a
 * "type" the web interface draws (stats, text, table, buttons, ranking, map, meters, cards, people, form, embed,
 * group).
 * Colours of map areas are checked (only "#RRGGBB"), links and frames only http(s), everything else is shown as text,
 * never as HTML.
 */
final class SelfJson {

    private SelfJson() {
    }

    static List<Map<String, Object>> blocks(List<SelfBlock> blocks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SelfBlock block : blocks == null ? List.<SelfBlock>of() : blocks) {
            Map<String, Object> m;
            try {
                m = block(block);
            } catch (RuntimeException e) {
                m = null; // a broken or unknown block (newer API than this MRadmin) - leave it out, not the whole page
            }
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
            case SelfBlock.Meters x -> {
                m.put("type", "meters");
                m.put("title", text(x.title()));
                m.put("text", text(x.text()));
                m.put("meters", meters(x.meters()));
            }
            case SelfBlock.Cards x -> {
                m.put("type", "cards");
                m.put("title", text(x.title()));
                m.put("empty", text(x.empty()));
                List<Map<String, Object>> cards = new ArrayList<>();
                for (SelfBlock.Card c : list(x.cards())) {
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("id", text(c.id()));
                    card.put("title", text(c.title()));
                    card.put("subtitle", text(c.subtitle()));
                    card.put("badge", text(c.badge()));
                    card.put("tone", tone(c.tone()));
                    card.put("stats", stats(c.stats()));
                    card.put("meters", meters(c.meters()));
                    card.put("primary", c.primary() == null ? null : buttons(List.of(c.primary())).getFirst());
                    card.put("buttons", buttons(c.buttons()));
                    card.put("details", blocks(c.details()));
                    cards.add(card);
                }
                m.put("cards", cards);
            }
            case SelfBlock.People x -> {
                m.put("type", "people");
                m.put("title", text(x.title()));
                m.put("text", text(x.text()));
                m.put("empty", text(x.empty()));
                List<Map<String, Object>> people = new ArrayList<>();
                for (SelfBlock.Person p : list(x.people())) {
                    Map<String, Object> person = new LinkedHashMap<>();
                    person.put("uuid", p.player().toString());
                    person.put("name", text(p.name()));
                    person.put("detail", text(p.detail()));
                    person.put("badge", text(p.badge()));
                    person.put("tone", tone(p.tone()));
                    person.put("buttons", buttons(p.buttons()));
                    people.add(person);
                }
                m.put("people", people);
            }
            case SelfBlock.Form x -> {
                m.put("type", "form");
                m.put("title", text(x.title()));
                m.put("text", text(x.text()));
                m.put("submit", text(x.submit()));
                m.put("action", text(x.action()));
                m.put("args", x.args() == null ? Map.of() : x.args());
                List<Map<String, Object>> fields = new ArrayList<>();
                for (SelfBlock.Field f : list(x.fields())) {
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("key", text(f.key()));
                    field.put("label", text(f.label()));
                    field.put("type", "select".equals(f.type()) ? "select" : "text");
                    field.put("value", text(f.value()));
                    field.put("options", list(f.options()).stream()
                            .map(o -> Map.of("value", text(o.value()), "label", text(o.label()))).toList());
                    field.put("maxLength", Math.clamp(f.maxLength(), 1, 200));
                    fields.add(field);
                }
                m.put("fields", fields);
            }
            case SelfBlock.Group x -> {
                m.put("type", "group");
                m.put("title", text(x.title()));
                m.put("text", text(x.text()));
                m.put("blocks", blocks(x.blocks()));
            }
            case SelfBlock.Embed x -> {
                String url = link(x.url());
                if (url == null) {
                    return null;
                }
                m.put("type", "embed");
                m.put("title", text(x.title()));
                m.put("url", url);
                m.put("height", Math.clamp(x.height(), 200, 900));
                m.put("linkLabel", text(x.linkLabel()));
            }
        }
        return m;
    }

    private static List<Map<String, Object>> stats(List<SelfBlock.Stat> stats) {
        return list(stats).stream().map(x -> Map.<String, Object>of("label", text(x.label()), "value", text(x.value()),
                "tone", tone(x.tone()))).toList();
    }

    private static List<Map<String, Object>> meters(List<SelfBlock.Meter> meters) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SelfBlock.Meter x : list(meters)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", text(x.label()));
            m.put("value", text(x.value()));
            m.put("fill", Double.isFinite(x.fill()) ? Math.clamp(x.fill(), 0, 1) : 0);
            m.put("mark", Double.isFinite(x.mark()) && x.mark() >= 0 ? Math.min(1, x.mark()) : -1);
            m.put("hint", text(x.hint()));
            m.put("tone", tone(x.tone()));
            result.add(m);
        }
        return result;
    }

    private static <T> List<T> list(List<T> list) {
        return list == null ? List.of() : list;
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
