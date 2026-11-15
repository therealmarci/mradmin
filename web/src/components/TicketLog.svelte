<script>
  // The log saved with a ticket (since 1.7, tickets.attach-log): performance per second as charts, an activity strip
  // with warnings, errors and events (a click narrows the list to that time), tiles with server data and the player's
  // surroundings, and the server log plus events in one list with filters. Team only (mradmin.tickets.log).
  //
  // log: the snapshot (GET /api/tickets/{id}/log)
  import LineChart from './LineChart.svelte';
  import { t } from '../lib/i18n.js';

  let { log, onclose = null } = $props();

  // ---------------------------------------------------------------- charts
  const points = $derived.by(() => {
    const f = log.health?.fields ?? [];
    return (log.health?.rows ?? []).map((r) => Object.fromEntries(f.map((k, i) => [k, r[i]])));
  });

  const num = (v) => (v == null ? '-' : Math.round(v).toLocaleString());
  const ms = (v) => (v == null ? '-' : v.toFixed(1) + ' ms');
  const pct = (v) => (v == null ? '-' : Math.round(v) + ' %');
  const mb = (v) => (v == null ? '-' : v >= 1024 ? (v / 1024).toFixed(1) + ' GB' : Math.round(v) + ' MB');
  const tps = (v) => (v == null ? '-' : v.toFixed(1));

  const METRICS = $derived([
    { id: 'tps', label: 'TPS', yMax: 20, format: tps, series: [{ key: 'tps', label: 'TPS', color: 'var(--series-1)' }] },
    { id: 'mspt', label: t('tlog.mspt'), format: ms, refs: [{ value: 50, label: '50 ms' }], series: [
      { key: 'mspt', label: t('health.avg'), color: 'var(--series-1)' },
      { key: 'msptP95', label: '95 %', color: 'var(--series-2)' },
      { key: 'msptMax', label: t('health.max'), color: 'var(--series-3)' }] },
    { id: 'cpu', label: 'CPU', yMax: 100, format: pct, series: [
      { key: 'cpu', label: t('health.server'), color: 'var(--series-1)' },
      { key: 'cpuSystem', label: t('health.system'), color: 'var(--series-2)' }] },
    { id: 'ram', label: 'RAM', format: mb, series: [{ key: 'heapUsed', label: t('health.used'), color: 'var(--series-1)' }] },
    { id: 'world', label: t('tlog.world'), format: num, series: [
      { key: 'entities', label: t('tlog.entities'), color: 'var(--series-1)' },
      { key: 'tiles', label: t('tlog.tiles'), color: 'var(--series-2)' },
      { key: 'chunks', label: t('tlog.chunks'), color: 'var(--series-3)' }] },
    { id: 'players', label: t('tlog.players'), format: num, series: [
      { key: 'players', label: t('tlog.players'), color: 'var(--series-1)' },
      { key: 'ping', label: 'Ping', color: 'var(--series-2)' }] },
  ]);
  let metric = $state('tps');
  const chosen = $derived(METRICS.find((m) => m.id === metric) ?? METRICS[0]);

  // Worst values of the time span as small numbers above the charts.
  const peaks = $derived.by(() => {
    if (!points.length) return null;
    let minTps = 20, maxMspt = 0, maxRam = 0, maxEnt = 0;
    for (const p of points) {
      minTps = Math.min(minTps, p.tps);
      maxMspt = Math.max(maxMspt, p.msptMax);
      maxRam = Math.max(maxRam, p.heapUsed);
      maxEnt = Math.max(maxEnt, p.entities);
    }
    return { minTps, maxMspt, maxRam, maxEnt, heapMax: points.at(-1).heapMax };
  });

  // --------------------------------------------------------------- entries
  const entries = $derived.by(() => {
    const list = [];
    for (const [time, level, text] of log.console?.lines ?? []) list.push({ time, kind: 'console', level, text });
    for (const e of log.events ?? []) list.push({ time: e.time, kind: 'event', level: 'event', text: e.text, source: e.source, color: e.color });
    list.sort((a, b) => a.time - b.time);
    return list;
  });
  const counts = $derived({
    warn: entries.filter((e) => e.level === 'warn').length,
    error: entries.filter((e) => e.level === 'error').length,
    event: entries.filter((e) => e.kind === 'event').length,
  });

  // Activity strip: the span in 90 slices, each coloured by the worst thing in it.
  const SLICES = 90;
  const span = $derived(Math.max(1, log.time - log.from));
  const slices = $derived.by(() => {
    const s = Array.from({ length: SLICES }, () => ({ info: 0, warn: 0, error: 0, event: 0 }));
    for (const e of entries) {
      const i = Math.min(SLICES - 1, Math.max(0, Math.floor(((e.time - log.from) / span) * SLICES)));
      s[i][e.level === 'warn' || e.level === 'error' || e.level === 'event' ? e.level : 'info']++;
    }
    return s;
  });
  const sliceMax = $derived(Math.max(1, ...slices.map((s) => s.info + s.warn + s.error + s.event)));

  let source = $state('all'); // all | console | event
  let levels = $state({ info: true, warn: true, error: true });
  let query = $state('');
  let focus = $state(null); // [from, to] from the strip
  let shown = $state(300);

  const filtered = $derived.by(() => {
    const q = query.trim().toLowerCase();
    return entries.filter((e) => {
      if (source !== 'all' && e.kind !== source) return false;
      if (e.kind === 'console' && !levels[e.level]) return false;
      if (focus && (e.time < focus[0] || e.time >= focus[1])) return false;
      return !q || e.text.toLowerCase().includes(q) || (e.source ?? '').toLowerCase().includes(q);
    });
  });
  // newest at the bottom like a console; the last `shown` entries, older ones on request
  const visible = $derived(filtered.slice(Math.max(0, filtered.length - shown)));

  function pickSlice(i) {
    const a = log.from + (span * i) / SLICES;
    focus = focus && focus[0] === a ? null : [a, log.from + (span * (i + 1)) / SLICES];
    shown = 300;
  }

  const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const duration = (ms) => {
    const m = Math.floor(ms / 60000);
    return m >= 1440 ? Math.floor(m / 1440) + ' d ' + Math.floor((m % 1440) / 60) + ' h'
      : m >= 60 ? Math.floor(m / 60) + ' h ' + (m % 60) + ' min' : m + ' min';
  };
  const ticketX = $derived(((log.time - log.from) / span) * 100);

  const people = $derived([log.player && { who: t('tlog.creator'), p: log.player }, log.target && { who: t('tlog.target'), p: log.target }].filter(Boolean));
