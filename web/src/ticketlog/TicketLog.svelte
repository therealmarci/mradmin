<script>
  // The log saved with a ticket (since 1.7, tickets.attach-log), Team only (mradmin.tickets.log). On top the shared
  // timeline: its cursor is the moment every tab shows - the map plays there, the chart marks it, the log list scrolls
  // to it; chips jump to the next error, lag or death. Below the tabs, every piece of information in exactly one:
  //   <plugin part>   only for tickets a plugin opened (e.g. a locked chunk loader) - never in the sealed log
  //   Player & map    map with moment tiles and chronicle, the people involved
  //   Performance     charts per second (worst value on every metric button), worlds and crowded chunks
  //   Log             server log and events in one list with filters
  //   Server          software, settings, plugins
  //
  // The same component runs in MRadmin (page Tickets › #n › Log) and - built as its own bundle (vite.viewer.config.js) -
  // on the MRtechnology website: both always show exactly the same. Texts only from ./texts.js.
  //
  // log: the snapshot (GET /api/tickets/{id}/log)   lang: "de"/"en"
  import LineChart from '../components/LineChart.svelte';
  import LogMap from './LogMap.svelte';
  import LogTimeline from './LogTimeline.svelte';
  import ModulePart from './ModulePart.svelte';
  import { tl } from './texts.js';
  import { untrack } from 'svelte';
  import { safeColor } from './safe.js';

  let { log, lang = 'de' } = $props();
  const T = (k, v) => tl(lang, k, v);

  const num = (v) => (v == null ? '-' : Math.round(v).toLocaleString());
  const ms = (v) => (v == null ? '-' : v.toFixed(1) + ' ms');
  const pct = (v) => (v == null ? '-' : Math.round(v) + ' %');
  const mb = (v) => (v == null ? '-' : v >= 1024 ? (v / 1024).toFixed(1) + ' GB' : Math.round(v) + ' MB');
  const tps = (v) => (v == null ? '-' : v.toFixed(1));
  const clock = (t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const duration = (t) => {
    const m = Math.floor(t / 60000);
    return m >= 1440 ? Math.floor(m / 1440) + ' d ' + Math.floor((m % 1440) / 60) + ' h'
      : m >= 60 ? Math.floor(m / 60) + ' h ' + (m % 60) + ' min' : m + ' min';
  };
  const span = $derived(Math.max(1, log.time - log.from));

  // ------------------------------------------------------------------ data
  const points = $derived.by(() => {
    const f = log.health?.fields ?? [];
    return (log.health?.rows ?? []).map((r) => Object.fromEntries(f.map((k, i) => [k, r[i]])));
  });
  const entries = $derived.by(() => {
    const list = [];
    // values from the file are data: only known levels, only #rrggbb colours (a log may come from anyone)
    for (const [time, level, text] of log.console?.lines ?? []) list.push({ time: Number(time), kind: 'console', level: ['warn', 'error'].includes(level) ? level : 'info', text: String(text) });
    for (const e of log.events ?? []) list.push({ time: Number(e.time), kind: 'event', level: 'event', text: String(e.text), source: String(e.source), color: safeColor(e.color) });
    list.sort((a, b) => a.time - b.time);
    return list;
  });
  const playerEvents = $derived((log.timeline?.minutes ?? []).flatMap((m) => (m.events ?? []).map((e) => ({ time: Number(e.time), kind: String(e.kind) }))));
  const hasMap = $derived(!!(log.area || log.timeline));
  const people = $derived([log.player && { who: T('creator'), p: log.player, onMap: hasMap }, log.target && { who: T('target'), p: log.target, onMap: false }].filter(Boolean));

  // ------------------------------------------------------------- timeline
  // the log never changes while shown: the starting values come from it once
  let cursor = $state(untrack(() => log.time));
  const marks = $derived({
    perf: points.map((p) => ({ time: p.time, tone: p.mspt > 50 || p.tps < 15 ? 'bad' : p.msptMax > 50 || p.tps < 19 ? 'warn' : '' })).filter((m) => m.tone),
    log: entries.filter((e) => e.level !== 'info').map((e) => ({ time: e.time, tone: e.level === 'error' ? 'bad' : e.level === 'warn' ? 'warn' : 'act' })),
    player: playerEvents.map((e) => ({ time: e.time, kind: e.kind, tone: e.kind === 'death' ? 'bad' : 'act' })),
  });

  // ------------------------------------------------------------------ tabs
  const TABS = $derived([
    log.module && { id: 'module', label: log.module.title },
    (hasMap || people.length) && { id: 'map', label: T('tabMap') },
    points.length && { id: 'perf', label: T('tabPerf') },
    { id: 'log', label: T('tabLog') },
    { id: 'server', label: T('tabServer') },
  ].filter(Boolean));
  let tab = $state(untrack(() => (log.module ? 'module' : log.area || log.timeline || log.player ? 'map' : 'log')));

  // ----------------------------------------------------------- performance
  const peak = (key, pick = Math.max) => (points.length ? pick(...points.map((p) => p[key] ?? 0)) : null);
  const minTps = $derived(peak('tps', Math.min));
  const maxTick = $derived(peak('msptMax'));
  const METRICS = $derived([
    { id: 'tps', label: 'TPS', peak: tps(minTps), hint: T('lowest'), tone: minTps < 15 ? 'bad' : minTps < 19 ? 'warn' : '',
      yMax: 20, format: tps, series: [{ key: 'tps', label: 'TPS', color: 'var(--series-1)' }] },
    { id: 'mspt', label: T('mspt'), peak: ms(maxTick), hint: T('longestTick'), tone: maxTick > 100 ? 'bad' : maxTick > 50 ? 'warn' : '',
      format: ms, refs: [{ value: 50, label: '50 ms' }], series: [
      { key: 'mspt', label: T('avg'), color: 'var(--series-1)' },
      { key: 'msptP95', label: '95 %', color: 'var(--series-2)' },
      { key: 'msptMax', label: T('max'), color: 'var(--series-3)' }] },
    { id: 'cpu', label: 'CPU', peak: pct(peak('cpu')), hint: T('highest'), yMax: 100, format: pct, series: [
      { key: 'cpu', label: T('server'), color: 'var(--series-1)' },
      { key: 'cpuSystem', label: T('system'), color: 'var(--series-2)' }] },
    { id: 'ram', label: 'RAM', peak: mb(peak('heapUsed')), hint: T('highestOf', { max: mb(points.at(-1)?.heapMax) }), format: mb,
      series: [{ key: 'heapUsed', label: T('used'), color: 'var(--series-1)' }] },
    { id: 'world', label: T('world'), peak: num(peak('entities')), hint: T('highest') + ' · ' + T('entities'), format: num, series: [
      { key: 'entities', label: T('entities'), color: 'var(--series-1)' },
      { key: 'tiles', label: T('tiles'), color: 'var(--series-2)' },
      { key: 'chunks', label: T('chunks'), color: 'var(--series-3)' }] },
    { id: 'players', label: T('players'), peak: num(peak('players')), hint: T('highest') + ' · Ping ' + num(peak('ping')) + ' ms', format: num, series: [
      { key: 'players', label: T('players'), color: 'var(--series-1)' },
      { key: 'ping', label: 'Ping', color: 'var(--series-2)' }] },
  ]);
  let metric = $state('tps');
  const chosen = $derived(METRICS.find((m) => m.id === metric) ?? METRICS[0]);

  // Zoom: 1, 10 or 30 minutes (one value per second) around the moment of the timeline, kept inside the log.
  const spanMinutes = $derived(Math.max(1, Math.round(span / 60000)));
  const ZOOMS = $derived([...[1, 10, 30].filter((m) => m < spanMinutes), spanMinutes].filter((m, i, all) => all.indexOf(m) === i));
  let zoom = $state(30);
  const zoomNow = $derived(Math.min(zoom, spanMinutes));
  const zoomFrom = $derived(Math.max(log.from, Math.min(log.time - zoomNow * 60000, cursor - zoomNow * 30000)));
  const zoomTo = $derived(Math.min(log.time, zoomFrom + zoomNow * 60000));
  const zoomPoints = $derived(points.filter((p) => p.time >= zoomFrom && p.time <= zoomTo));

  // ------------------------------------------------------------------- log
  let source = $state('all'); // all | console | event
  let levels = $state({ info: true, warn: true, error: true });
  let query = $state('');
  let before = $state(200);
  let after = $state(100);
  const filtered = $derived.by(() => {
    const q = query.trim().toLowerCase();
    return entries.filter((e) => {
      if (source !== 'all' && e.kind !== source) return false;
      if (e.kind === 'console' && !levels[e.level]) return false;
      return !q || e.text.toLowerCase().includes(q) || (e.source ?? '').toLowerCase().includes(q);
    });
  });
  // the entry at the moment of the timeline; the list shows the entries around it
  const anchor = $derived.by(() => {
    let i = -1;
    for (let j = 0; j < filtered.length && filtered[j].time <= cursor; j++) i = j;
    return i;
  });
  const startAt = $derived(Math.max(0, anchor - before));
  const visible = $derived(filtered.slice(startAt, Math.max(anchor + 1 + after, startAt)));
  let lines = $state();
  $effect(() => {
    const el = lines?.querySelector('.line.now');
    if (!el) return;
    const top = el.offsetTop;
    if (top < lines.scrollTop || top > lines.scrollTop + lines.clientHeight - el.offsetHeight) lines.scrollTop = top - lines.clientHeight / 2;
  });
</script>

<section class="card tlog">
  <LogTimeline {lang} from={log.from} to={log.time} {marks} bind:time={cursor} />

  <div class="ttabs" role="tablist">
    {#each TABS as t (t.id)}
      <button role="tab" aria-selected={tab === t.id} class:on={tab === t.id} onclick={() => (tab = t.id)}>{t.label}</button>
    {/each}
  </div>

  {#if tab === 'module'}
    <ModulePart part={log.module} {lang} from={log.from} to={log.time} />

  {:else if tab === 'map'}
    <div class="pane">
      {#if hasMap}
        <LogMap {log} {lang} time={cursor} onseek={(t) => (cursor = t)}
          mark={log.module?.area ? { ...log.module.area, label: T('loaderArea') + ': ' + log.module.title } : null} />
      {/if}
      {#if people.length}
        <div class="cols">
          {#each people as { who, p, onMap }}
            <section class="block">
              <h3><span class="muted">{who}</span> {p.name} <small class="muted">{T('atCreation')}</small></h3>
              <dl>
                <dt>{T('position')}</dt><dd>{p.world} · {Math.round(p.x)} {Math.round(p.y)} {Math.round(p.z)} <small class="muted">({T('chunk')} {p.chunk?.join(' ')})</small></dd>
                <dt>{T('biome')}</dt><dd>{p.biome} · {T('light')} {p.light}</dd>
                <dt>{T('gameMode')}</dt><dd>{p.gameMode}{p.flying ? ' · ' + T('flying') : ''}{p.vehicle ? ' · ' + p.vehicle : ''}{p.op ? ' · OP' : ''}</dd>
                {#if !onMap}
                  <dt>{T('state')}</dt><dd>❤ {p.health}/{p.maxHealth} · 🍗 {p.food} · Lvl {p.level}</dd>
                  <dt>{T('hand')}</dt><dd>{p.hand ?? '-'}</dd>
                {:else}
                  <dt>{T('level')}</dt><dd>{p.level}</dd>
                {/if}
                {#if p.effects?.length}<dt>{T('effects')}</dt><dd>{p.effects.join(', ')}</dd>{/if}
                <dt>{T('client')}</dt><dd>{p.client ?? '-'} · {T('protocol')} {p.protocol} · {p.locale} · {T('viewDistance')} {p.viewDistance} · Ping {p.ping} ms</dd>
                {#if !onMap && p.players?.length}<dt>{T('nearPlayers')}</dt><dd>{p.players.map((o) => `${o.name} (${Math.round(o.distance)} m)`).join(', ')}</dd>{/if}
              </dl>
              <div class="counts">
                {#if !onMap}
                  <div>
                    <h4>{T('entitiesNear')}</h4>
                    {#each (p.entities ?? []).slice(0, 8) as [type, n]}<div class="crow"><span>{type}</span><b>{n}</b></div>{:else}<span class="muted small">-</span>{/each}
                  </div>
                {/if}
                <div>
                  <h4>{T('tilesNear')} <small class="muted">{p.tileArea}</small></h4>
                  {#each (p.tiles ?? []).slice(0, 8) as [type, n]}<div class="crow"><span>{type}</span><b>{n}</b></div>{:else}<span class="muted small">-</span>{/each}
                </div>
              </div>
            </section>
          {/each}
        </div>
      {/if}
    </div>

  {:else if tab === 'perf'}
    <div class="pane">
      <div class="mtiles" role="tablist">
        {#each METRICS as m (m.id)}
          <button role="tab" aria-selected={metric === m.id} class:on={metric === m.id} onclick={() => (metric = m.id)}>
            <span>{m.label}</span><b class={m.tone ?? ''}>{m.peak}</b><small>{m.hint}</small>
          </button>
        {/each}
      </div>
      <div class="zoom">
        <span class="muted small">{T('zoom')}</span>
        <div class="seg" role="group" aria-label={T('zoom')}>
          {#each ZOOMS as m}
            <button class:on={zoomNow === m} onclick={() => (zoom = m)}>{T('min', { n: m })}</button>
          {/each}
        </div>
      </div>
      {#key metric + zoomNow}
        <LineChart title={chosen.label} points={zoomPoints} from={zoomFrom} to={zoomTo} seconds smooth={zoomNow > 1} yMax={chosen.yMax ?? null}
          format={chosen.format} series={chosen.series} refs={chosen.refs ?? []} cursor={cursor} onpick={(t) => (cursor = t)} />
      {/key}
      <div class="cols">
        <section class="block">
          <h3>{T('worlds')} <small class="muted">{T('atCreation')}</small></h3>
          {#each log.server?.worlds ?? [] as w}
            <div class="crow"><span>{w.name} <small class="muted">{w.environment} · {w.weather}</small></span>
              <b>{num(w.players)} 👤 · {num(w.chunks)} ▦ · {num(w.entities)} E · {num(w.tiles)} T</b></div>
          {/each}
        </section>
        {#if log.hotChunks?.length}
          <section class="block">
            <h3>{T('hotChunks')} <small class="muted">{T('atCreation')}</small></h3>
            {#each log.hotChunks as c}
              <div class="crow"><span>{c.world} {c.x} {c.z} <small class="muted">{c.types.slice(0, 3).map(([k, n]) => `${k} ${n}`).join(', ')}</small></span><b>{c.count}</b></div>
            {/each}
          </section>
        {/if}
      </div>
    </div>

  {:else if tab === 'log'}
    <div class="pane">
      <div class="filters">
        <div class="seg">
          {#each [['all', T('all')], ['console', T('console')], ['event', T('events')]] as [id, label]}
            <button class:on={source === id} onclick={() => (source = id)}>{label}</button>
          {/each}
        </div>
        {#if source !== 'event'}
          <div class="seg">
            {#each [['info', 'Info'], ['warn', T('warnings')], ['error', T('errors')]] as [id, label]}
              <button class:on={levels[id]} class={id} onclick={() => (levels[id] = !levels[id])}>{label}</button>
            {/each}
          </div>
        {/if}
        <input type="search" bind:value={query} placeholder={T('search')} />
      </div>
      <div class="lines" bind:this={lines}>
        {#if startAt > 0}
          <button class="more" onclick={() => (before += 300)}>{T('older', { count: startAt })}</button>
        {/if}
        {#each visible as e, i}
          <button class="line {e.level}" class:now={startAt + i === anchor} class:future={e.time > cursor} onclick={() => (cursor = e.time)}>
            <span class="lt">{clock(e.time)}</span>
            {#if e.kind === 'event'}<span class="src" style="--c: {e.color}">{e.source}</span>{/if}
            <span class="tx">{e.text}</span>
          </button>
        {:else}
          <p class="muted small">{T('empty')}</p>
        {/each}
        {#if startAt + visible.length < filtered.length}
          <button class="more" onclick={() => (after += 300)}>{T('newer', { count: filtered.length - startAt - visible.length })}</button>
        {/if}
        {#if log.console?.cut}<p class="muted small">{T('cut')}</p>{/if}
      </div>
    </div>

  {:else if tab === 'server'}
    <div class="pane cols">
      <section class="block">
        <h3>{T('serverData')}</h3>
        <dl>
          <dt>{T('software')}</dt><dd>{log.server?.software}</dd>
          <dt>Minecraft</dt><dd>{log.server?.minecraft}</dd>
          <dt>MRadmin</dt><dd>{log.server?.mradmin}</dd>
          <dt>Java</dt><dd>{log.server?.java}</dd>
          <dt>{T('system')}</dt><dd>{log.server?.os} · {log.server?.cores} {T('cores')} · {mb(log.server?.memoryMax)}</dd>
          <dt>{T('uptime')}</dt><dd>{duration(log.server?.uptime ?? 0)}</dd>
          <dt>{T('slots')}</dt><dd>{log.server?.maxPlayers}</dd>
          <dt>{T('distances')}</dt><dd>{log.server?.viewDistance} / {log.server?.simulationDistance}</dd>
          <dt>{T('storage')}</dt><dd>{log.server?.storage}</dd>
        </dl>
      </section>
      <section class="block">
        <h3>{T('plugins', { count: log.server?.plugins?.length ?? 0 })}</h3>
        <div class="plist">
          {#each log.server?.plugins ?? [] as p}
            <span class:off={!p.enabled}>{p.name} <small>{p.version}</small></span>
          {/each}
        </div>
      </section>
    </div>
  {/if}
</section>

<style>
  .tlog {
    display: grid;
    gap: 14px;
    min-width: 0;
  }

  h3 {
    margin: 0;
    font-size: 0.9rem;
  }

  h3 small {
    font-weight: 500;
    font-size: 0.72rem;
  }

  h4 {
    margin: 0 0 4px;
    font-size: 0.78rem;
    color: var(--muted);
    font-weight: 600;
  }

  p {
    margin: 0;
  }

  .ttabs {
    display: flex;
    gap: 2px;
    overflow-x: auto;
    border-bottom: 1px solid var(--border);
    scrollbar-width: none;
  }

  .ttabs button {
    flex: none;
    padding: 8px 12px;
    margin-bottom: -1px;
    border: 0;
    border-bottom: 2px solid transparent;
    border-radius: 0;
    background: transparent;
    color: var(--muted);
    font-size: 0.86rem;
    font-weight: 600;
  }

  .ttabs button:hover {
    color: var(--text);
  }

  .ttabs button.on {
    color: var(--text);
    border-bottom-color: var(--accent);
  }

  .pane {
    display: grid;
    gap: 12px;
    min-width: 0;
  }

  .block {
    display: grid;
    gap: 10px;
    align-content: start;
    min-width: 0;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid var(--border);
  }

  .cols {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 12px;
    align-items: start;
  }

  .bad {
    color: var(--bad);
  }

  .warn {
    color: var(--warn);
  }

  .mtiles {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 8px;
  }

  .mtiles button {
    display: grid;
    justify-content: flex-start;
    justify-items: start;
    gap: 2px;
    padding: 8px 12px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text);
    text-align: left;
  }

  .mtiles button span {
    font-size: 0.74rem;
    color: var(--muted);
    font-weight: 600;
  }

  .mtiles button b {
    font-size: 1.1rem;
    font-weight: 700;
  }

  .mtiles button small {
    font-size: 0.72rem;
    color: var(--muted);
  }

  .mtiles button.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, transparent);
  }

  .zoom {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    margin-bottom: -4px;
  }

  .seg {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .seg button {
    padding: 5px 11px;
    font-size: 0.8rem;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

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

  dl {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    gap: 5px 14px;
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
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 0 14px;
  }

  .crow {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.8rem;
    padding: 3px 0;
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

  .plist {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
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
    position: relative;
    max-height: 620px;
    overflow: auto;
    padding: 8px 6px;
    border-radius: 10px;
    background: var(--code-bg, var(--surface-2));
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.76rem;
    line-height: 1.45;
  }

  .line {
    display: flex;
    justify-content: flex-start;
    align-items: baseline;
    gap: 8px;
    width: 100%;
    padding: 1px 4px;
    border: 0;
    border-radius: 4px;
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
  }

  .line:hover {
    background: color-mix(in srgb, var(--muted) 10%, transparent);
  }

  .line.future {
    opacity: 0.5;
  }

  .line.now {
    background: color-mix(in srgb, var(--accent) 18%, transparent);
    opacity: 1;
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
    margin: 4px auto 8px;
    font-size: 0.78rem;
    padding: 4px 12px;
  }
</style>
