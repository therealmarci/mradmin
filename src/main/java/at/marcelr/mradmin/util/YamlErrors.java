package at.marcelr.mradmin.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns SnakeYAML error messages into something an admin can act on. */
public final class YamlErrors {

    private static final Pattern LINE = Pattern.compile("line (\\d+)");

    private YamlErrors() {
    }

    /**
     * SnakeYAML errors are "while parsing ..." / " in 'string', line 84, column 3:" / indented excerpt / "expected ...".
     * Keeps the last message line (not indented) and the last line number - where the parser gave up, usually right
     * after the typo.
     *
     * @return [problem, line] (line "?" if unknown)
     */
    public static String[] describe(String message) {
        if (message == null || message.isBlank()) {
            return new String[]{"?", "?"};
        }
        Matcher m = LINE.matcher(message);
        String line = "?";
        while (m.find()) {
            line = m.group(1);
        }
        String problem = message.lines()
                .filter(l -> !l.isBlank() && !Character.isWhitespace(l.charAt(0)))
                .reduce((a, b) -> b).orElse(message.trim()).replaceAll(":$", "");
        return new String[]{problem, line};
    }
}