</script>

<section class="card tlog">
  <header class="t-head">
    <div>
      <h2>{t('tlog.title')}</h2>
      <p class="muted small">{t('tlog.span', { from: clock(log.from), to: clock(log.time), minutes: Math.round(span / 60000) })}</p>
    </div>
    <div class="t-counts">
      {#if counts.error}<span class="pill bad">{counts.error} {t('tlog.errors')}</span>{/if}
      {#if counts.warn}<span class="pill warn">{counts.warn} {t('tlog.warnings')}</span>{/if}
      <span class="pill">{counts.event} {t('tlog.events')}</span>
      {#if onclose}<button class="secondary small" onclick={onclose}>{t('tlog.hide')}</button>{/if}
    </div>
  </header>

  {#if points.length}
    {#if peaks}
      <div class="peaks">
        <div><span>{t('tlog.minTps')}</span><b class:bad={peaks.minTps < 15} class:warn={peaks.minTps >= 15 && peaks.minTps < 19}>{tps(peaks.minTps)}</b></div>
        <div><span>{t('tlog.maxMspt')}</span><b class:bad={peaks.maxMspt > 100} class:warn={peaks.maxMspt > 50 && peaks.maxMspt <= 100}>{ms(peaks.maxMspt)}</b></div>
        <div><span>{t('tlog.maxRam')}</span><b>{mb(peaks.maxRam)} <small>/ {mb(peaks.heapMax)}</small></b></div>
        <div><span>{t('tlog.maxEntities')}</span><b>{num(peaks.maxEnt)}</b></div>
      </div>
    {/if}
    <div class="metrics" role="tablist">
      {#each METRICS as m (m.id)}
        <button role="tab" aria-selected={metric === m.id} class:on={metric === m.id} onclick={() => (metric = m.id)}>{m.label}</button>
      {/each}
    </div>
    {#key metric}
      <LineChart title={chosen.label} {points} from={log.from} to={log.time} seconds smooth yMax={chosen.yMax ?? null}
        format={chosen.format} series={chosen.series} refs={chosen.refs ?? []} />
    {/key}
  {:else}
    <p class="muted small">{t('tlog.noHealth')}</p>
  {/if}

  <!-- Activity: what happened when; a click narrows the list to that time. -->
  <div class="strip-wrap">
    <div class="strip" role="group" aria-label={t('tlog.activity')}>
      {#each slices as s, i}
        {@const total = s.info + s.warn + s.error + s.event}
        <button class="slice" class:sel={focus && Math.abs(focus[0] - (log.from + (span * i) / SLICES)) < 1}
          class:err={s.error} class:wrn={!s.error && s.warn} class:evt={!s.error && !s.warn && s.event}
          style="--h: {total ? 18 + (total / sliceMax) * 82 : 4}%"
          title={`${clock(log.from + (span * i) / SLICES)} · ${s.error} ${t('tlog.errors')}, ${s.warn} ${t('tlog.warnings')}, ${s.event} ${t('tlog.events')}, ${s.info} info`}
          aria-label={clock(log.from + (span * i) / SLICES)} onclick={() => pickSlice(i)}></button>
      {/each}
      <span class="ticket-mark" style="left: {ticketX}%" title={t('tlog.created')}></span>
    </div>
    <div class="strip-axis muted"><span>{clock(log.from)}</span><span>{t('tlog.created')} {clock(log.time)}</span></div>
  </div>

  <!-- Server and the people involved. -->
  <div class="tiles">
    <div class="tile">
      <h3>{t('tlog.server')}</h3>
      <dl>
        <dt>{t('tlog.software')}</dt><dd>{log.server?.software}</dd>
        <dt>Minecraft</dt><dd>{log.server?.minecraft}</dd>
        <dt>MRadmin</dt><dd>{log.server?.mradmin}</dd>
        <dt>Java</dt><dd>{log.server?.java}</dd>
        <dt>{t('tlog.system')}</dt><dd>{log.server?.os} · {log.server?.cores} {t('tlog.cores')} · {mb(log.server?.memoryMax)}</dd>
        <dt>{t('tlog.uptime')}</dt><dd>{duration(log.server?.uptime ?? 0)}</dd>
        <dt>TPS</dt><dd>{(log.server?.tps ?? []).map(tps).join(' / ')} · {ms(log.server?.mspt)}</dd>
        <dt>{t('tlog.players')}</dt><dd>{log.server?.online} / {log.server?.maxPlayers}</dd>
        <dt>{t('tlog.distances')}</dt><dd>{log.server?.viewDistance} / {log.server?.simulationDistance}</dd>
        <dt>{t('tlog.storage')}</dt><dd>{log.server?.storage}</dd>
      </dl>
    </div>

    {#each people as { who, p }}
      <div class="tile">
        <h3>{who}: {p.name}</h3>
        <dl>
          <dt>{t('tlog.position')}</dt><dd>{p.world} · {Math.round(p.x)} {Math.round(p.y)} {Math.round(p.z)} <small class="muted">({t('tlog.chunk')} {p.chunk?.join(' ')})</small></dd>
          <dt>{t('tlog.biome')}</dt><dd>{p.biome} · {t('tlog.light')} {p.light}</dd>
          <dt>{t('tlog.gameMode')}</dt><dd>{p.gameMode}{p.flying ? ' · ' + t('tlog.flying') : ''}{p.vehicle ? ' · ' + p.vehicle : ''}{p.op ? ' · OP' : ''}</dd>
          <dt>{t('tlog.state')}</dt><dd>❤ {p.health}/{p.maxHealth} · 🍗 {p.food} · Lvl {p.level}</dd>
          <dt>{t('tlog.client')}</dt><dd>{p.client ?? '-'} · {t('tlog.protocol')} {p.protocol} · {p.locale} · {t('tlog.viewDistance')} {p.viewDistance}</dd>
          <dt>Ping</dt><dd>{p.ping} ms</dd>
          <dt>{t('tlog.hand')}</dt><dd>{p.hand ?? '-'}</dd>
          {#if p.effects?.length}<dt>{t('tlog.effects')}</dt><dd>{p.effects.join(', ')}</dd>{/if}
          {#if p.players?.length}<dt>{t('tlog.nearPlayers')}</dt><dd>{p.players.map((o) => `${o.name} (${Math.round(o.distance)} m)`).join(', ')}</dd>{/if}
        </dl>
        <div class="counts">
          <div>
            <h4>{t('tlog.entitiesNear')}</h4>
            {#each (p.entities ?? []).slice(0, 8) as [type, n]}<div class="crow"><span>{type}</span><b>{n}</b></div>{:else}<span class="muted small">-</span>{/each}
          </div>
          <div>
            <h4>{t('tlog.tilesNear')} <small class="muted">{p.tileArea}</small></h4>
            {#each (p.tiles ?? []).slice(0, 8) as [type, n]}<div class="crow"><span>{type}</span><b>{n}</b></div>{:else}<span class="muted small">-</span>{/each}
          </div>
        </div>
      </div>
    {/each}

    <div class="tile">
      <h3>{t('tlog.worlds')}</h3>
      {#each log.server?.worlds ?? [] as w}
        <div class="crow"><span>{w.name} <small class="muted">{w.environment} · {w.weather}</small></span>
          <b>{num(w.players)} 👤 · {num(w.chunks)} ▦ · {num(w.entities)} E · {num(w.tiles)} T</b></div>
      {/each}
      {#if log.hotChunks?.length}
        <h4>{t('tlog.hotChunks')}</h4>
        {#each log.hotChunks as c}
          <div class="crow"><span>{c.world} {c.x} {c.z} <small class="muted">{c.types.slice(0, 3).map(([k, n]) => `${k} ${n}`).join(', ')}</small></span><b>{c.count}</b></div>
        {/each}
      {/if}
    </div>
  </div>

  <details class="plugins">
    <summary>{t('tlog.plugins', { count: log.server?.plugins?.length ?? 0 })}</summary>
    <div class="plist">
      {#each log.server?.plugins ?? [] as p}
        <span class:off={!p.enabled}>{p.name} <small>{p.version}</small></span>
      {/each}
    </div>
  </details>

  <!-- Server log and events in one list. -->
  <div class="filters">
    <div class="seg">
      {#each [['all', t('tlog.all')], ['console', t('tlog.console')], ['event', t('tlog.events')]] as [id, label]}
        <button class:on={source === id} onclick={() => (source = id)}>{label}</button>
      {/each}
    </div>
    {#if source !== 'event'}
      <div class="seg">
        {#each [['info', 'Info'], ['warn', t('tlog.warnings')], ['error', t('tlog.errors')]] as [id, label]}
          <button class:on={levels[id]} class={id} onclick={() => (levels[id] = !levels[id])}>{label}</button>
        {/each}
      </div>
    {/if}
    <input type="search" bind:value={query} placeholder={t('tlog.search')} />
    {#if focus}
      <button class="secondary small" onclick={() => (focus = null)}>{clock(focus[0])}-{clock(focus[1])} ✕</button>
    {/if}
  </div>

  <div class="lines">
    {#if filtered.length > visible.length}
      <button class="more" onclick={() => (shown += 500)}>{t('tlog.older', { count: filtered.length - visible.length })}</button>
    {/if}
    {#each visible as e}
      <div class="line {e.level}">
        <span class="lt">{clock(e.time)}</span>
        {#if e.kind === 'event'}
          <span class="src" style="--c: {e.color}">{e.source}</span>
        {/if}
        <span class="tx">{e.text}</span>
      </div>
    {:else}
      <p class="muted small">{t('tlog.empty')}</p>
    {/each}
    {#if log.console?.cut}<p class="muted small">{t('tlog.cut')}</p>{/if}
  </div>
</section>

<style>
  .tlog {
    display: grid;
    gap: 14px;
    min-width: 0;
  }

  .t-head {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  h3 {
    margin: 0 0 8px;
    font-size: 0.9rem;
  }

  h4 {
    margin: 10px 0 4px;
    font-size: 0.78rem;
    color: var(--muted);
    font-weight: 600;
  }

  p {
    margin: 0;
  }

  .t-counts {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  .pill {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .pill.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .pill.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  .peaks {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
    gap: 8px;
  }

  .peaks > div {
    display: grid;
    gap: 2px;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .peaks span {
    font-size: 0.72rem;
    color: var(--muted);
  }

  .peaks b {
    font-size: 1.05rem;
  }

  .peaks b small {
    font-weight: 500;
    color: var(--muted);
    font-size: 0.75rem;
  }

  .bad {
    color: var(--bad);
  }

  .warn {
    color: var(--warn);
  }

  .metrics,
  .seg {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .metrics button,
  .seg button {
    padding: 5px 11px;
    font-size: 0.8rem;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

  .metrics button.on,
  .seg button.on {
    color: var(--text);
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .seg button.warn.on {
    border-color: var(--warn);
    background: color-mix(in srgb, var(--warn) 12%, transparent);
  }

  .seg button.error.on {
    border-color: var(--bad);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .strip-wrap {
    display: grid;
    gap: 4px;
  }

  .strip {
    position: relative;
    display: flex;
    align-items: flex-end;
    gap: 1px;
    height: 46px;
    padding: 4px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .slice {
    flex: 1;
    min-width: 0;
    height: var(--h);
    padding: 0;
    border: 0;
    border-radius: 2px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
  }

  .slice.evt {
    background: var(--accent);
  }

  .slice.wrn {
    background: var(--warn);
  }

  .slice.err {
    background: var(--bad);
  }

  .slice.sel {
    outline: 2px solid var(--text);
  }

  .ticket-mark {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    margin-left: -3px;
    background: var(--text);
    pointer-events: none;
  }

  .strip-axis {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
  }

  .tiles {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 10px;
  }

  .tile {
    min-width: 0;
    padding: 12px;
    border-radius: 12px;
    border: 1px solid var(--border);
  }

  dl {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    gap: 4px 12px;
    margin: 0;
    font-size: 0.82rem;
  }

  dt {
    max-width: 9rem;
    color: var(--muted);
  }

  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }

  .counts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 0 14px;
  }

  .crow {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.8rem;
    padding: 2px 0;
    border-bottom: 1px solid color-mix(in srgb, var(--border) 50%, transparent);
  }

  .crow span {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .crow b {
    flex: none;
    font-weight: 600;
  }

  .plugins summary {
    cursor: pointer;
    font-weight: 600;
    font-size: 0.88rem;
  }

  .plist {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
  }

  .plist span {
    font-size: 0.78rem;
    padding: 3px 8px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .plist span.off {
    color: var(--bad);
    text-decoration: line-through;
  }

  .plist small {
    color: var(--muted);
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .filters input {
    flex: 1 1 180px;
    width: auto;
  }

  .lines {
    max-height: 560px;
    overflow: auto;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--code-bg, var(--surface-2));
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.76rem;
    line-height: 1.45;
  }

  .line {
    display: flex;
    gap: 8px;
    padding: 1px 0;
  }

  .line.warn .tx {
    color: var(--warn);
  }

  .line.error .tx {
    color: var(--bad);
  }

  .lt {
    flex: none;
    color: var(--muted);
  }

  .src {
    flex: none;
    font-weight: 700;
    color: var(--c, var(--accent));
  }

  .tx {
    min-width: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .more {
    display: block;
    margin: 0 auto 8px;
    font-size: 0.78rem;
    padding: 4px 12px;
  }
</style>
