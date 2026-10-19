<script>
  // A slim line chart for the health dashboard (one measure per chart - never two y-axes).
  // 2px lines, hairline gridlines, end labels when they do not collide, a crosshair that snaps to the nearest
  // point in time with one tooltip for every series, arrow keys for keyboard users, and a table view.
  import { t } from '../lib/i18n.js';

  let {
    title,
    points = [],
    series = [], // [{ key, label, color }] - color as CSS value, e.g. 'var(--series-1)'
    format = (v) => String(v), // value -> text (with unit)
    yMax = null, // fixed top of the scale (e.g. 20 TPS, 100 %), otherwise from the data
    refs = [], // reference lines [{ value, label }]
    long = false, // range longer than a day: dates on the x axis
    seconds = false, // short range: times with seconds
    height = 190,
  } = $props();

  const pad = { top: 12, right: 52, bottom: 24, left: 48 };
  let width = $state(600);
  let index = $state(-1);
  let table = $state(false);

  const clock = { hour: '2-digit', minute: '2-digit' };
  const time = (ms) =>
    new Date(ms).toLocaleString(undefined, long ? { day: '2-digit', month: '2-digit', ...clock } : seconds ? { ...clock, second: '2-digit' } : clock);
  const axisTime = (ms) =>
    new Date(ms).toLocaleString(undefined, long ? { day: '2-digit', month: '2-digit' } : seconds ? { ...clock, second: '2-digit' } : clock);

  const t0 = $derived(points.length ? points[0].time : 0);
  const t1 = $derived(points.length ? points.at(-1).time : 1);
  const innerW = $derived(Math.max(10, width - pad.left - pad.right));
  const innerH = $derived(height - pad.top - pad.bottom);

  // A clean top value: 1, 2, 2.5 or 5 times a power of ten.
  function nice(max) {
    if (max <= 0) return 1;
    const p = 10 ** Math.floor(Math.log10(max));
    for (const m of [1, 2, 2.5, 5, 10]) if (m * p >= max) return m * p;
    return 10 * p;
  }
  const top = $derived.by(() => {
    if (yMax !== null) return yMax;
    let m = 0;
    for (const p of points) for (const s of series) m = Math.max(m, p[s.key] ?? 0);
    for (const r of refs) m = Math.max(m, r.value);
    return nice(m * 1.05);
  });
  const ticks = $derived([0, 0.25, 0.5, 0.75, 1].map((f) => top * f));

  const x = (ms) => pad.left + (t1 === t0 ? innerW : ((ms - t0) / (t1 - t0)) * innerW);
  const y = (v) => pad.top + innerH - (Math.max(0, Math.min(v, top)) / top) * innerH;

  // A gap of more than 3 steps (server offline) breaks the line instead of drawing across it.
  const segments = $derived.by(() => {
    if (points.length < 2) return [points];
    const gaps = points.slice(1).map((p, i) => p.time - points[i].time).sort((a, b) => a - b);
    const step = gaps[Math.floor(gaps.length / 2)] || 1;
    const result = [[points[0]]];
    for (let i = 1; i < points.length; i++) {
      if (points[i].time - points[i - 1].time > step * 3) result.push([]);
      result.at(-1).push(points[i]);
    }
    return result;
  });

  function path(key) {
    return segments
      .filter((seg) => seg.length)
      .map((seg) => seg.map((p, i) => `${i ? 'L' : 'M'}${x(p.time).toFixed(1)},${y(p[key] ?? 0).toFixed(1)}`).join(''))
      .join('');
  }

  // Single series: a light wash under the line, closed down to the baseline.
  function area(key) {
    const base = y(0).toFixed(1);
    return segments
      .filter((seg) => seg.length > 1)
      .map((seg) => {
        const line = seg.map((p, i) => `${i ? 'L' : 'M'}${x(p.time).toFixed(1)},${y(p[key] ?? 0).toFixed(1)}`).join('');
        return `${line}L${x(seg.at(-1).time).toFixed(1)},${base}L${x(seg[0].time).toFixed(1)},${base}Z`;
      })
      .join('');
  }

  // End labels only when they do not collide - otherwise the legend and the tooltip carry identity.
  const endLabels = $derived.by(() => {
    if (!points.length) return [];
    const last = points.at(-1);
    const labels = series.map((s) => ({ ...s, y: y(last[s.key] ?? 0), value: last[s.key] }));
    const sorted = [...labels].sort((a, b) => a.y - b.y);
    for (let i = 1; i < sorted.length; i++) if (sorted[i].y - sorted[i - 1].y < 14) return [];
    return labels;
  });

  function nearest(px) {
    if (!points.length) return -1;
    const target = t0 + ((px - pad.left) / innerW) * (t1 - t0);
    let lo = 0;
    let hi = points.length - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (points[mid].time < target) lo = mid;
      else hi = mid;
    }
    return Math.abs(points[lo].time - target) <= Math.abs(points[hi].time - target) ? lo : hi;
  }

  function move(event) {
    const rect = event.currentTarget.getBoundingClientRect();
    index = nearest(((event.clientX - rect.left) / rect.width) * width);
  }

  function keys(event) {
    if (!points.length) return;
    if (event.key === 'ArrowLeft') index = Math.max(0, (index < 0 ? points.length : index) - 1);
    else if (event.key === 'ArrowRight') index = Math.min(points.length - 1, index + 1);
    else if (event.key === 'Escape') index = -1;
    else return;
    event.preventDefault();
  }

  const hover = $derived(index >= 0 && index < points.length ? points[index] : null);
  const tipLeft = $derived(hover ? Math.min(Math.max(x(hover.time) + 12, 0), width - 170) : 0);
  const xTicks = $derived(points.length ? [0, 0.25, 0.5, 0.75, 1].map((f) => t0 + (t1 - t0) * f) : []);
