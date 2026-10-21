package at.marcelr.mradmin.announce;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Arguments of {@code /mradmin announce} for scripts: words, and {@code name=value} pairs whose value may be quoted
 * ({@code subtitle="in 5 minutes"}, {@code \"} for a quote inside). Example from a restart script:
 * <pre>
 *   mradmin announce title="&lt;red&gt;Restart" subtitle="in &lt;white&gt;5 minutes" sound=BLOCK_NOTE_BLOCK_BELL
 *   mradmin announce event what="Build contest"     (own preset with &lt;what&gt;)
 * </pre>
 *
 * @param words  words that are not pairs (preset name and its values, or a plain text)
 * @param values pairs in the given order, names in lower case
 */
public record AnnounceArgs(List<String> words, Map<String, String> values) {

    /** Names that control the announcement itself; every other name is a value for the preset's placeholders. */
    public static final Set<String> PARTS = Set.of("title", "subtitle", "chat", "actionbar", "discord", "sound",
            "volume", "pitch", "stay", "fade-in", "fade-out", "to", "id");

    public static AnnounceArgs parse(String raw) {
        List<String> words = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        int i = 0;
        int length = raw.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(raw.charAt(i))) {
                i++;
            }
            if (i >= length) {
                break;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(raw.charAt(i)) && raw.charAt(i) != '=' && raw.charAt(i) != '"') {
                i++;
            }
            boolean pair = i < length && raw.charAt(i) == '=' && i > start && isName(raw.substring(start, i));
            if (pair) {
                String name = raw.substring(start, i).toLowerCase(Locale.ROOT);
                i++;
                StringBuilder value = new StringBuilder();
                i = readValue(raw, i, value);
                values.put(name, value.toString());
            } else {
                StringBuilder word = new StringBuilder();
                i = readValue(raw, start, word);
                words.add(word.toString());
            }
        }
        return new AnnounceArgs(List.copyOf(words), values);
    }

    /** Whether the announcement is built from parts (title=, subtitle= ...) instead of a preset or plain text. */
    public boolean hasParts() {
        return values.keySet().stream().anyMatch(PARTS::contains);
    }

    /** The values that are no parts - placeholders for a preset ({@code time="5m"} fills {@code <time>}). */
    public Map<String, String> placeholders() {
        Map<String, String> result = new LinkedHashMap<>(values);
        result.keySet().removeAll(PARTS);
        return result;
    }

    /** Reads a quoted or plain value starting at {@code i}; returns the index after it. */
    private static int readValue(String raw, int i, StringBuilder out) {
        int length = raw.length();
        if (i < length && raw.charAt(i) == '"') {
            i++;
            while (i < length && raw.charAt(i) != '"') {
                if (raw.charAt(i) == '\\' && i + 1 < length) {
                    i++;
                }
                out.append(raw.charAt(i));
                i++;
            }
            return Math.min(length, i + 1);
        }
        while (i < length && !Character.isWhitespace(raw.charAt(i))) {
            out.append(raw.charAt(i));
            i++;
        }
        return i;
    }

    private static boolean isName(String text) {
        return text.matches("[A-Za-z][A-Za-z0-9_-]*");
    }
}
