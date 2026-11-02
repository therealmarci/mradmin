<script>
  // A small chart without axes for tiles and slides: the course of one measure as a line with a soft area below,
  // an optional dashed limit (e.g. 50 ms per tick) and a dot at the latest value. The details are on the health page.

  /**
   * values: numbers in time order (null = gap, skipped; with gaps the line breaks there and the stretch is shaded);
   * min/max: fixed scale, otherwise from the values.
   */
  let { values = [], color = 'var(--accent)', min = 0, max = null, limit = null, height = 56, label = '', gaps = false } = $props();

  const W = 200;

  const clean = $derived(values.map((v) => (v == null || !Number.isFinite(v) || v < 0 ? null : v)));
  const top = $derived(Math.max(max ?? 0, ...clean.filter((v) => v !== null), limit ?? 0, min + 1e-9) || 1);
  const x = (i) => (clean.length < 2 ? W : (i / (clean.length - 1)) * W);
  const y = (v) => height - 3 - ((v - min) / (top - min)) * (height - 6);

  // The stretches of points that are joined: all of them, or with gaps only the ones between two nulls.
  const runs = $derived.by(() => {
    const result = [[]];
    clean.forEach((v, i) => {
      if (v !== null) result.at(-1).push([x(i), y(v)]);
      else if (gaps && result.at(-1).length) result.push([]);
    });
    return result.filter((run) => run.length);
  });
  const lines = $derived(runs.map((run) => run.map(([a, b]) => `${a.toFixed(1)},${b.toFixed(1)}`).join(' ')));
  const area = $derived(
    runs
      .filter((run) => run.length > 1)
      .map((run) => `M${run[0][0].toFixed(1)},${height} ` + run.map(([a, b]) => `L${a.toFixed(1)},${b.toFixed(1)}`).join(' ') + ` L${run.at(-1)[0].toFixed(1)},${height} Z`)
      .join(' '),
  );
  const shades = $derived.by(() => {
    const result = [];
    let start = -1;
    clean.forEach((v, i) => {
      if (v === null && start < 0) start = i;
      else if (v !== null && start >= 0) {
        result.push([x(start), x(i)]);
        start = -1;
      }
    });
    if (start >= 0) result.push([x(start), W]);
    return gaps ? result : [];
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
  {#each shades as [from, to]}
    <rect class="shade" x={from} width={Math.max(0, to - from)} y="0" {height} />
  {/each}
  {#if area}<path d={area} fill="url(#{id})" />{/if}
  {#each lines as line}
    <polyline points={line} fill="none" stroke={color} stroke-width="2" vector-effect="non-scaling-stroke"
      stroke-linejoin="round" stroke-linecap="round" />
  {/each}
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

  .shade {
    fill: var(--muted);
    opacity: 0.18;
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
