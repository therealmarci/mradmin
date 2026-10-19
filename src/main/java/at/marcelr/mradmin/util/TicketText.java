package at.marcelr.mradmin.util;

/** Cleaning and shortening of texts players type into ticket forms. */
public final class TicketText {

    private TicketText() {
    }

    /**
     * Trims the text, unifies line breaks, removes control characters and collapses more than one empty line.
     * {@code null} becomes "".
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(unified.length());
        for (int i = 0; i < unified.length(); i++) {
            char c = unified.charAt(i);
            if (c == '\t') {
                out.append(' ');
            } else if (c == '\n' || !Character.isISOControl(c)) {
                out.append(c);
            }
        }
        String result = out.toString().replaceAll("[ ]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        return result.strip();
    }

    /** Cuts the text to {@code max} characters (without "…"), e.g. as a safety net behind the form's own limit. */
    public static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).strip();
    }

    /** One line of at most {@code max} characters for chat and item lore: line breaks become spaces, "…" if cut. */
    public static String preview(String text, int max) {
        String line = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        if (line.length() <= max) {
            return line;
        }
        return line.substring(0, Math.max(1, max - 1)).strip() + "…";
    }
}
