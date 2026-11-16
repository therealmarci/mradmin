<script>
  // The log saved with a ticket (since 1.7, tickets.attach-log), Team only (mradmin.tickets.log). One head with the
  // time span and the counts, below five tabs - every piece of information stands in exactly one of them:
  //   Overview      the plugin's own part (e.g. a locked chunk loader), a timeline with four lanes (server log,
  //                 events, performance, player) and the findings, each with a link to the tab that has the details
  //   Player & map  the playable map with the minute table and inventory, the people involved
  //   Performance   charts per second (worst value on every metric button), worlds and crowded chunks
  //   Log           server log and events in one list with filters
  //   Server        software, settings, plugins
  //
  // The same component runs in MRadmin and - built as its own bundle (vite.viewer.config.js) - on the MRtechnology
  // website for logs forwarded to MRtechnology: both always show exactly the same. Texts only from ./texts.js.
  //
  // log: the snapshot (GET /api/tickets/{id}/log)   lang: "de"/"en"   onclose: shows a hide button
  import LineChart from '../components/LineChart.svelte';
  import LogMap from './LogMap.svelte';
  import ModulePart from './ModulePart.svelte';
  import { tl } from './texts.js';
  import { safeColor } from './safe.js';

  let { log, lang = 'de', onclose = null } = $props();
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
  const counts = $derived({
    warn: entries.filter((e) => e.level === 'warn').length,
    error: entries.filter((e) => e.level === 'error').length,
    event: entries.filter((e) => e.kind === 'event').length,
  });

  // what happened to the creator (logging service): joins, deaths, teleports with their time
  const playerEvents = $derived((log.timeline?.minutes ?? []).flatMap((m) => (m.events ?? []).map((e) => ({ time: Number(e.time), kind: String(e.kind), text: String(e.text ?? '') }))));
  const hasMap = $derived(!!(log.area || log.timeline));
  const people = $derived([log.player && { who: T('creator'), p: log.player, onMap: hasMap }, log.target && { who: T('target'), p: log.target, onMap: false }].filter(Boolean));

  // ------------------------------------------------------------------ tabs
  const TABS = $derived([
    { id: 'overview', label: T('tabOverview') },
    (hasMap || people.length) && { id: 'map', label: T('tabMap') },
    points.length && { id: 'perf', label: T('tabPerf') },
    { id: 'log', label: T('tabLog'), count: entries.length },
    { id: 'server', label: T('tabServer') },
  ].filter(Boolean));
  let tab = $state('overview');
  let seek = $state(null); // {time} - the map jumps there

  function showLog(from, to, level = null) {
    focus = from == null ? null : [from, to];
    source = 'all';
    levels = level ? { info: false, warn: level === 'warn', error: true } : { info: true, warn: true, error: true };
    shown = 300;
    tab = 'log';
  }
  function showMap(time) {
    seek = { time };
    tab = 'map';
  }

  // -------------------------------------------------------------- overview
  // The span in slices; four lanes, each slice coloured by the worst thing in it.
  const SLICES = 90;
  const sliceOf = (t) => Math.min(SLICES - 1, Math.max(0, Math.floor(((t - log.from) / span) * SLICES)));
  const sliceFrom = (i) => log.from + (span * i) / SLICES;
  const lanes = $derived.by(() => {
    const mk = () => Array.from({ length: SLICES }, () => ({ n: 0, tone: '' }));
    const rank = { '': 0, act: 1, warn: 2, bad: 3 };
    const put = (lane, t, tone) => {
      const s = lane[sliceOf(t)];
      s.n++;
      if (rank[tone] > rank[s.tone]) s.tone = tone;
    };
    const logLane = mk(), evLane = mk(), perfLane = mk(), playerLane = mk();
    for (const e of entries) {
      if (e.kind === 'event') put(evLane, e.time, 'act');
      else put(logLane, e.time, e.level === 'error' ? 'bad' : e.level === 'warn' ? 'warn' : '');
    }
    for (const p of points) {
      const tone = p.mspt > 50 || p.tps < 15 ? 'bad' : p.msptMax > 50 || p.tps < 19 ? 'warn' : '';
      if (tone) put(perfLane, p.time, tone);
    }
    for (const e of playerEvents) put(playerLane, e.time, e.kind === 'death' ? 'bad' : 'act');
    return [
      { id: 'log', label: T('laneLog'), slices: logLane },
      { id: 'event', label: T('laneEvents'), slices: evLane },
      points.length && { id: 'perf', label: T('lanePerf'), slices: perfLane },
      log.timeline && { id: 'player', label: T('lanePlayer'), slices: playerLane },
    ].filter(Boolean);
  });
  function pickSlice(lane, i) {
    const from = sliceFrom(i), to = sliceFrom(i + 1);
    if (lane === 'perf') tab = 'perf';
    else if (lane === 'player') showMap(from);
    else showLog(from, to);
  }
  const ticketX = $derived(((log.time - log.from) / span) * 100);

  // Findings: what stands out, each once, with the tab that has the details.
  const findings = $derived.by(() => {
    const out = [];
    const lag = points.filter((p) => p.mspt > 50);
    const worst = points.reduce((a, p) => (p.msptMax > (a?.msptMax ?? 0) ? p : a), null);
    const low = points.reduce((a, p) => (p.tps < (a?.tps ?? 20) ? p : a), null);
    if (lag.length) out.push({ tone: 'bad', time: lag[0].time, text: T('fLag', { n: lag.length, max: ms(worst.msptMax) }), go: 'perf', metric: 'mspt' });
    else if (worst && worst.msptMax > 100) out.push({ tone: 'warn', time: worst.time, text: T('fSpike', { max: ms(worst.msptMax) }), go: 'perf', metric: 'mspt' });
    if (low && low.tps < 19) out.push({ tone: low.tps < 15 ? 'bad' : 'warn', time: low.time, text: T('fTps', { tps: tps(low.tps) }), go: 'perf', metric: 'tps' });
    const firstError = entries.find((e) => e.level === 'error');
    if (firstError) out.push({ tone: 'bad', time: firstError.time, text: T('fErrors', { n: counts.error }), detail: firstError.text.split('\n')[0], go: 'log', level: 'error' });
    const firstWarn = entries.find((e) => e.level === 'warn');
    if (firstWarn) out.push({ tone: 'warn', time: firstWarn.time, text: T('fWarnings', { n: counts.warn }), detail: firstWarn.text.split('\n')[0], go: 'log', level: 'warn' });
    for (const e of playerEvents.filter((e) => e.kind === 'death')) out.push({ tone: 'bad', time: e.time, text: T('fDeath'), detail: e.text, go: 'map' });
    return out.sort((a, b) => a.time - b.time);
  });
  function follow(f) {
    if (f.go === 'perf') {
      metric = f.metric ?? metric;
      tab = 'perf';
    } else if (f.go === 'map') showMap(f.time);
    else showLog(null, null, f.level);
  }

  // ----------------------------------------------------------- performance
  const peak = (key, pick = Math.max) => (points.length ? pick(...points.map((p) => p[key] ?? 0)) : null);
  const METRICS = $derived([
    { id: 'tps', label: 'TPS', peak: tps(peak('tps', Math.min)), hint: T('lowest'), tone: peak('tps', Math.min) < 15 ? 'bad' : peak('tps', Math.min) < 19 ? 'warn' : '',
      yMax: 20, format: tps, series: [{ key: 'tps', label: 'TPS', color: 'var(--series-1)' }] },
    { id: 'mspt', label: T('mspt'), peak: ms(peak('msptMax')), hint: T('longestTick'), tone: peak('msptMax') > 100 ? 'bad' : peak('msptMax') > 50 ? 'warn' : '',
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

  // Zoom: the last 1, 10 or 30 minutes before the ticket, always one value per second (only spans the log has).
  const spanMinutes = $derived(Math.max(1, Math.round(span / 60000)));
  const ZOOMS = $derived([...[1, 10, 30].filter((m) => m < spanMinutes), spanMinutes].filter((m, i, all) => all.indexOf(m) === i));
  let zoom = $state(30);
  const zoomNow = $derived(Math.min(zoom, spanMinutes));
  const zoomFrom = $derived(log.time - zoomNow * 60000);
  const zoomPoints = $derived(points.filter((p) => p.time >= zoomFrom));

  // ------------------------------------------------------------------- log
  let source = $state('all'); // all | console | event
  let levels = $state({ info: true, warn: true, error: true });
  let query = $state('');
  let focus = $state(null); // [from, to] from the timeline or the map
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
</script>

<section class="card tlog">
  <header class="t-head">
    <div>
      <h2>{T('title')}</h2>
      <p class="muted small">{T('span', { from: clock(log.from), to: clock(log.time), minutes: Math.round(span / 60000) })}</p>
    </div>
    <div class="t-counts">
      {#if counts.error}<span class="pill bad">{counts.error} {T('errors')}</span>{/if}
      {#if counts.warn}<span class="pill warn">{counts.warn} {T('warnings')}</span>{/if}
      <span class="pill">{counts.event} {T('events')}</span>
      {#if onclose}<button class="secondary small" onclick={onclose}>{T('hide')}</button>{/if}
    </div>
  </header>

  <div class="ttabs" role="tablist">
    {#each TABS as t (t.id)}
      <button role="tab" aria-selected={tab === t.id} class:on={tab === t.id} onclick={() => { seek = null; tab = t.id; }}>
        {t.label}{#if t.count != null}<small>{t.count}</small>{/if}
      </button>
    {/each}
  </div>

  {#if tab === 'overview'}
    <div class="pane">
      {#if log.module}
        <ModulePart part={log.module} {lang} from={log.from} to={log.time} />
      {/if}

      <section class="block">
        <div class="bhead">
          <h3>{T('timeline')}</h3>
          <span class="muted small">{T('timelineHint')}</span>
        </div>
        <div class="lanes">
          {#each lanes as lane (lane.id)}
            <span class="lname">{lane.label}</span>
            <div class="lane" role="group" aria-label={lane.label}>
              {#each lane.slices as s, i}
                <button class="slice {s.tone}" class:has={s.n} title="{clock(sliceFrom(i))} · {s.n}" aria-label="{lane.label} {clock(sliceFrom(i))}"
                  disabled={!s.n} onclick={() => pickSlice(lane.id, i)}></button>
              {/each}
              <span class="ticket-mark" style="left: {ticketX}%"></span>
            </div>
          {/each}
          <span></span>
          <div class="axis muted"><span>{clock(log.from)}</span><span>{T('created')} {clock(log.time)}</span></div>
        </div>
      </section>

      <section class="block">
        <h3>{T('findings')}</h3>
        {#each findings as f}
          <button class="finding {f.tone}" onclick={() => follow(f)}>
            <span class="ftime">{clock(f.time)}</span>
            <span class="ftext"><b>{f.text}</b>{#if f.detail}<small>{f.detail}</small>{/if}</span>
            <span class="fgo">{T('go_' + f.go)} →</span>
          </button>
        {:else}
          <p class="muted small">{T('noFindings')}</p>
        {/each}
      </section>
    </div>

  {:else if tab === 'map'}
    <div class="pane">
      {#if hasMap}
        <LogMap {log} {lang} {seek} onlog={(from, to) => showLog(from, to)}
          mark={log.module?.area ? { ...log.module.area, label: T('loaderArea') + ': ' + log.module.title } : null} />
      {/if}
      {#if people.length}
        <div class="cols">
          {#each people as { who, p, onMap }}
            <section class="block">
              <h3><span class="muted">{who}</span> {p.name}</h3>
              <dl>
                <dt>{T('position')}</dt><dd>{p.world} · {Math.round(p.x)} {Math.round(p.y)} {Math.round(p.z)} <small class="muted">({T('chunk')} {p.chunk?.join(' ')})</small></dd>
                <dt>{T('biome')}</dt><dd>{p.biome} · {T('light')} {p.light}</dd>
                <dt>{T('gameMode')}</dt><dd>{p.gameMode}{p.flying ? ' · ' + T('flying') : ''}{p.vehicle ? ' · ' + p.vehicle : ''}{p.op ? ' · OP' : ''}</dd>
                <dt>{T('state')}</dt><dd>❤ {p.health}/{p.maxHealth} · 🍗 {p.food} · Lvl {p.level}</dd>
                <dt>{T('hand')}</dt><dd>{p.hand ?? '-'}</dd>
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
        <LineChart title={chosen.label} points={zoomPoints} from={zoomFrom} to={log.time} seconds smooth={zoomNow > 1} yMax={chosen.yMax ?? null}
          format={chosen.format} series={chosen.series} refs={chosen.refs ?? []} />
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
            <h3>{T('hotChunks')}</h3>
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
        {#if focus}
          <button class="secondary small" onclick={() => (focus = null)}>{clock(focus[0])}–{clock(focus[1])} ✕</button>
        {/if}
      </div>
      <div class="lines">
        {#if filtered.length > visible.length}
          <button class="more" onclick={() => (shown += 500)}>{T('older', { count: filtered.length - visible.length })}</button>
        {/if}
        {#each visible as e}
          <div class="line {e.level}">
            <span class="lt">{clock(e.time)}</span>
            {#if e.kind === 'event'}<span class="src" style="--c: {e.color}">{e.source}</span>{/if}
            <span class="tx">{e.text}</span>
          </div>
        {:else}
          <p class="muted small">{T('empty')}</p>
        {/each}
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
    margin: 0;
    font-size: 0.9rem;
  }

  h3 small {
    font-weight: 500;
    font-size: 0.75rem;
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

  /* tabs: one line, underlined, scroll sideways on small screens */
  .ttabs {
    display: flex;
    gap: 2px;
    overflow-x: auto;
    border-bottom: 1px solid var(--border);
    scrollbar-width: none;
  }

  .ttabs button {
    flex: none;
    display: flex;
    align-items: center;
    gap: 6px;
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

  .ttabs small {
    font-size: 0.7rem;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
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

  .bhead {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .zoom {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    margin-bottom: -4px;
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

  /* timeline with lanes */
  .lanes {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    align-items: center;
    gap: 6px 10px;
  }

  .lname {
    font-size: 0.76rem;
    color: var(--muted);
  }

  .lane {
    position: relative;
    display: flex;
    gap: 1px;
    height: 20px;
    padding: 3px;
    border-radius: 6px;
    background: var(--surface-2);
  }

  .slice {
    flex: 1;
    min-width: 0;
    padding: 0;
    border: 0;
    border-radius: 2px;
    background: transparent;
  }

  .slice.has {
    cursor: pointer;
    background: color-mix(in srgb, var(--muted) 40%, transparent);
  }

  .slice.act {
    background: var(--accent);
  }

  .slice.warn {
    background: var(--warn);
  }

  .slice.bad {
    background: var(--bad);
  }

  .slice.has:hover {
    outline: 2px solid var(--text);
  }

  .ticket-mark {
    position: absolute;
    top: -2px;
    bottom: -2px;
    width: 2px;
    margin-left: -3px;
    background: var(--text);
    pointer-events: none;
  }

  .axis {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
  }

  /* findings */
  .finding {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    width: 100%;
    padding: 8px 12px;
    border: 0;
    border-left: 3px solid var(--muted);
    border-radius: 8px;
    background: var(--surface-2);
    color: var(--text);
    text-align: left;
  }

  .finding.bad {
    border-left-color: var(--bad);
  }

  .finding.warn {
    border-left-color: var(--warn);
  }

  .finding:hover .fgo {
    color: var(--accent);
  }

  .ftime {
    font-size: 0.76rem;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .ftext {
    display: grid;
    min-width: 0;
    font-size: 0.84rem;
  }

  .ftext small {
    color: var(--muted);
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.72rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .fgo {
    font-size: 0.76rem;
    color: var(--muted);
    white-space: nowrap;
  }

  /* metric buttons with their worst value */
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

  .counts h4 {
    margin-top: 0;
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
    max-height: 620px;
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
