package at.marcelr.mradmin.discord;

import at.marcelr.mradmin.maintenance.Window;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides what the community Discord shows about maintenance. Every maintenance gets exactly one message that lives
 * through its states - scheduled, running, then ended or cancelled - and is edited instead of posting again.
 * Pure logic without server or Discord access: {@link #update} returns what has to be posted or edited.
 * <p>
 * Each maintenance is followed on its own (several at once are possible). Rules:
 * <ul>
 *     <li>A new maintenance is posted once it is running or at most {@code announceAhead} away (and at least
 *     {@code minLength} long). Only this first post may ping.</li>
 *     <li>Start and end are edited in right away; new times and titles are combined over {@code debounce}.</li>
 *     <li>A scheduled maintenance that disappears before its start is cancelled; once its start has passed (e.g. the
 *     server was offline meanwhile) it counts as held and ended.</li>
 *     <li>The actual begin and end are kept, so an early end shows when it really ended.</li>
 *     <li>While Uptime Kuma is not up to date ({@code kumaStale}), its maintenances are left alone - otherwise a
 *     reconnect would end and re-post them.</li>
 * </ul>
 */
final class MaintenanceBoard {

    enum Status { SCHEDULED, RUNNING, ENDED, CANCELLED }

    /** Settings that matter for the decisions. */
    record Rules(Duration announceAhead, Duration minLength, Duration debounce) {
    }

    /** One maintenance and its Discord message. */
    static final class Entry {
        final String key;
        String title;
        /** Planned times (latest known; end null = open end). */
        Instant start;
        Instant end;
        /** When it really began / ended. */
        Instant startedAt;
        Instant endedAt;
        Status status;
        /** The times changed after the first post. */
        boolean changed;
        /** Times seen but not yet edited into the message. */
        Instant dirtySince;
        /** The Discord message; managed by the announcer. */
        AnnounceWebhook.Post post;

        Entry(String key) {
            this.key = key;
        }

        /** Whether it ended more than a minute before the planned end. */
        boolean endedEarly() {
            return status == Status.ENDED && end != null && endedAt != null
                    && endedAt.isBefore(end.minusSeconds(60));
        }
    }

    /**
     * Something to send.
     *
     * @param post true = new message, false = edit the existing one
     * @param ping mention the role (first post only)
     */
    record Change(Entry entry, boolean post, boolean ping) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Maintenance that is being followed (finished ones are dropped after their last edit). */
    Collection<Entry> entries() {
        return entries.values();
    }

    /** Puts back a followed maintenance after a restart. */
    void restore(Entry entry) {
        entries.put(entry.key, entry);
    }

    /**
     * @param windows   all known maintenance windows (not merged)
     * @param kumaStale Uptime Kuma is configured but its list is not current
     * @return the messages to post or edit, in order
     */
    List<Change> update(List<Window> windows, Instant now, boolean kumaStale, Rules rules) {
        List<Change> changes = new ArrayList<>();
        for (Entry e : List.copyOf(entries.values())) {
            if (kumaStale && e.key.startsWith(Window.KUMA + ":")) {
                continue;
            }
            Window w = windows.stream().filter(x -> x.key().equals(e.key)).findFirst().orElse(null);
            follow(e, w, now, rules, changes);
        }
        for (Window w : windows) {
            if (entries.containsKey(w.key()) || (kumaStale && w.source().equals(Window.KUMA)) || !longEnough(w, rules)) {
                continue;
            }
            boolean running = w.activeAt(now);
            // Only ahead of the start: a window whose end just passed may still be listed for a moment.
            boolean soon = w.upcomingAt(now) && !w.start().isAfter(now.plus(rules.announceAhead()));
            if (!running && !soon) {
                continue;
            }
            Entry e = new Entry(w.key());
            take(e, w);
            if (running) {
                e.status = Status.RUNNING;
                e.startedAt = startOf(w, now);
            } else {
                e.status = Status.SCHEDULED;
            }
            entries.put(e.key, e);
            changes.add(new Change(e, true, true));
        }
        return changes;
    }

    private void follow(Entry e, Window w, Instant now, Rules rules, List<Change> changes) {
        switch (e.status) {
            case SCHEDULED -> {
                if (e.end != null && !now.isBefore(e.end) && (w == null || !w.activeAt(now))) {
                    // Its whole time passed while nobody was watching (server offline).
                    e.startedAt = e.start;
                    finish(e, Status.ENDED, e.end, changes);
                } else if (w == null) {
                    boolean begun = e.start != null && !now.isBefore(e.start);
                    if (begun) {
                        e.startedAt = e.start;
                    }
                    finish(e, begun ? Status.ENDED : Status.CANCELLED, now, changes);
                } else if (w.activeAt(now)) {
                    take(e, w);
                    e.status = Status.RUNNING;
                    e.startedAt = startOf(w, now);
                    e.dirtySince = null;
                    changes.add(new Change(e, false, false));
                } else {
                    debounce(e, w, !Objects.equals(e.start, w.start()) || !Objects.equals(e.end, w.end()), now, rules, changes);
                }
            }
            case RUNNING -> {
                if (w == null || !w.activeAt(now)) {
                    finish(e, Status.ENDED, e.end != null && !now.isBefore(e.end) ? e.end : now, changes);
                } else {
                    // A running Kuma maintenance may report no begin - only the end counts.
                    debounce(e, w, !Objects.equals(e.end, w.end()), now, rules, changes);
                }
            }
            default -> entries.remove(e.key);
        }
    }

    /** Edits new times or a new title in once they stayed the same for {@code debounce}. */
    private static void debounce(Entry e, Window w, boolean timesChanged, Instant now, Rules rules, List<Change> changes) {
        boolean titleChanged = !Objects.equals(e.title, w.title());
        if (!timesChanged && !titleChanged) {
            e.dirtySince = null;
            return;
        }
        if (e.dirtySince == null) {
            e.dirtySince = now;
        }
        if (now.isBefore(e.dirtySince.plus(rules.debounce()))) {
            return;
        }
        if (timesChanged) {
            e.changed = true;
        }
        take(e, w);
        e.dirtySince = null;
        changes.add(new Change(e, false, false));
    }

    private void finish(Entry e, Status status, Instant at, List<Change> changes) {
        e.status = status;
        e.endedAt = status == Status.ENDED ? at : null;
        e.dirtySince = null;
        entries.remove(e.key);
        changes.add(new Change(e, false, false));
    }

    /** Takes title and times of the window; a running one without a known begin keeps the begin it had. */
    private static void take(Entry e, Window w) {
        e.title = w.title() == null ? "" : w.title();
        if (w.start() != null || e.status != Status.RUNNING) {
            e.start = w.start();
        }
        e.end = w.end();
    }

    /** The real begin: the planned one if it has passed (e.g. the server was offline), otherwise now. */
    private static Instant startOf(Window w, Instant now) {
        return w.start() != null && !w.start().isAfter(now) ? w.start() : now;
    }

    private static boolean longEnough(Window w, Rules rules) {
        if (rules.minLength().isZero() || w.start() == null || w.end() == null) {
            return true;
        }
        return Duration.between(w.start(), w.end()).compareTo(rules.minLength()) >= 0;
    }
}
