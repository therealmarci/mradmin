<script>
  // A small chart without axes for tiles and slides: the course of one measure as a line with a soft area below,
  // an optional dashed limit (e.g. 50 ms per tick) and a dot at the latest value. The details are on the health page.

  /** values: numbers in time order (null = gap, skipped); min/max: fixed scale, otherwise from the values. */
  let { values = [], color = 'var(--accent)', min = 0, max = null, limit = null, height = 56, label = '' } = $props();

  const W = 200;

  const clean = $derived(values.map((v) => (v == null || !Number.isFinite(v) || v < 0 ? null : v)));
  const top = $derived(Math.max(max ?? 0, ...clean.filter((v) => v !== null), limit ?? 0, min + 1e-9) || 1);
  const x = (i) => (clean.length < 2 ? W : (i / (clean.length - 1)) * W);
  const y = (v) => height - 3 - ((v - min) / (top - min)) * (height - 6);

  const line = $derived(
    clean.map((v, i) => (v === null ? null : `${x(i).toFixed(1)},${y(v).toFixed(1)}`)).filter(Boolean).join(' '),
  );
  const area = $derived.by(() => {
    const pts = clean.map((v, i) => (v === null ? null : [x(i), y(v)])).filter(Boolean);
    if (pts.length < 2) return '';
    return `M${pts[0][0].toFixed(1)},${height} ` + pts.map(([a, b]) => `L${a.toFixed(1)},${b.toFixed(1)}`).join(' ') + ` L${pts.at(-1)[0].toFixed(1)},${height} Z`;
  });
  const last = $derived.by(() => {
    for (let i = clean.length - 1; i >= 0; i--) if (clean[i] !== null) return [x(i), y(clean[i])];
    return null;
  });
  const id = 'spark-' + Math.random().toString(36).slice(2, 8);
</script>

<svg class="spark" viewBox="0 0 {W} {height}" preserveAspectRatio="none" style="height: {height}px; --c: {color}"
  role="img" aria-label={label}>
  <defs>
    <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color={color} stop-opacity="0.32" />
      <stop offset="100%" stop-color={color} stop-opacity="0" />
    </linearGradient>
  </defs>
  {#if limit !== null && limit <= top}
    <line class="limit" x1="0" x2={W} y1={y(limit)} y2={y(limit)} />
  {/if}
  {#if area}<path d={area} fill="url(#{id})" />{/if}
  {#if line}<polyline points={line} fill="none" stroke={color} stroke-width="2" vector-effect="non-scaling-stroke"
      stroke-linejoin="round" stroke-linecap="round" />{/if}
</svg>
{#if last}
  <!-- The dot outside the stretched SVG, so it stays round. -->
  <span class="dot" style="left: {(last[0] / W) * 100}%; top: {last[1]}px; background: {color}"></span>
{/if}

<style>
  .spark {
    display: block;
    width: 100%;
    overflow: visible;
  }

  .limit {
    stroke: var(--bad);
    stroke-width: 1;
    stroke-dasharray: 4 4;
    opacity: 0.6;
    vector-effect: non-scaling-stroke;
  }

  .dot {
    position: absolute;
    width: 7px;
    height: 7px;
    margin: -3.5px 0 0 -3.5px;
    border-radius: 50%;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--surface) 70%, transparent);
    pointer-events: none;
  }
</style>