</script>

<section class="card chart">
  <div class="head">
    <h3>{title}</h3>
    <button class="secondary mini" onclick={() => (table = !table)} aria-pressed={table}>
      {table ? t('health.chart') : t('health.table')}
    </button>
  </div>
  {#if series.length > 1}
    <div class="legend">
      {#each series as s}
        <span><i style="background: {s.color}"></i>{s.label}</span>
      {/each}
    </div>
  {/if}

  {#if !points.length}
    <p class="muted small empty">{t('health.noData')}</p>
  {:else if table}
    <div class="table">
      <table>
        <thead>
          <tr><th>{t('health.time')}</th>{#each series as s}<th>{s.label}</th>{/each}</tr>
        </thead>
        <tbody>
          {#each [...points].reverse() as p (p.time)}
            <tr><td>{time(p.time)}</td>{#each series as s}<td>{format(p[s.key])}</td>{/each}</tr>
          {/each}
        </tbody>
      </table>
    </div>
  {:else}
    <div class="plot" bind:clientWidth={width}>
      <svg
        viewBox="0 0 {width} {height}"
        {height}
        role="img"
        aria-label={title}
        tabindex="0"
        onpointermove={move}
        onpointerleave={() => (index = -1)}
        onkeydown={keys}
      >
        {#each ticks as v}
          <line class="grid" x1={pad.left} x2={pad.left + innerW} y1={y(v)} y2={y(v)} />
          <text class="axis" x={pad.left - 6} y={y(v) + 4} text-anchor="end">{format(v)}</text>
        {/each}
        {#each xTicks as tt, i}
          <text class="axis" x={x(tt)} y={height - 6} text-anchor={i === 0 ? 'start' : i === 4 ? 'end' : 'middle'}>{axisTime(tt)}</text>
        {/each}
        {#each refs as r}
          <line class="ref" x1={pad.left} x2={pad.left + innerW} y1={y(r.value)} y2={y(r.value)} />
          <text class="axis" x={pad.left + 4} y={y(r.value) - 4}>{r.label}</text>
        {/each}
        {#if series.length === 1}
          <path d={area(series[0].key)} class="wash" style="fill: {series[0].color}" />
        {/if}
        {#each series as s}
          <path d={path(s.key)} class="line" style="stroke: {s.color}" />
        {/each}
        {#each endLabels as l}
          <text class="end" x={pad.left + innerW + 6} y={l.y + 4}>{format(l.value)}</text>
        {/each}
        {#if hover}
          <line class="cross" x1={x(hover.time)} x2={x(hover.time)} y1={pad.top} y2={pad.top + innerH} />
          {#each series as s}
            <circle cx={x(hover.time)} cy={y(hover[s.key] ?? 0)} r="4.5" style="fill: {s.color}" class="dot" />
          {/each}
        {/if}
      </svg>
      {#if hover}
        <div class="tip" style="left: {tipLeft}px">
          <div class="tip-time">{time(hover.time)}</div>
          {#each series as s}
            <div class="row"><i style="background: {s.color}"></i><strong>{format(hover[s.key])}</strong><span>{s.label}</span></div>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</section>

<style>
  .chart {
    padding: 16px 16px 10px;
    min-width: 0;
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }

  h3 {
    font-size: 0.95rem;
    margin: 0;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.78rem;
    font-weight: 600;
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
    font-size: 0.8rem;
    color: var(--muted);
    margin-top: 6px;
  }

  .legend i,
  .row i {
    display: inline-block;
    width: 14px;
    height: 2px;
    border-radius: 1px;
    vertical-align: middle;
    margin-right: 6px;
  }

  .plot {
    position: relative;
    margin-top: 6px;
  }

  svg {
    width: 100%;
    display: block;
    outline: none;
    touch-action: pan-y;
  }

  svg:focus-visible {
    box-shadow: 0 0 0 2px var(--accent);
    border-radius: 6px;
  }

  .grid {
    stroke: var(--grid);
    stroke-width: 1;
  }

  .ref {
    stroke: var(--axis);
    stroke-width: 1;
  }

  .axis,
  .end {
    fill: var(--muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .end {
    fill: var(--text);
    font-weight: 600;
  }

  .line {
    fill: none;
    stroke-width: 2;
    stroke-linejoin: round;
    stroke-linecap: round;
  }

  .wash {
    opacity: 0.1;
    stroke: none;
  }

  .cross {
    stroke: var(--muted);
    stroke-width: 1;
  }

  .dot {
    stroke: var(--surface);
    stroke-width: 2;
  }

  .tip {
    position: absolute;
    top: 4px;
    min-width: 150px;
    pointer-events: none;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 10px;
    box-shadow: var(--shadow);
    padding: 8px 10px;
    font-size: 0.8rem;
  }

  .tip-time {
    color: var(--muted);
    margin-bottom: 4px;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .row span {
    color: var(--muted);
  }

  .table {
    max-height: 260px;
    overflow: auto;
    margin-top: 8px;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.82rem;
    font-variant-numeric: tabular-nums;
  }

  th,
  td {
    text-align: right;
    padding: 4px 8px;
    border-bottom: 1px solid var(--grid);
  }

  th:first-child,
  td:first-child {
    text-align: left;
  }

  th {
    color: var(--muted);
    font-weight: 600;
    position: sticky;
    top: 0;
    background: var(--surface);
  }

  .empty {
    padding: 30px 0;
    text-align: center;
  }
</style>
