// A line that shows the state of a value in its colour and lies on 0 while the loader is off - the same in the small
// and the large chart.
import { curve } from './curve.js';

/**
 * Everything to draw in time order. points: [{ time, value, color }]; phases: [{ from, to, color }] in which the line
 * lies on 0, also over measurements; live: where the line ends right now, as a point, or null.
 */
export function stateEntries(points, phases, live) {
  const all = live ? [...points, live] : points;
  const measured = all.filter((p) => !phases.some((b) => p.time >= b.from && p.time < b.to));
  return [
    ...measured.map((p) => ({ ...p, phase: null })),
    ...phases.flatMap((b) => [
      { time: b.from, value: 0, color: b.color, phase: b },
      { time: b.to, value: 0, color: b.color, phase: b },
    ]),
  ].sort((a, b) => a.time - b.time);
}

/**
 * Curves of one colour, as [{ color, d }] for SVG paths. A stretch between two entries has the colour of the later one:
 * the fall already shows the new state, the rise the state after the phase. Without an entry for maxGap ms the server
 * was away: the line breaks instead of crossing that time.
 */
export function statePieces(entries, x, y, maxGap) {
  const runs = [];
  entries.forEach((b, i) => {
    const a = entries[i - 1];
    if (a && (b.time - a.time <= maxGap || (a.phase && a.phase === b.phase))) runs.at(-1).push(b);
    else runs.push([b]);
  });
  const result = [];
  for (const run of runs) {
    let open = null;
    curve(run.map((e) => [x(e.time), y(e.value)])).forEach((piece, i) => {
      const color = run[i + 1].color;
      if (open && open.color === color) {
        open.d += piece;
      } else {
        open = { color, d: `M${x(run[i].time).toFixed(1)},${y(run[i].value).toFixed(1)}${piece}` };
        result.push(open);
      }
    });
  }
  return result;
}
