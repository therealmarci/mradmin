package at.marcelr.mradmin.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToLongFunction;

/** Puts the health history of one range together from several resolutions. Pure logic, no server access. */
final class HistoryFill {

    private HistoryFill() {
    }

    /**
     * Points since {@code since}, from the finest source first; each coarser source only fills in before the finer
     * ones start (the fine points are only kept in memory and begin with the last restart).
     *
     * @param sources each sorted by time, finest first
     */
    static <P> List<P> fill(List<? extends Collection<P>> sources, long since, ToLongFunction<P> time) {
        List<P> points = new ArrayList<>();
        long until = Long.MAX_VALUE;
        for (Collection<P> source : sources) {
            List<P> part = new ArrayList<>();
            for (P p : source) {
                long t = time.applyAsLong(p);
                if (t >= since && t < until) {
                    part.add(p);
                }
            }
            points.addAll(0, part);
            if (!source.isEmpty()) {
                until = Math.min(until, time.applyAsLong(source.iterator().next()));
            }
        }
        return points;
    }
}
