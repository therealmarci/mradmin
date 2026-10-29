<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import LineChart from '../components/LineChart.svelte';
  import { ICONS } from '../lib/icons.js';

  const RANGES = [
    { key: '1m', ms: 60e3 },
    { key: '1h', ms: 3600e3 },
    { key: '6h', ms: 6 * 3600e3 },
    { key: '24h', ms: 24 * 3600e3 },
    { key: '7d', ms: 7 * 86400e3 },
    { key: '30d', ms: 30 * 86400e3 },
  ];

  let range = $state(sessionStorage.getItem('mradmin.health.range') ?? '1h');
  let history = $state(null);
  let system = $state(null);
  let lag = $state(null);
  let lagBusy = $state(false);
  let error = $state('');
  let loading = $state(false);
  let live = $state(null);

  // The range that was asked for - an answer for an older range (slow network) is dropped.
  async function loadHistory(first = false) {
    const asked = range;
    if (first) loading = true;
    try {
      const result = await api('GET', '/health?range=' + asked);
      if (asked === range) history = result;
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      loading = false;
    }
  }

  async function loadLatest() {
    try {
      const point = await api('GET', '/health/latest');
      if (point && point.time) live = point;
    } catch {
      // the charts show the error
    }
  }

  async function loadSystem() {
    try {
      system = await api('GET', '/health/system');
    } catch {
      // the charts show the error
    }
  }

  async function loadLag() {
    lagBusy = true;
    try {
      lag = await api('GET', '/health/lag');
    } catch (e) {
      error = errorText(e);
    } finally {
      lagBusy = false;
    }
  }

  // Live: 1 min and 1 h every second (the server measures every second), 6 h / 24 h every 10 s (its 10-second
  // points), longer every minute. The key figures above always refresh every second.
  const perSecond = $derived(range === '1m' || range === '1h');
  $effect(() => {
    range;
    loadHistory(true);
    const every = perSecond ? 1000 : range === '6h' || range === '24h' ? 10000 : 60000;
    const timer = setInterval(loadHistory, every);
    return () => clearInterval(timer);
  });

  $effect(() => {
    if (perSecond) return;
    loadLatest();
    const timer = setInterval(loadLatest, 1000);
    return () => clearInterval(timer);
  });

  $effect(() => {
    loadSystem();
    loadLag();
    const timer = setInterval(loadSystem, 10000);
    return () => clearInterval(timer);
  });

  function choose(key) {
    range = key;
    try {
      sessionStorage.setItem('mradmin.health.range', key);
    } catch {
      // fine
    }
  }

  // gcMax: the highest second of a combined point (points stored by older versions only have the average).
  const points = $derived((history?.points ?? []).map((p) => ({ ...p, gcMax: Math.max(p.gcMax ?? 0, p.gc ?? 0) })));
  // The chosen range in server time - the time axis covers it even where there are no points.
  const from = $derived(history?.from ?? null);
  const to = $derived(history?.to ?? null);
  const long = $derived(range === '7d' || range === '30d');
  const latest = $derived((perSecond ? points.at(-1) : live) ?? system?.latest);

  const ms = (v) => (v ?? 0).toFixed(1) + ' ms';
  const pct = (v) => (v < 0 || v == null ? '–' : Math.round(v) + ' %');
  const mb = (v) => (v >= 1024 ? (v / 1024).toFixed(1) + ' GB' : Math.round(v ?? 0) + ' MB');
  const num = (v) => (v >= 10000 ? (v / 1000).toFixed(1) + 'K' : String(Math.round(v ?? 0)));
  const tps = (v) => (v ?? 0).toFixed(1);
  // GC: small values with a decimal - averaged over minutes they are often below 1 ms per second.
  const gcMs = (v) => ((v ?? 0) < 10 ? (v ?? 0).toFixed(1) : String(Math.round(v))) + ' ms';
  // Small sizes (the database) in KB, otherwise like mb().
  const size = (v) => (v < 1 ? Math.max(1, Math.round(v * 1024)) + ' KB' : v < 10 ? v.toFixed(1) + ' MB' : mb(v));

  // MRchunks is optional: only with it the chunk chart shows the chunks its loaders keep loaded (-1 = no MRchunks).
  const loaders = $derived((latest?.loaderChunks ?? -1) >= 0);

  function uptime(millis) {
    const m = Math.floor(millis / 60000);
    const d = Math.floor(m / 1440);
    const h = Math.floor((m % 1440) / 60);
    return d ? `${d} d ${h} h` : h ? `${h} h ${m % 60} min` : `${m} min`;
  }

  // TPS state: icon + label, never colour alone.
  const state = $derived(
    !latest ? null : latest.tps >= 19.5 ? { tone: 'good', icon: '✓', label: t('health.good') }
      : latest.tps >= 15 ? { tone: 'warn', icon: '!', label: t('health.strained') }
      : { tone: 'bad', icon: '✕', label: t('health.lagging') },
  );
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.pulse} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.pulse} /></svg>
      </span>
      <div>
        <h1>{t('health.title')}</h1>
        <p class="muted">{t('health.subtitle')}</p>
      </div>
    </div>
    {#if state}<span class="state {state.tone}"><b>{state.icon}</b> {state.label}</span>{/if}
  </header>

  <div class="switch" role="group" aria-label={t('health.range')}>
    {#each RANGES as r}
      <button class:active={range === r.key} onclick={() => choose(r.key)}
        disabled={history && r.ms > Math.max(history.history, 24 * 3600e3)}>{t('health.range.' + r.key)}</button>
    {/each}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if latest}
    <div class="kpis">
      <div class="kpi {state?.tone ?? ''}">
        <div class="label">TPS</div>
        <div class="value">{tps(latest.tps)}</div>
        <div class="bar"><span style="width: {Math.min(100, ((latest.tps ?? 0) / 20) * 100)}%"></span></div>
      </div>
      <div class="kpi {latest.mspt > 50 ? 'bad' : latest.mspt > 40 ? 'warn' : ''}">
        <div class="label">MSPT</div>
        <div class="value">{ms(latest.mspt)}</div>
        <div class="sub">95 %: {ms(latest.msptP95)} · max {ms(latest.msptMax)}</div>
      </div>
      <div class="kpi">
        <div class="label">{t('health.cpu')}</div>
        <div class="value">{pct(latest.cpu)}</div>
        <div class="sub">{t('health.system')}: {pct(latest.cpuSystem)}</div>
      </div>
      <div class="kpi">
        <div class="label">{t('health.ram')}</div>
        <div class="value">{mb(latest.heapUsed)}</div>
        <div class="bar"><span style="width: {Math.min(100, (latest.heapUsed / latest.heapMax) * 100)}%"></span></div>
        <div class="sub">{t('health.of')} {mb(latest.heapMax)}</div>
      </div>
      <div class="kpi">
        <div class="label">{t('health.ping')}</div>
        <div class="value">{latest.players > 0 && latest.ping >= 0 ? Math.round(latest.ping) + ' ms' : '–'}</div>
        <div class="sub">{latest.players} {t('health.players')}</div>
      </div>
      {#if system}
        <div class="kpi">
          <div class="label">{t('health.uptime')}</div>
          <div class="value">{uptime(system.uptime)}</div>
          <div class="sub">{system.server}</div>
        </div>
        <div class="kpi">
          <div class="label">{t('health.serverSize')}</div>
          <div class="value">{system.serverSize >= 0 ? mb(system.serverSize) : '…'}</div>
          <div class="sub">{t('health.serverSizeHint')}</div>
        </div>
        <div class="kpi">
          <div class="label">{t('health.dbSize')}</div>
          <div class="value">{system.dbSize >= 0 ? size(system.dbSize) : '…'}</div>
          <div class="sub">{t('health.storage.' + system.storage)}</div>
        </div>
      {/if}
    </div>
  {/if}

  <div class="charts" class:refreshing={loading && history}>
    <LineChart title={t('health.chart.tps')} {points} {from} {to} {long} seconds={range === '1m'} yMax={20} format={tps}
      series={[{ key: 'tps', label: 'TPS', color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.mspt')} {points} {from} {to} {long} seconds={range === '1m'} format={ms}
      refs={[{ value: 50, label: t('health.budget') }]}
      series={[
        { key: 'mspt', label: t('health.avg'), color: 'var(--series-1)' },
        { key: 'msptP95', label: '95 %', color: 'var(--series-2)' },
        { key: 'msptMax', label: t('health.max'), color: 'var(--series-3)' },
      ]} />
    <LineChart title={t('health.chart.cpu')} {points} {from} {to} {long} seconds={range === '1m'} yMax={100} format={pct}
      series={[
        { key: 'cpu', label: t('health.server'), color: 'var(--series-1)' },
        { key: 'cpuSystem', label: t('health.system'), color: 'var(--series-2)' },
      ]} />
    <LineChart title={t('health.chart.ram')} {points} {from} {to} {long} seconds={range === '1m'} format={mb}
      refs={latest ? [{ value: latest.heapMax, label: t('health.max') }] : []}
      series={[{ key: 'heapUsed', label: t('health.used'), color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.players')} {points} {from} {to} {long} seconds={range === '1m'} format={num}
      series={[{ key: 'players', label: t('health.players'), color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.gc')} {points} {from} {to} {long} seconds={range === '1m'} format={gcMs}
      series={[
        { key: 'gc', label: t('health.avg'), color: 'var(--series-1)' },
        { key: 'gcMax', label: t('health.peak'), color: 'var(--series-3)' },
      ]} />
    <LineChart title={t('health.chart.chunks')} {points} {from} {to} {long} seconds={range === '1m'} format={num}
      series={[
        { key: 'chunks', label: t('health.loaded'), color: 'var(--series-1)' },
        ...(loaders ? [{ key: 'loaderChunks', label: t('health.ofLoaders'), color: 'var(--series-3)' }] : []),
      ]} />
    <LineChart title={t('health.chart.ping')} {points} {from} {to} {long} seconds={range === '1m'} format={(v) => (v == null || v < 0 ? '–' : Math.round(v) + ' ms')}
      series={[{ key: 'ping', label: t('health.ping'), color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.entities')} {points} {from} {to} {long} seconds={range === '1m'} format={num}
      series={[{ key: 'entities', label: 'Entities', color: 'var(--series-1)' }]} />
  </div>

  <div class="two">
    {#if system}
      <section class="card">
        <div class="c-head">
          <span class="c-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.grid} /></svg>
          </span>
          <h3>{t('health.worlds')}</h3>
        </div>
        <div class="table">
          <table>
            <thead>
              <tr><th>{t('health.world')}</th><th>Chunks</th><th>Entities</th><th>{t('health.tiles')}</th><th>{t('health.players')}</th></tr>
            </thead>
            <tbody>
              {#each system.worlds as w (w.name)}
                <tr><td>{w.name}</td><td>{num(w.chunks)}</td><td>{num(w.entities)}</td><td>{num(w.tiles)}</td><td>{w.players}</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
        <div class="c-head spaced">
          <span class="c-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.terminal} /></svg>
          </span>
          <h3>{t('health.systemInfo')}</h3>
        </div>
        <div class="facts">
          <div class="fact"><span>Server</span><b>{system.server}</b></div>
          <div class="fact"><span>Java</span><b>{system.java}</b></div>
          <div class="fact"><span>{t('health.os')}</span><b>{system.os} · {system.cores} {t('health.cores')}</b></div>
          <div class="fact"><span>Threads</span><b>{system.threads}</b></div>
          <div class="fact"><span>Plugins</span><b>{system.plugins}</b></div>
          <div class="fact"><span>{t('health.disk')}</span><b>{mb(system.diskFree)} {t('health.free')} {t('health.of')} {mb(system.diskTotal)}</b>
            <div class="bar"><span style="width: {Math.min(100, (1 - system.diskFree / Math.max(1, system.diskTotal)) * 100)}%"></span></div></div>
        </div>
      </section>
    {/if}

    <section class="card">
      <div class="c-head">
        <span class="c-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
            stroke-linejoin="round"><path d={ICONS.box} /></svg>
        </span>
        <h3>{t('health.lag')}</h3>
        <button class="secondary mini" onclick={loadLag} disabled={lagBusy}>{t('health.recount')}</button>
      </div>
      <p class="muted small">{t('health.lagHint')}</p>
      {#if lag}
        {#if lag.chunks.length === 0}
          <p class="muted">{t('health.lagNone')}</p>
        {:else}
          <ol class="lag">
            {#each lag.chunks as c, i}
              <li>
                <span class="rank">{i + 1}</span>
                <div class="lag-body">
                  <div class="lag-row"><strong>{c.count}</strong> <span class="muted small mono">{c.world} {c.x} {c.z}</span></div>
                  <div class="muted small">{c.types.map((ty) => `${ty.count}× ${ty.type}`).join(' · ')}</div>
                </div>
              </li>
            {/each}
          </ol>
        {/if}
      {/if}
    </section>
  </div>
</div>

<style>
  .page {
    --hm: var(--accent);
    display: grid;
    gap: 16px;
  }

  /* Head: like the other pages - symbol, title, and the state of the server on the right. */
  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--hm) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--hm) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--hm);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--hm);
    background: color-mix(in srgb, var(--hm) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--hm) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .state {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    border-radius: 999px;
    font-weight: 700;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .state b {
    display: inline-grid;
    place-items: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    color: #fff;
    font-size: 0.7rem;
  }

  .state.good {
    color: var(--good);
    border-color: color-mix(in srgb, var(--good) 40%, var(--border));
  }

  .state.good b {
    background: var(--good);
  }

  .state.warn {
    color: var(--warn);
    border-color: color-mix(in srgb, var(--warn) 40%, var(--border));
  }

  .state.warn b {
    background: var(--warn);
  }

  .state.bad {
    color: var(--bad);
    border-color: color-mix(in srgb, var(--bad) 40%, var(--border));
  }

  .state.bad b {
    background: var(--bad);
  }

  /* The range as one switch */
  .switch {
    justify-self: start;
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    background: transparent;
    color: var(--muted);
    padding: 6px 14px;
    border-radius: 9px;
    font-size: 0.88rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  /* Key figures: small tiles, the tone of the value on the left edge. */
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .kpi {
    --tn: var(--hm);
    display: grid;
    gap: 4px;
    align-content: start;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--hm) 10%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .kpi.good {
    --tn: var(--good);
  }

  .kpi.warn {
    --tn: var(--warn);
  }

  .kpi.bad {
    --tn: var(--bad);
  }

  .kpi.good .value,
  .kpi.warn .value,
  .kpi.bad .value {
    color: var(--tn);
  }

  .label {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.15;
    letter-spacing: -0.01em;
    overflow-wrap: anywhere;
  }

  .sub {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .bar {
    height: 6px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    margin: 4px 0 2px;
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--tn, var(--hm));
  }

  .charts {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
    transition: opacity 0.2s;
  }

  .refreshing {
    opacity: 0.85;
  }

  .two {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
    align-items: start;
  }

  /* Cards below: a head with symbol */
  .c-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }

  .c-head .mini {
    margin-left: auto;
  }

  .c-icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 10px;
    color: var(--hm);
    background: color-mix(in srgb, var(--hm) 14%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--hm) 30%, transparent);
  }

  h3 {
    font-size: 1rem;
    margin: 0;
  }

  .spaced {
    margin-top: 20px;
  }

  .table {
    overflow-x: auto;
    border-radius: 10px;
    border: 1px solid var(--border);
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
    font-variant-numeric: tabular-nums;
  }

  th,
  td {
    text-align: right;
    padding: 7px 10px;
    border-bottom: 1px solid var(--grid);
  }

  tbody tr:last-child td {
    border-bottom: 0;
  }

  th:first-child,
  td:first-child {
    text-align: left;
  }

  th {
    color: var(--muted);
    font-weight: 600;
    background: var(--surface-2);
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 180px), 1fr));
  }

  .fact {
    display: grid;
    gap: 2px;
    align-content: start;
    padding: 8px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    min-width: 0;
  }

  .fact span {
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 600;
  }

  .fact b {
    font-size: 0.88rem;
    overflow-wrap: anywhere;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.78rem;
  }

  .lag {
    list-style: none;
    margin: 10px 0 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .lag li {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    padding: 8px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
  }

  .rank {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 50%;
    font-size: 0.72rem;
    font-weight: 800;
    color: var(--hm);
    background: color-mix(in srgb, var(--hm) 15%, transparent);
  }

  .lag-body {
    min-width: 0;
  }

  .lag-row {
    display: flex;
    gap: 10px;
    align-items: baseline;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }
</style>
