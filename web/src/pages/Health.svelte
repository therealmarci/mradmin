<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import LineChart from '../components/LineChart.svelte';

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

  const points = $derived(history?.points ?? []);
  const long = $derived(range === '7d' || range === '30d');
  const latest = $derived((perSecond ? points.at(-1) : live) ?? system?.latest);

  const ms = (v) => (v ?? 0).toFixed(1) + ' ms';
  const pct = (v) => (v < 0 || v == null ? '–' : Math.round(v) + ' %');
  const mb = (v) => (v >= 1024 ? (v / 1024).toFixed(1) + ' GB' : Math.round(v ?? 0) + ' MB');
  const num = (v) => (v >= 10000 ? (v / 1000).toFixed(1) + 'K' : String(Math.round(v ?? 0)));
  const tps = (v) => (v ?? 0).toFixed(1);

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

<div class="stack">
  <div class="head">
    <div>
      <h1>{t('health.title')}</h1>
      <p class="muted small">{t('health.subtitle')}</p>
    </div>
  </div>

  <div class="ranges" role="group" aria-label={t('health.range')}>
    {#each RANGES as r}
      <button class:active={range === r.key} onclick={() => choose(r.key)}
        disabled={history && r.ms > Math.max(history.history, 24 * 3600e3)}>{t('health.range.' + r.key)}</button>
    {/each}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if latest}
    <div class="kpis">
      <div class="card kpi">
        <div class="label">TPS</div>
        <div class="value">{tps(latest.tps)}</div>
        {#if state}<div class="status {state.tone}"><b>{state.icon}</b> {state.label}</div>{/if}
      </div>
      <div class="card kpi">
        <div class="label">MSPT</div>
        <div class="value">{ms(latest.mspt)}</div>
        <div class="sub">95 %: {ms(latest.msptP95)} · max {ms(latest.msptMax)}</div>
      </div>
      <div class="card kpi">
        <div class="label">{t('health.cpu')}</div>
        <div class="value">{pct(latest.cpu)}</div>
        <div class="sub">{t('health.system')}: {pct(latest.cpuSystem)}</div>
      </div>
      <div class="card kpi">
        <div class="label">{t('health.ram')}</div>
        <div class="value">{mb(latest.heapUsed)}</div>
        <div class="meter"><div style="width: {Math.min(100, (latest.heapUsed / latest.heapMax) * 100)}%"></div></div>
        <div class="sub">{t('health.of')} {mb(latest.heapMax)}</div>
      </div>
      <div class="card kpi">
        <div class="label">{t('health.uptime')}</div>
        <div class="value">{uptime(system.uptime)}</div>
        <div class="sub">{latest.players} {t('health.players')}</div>
      </div>
    </div>
  {/if}

  <div class="charts" class:refreshing={loading && history}>
    <LineChart title={t('health.chart.tps')} {points} {long} seconds={range === '1m'} yMax={20} format={tps}
      series={[{ key: 'tps', label: 'TPS', color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.mspt')} {points} {long} seconds={range === '1m'} format={ms}
      refs={[{ value: 50, label: t('health.budget') }]}
      series={[
        { key: 'mspt', label: t('health.avg'), color: 'var(--series-1)' },
        { key: 'msptP95', label: '95 %', color: 'var(--series-2)' },
        { key: 'msptMax', label: t('health.max'), color: 'var(--series-3)' },
      ]} />
    <LineChart title={t('health.chart.cpu')} {points} {long} seconds={range === '1m'} yMax={100} format={pct}
      series={[
        { key: 'cpu', label: t('health.server'), color: 'var(--series-1)' },
        { key: 'cpuSystem', label: t('health.system'), color: 'var(--series-2)' },
      ]} />
    <LineChart title={t('health.chart.ram')} {points} {long} seconds={range === '1m'} format={mb}
      refs={latest ? [{ value: latest.heapMax, label: t('health.max') }] : []}
      series={[{ key: 'heapUsed', label: t('health.used'), color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.players')} {points} {long} seconds={range === '1m'} format={num}
      series={[{ key: 'players', label: t('health.players'), color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.gc')} {points} {long} seconds={range === '1m'} format={(v) => Math.round(v ?? 0) + ' ms'}
      series={[{ key: 'gc', label: 'GC', color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.chunks')} {points} {long} seconds={range === '1m'} format={num}
      series={[{ key: 'chunks', label: 'Chunks', color: 'var(--series-1)' }]} />
    <LineChart title={t('health.chart.entities')} {points} {long} seconds={range === '1m'} format={num}
      series={[{ key: 'entities', label: 'Entities', color: 'var(--series-1)' }]} />
  </div>

  <div class="two">
    {#if system}
      <section class="card">
        <h3>{t('health.worlds')}</h3>
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
        <h3 class="spaced">{t('health.systemInfo')}</h3>
        <dl class="facts">
          <dt>Server</dt><dd>{system.server}</dd>
          <dt>Java</dt><dd>{system.java}</dd>
          <dt>{t('health.os')}</dt><dd>{system.os} · {system.cores} {t('health.cores')}</dd>
          <dt>Threads</dt><dd>{system.threads}</dd>
          <dt>Plugins</dt><dd>{system.plugins}</dd>
          <dt>{t('health.disk')}</dt><dd>{mb(system.diskFree)} {t('health.free')} {t('health.of')} {mb(system.diskTotal)}</dd>
        </dl>
      </section>
    {/if}

    <section class="card">
      <div class="lag-head">
        <h3>{t('health.lag')}</h3>
        <button class="secondary mini" onclick={loadLag} disabled={lagBusy}>{t('health.recount')}</button>
      </div>
      <p class="muted small">{t('health.lagHint')}</p>
      {#if lag}
        {#if lag.chunks.length === 0}
          <p class="muted">{t('health.lagNone')}</p>
        {:else}
          <ol class="lag">
            {#each lag.chunks as c}
              <li>
                <div class="lag-row"><strong>{c.count}</strong> <span class="muted small mono">{c.world} {c.x} {c.z}</span></div>
                <div class="muted small">{c.types.map((ty) => `${ty.count}× ${ty.type}`).join(' · ')}</div>
              </li>
            {/each}
          </ol>
        {/if}
      {/if}
    </section>
  </div>
</div>

<style>
  .head h1 {
    margin: 0;
  }

  .ranges {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .ranges button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 0.9rem;
  }

  .ranges button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  }

  .kpi {
    padding: 14px 16px;
  }

  .label {
    color: var(--muted);
    font-size: 0.82rem;
    font-weight: 600;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 700;
    margin: 2px 0;
  }

  .sub {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .status {
    font-size: 0.82rem;
    font-weight: 600;
  }

  .status b {
    display: inline-grid;
    place-items: center;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    color: #fff;
    font-size: 0.7rem;
    margin-right: 4px;
  }

  .good {
    color: var(--good);
  }

  .good b {
    background: var(--good);
  }

  .warn {
    color: var(--warn);
  }

  .warn b {
    background: var(--warn);
  }

  .bad {
    color: var(--bad);
  }

  .bad b {
    background: var(--bad);
  }

  .meter {
    height: 6px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--series-1) 18%, var(--surface));
    margin: 6px 0 4px;
    overflow: hidden;
  }

  .meter div {
    height: 100%;
    background: var(--series-1);
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
  }

  h3 {
    font-size: 0.95rem;
    margin: 0 0 8px;
  }

  .spaced {
    margin-top: 18px;
  }

  .table {
    overflow-x: auto;
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
    padding: 5px 8px;
    border-bottom: 1px solid var(--grid);
  }

  th:first-child,
  td:first-child {
    text-align: left;
  }

  th {
    color: var(--muted);
    font-weight: 600;
  }

  .facts {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 4px 14px;
    font-size: 0.88rem;
    margin: 0;
  }

  .facts dt {
    color: var(--muted);
  }

  .facts dd {
    margin: 0;
    word-break: break-word;
  }

  .lag-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.78rem;
  }

  .lag {
    margin: 0;
    padding-left: 1.4em;
    display: grid;
    gap: 8px;
  }

  .lag-row {
    display: flex;
    gap: 10px;
    align-items: baseline;
  }
</style>
