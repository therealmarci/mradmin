package at.marcelr.mradmin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The form of a config file in the web interface, read from a form description (the plugin's {@code web-config.yml},
 * format in docs/MODULE-API.md): sections with fields, each with a type, limits and names in German and English.
 * <ul>
 *     <li>{@link #view} turns the file into what the browser shows - secrets only as "set" / "empty".</li>
 *     <li>{@link #apply} checks the changes the browser sends against the form and writes them into the file. Only keys
 *     of the form can be changed, every value must fit its type and limits.</li>
 * </ul>
 * No Bukkit server needed (only its YAML classes), so it is tested on its own.
 */
public final class ConfigForm {

    public enum Type { BOOL, INT, DECIMAL, STRING, TEXT, SECRET, CHOICE, COLOR, DURATION, STRINGS, INTS, CHOICES, WORLDS, TABLE }

    /** A text in German and English (missing: the other one). */
    public record Text(String de, String en) {
        Map<String, String> json() {
            return Map.of("de", de, "en", en);
        }
    }

    public record Option(String value, Text name) {
    }

    /**
     * A column of a table: one value per row, e.g. "enabled" (bool) and "color" (color) of a Discord event.
     * {@code fallback}: the value while a row does not have the key (e.g. "enabled: true" of ticket categories).
     */
    public record Column(String key, Type type, Text name, Double min, Double max, Object fallback) {
    }

    /**
     * @param when     shown only if this is true: "key" (a bool is on), "!key" (off), "key=a|b" (one of the values)
     * @param count    lists: exactly this many entries (0 = any)
     * @param item     lists: "duration" = every entry is a duration
     * @param permanent durations: "permanent" is allowed too
     * @param rows     tables: names of the rows (rows without a name show their key)
     */
    public record Field(String key, Type type, Text name, Text help, Text unit, boolean restart, String when,
                        Double min, Double max, int maxLength, int count, String item, boolean permanent,
                        List<Option> options, List<Column> columns, Map<String, Text> rows) {
    }

    public record Section(String id, Text name, Text help, String icon, List<Field> fields) {
    }

    /** A value that does not fit the form: {@code key} of the field, {@code error} the error key for the browser. */
    public static final class Invalid extends Exception {
        public final String key;
        public final String error;

        Invalid(String key, String error) {
            super(key + ": " + error);
            this.key = key;
            this.error = error;
        }
    }

    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern DURATION = Pattern.compile("(\\d+[dhms])+|\\d+");
    private static final Pattern ROW_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final int MAX_TEXT = 2000;
    private static final int MAX_LIST = 100;

    private final List<Section> sections;
    private final Map<String, Field> fields = new LinkedHashMap<>();

    private ConfigForm(List<Section> sections) {
        this.sections = sections;
        for (Section s : sections) {
            for (Field f : s.fields()) {
                fields.put(f.key(), f);
            }
        }
    }

    public List<Section> sections() {
        return sections;
    }

    public Field field(String key) {
        return fields.get(key);
    }

    // -------------------------------------------------------------------- reading the description

    public static ConfigForm parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration form = new YamlConfiguration();
        form.loadFromString(strip(yaml));
        List<Section> sections = new ArrayList<>();
        for (Map<?, ?> s : form.getMapList("sections")) {
            String id = string(s.get("id"));
            List<Field> fields = new ArrayList<>();
            if (s.get("fields") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> f) {
                        fields.add(field(f));
                    }
                }
            }
            sections.add(new Section(id, text(s.get("name"), id), text(s.get("help"), ""), string(s.get("icon")), List.copyOf(fields)));
        }
        return new ConfigForm(List.copyOf(sections));
    }

    private static Field field(Map<?, ?> f) throws InvalidConfigurationException {
        String key = string(f.get("key"));
        if (key.isEmpty()) {
            throw new InvalidConfigurationException("field without key");
        }
        Type type;
        try {
            type = Type.valueOf(string(f.get("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("field " + key + ": unknown type " + f.get("type"));
        }
        List<Option> options = new ArrayList<>();
        if (f.get("options") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String value = string(m.get("value"));
                    options.add(new Option(value, new Text(string(m.containsKey("de") ? m.get("de") : value),
                            string(m.containsKey("en") ? m.get("en") : value))));
                } else {
                    options.add(new Option(string(o), new Text(string(o), string(o))));
                }
            }
        }
        if ((type == Type.CHOICE || type == Type.CHOICES) && options.isEmpty()) {
            throw new InvalidConfigurationException("field " + key + ": choice without options");
        }
        List<Column> columns = new ArrayList<>();
        if (f.get("columns") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> c) {
                    Type ct = Type.valueOf(string(c.get("type")).toUpperCase(Locale.ROOT));
                    if (ct != Type.BOOL && ct != Type.COLOR && ct != Type.INT && ct != Type.STRING) {
                        throw new InvalidConfigurationException("field " + key + ": column type " + ct + " is not allowed");
                    }
                    columns.add(new Column(string(c.get("key")), ct, text(c.get("name"), string(c.get("key"))),
                            number(c.get("min")), number(c.get("max")), c.get("default")));
                }
            }
        }
        if (type == Type.TABLE && columns.isEmpty()) {
            throw new InvalidConfigurationException("field " + key + ": table without columns");
        }
        Map<String, Text> rows = new LinkedHashMap<>();
        if (f.get("rows") instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                rows.put(string(e.getKey()), text(e.getValue(), string(e.getKey())));
            }
        }
        int maxLength = f.get("max-length") instanceof Number n ? n.intValue() : MAX_TEXT;
        return new Field(key, type, text(f.get("name"), key), text(f.get("help"), ""), text(f.get("unit"), ""),
                Boolean.TRUE.equals(f.get("restart")), string(f.get("when")), number(f.get("min")), number(f.get("max")),
                Math.clamp(maxLength, 1, MAX_TEXT), f.get("count") instanceof Number n ? n.intValue() : 0,
                string(f.get("item")), Boolean.TRUE.equals(f.get("permanent")), List.copyOf(options), List.copyOf(columns),
                rows);
    }

    private static Text text(Object value, String fallback) {
        if (value instanceof Map<?, ?> m) {
            String de = string(m.get("de"));
            String en = string(m.get("en"));
            return new Text(de.isEmpty() ? en : de, en.isEmpty() ? de : en);
        }
        String plain = value == null ? fallback : string(value);
        return new Text(plain, plain);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    static String strip(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    // -------------------------------------------------------------------- for the browser

    /** Sections, fields and values of the file ({@code defaults}: the bundled config). Secrets are never sent. */
    public List<Map<String, Object>> view(YamlConfiguration file, YamlConfiguration defaults) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Section s : sections) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Field f : s.fields()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", f.key());
                m.put("type", f.type().name().toLowerCase(Locale.ROOT));
                m.put("name", f.name().json());
                m.put("help", f.help().json());
                m.put("unit", f.unit().json());
                m.put("restart", f.restart());
                m.put("when", f.when());
                m.put("min", f.min());
                m.put("max", f.max());
                m.put("maxLength", f.maxLength());
                m.put("count", f.count());
                m.put("item", f.item());
                m.put("permanent", f.permanent());
                if (!f.options().isEmpty()) {
                    m.put("options", f.options().stream().map(o -> Map.of("value", o.value(), "name", o.name().json())).toList());
                }
                if (f.type() == Type.TABLE) {
                    m.put("columns", f.columns().stream().map(c -> {
                        Map<String, Object> cm = new LinkedHashMap<>();
                        cm.put("key", c.key());
                        cm.put("type", c.type().name().toLowerCase(Locale.ROOT));
                        cm.put("name", c.name().json());
                        cm.put("min", c.min());
                        cm.put("max", c.max());
                        return cm;
                    }).toList());
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (String row : rowKeys(f, file, defaults)) {
                        Text name = f.rows().getOrDefault(row, new Text(row, row));
                        rows.add(Map.of("key", row, "name", name.json()));
                    }
                    m.put("rows", rows);
                }
                if (f.type() == Type.SECRET) {
                    m.put("value", "");
                    m.put("set", !file.getString(f.key(), "").isBlank());
                    m.put("default", "");
                } else {
                    m.put("value", read(f, file, defaults, false));
                    m.put("default", read(f, defaults, defaults, true));
                }
                list.add(m);
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.id());
            sm.put("name", s.name().json());
            sm.put("help", s.help().json());
            sm.put("icon", s.icon());
            sm.put("fields", list);
            result.add(sm);
        }
        return result;
    }

    /** Rows of a table: those of the bundled config first, then extra ones in the file. */
    private static Set<String> rowKeys(Field f, YamlConfiguration file, YamlConfiguration defaults) {
        Set<String> rows = new LinkedHashSet<>();
        for (YamlConfiguration c : List.of(defaults, file)) {
            ConfigurationSection section = c.getConfigurationSection(f.key());
            if (section != null) {
                rows.addAll(section.getKeys(false));
            }
        }
        return rows;
    }

    /** The value as the browser shows it (missing in the file: the default). */
    private Object read(Field f, YamlConfiguration c, YamlConfiguration defaults, boolean isDefault) {
        Object raw = c.isSet(f.key()) ? c.get(f.key()) : defaults.get(f.key());
        return switch (f.type()) {
            case BOOL -> raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
            case INT -> raw instanceof Number n ? (Object) n.longValue() : parseLong(raw);
            case DECIMAL -> raw instanceof Number n ? (Object) n.doubleValue() : parseDouble(raw);
            case STRINGS, CHOICES, WORLDS -> raw instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            case INTS -> raw instanceof List<?> l ? l.stream().map(ConfigForm::parseLong).toList() : List.of();
            case TABLE -> {
                Map<String, Object> rows = new LinkedHashMap<>();
                for (String row : isDefault ? rowKeys(f, defaults, defaults) : rowKeys(f, c, defaults)) {
                    Map<String, Object> cells = new LinkedHashMap<>();
                    for (Column col : f.columns()) {
                        String path = f.key() + "." + row + "." + col.key();
                        Object v = c.isSet(path) ? c.get(path) : defaults.isSet(path) ? defaults.get(path) : col.fallback();
                        cells.put(col.key(), switch (col.type()) {
                            case BOOL -> v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
                            case INT -> v instanceof Number n ? (Object) n.longValue() : parseLong(v);
                            default -> v == null ? "" : String.valueOf(v);
                        });
                    }
                    rows.put(row, cells);
                }
                yield rows;
            }
            default -> raw == null ? "" : String.valueOf(raw);
        };
    }

    private static Object parseLong(Object raw) {
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object parseDouble(Object raw) {
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------- changes from the browser

    /**
     * Checks all changes first and only then writes them into {@code file} - either all or none.
     *
     * @param changes key of a field -> new value (tables: row -> column -> value, only the changed cells are needed)
     * @return the keys that really changed
     * @throws Invalid for the first value that does not fit
     */
    public List<String> apply(YamlConfiguration file, YamlConfiguration defaults, Map<String, Object> changes) throws Invalid {
        Map<String, Object> checked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            Field f = fields.get(e.getKey());
            if (f == null) {
                throw new Invalid(e.getKey(), "config-unknown-key");
            }
            if (f.type() == Type.TABLE) {
                if (!(e.getValue() instanceof Map<?, ?> rows)) {
                    throw new Invalid(f.key(), "config-invalid");
                }
                Set<String> known = rowKeys(f, file, defaults);
                for (Map.Entry<?, ?> row : rows.entrySet()) {
                    String rowKey = String.valueOf(row.getKey());
                    if (!known.contains(rowKey) || !ROW_KEY.matcher(rowKey).matches() || !(row.getValue() instanceof Map<?, ?> cells)) {
                        throw new Invalid(f.key(), "config-invalid");
                    }
                    for (Map.Entry<?, ?> cell : cells.entrySet()) {
                        Column col = f.columns().stream().filter(c -> c.key().equals(String.valueOf(cell.getKey()))).findFirst()
                                .orElseThrow(() -> new Invalid(f.key(), "config-invalid"));
                        Field asField = new Field(f.key(), col.type(), col.name(), f.help(), f.unit(), false, "", col.min(), col.max(),
                                200, 0, "", false, List.of(), List.of(), Map.of());
                        checked.put(f.key() + "." + rowKey + "." + col.key(), check(asField, cell.getValue()));
                    }
                }
            } else {
                checked.put(f.key(), check(f, e.getValue()));
            }
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> e : checked.entrySet()) {
            Object old = file.get(e.getKey());
            if (old == null ? e.getValue() != null : !equal(old, e.getValue())) {
                file.set(e.getKey(), e.getValue());
                changed.add(e.getKey());
            }
        }
        return changed;
    }

    private static boolean equal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            return x.stream().map(String::valueOf).toList().equals(y.stream().map(String::valueOf).toList());
        }
        return a.equals(b);
    }

    /** The value as it goes into the file, or {@link Invalid}. */
    static Object check(Field f, Object value) throws Invalid {
        String key = f.key();
        switch (f.type()) {
            case BOOL -> {
                if (value instanceof Boolean b) {
                    return b;
                }
                throw new Invalid(key, "config-invalid");
            }
            case INT -> {
                if (!(value instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                    throw new Invalid(key, "config-number");
                }
                range(f, n.doubleValue());
                long l = n.longValue();
                return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
            }
            case DECIMAL -> {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    throw new Invalid(key, "config-number");
                }
                range(f, n.doubleValue());
                return n.doubleValue();
            }
            case CHOICE -> {
                String s = String.valueOf(value);
                if (f.options().stream().noneMatch(o -> o.value().equals(s))) {
                    throw new Invalid(key, "config-invalid");
                }
                return s;
            }
            case COLOR -> {
                String s = String.valueOf(value).trim();
                if (!COLOR.matcher(s).matches()) {
                    throw new Invalid(key, "config-color");
                }
                return s.toUpperCase(Locale.ROOT);
            }
            case DURATION -> {
                return duration(f, String.valueOf(value));
            }
            case STRINGS, WORLDS -> {
                if (!(value instanceof List<?> list) || list.size() > MAX_LIST) {
                    throw new Invalid(key, "config-invalid");
                }
                if (f.count() > 0 && list.size() != f.count()) {
                    throw new Invalid(key, "config-count");
                }
                List<String> result = new ArrayList<>();
                for (Object o : list) {
                    String s = o == null ? "" : String.valueOf(o).trim();
                    if (s.isEmpty() || s.length() > 200 || s.indexOf('\n') >= 0) {
                        throw new Invalid(key, "config-invalid");
                    }
                    result.add("duration".equals(f.item()) ? duration(f, s) : s);
                }
                return result;
            }
            case CHOICES -> {
                if (!(value instanceof List<?> list) || list.size() > MAX_LIST) {
                    throw new Invalid(key, "config-invalid");
                }
                List<String> result = new ArrayList<>();
                for (Object o : list) {
                    String s = String.valueOf(o);
                    if (f.options().stream().noneMatch(op -> op.value().equals(s))) {
                        throw new Invalid(key, "config-invalid");
                    }
                    if (!result.contains(s)) {
                        result.add(s);
                    }
                }
                return result;
            }
            case INTS -> {
                if (!(value instanceof List<?> list) || list.size() > MAX_LIST) {
                    throw new Invalid(key, "config-invalid");
                }
                if (f.count() > 0 && list.size() != f.count()) {
                    throw new Invalid(key, "config-count");
                }
                List<Integer> result = new ArrayList<>();
                for (Object o : list) {
                    if (!(o instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                            || Math.abs(n.doubleValue()) > Integer.MAX_VALUE) {
                        throw new Invalid(key, "config-number");
                    }
                    range(f, n.doubleValue());
                    result.add(n.intValue());
                }
                return result;
            }
            case STRING, SECRET, TEXT -> {
                if (!(value instanceof String s)) {
                    throw new Invalid(key, "config-invalid");
                }
                if (s.length() > f.maxLength()) {
                    throw new Invalid(key, "config-too-long");
                }
                if (f.type() != Type.TEXT && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0)) {
                    throw new Invalid(key, "config-invalid");
                }
                return f.type() == Type.TEXT ? s : s.trim();
            }
            default -> throw new Invalid(key, "config-invalid");
        }
    }

    private static String duration(Field f, String raw) throws Invalid {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (f.permanent() && s.equals("permanent")) {
            return s;
        }
        if (!DURATION.matcher(s).matches()) {
            throw new Invalid(f.key(), "config-duration");
        }
        return s;
    }

    private static void range(Field f, double v) throws Invalid {
        if ((f.min() != null && v < f.min()) || (f.max() != null && v > f.max())) {
            throw new Invalid(f.key(), "config-range");
        }
    }
}
