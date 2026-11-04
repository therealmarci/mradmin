<script>
  // A small chart without axes for one value of a chunk loader, see lib/stateline.js. The details are in the large chart.
  import { stateEntries, statePieces } from '../lib/stateline.js';

  /**
   * points: [{ time, value, color }] in time order; phases: [{ from, to, color }] in which the line lies on 0;
   * live: the same as a point, where the line ends right now (null while the loader is off); from/to: the range shown;
   * gap: up to how many ms between two entries the line runs on (from the server, it knows the detail of the points).
   */
  let { points = [], phases = [], live = null, from = 0, to = 0, gap = null, height = 28, label = '' } = $props();

  const W = 200;

  const entries = $derived(stateEntries(points, phases, live));
  const top = $derived(Math.max(1e-9, ...entries.filter((e) => e.time >= from && e.time <= to).map((e) => e.value)));
  const x = (ms) => ((ms - from) / Math.max(1, to - from)) * W;
  const y = (v) => height - 3 - (Math.min(v, top) / top) * (height - 7);
  const lines = $derived(statePieces(entries, x, y, gap ?? 3 * Math.max(20000, (to - from) / 300)));
</script>

<svg viewBox="0 0 {W} {height}" preserveAspectRatio="none" style="height: {height}px" role="img" aria-label={label}>
  {#each lines as line}
    <path d={line.d} fill="none" stroke={line.color} stroke-width="2" vector-effect="non-scaling-stroke"
      stroke-linejoin="round" stroke-linecap="round" />
  {/each}
</svg>

<style>
  svg {
    display: block;
    width: 100%;
    /* The entries just before the range are cut off at the edge. */
    overflow: hidden;
  }
</style>
