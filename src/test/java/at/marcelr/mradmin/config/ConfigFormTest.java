package at.marcelr.mradmin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFormTest {

    private static final String FORM = """
            sections:
              - id: sleep
                name: {de: Schlafen, en: Sleeping}
                fields:
                  - {key: sleep.percentage, type: int, min: 0, max: 100, name: {de: Prozent, en: Percentage}}
                  - {key: sleep.display, type: choice, options: [{value: bossbar, de: Leiste, en: Bar}, actionbar, none]}
                  - {key: sleep.enabled, type: bool}
                  - {key: nosleep.cooldown, type: duration}
                  - {key: gui.bans, type: strings, count: 2, item: duration, permanent: true}
                  - {key: discord.webhook-url, type: secret}
                  - {key: view.ranges, type: ints, min: 0, max: 500}
                  - key: discord.events
                    type: table
                    columns: [{key: enabled, type: bool}, {key: color, type: color}]
                    rows: {skip: {de: Nacht übersprungen, en: Night skipped}}
            """;

    private static final String DEFAULTS = """
            sleep:
              percentage: 50
              display: bossbar
              enabled: true
            nosleep:
              cooldown: 15m
            gui:
              bans: [1h, permanent]
            view:
              ranges: [10, 0]
            discord:
              webhook-url: ""
              events:
                skip: {enabled: true, color: "#F1C40F"}
                ban: {enabled: true, color: "#C0392B"}
            """;

    private static final String FILE = """
            # Percentage of players
            sleep:
              # how many
              percentage: 30
              display: actionbar
            discord:
              webhook-url: "https://discord.com/api/webhooks/1/secret"
            """;

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    @Test
    void readsTheFormAndShowsValuesWithDefaults() throws Exception {
        ConfigForm form = ConfigForm.parse(FORM);
        List<Map<String, Object>> view = form.view(yaml(FILE), yaml(DEFAULTS));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get(0).get("fields");
        assertEquals(30L, fields.get(0).get("value"));
        assertEquals(50L, fields.get(0).get("default"));
        assertEquals("actionbar", fields.get(1).get("value"));
        assertEquals(true, fields.get(2).get("value"));
        assertEquals("15m", fields.get(3).get("value"));
        // secrets never leave the server
        assertEquals("", fields.get(5).get("value"));
        assertEquals(true, fields.get(5).get("set"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> rows = (Map<String, Map<String, Object>>) fields.get(7).get("value");
        assertEquals(List.of(10L, 0L), fields.get(6).get("value"));
        assertEquals(List.of("skip", "ban"), List.copyOf(rows.keySet()));
        assertEquals("#C0392B", rows.get("ban").get("color"));
    }

    @Test
    void writesCheckedChangesAndKeepsComments() throws Exception {
        ConfigForm form = ConfigForm.parse(FORM);
        YamlConfiguration file = yaml(FILE);
        List<String> changed = form.apply(file, yaml(DEFAULTS), Map.of(
                "sleep.percentage", 75L,
                "gui.bans", List.of("2h", "PERMANENT"),
                "discord.events", Map.of("ban", Map.of("enabled", false, "color", "#00ff00"))));
        assertEquals(75, file.getInt("sleep.percentage"));
        assertEquals(List.of("2h", "permanent"), file.getStringList("gui.bans"));
        assertFalse(file.getBoolean("discord.events.ban.enabled"));
        assertEquals("#00FF00", file.getString("discord.events.ban.color"));
        assertEquals(4, changed.size());
        String saved = file.saveToString();
        assertTrue(saved.contains("# how many"), saved);
        assertTrue(saved.contains("# Percentage of players"), saved);
    }

    @Test
    void unchangedValuesAreNotWritten() throws Exception {
        ConfigForm form = ConfigForm.parse(FORM);
        assertTrue(form.apply(yaml(FILE), yaml(DEFAULTS), Map.of("sleep.percentage", 30L)).isEmpty());
    }

    @Test
    void refusesValuesThatDoNotFit() throws Exception {
        ConfigForm form = ConfigForm.parse(FORM);
        YamlConfiguration defaults = yaml(DEFAULTS);
        assertEquals("config-range", invalid(form, defaults, Map.of("sleep.percentage", 101L)));
        assertEquals("config-number", invalid(form, defaults, Map.of("sleep.percentage", 1.5)));
        assertEquals("config-invalid", invalid(form, defaults, Map.of("sleep.display", "title")));
        assertEquals("config-invalid", invalid(form, defaults, Map.of("sleep.enabled", "yes")));
        assertEquals("config-duration", invalid(form, defaults, Map.of("nosleep.cooldown", "15 minutes")));
        assertEquals("config-count", invalid(form, defaults, Map.of("gui.bans", List.of("1h"))));
        assertEquals("config-duration", invalid(form, defaults, Map.of("gui.bans", List.of("1h", "soon"))));
        assertEquals("config-unknown-key", invalid(form, defaults, Map.of("config-version", 99L)));
        assertEquals("config-invalid", invalid(form, defaults, Map.of("discord.events", Map.of("unknown", Map.of("enabled", true)))));
        assertEquals("config-color", invalid(form, defaults, Map.of("discord.events", Map.of("skip", Map.of("color", "red")))));
        assertEquals("config-invalid", invalid(form, defaults, Map.of("discord.webhook-url", "a\nb")));
        assertEquals("config-range", invalid(form, defaults, Map.of("view.ranges", List.of(10L, 900L))));
        assertEquals("config-number", invalid(form, defaults, Map.of("view.ranges", List.of(1.5))));
        assertEquals("accepted", invalid(form, defaults, Map.of("view.ranges", List.of(5L, 0L))));
    }

    @Test
    void allOrNothing() throws Exception {
        ConfigForm form = ConfigForm.parse(FORM);
        YamlConfiguration file = yaml(FILE);
        java.util.Map<String, Object> changes = new java.util.LinkedHashMap<>();
        changes.put("sleep.percentage", 80L);
        changes.put("sleep.display", "nope");
        assertThrows(ConfigForm.Invalid.class, () -> form.apply(file, yaml(DEFAULTS), changes));
        assertEquals(30, file.getInt("sleep.percentage"));
    }

    private static String invalid(ConfigForm form, YamlConfiguration defaults, Map<String, Object> changes) throws Exception {
        try {
            form.apply(yaml(FILE), defaults, changes);
            return "accepted";
        } catch (ConfigForm.Invalid e) {
            return e.error;
        }
    }
}
