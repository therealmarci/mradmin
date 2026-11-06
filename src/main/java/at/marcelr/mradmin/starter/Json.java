package at.marcelr.mradmin.starter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for the starter. The starter runs before Paper (plain {@code java -jar}), so Gson - which Paper
 * brings - is not there. Objects become {@link Map}, arrays {@link List}, numbers {@link Double} or {@link Long}.
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text);
        json.space();
        Object value = json.value();
        json.space();
        if (json.pos != text.length()) {
            throw json.error("unexpected text after the value");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public static List<Object> array(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    public static String string(Object value) {
        return value instanceof String s ? s : null;
    }

    public static long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private Object value() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> obj();
            case '[' -> arr();
            case '"' -> str();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> num();
        };
    }

    private Map<String, Object> obj() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        space();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            space();
            if (!peek('"')) {
                throw error("expected a name");
            }
            String key = str();
            space();
            expect(':');
            space();
            map.put(key, value());
            space();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> arr() {
        List<Object> list = new ArrayList<>();
        pos++;
        space();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            space();
            list.add(value());
            space();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String str() {
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("broken \\u escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> out.append(e);
            }
        }
        throw error("unclosed string");
    }

    private Object num() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String n = text.substring(start, pos);
        if (n.isEmpty()) {
            throw error("unexpected character '" + text.charAt(start) + "'");
        }
        if (n.contains(".") || n.contains("e") || n.contains("E")) {
            return Double.parseDouble(n);
        }
        return Long.parseLong(n);
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected word");
        }
        pos += word.length();
        return value;
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private void space() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Invalid JSON at " + pos + ": " + what);
    }
}
