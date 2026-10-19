package at.marcelr.mradmin.util;

import java.util.List;
import java.util.Locale;

/** The block list of the web console (web.console.blocked). */
public final class CommandFilter {

    private CommandFilter() {
    }

    /**
     * The blocked entry the command starts with, or null. Every part after "run" (execute ... run &lt;command&gt;)
     * counts as a command of its own; a leading "/" and namespaces ("minecraft:op") are ignored, case and extra spaces
     * too. Entries may have several words ("mradmin web").
     */
    public static String blocked(String command, List<String> blocked) {
        String normalized = command.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        for (String part : normalized.split(" run ")) {
            String p = part.strip();
            if (p.startsWith("/")) {
                p = p.substring(1);
            }
            int space = p.indexOf(' ');
            String first = space < 0 ? p : p.substring(0, space);
            int colon = first.lastIndexOf(':');
            if (colon >= 0) {
                p = first.substring(colon + 1) + (space < 0 ? "" : p.substring(space));
            }
            for (String entry : blocked) {
                String e = entry.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
                if (e.startsWith("/")) {
                    e = e.substring(1);
                }
                if (!e.isEmpty() && (p.equals(e) || p.startsWith(e + " "))) {
                    return entry;
                }
            }
        }
        return null;
    }
}
