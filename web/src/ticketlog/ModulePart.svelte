<script>
  // The part a plugin gives for the log of a ticket it opened (API 16, AdminModule#reportLog), e.g. MRchunks' chunk
  // loader: title with state, why, facts and its measurements against warn and limit lines. Only in MRadmin - it is
  // never in the sealed log for MRtechnology.
  //
  // part: log.module   lang   from/to: span of the log (charts use the same time axis)
  import LineChart from '../components/LineChart.svelte';
  import { tl } from './texts.js';

  let { part, lang, from, to } = $props();

  const charts = $derived((part.charts ?? []).map((c) => ({
    ...c,
    points: (c.points ?? []).map(([time, value]) => ({ time, value })),
    refs: [c.warn != null && { value: c.warn, label: '⚠ ' + c.warn }, c.limit != null && { value: c.limit, label: '⛔ ' + c.limit }].filter(Boolean),
  })));
  const fmt = (unit) => (v) => (v == null ? '-' : (Math.round(v * 100) / 100).toLocaleString() + (unit ? ' ' + unit : ''));
</script>

<section class="mpart tone-{part.status?.tone ?? 'none'}">
  <header>
    <div>
      <span class="from">{tl(lang, 'module', { plugin: part.plugin })}</span>
      <h3>{part.title}</h3>
      {#if part.reason}<p>{part.reason}</p>{/if}
    </div>
    {#if part.status}<span class="pill">{part.status.text}</span>{/if}
  </header>
  {#if part.facts?.length}
    <dl>
      {#each part.facts as [label, value]}<div><dt>{label}</dt><dd>{value}</dd></div>{/each}
    </dl>
  {/if}
  {#if charts.length}
    <div class="charts">
      {#each charts as c}
        <LineChart title={c.title} points={c.points} {from} {to} seconds format={fmt(c.unit)} refs={c.refs}
          series={[{ key: 'value', label: c.title, color: 'var(--series-1)' }]} />
      {/each}
    </div>
  {/if}
</section>

<style>
  .mpart {
    display: grid;
    gap: 12px;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--border);
    border-left: 4px solid var(--muted);
    background: color-mix(in srgb, var(--surface-2) 50%, transparent);
  }

  .tone-bad {
    border-left-color: var(--bad);
  }

  .tone-warn {
    border-left-color: var(--warn);
  }

  .tone-good {
    border-left-color: var(--good);
  }

  header {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: flex-start;
    gap: 10px;
  }

  .from {
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  h3 {
    margin: 2px 0 4px;
    font-size: 1rem;
  }

  p {
    margin: 0;
    font-size: 0.86rem;
  }

  .pill {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .tone-bad .pill {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .tone-warn .pill {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  dl {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 6px 14px;
    margin: 0;
  }

  dt {
    font-size: 0.72rem;
    color: var(--muted);
  }

  dd {
    margin: 0;
    font-weight: 600;
    font-size: 0.88rem;
    overflow-wrap: anywhere;
  }

  .charts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 10px;
  }
</style>
