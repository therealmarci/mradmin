<script>
  // Activity chart of the page Log: one bar per time slice of the range, stacked by kind (events, console info,
  // warnings, errors). Drag to choose a stretch of time, a click picks one bar; the choice goes to onselect({from, to})
  // and is shaded. chart = {start, step, bars: [[events, info, warn, error], ...]} from /api/logs.
  import { t } from '../lib/i18n.js';

  let { chart, selection = null, onselect } = $props();

  const COLORS = ['var(--accent)', '#8b95a5', '#f59e0b', '#e5484d'];
  const KEYS = ['logs.chart.events', 'logs.chart.info', 'logs.chart.warn', 'logs.chart.error'];
  const H = 100;

  let box = $state();
  let hover = $state(-1);
  let drag = $state(null);

  const bars = $derived(chart?.bars ?? []);
  const n = $derived(bars.length);
  const max = $derived(Math.max(1, ...bars.map((b) => b[0] + b[1] + b[2] + b[3])));
  const span = $derived(n * (chart?.step ?? 1));
  const long = $derived(span > 86400000 * 1.5);

  function index(event) {
    const r = box.getBoundingClientRect();
    return Math.min(n - 1, Math.max(0, Math.floor(((event.clientX - r.left) / r.width) * n)));
  }

  function down(event) {
    if (!n) return;
    box.setPointerCapture(event.pointerId);
    const i = index(event);
    drag = { a: i, b: i };
  }

  function move(event) {
    if (!n) return;
    const i = index(event);
    hover = i;
    if (drag) drag = { ...drag, b: i };
  }

  function up() {
    if (!drag) return;
    const lo = Math.min(drag.a, drag.b);
    const hi = Math.max(drag.a, drag.b);
    drag = null;
    onselect?.({ from: chart.start + lo * chart.step, to: chart.start + (hi + 1) * chart.step });
  }

  // shaded part: while dragging the drag, otherwise the chosen stretch
  const shade = $derived.by(() => {
    if (drag) return { lo: Math.min(drag.a, drag.b), hi: Math.max(drag.a, drag.b) + 1 };
    if (!selection || !chart) return null;
    return { lo: (selection.from - chart.start) / chart.step, hi: (selection.to - chart.start) / chart.step };
  });

  const fmt = (ms) =>
    long
      ? new Date(ms).toLocaleString([], { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
      : new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  const ticks = $derived(n ? [0, 0.25, 0.5, 0.75, 1].map((f) => ({ f, label: fmt(chart.start + f * span) })) : []);
  const tip = $derived(hover >= 0 && hover < n ? { i: hover, b: bars[hover], from: chart.start + hover * chart.step } : null);
</script>

<div class="chart">
  <div class="legend">
    {#each KEYS as key, i}
      <span><i style="background:{COLORS[i]}"></i>{t(key)}</span>
    {/each}
    <span class="hint">{t('logs.chart.hint')}</span>
  </div>
  <div class="plot" bind:this={box} role="slider" tabindex="0" aria-label={t('logs.chart.label')} aria-valuenow={hover}
    onpointerdown={down} onpointermove={move} onpointerup={up} onpointerleave={() => (hover = -1)}>
    <svg viewBox="0 0 {Math.max(1, n)} {H}" preserveAspectRatio="none" aria-hidden="true">
      {#if shade}
        <rect class="shade" x={shade.lo} y="0" width={Math.max(0.3, shade.hi - shade.lo)} height={H} />
      {/if}
      {#each bars as b, i}
        {@const total = b[0] + b[1] + b[2] + b[3]}
        {#if total}
          {@const parts = [b[3], b[2], b[1], b[0]]}
          {@const colors = [COLORS[3], COLORS[2], COLORS[1], COLORS[0]]}
          {#each parts as v, k}
            {#if v}
              {@const below = parts.slice(0, k).reduce((s, x) => s + x, 0)}
              <rect x={i + 0.12} width="0.76" y={H - ((below + v) / max) * (H - 4)} height={(v / max) * (H - 4)}
                fill={colors[k]} class:dim={hover >= 0 && hover !== i} />
            {/if}
          {/each}
        {/if}
      {/each}
    </svg>
    {#if tip}
      <div class="tip" style="left:{((tip.i + 0.5) / n) * 100}%; --shift:{tip.i < n * 0.15 ? '0%' : tip.i > n * 0.85 ? '-100%' : '-50%'}">
        <b>{fmt(tip.from)} - {fmt(tip.from + chart.step)}</b>
        {#each KEYS as key, k}
          {#if tip.b[k]}<span><i style="background:{COLORS[k]}"></i>{t(key)}: {tip.b[k]}</span>{/if}
        {/each}
        {#if !tip.b.some((v) => v)}<span class="muted">{t('logs.chart.none')}</span>{/if}
      </div>
    {/if}
  </div>
  <div class="axis">
    {#each ticks as tick}
      <span style="left:{tick.f * 100}%">{tick.label}</span>
    {/each}
  </div>
</div>

<style>
  .chart {
    display: grid;
    gap: 6px;
    padding: 12px 14px 22px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
    font-size: 0.76rem;
    color: var(--muted);
  }

  .legend span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  .legend i,
  .tip i {
    width: 9px;
    height: 9px;
    border-radius: 2px;
  }

  .hint {
    margin-left: auto;
  }

  .plot {
    position: relative;
    height: 110px;
    cursor: crosshair;
    touch-action: none;
    user-select: none;
    border-bottom: 1px solid var(--border);
  }

  svg {
    width: 100%;
    height: 100%;
    display: block;
  }

  rect.dim {
    opacity: 0.55;
  }

  .shade {
    fill: color-mix(in srgb, var(--accent) 18%, transparent);
  }

  .tip {
    position: absolute;
    top: 4px;
    transform: translateX(var(--shift, -50%));
    z-index: 3;
    display: grid;
    gap: 2px;
    min-width: 140px;
    padding: 7px 9px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    box-shadow: var(--shadow);
    font-size: 0.76rem;
    white-space: nowrap;
    pointer-events: none;
  }

  .tip span {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .axis {
    position: relative;
    height: 12px;
    font-size: 0.72rem;
    color: var(--muted);
  }

  .axis span {
    position: absolute;
    top: 0;
    transform: translateX(-50%);
    white-space: nowrap;
  }

  .axis span:first-child {
    transform: none;
  }

  .axis span:last-child {
    transform: translateX(-100%);
  }

</style>
