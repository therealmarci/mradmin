<script>
  // A small chart without axes for one value of a chunk loader: the line takes the colour of the state the loader was
  // in at that time and falls to 0 while the loader was off, whatever the reason. The details are in the large chart.

  /**
   * points: [{ time, value, color }] in time order; phases: [{ from, to, color }] in which the line lies on 0;
   * from/to: the range shown.
   */
  let { points = [], phases = [], from = 0, to = 0, height = 28, label = '' } = $props();

  const W = 200;
  // Without an entry for this long the server was away: the line breaks instead of crossing that time.
  const MAX_GAP = $derived(3 * Math.max(20000, (to - from) / 300));

  const measured = $derived(points.filter((p) => !phases.some((b) => p.time >= b.from && p.time < b.to)));
  const top = $derived(Math.max(1e-9, ...measured.map((p) => p.value)));
  const x = (ms) => ((Math.min(to, Math.max(from, ms)) - from) / Math.max(1, to - from)) * W;
  const y = (v) => height - 2 - (v / top) * (height - 4);

  // Everything in time order; a phase is its start and end on the 0 line.
  const entries = $derived(
    [
      ...measured.map((p) => ({ ...p, phase: null })),
      ...phases.flatMap((b) => [
        { time: b.from, value: 0, color: b.color, phase: b },
        { time: b.to, value: 0, color: b.color, phase: b },
      ]),
    ].sort((a, b) => a.time - b.time),
  );

  // Pieces of one colour. A stretch between two entries has the colour of the later one: the fall already shows the
  // new state, the rise the state after the phase.
  const lines = $derived.by(() => {
    const result = [];
    for (let i = 1; i < entries.length; i++) {
      const a = entries[i - 1];
      const b = entries[i];
      if (b.time - a.time > MAX_GAP && !(a.phase && a.phase === b.phase)) continue;
      const point = `${x(b.time).toFixed(1)},${y(b.value).toFixed(1)}`;
      const open = result.at(-1);
      if (open && open.color === b.color && open.end === a) {
        open.points += ' ' + point;
        open.end = b;
      } else {
        result.push({ color: b.color, points: `${x(a.time).toFixed(1)},${y(a.value).toFixed(1)} ${point}`, end: b });
      }
    }
    return result;
  });
</script>

<svg viewBox="0 0 {W} {height}" preserveAspectRatio="none" style="height: {height}px" role="img" aria-label={label}>
  {#each lines as line}
    <polyline points={line.points} fill="none" stroke={line.color} stroke-width="2" vector-effect="non-scaling-stroke"
      stroke-linejoin="round" stroke-linecap="round" />
  {/each}
</svg>

<style>
  svg {
    display: block;
    width: 100%;
    overflow: visible;
  }
</style>
