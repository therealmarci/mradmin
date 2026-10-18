package at.marcelr.mradmin.maintenance;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Picks the maintenance that matters right now out of all known windows. Pure logic, no server access. */
public final class Planner {

    private Planner() {
    }

    /**
     * The running maintenance (several running ones are merged into one: earliest begin, latest end, open-ended
     * if any of them is), otherwise the next upcoming one, otherwise null.
     */
    public static Window choose(Collection<Window> windows, Instant now) {
        List<Window> active = windows.stream().filter(w -> w.activeAt(now)).toList();
        if (!active.isEmpty()) {
            Window first = active.stream()
                    .min(Comparator.comparing(Window::start, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            if (active.size() == 1) {
                return first;
            }
            boolean openEnded = active.stream().anyMatch(w -> w.end() == null);
            Instant end = openEnded ? null : active.stream().map(Window::end).max(Comparator.naturalOrder()).orElse(null);
            return new Window(first.source(), first.id(), first.title(), first.start(), end, true);
        }
        return windows.stream()
                .filter(w -> w.upcomingAt(now))
                .min(Comparator.comparing(Window::start))
                .orElse(null);
    }
}
