<script>
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';
  import LineChart from '../components/LineChart.svelte';
  import StateLine from '../components/StateLine.svelte';

  let { id, me } = $props();

  const RANGE_MS = { '1m': 60000, '10m': 600000, '30m': 1800000, '1h': 3600000, '6h': 21600000, '12h': 43200000 };
  const RANGES = Object.keys(RANGE_MS);
  // The storage can be blocked (private window): then the range is simply not remembered.
  function savedRange() {
    try {
      const saved = sessionStorage.getItem('mradmin.chunks.range');
      return RANGES.includes(saved) ? saved : '1h';
    } catch {
      return '1h';
    }
  }
  let range = $state(savedRange());
  let history = $state(null);

  let loader = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      loader = await moduleData('mrchunks', 'loader', { id });
      error = '';
    } catch (e) {
      error = errorText(e);
      if (e.status === 404) loader = null;
    }
  }

  $effect(() => {
    id;
    untrack(load);
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  // An answer for an older range (slow network) is dropped. MRchunks before the charts: no history, no section.
  async function loadHistory() {
    const asked = range;
    try {
      const result = await moduleData('mrchunks', 'loaderHistory', { id, range: asked });
      if (asked === range) take(result);
    } catch {
      history = null;
    }
  }

  $effect(() => {
    id;
    range;
    untrack(loadHistory);
    // A new measurement should show soon after it was taken; on long ranges one more point changes little.
    const timer = setInterval(loadHistory, RANGE_MS[range] <= 3600000 ? 5000 : 20000);
    return () => clearInterval(timer);
  });

  // The charts only get new values every few seconds but should run smoothly: the time keeps moving in the browser, the
  // line reaches up to now with the newest value, and a new measurement glides in instead of jumping.
  const BLEND_MS = 800;
  const calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  let now = $state(Date.now());
  let serverOffset = 0;
  let blend = $state(1);
  let blendStart = 0;
  let before = null;

  // Applies new data; if that brings a new newest point, the line glides there.
  function change(apply) {
    const last = allPoints.at(-1);
    const sameRange = history?.range === range;
    apply();
    const next = allPoints.at(-1);
    if (!calm && sameRange && last && next && next.time !== last.time) {
      before = last;
      blendStart = performance.now();
      blend = 0;
    }
  }

  function take(result) {
    change(() => {
      serverOffset = result.to - Date.now();
      history = { ...result, range };
    });
  }

  // Live view: while this page is open the server measures this loader more often (mrchunks.admin.live). The page stops
  // asking in a tab in the background, then the server stops measuring. Without it everything stays as it is.
  const LIVE_KEEP_MS = 900000;
  let liveActive = $state(false);
  let liveBuffer = $state([]);

  $effect(() => {
    id;
    liveBuffer = [];
    liveActive = false;
    if (!me.may.mrchunksLive) return;
    let stopped = false;
    let timer;
    async function tick() {
      let wait = 2000;
      if (!document.hidden) {
        try {
          const answer = await moduleData('mrchunks', 'loaderLive', { id });
          liveActive = answer.live;
          wait = answer.live ? answer.seconds * 1000 : 5000;
          if (answer.point && answer.point.time !== liveBuffer.at(-1)?.time) {
            change(() => {
              liveBuffer = [...liveBuffer.filter((p) => p.time >= answer.point.time - LIVE_KEEP_MS), answer.point];
            });
          }
        } catch {
          // MRchunks without the live view, or the server is restarting.
          liveActive = false;
          wait = 30000;
        }
      }
      if (!stopped) timer = setTimeout(tick, wait);
    }
    tick();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  });

  // The server's points with the finer live points in their place. On long ranges they would only be a thick line, there
  // the newest one is enough.
  const allPoints = $derived.by(() => {
    const server = history?.points ?? [];
    const first = liveBuffer[0]?.time;
    if (first === undefined) return server;
    const newest = liveBuffer.at(-1);
    if (RANGE_MS[range] > 3600000) return server.at(-1)?.time < newest.time ? [...server, newest] : server;
    return [...server.filter((p) => p.time < first), ...liveBuffer, ...server.filter((p) => p.time > newest.time)];
  });
  // The tiles show the newest live value.
  const tileMetrics = $derived.by(() => {
    const newest = liveActive && loader?.active ? liveBuffer.at(-1) : null;
    return (loader?.metrics ?? []).map((m) => (newest ? { ...m, value: newest[m.key] ?? m.value } : m));
  });

  $effect(() => {
    // One pixel of the large chart: moving more often would not show.
    const step = calm ? 1000 : Math.min(5000, Math.max(50, RANGE_MS[range] / 1200));
    let frame;
    let last = 0;
    const loop = (time) => {
      if (blend < 1) blend = Math.min(1, (time - blendStart) / BLEND_MS);
      if (blend < 1 || time - last >= step) {
        last = time;
        now = Date.now() + serverOffset;
      }
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  });

  const rangeStart = $derived(now - RANGE_MS[range]);
  // The newest point on its way from the value before to the new one.
  const shown = $derived.by(() => {
    const last = allPoints.at(-1);
    if (!last || !before || blend >= 1) return last ?? null;
    const eased = blend * (2 - blend);
    const mixed = { time: last.time };
    for (const key of Object.keys(last)) {
      if (key !== 'time') mixed[key] = (before[key] ?? 0) + (last[key] - (before[key] ?? 0)) * eased;
    }
    return mixed;
  });
  const shownPoints = $derived(shown ? [...allPoints.slice(0, -1), shown] : []);

  // The metric shown large (a click on its tile); Esc or a click beside it closes it.
  let chart = $state(null);
  let chartHeight = $state(400);
  const chartMetric = $derived(chart && history ? tileMetrics.find((m) => m.key === chart) : null);

  function openChart(key) {
    if (!history) return;
    chartHeight = Math.max(260, Math.round(window.innerHeight * 0.62));
    chart = key;
  }

  function chooseRange(key) {
    range = key;
    try {
      sessionStorage.setItem('mradmin.chunks.range', key);
    } catch {
      // only a convenience
    }
  }

  async function act(action, extra = {}) {
    if (action === 'delete' && !confirm(t('chunks.deleteConfirm', { name: loader.name }))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrchunks', { action, id, ...extra });
      if (action === 'delete') {
        go('/chunks');
        return;
      }
      message = action === 'suspendLock' && extra.duration === '0' ? t('chunks.done.suspendEnd')
        : t('chunks.done.' + (action === 'conspicuousToggle' ? 'toggle' : action === 'conspicuousDummy' ? 'dummy' : action));
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Share of the lock limit, for the bar; the warn limit is marked on it.
  const share = (m) => (m.lock > 0 ? Math.min(100, (m.value / m.lock) * 100) : 0);
  const tone = (m) => (m.lock > 0 && m.value >= m.lock ? 'bad' : m.warn > 0 && m.value >= m.warn ? 'warn' : 'good');
  // A loader that is off shows its last measurement, grey - and a dash if it was never measured.
  const tileTone = (m) => (loader.lock ? 'locked' : !loader.active || loader.shed ? 'off' : tone(m));
  const value = (m) => (!loader.measured ? '–' : m.rate ? m.value.toFixed(1) + '/s' : String(Math.round(m.value)));
  // The stretches in which the loader was not on: grey with their name in the large chart, in the colour of the state
  // on the 0 line of the small ones.
  const PHASES = {
    off: { key: 'inactive', color: 'var(--muted)' },
    shed: { key: 'shed', color: 'var(--muted)' },
    locked: { key: 'locked', color: 'var(--value-locked)' },
  };
  // A phase that still runs ends now.
  const phases = $derived(
    (history?.phases ?? []).map((b) => ({
      ...b,
      to: b.to >= history.to ? now : b.to,
      label: t('chunks.status.' + PHASES[b.phase].key),
      color: PHASES[b.phase].color,
    })),
  );
  const running = $derived(!!history && !(history.phases ?? []).some((b) => b.to >= history.to));
  // The small line of one value: its colour only follows its own limits.
  const linePoint = (p, m) => {
    const value = p[m.key];
    const over = m.lock > 0 && value >= m.lock ? 'var(--bad)' : m.warn > 0 && value >= m.warn ? 'var(--value-warn)' : 'var(--good)';
    return { time: p.time, value, color: over };
  };
  // Where the line ends right now: the newest value, held until the next measurement.
  const livePoint = $derived(running && shown && now > shown.time ? { ...shown, time: now } : null);
  // MRchunks before the chunk budget sends no size.
  const sized = $derived(loader?.reach != null);
  // Support: switch the loader and its dummy only while it is over a warn limit (mrchunks.admin.toggle.conspicuous).
  const conspicuousOnly = $derived(!!loader?.conspicuous && !!me.may.mrchunksToggleConspicuous && !me.may.mrchunksManage);
  // A lock after repeated shutdowns cannot be suspended, only lifted (older MRchunks sends no durations).
  const canSuspend = $derived(!!me.may.mrchunksSuspendLock && !!loader?.suspendDurations?.length && !loader?.lock?.repeated);
  const span = (ms) => (ms % 3600000 ? Math.round(ms / 60000) + ' min' : ms / 3600000 + ' h');
</script>

<div class="page">
  <button class="back" onclick={() => go('/chunks')}>← {t('chunks.back')}</button>
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if loader}
    {@const st = STATUS[loader.status]}
    <!-- Head: owner, name, state (its colour on the left as in the list), the facts and what can be done. -->
    <section class="head {st.tone}">
      <div class="who">
        <img src={head(loader.owner)} alt="" width="56" height="56" />
        <div class="names">
          <span class="status">{#if st.icon}{st.icon} {/if}{t('chunks.status.' + loader.status)}</span>
          <h1>{loader.name}</h1>
          <div class="muted">
            {#if me.may.players}<button class="plink" onclick={() => go('/players/' + loader.owner)}>{loader.ownerName}</button>{:else}{loader.ownerName}{/if}
            · Nr. {loader.number}
          </div>
        </div>
      </div>

      {#if loader.lock}
        <div class="lock">
          <b>{t('chunks.status.locked')}</b>
          {t('chunks.lockedBy', { metric: loader.lock.metric, value: Math.round(loader.lock.value), limit: Math.round(loader.lock.limit) })} · {dateTime(loader.lock.since)}
        </div>
      {:else if loader.shed}
        <div class="lock shed">
          <b>{t('chunks.status.shed')}</b>
          {t('chunks.shedSince', { since: dateTime(loader.shed.since), back: dateTime(loader.shed.back) })}
        </div>
      {/if}
      {#if loader.suspension}
        <div class="lock suspended">
          <b>{t('chunks.suspended')}</b>
          {t('chunks.suspendedUntil', { until: dateTime(loader.suspension.until), by: loader.suspension.by })}
        </div>
      {/if}

      <div class="facts">
        <div class="fact"><span>{t('ticket.location')}</span><b class="mono coords"><i>{loader.world}</i><i>X {loader.x}</i><i>Y {loader.y}</i><i>Z {loader.z}</i></b></div>
        <div class="fact"><span>{t('chunks.created')}</span><b>{dateTime(loader.created)}</b></div>
        {#if sized}
          <div class="fact"><span>{t('chunks.sizeLabel')}</span><b>{t('chunks.sizeValue', { width: loader.width, length: loader.length, chunks: loader.chunks })}</b></div>
          <div class="fact"><span>{t('chunks.ownerBudget')}</span><b>{t('chunks.budgetValue', { used: loader.budgetUsed, budget: loader.budget < 0 ? '∞' : loader.budget })}</b>
            {#if loader.budget > 0}<div class="bar"><span style="width: {Math.min(100, (loader.budgetUsed / loader.budget) * 100)}%"></span></div>{/if}</div>
        {/if}
        {#if loader.dummiesEnabled}
          <div class="fact"><span>{t('chunks.dummy')}</span>
            <b class:good-text={loader.dummyPresent}>{loader.dummyPresent ? '🧍 ' + t('chunks.dummySits') : loader.dummy ? t('chunks.dummyWaiting') : t('chunks.dummyNone')}</b></div>
        {/if}
      </div>

      {#if me.may.mrchunksManage || me.may.mrchunksToggle || conspicuousOnly}
        <div class="actions">
          {#if loader.lock}
            {#if me.may.mrchunksManage}
              <button onclick={() => act('unlock')} disabled={busy}>{t('chunks.unlock')}</button>
            {/if}
          {:else}
            <button class:secondary={loader.active} onclick={() => act(conspicuousOnly && !me.may.mrchunksToggle ? 'conspicuousToggle' : 'toggle')} disabled={busy}>
              {loader.active ? t('chunks.switchOff') : t('chunks.switchOn')}
            </button>
          {/if}
          {#if (me.may.mrchunksManage || conspicuousOnly) && loader.dummiesEnabled}
            <button class="secondary" onclick={() => act(me.may.mrchunksManage ? 'dummy' : 'conspicuousDummy')} disabled={busy || (!loader.dummy && loader.dummyFits === false)}
              title={!loader.dummy && loader.dummyFits === false ? t('chunks.dummyNeedsSquare') : ''}>
              {loader.dummy ? t('chunks.dummyOff') : t('chunks.dummyOn')}
            </button>
          {/if}
          {#if me.may.mrchunksManage}
            <button class="danger push" onclick={() => act('delete')} disabled={busy}>{t('chunks.delete')}</button>
          {/if}
        </div>
      {/if}

      {#if canSuspend}
        <div class="actions suspend">
          <span class="muted small" title={t('chunks.suspendHint')}>{t(loader.lock ? 'chunks.suspendLocked' : 'chunks.suspend')}</span>
          {#each loader.suspendDurations as duration (duration)}
            <button class="secondary" onclick={() => act('suspendLock', { duration: String(duration) })} disabled={busy}>{span(duration)}</button>
          {/each}
          {#if loader.suspension}
            <button class="secondary push" onclick={() => act('suspendLock', { duration: '0' })} disabled={busy}>{t('chunks.suspendEnd')}</button>
          {/if}
        </div>
      {/if}
    </section>

    <div class="grid">
      <section class="card activity">
        <div class="c-head">
          <span class="c-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.pulse} /></svg>
          </span>
          <h2>{t('chunks.activity')}</h2>
          {#if liveActive}<span class="live" title={t('chunks.liveHint')}>{t('chunks.live')}</span>{/if}
        </div>
        {#if loader.metrics}
          <p class="muted small">
            {#if loader.loadedChunks != null}
              {t('chunks.measured', { ago: ago(loader.measured), loaded: loader.loadedChunks, total: loader.totalChunks })}
            {:else if loader.measured}
              {t('chunks.offLast', { ago: ago(loader.measured) })}
            {:else}
              {t('chunks.offNever')}
            {/if}
          </p>
          <p class="muted small">{t('chunks.ownLimits')}</p>
          <div class="metrics">
            {#each tileMetrics as m (m.key)}
              <button class="metric {tileTone(m)}" class:plain={!history} onclick={() => openChart(m.key)} title={history ? t('chunks.historyOpen') : ''}>
                <div class="mrow"><span>{m.label}</span><strong class="{tileTone(m)}-text">{value(m)} <small class="muted">/ {m.lock}</small></strong></div>
                <div class="mbar" title="{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}">
                  <div class="fill {tileTone(m)}" style="width: {share(m)}%"></div>
                  {#if m.lock > 0 && m.warn > 0}<div class="tick" style="left: {(m.warn / m.lock) * 100}%"></div>{/if}
                </div>
                <div class="muted tiny">{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}</div>
                <!-- Always there, so every tile has the same layout: the curve fills the bottom edge of the tile. -->
                <div class="well">
                  {#if history?.points.length > 1 || phases.length}
                    <StateLine points={shownPoints.map((p) => linePoint(p, m))} {phases} live={livePoint && linePoint(livePoint, m)}
                      from={rangeStart} to={now} height={40} label={m.label} />
                  {/if}
                </div>
              </button>
            {/each}
          </div>
        {:else}
          <p class="muted">{t('chunks.noActivity')}</p>
        {/if}

      </section>

      <div class="side">
        <section class="card">
          <div class="c-head">
            <span class="c-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round"><path d={ICONS.users} /></svg>
            </span>
            <h2>{t('chunks.members')}</h2>
            <span class="count">{loader.members.length}</span>
          </div>
          {#if loader.members.length === 0}
            <p class="muted small">{t('chunks.noMembers')}</p>
          {:else}
            <ul class="members">
              {#each loader.members as m}
                <li>
                  <img src={head(m.uuid)} alt="" width="28" height="28" />
                  <span class="m-body">
                    <strong>{m.name}</strong>
                    <span class="muted small">{m.scope === 'all' ? t('chunks.allLoaders') : t('chunks.thisLoader')} · {m.until ? t('chunks.until', { time: dateTime(m.until) }) : t('chunks.permanent')}</span>
                  </span>
                </li>
              {/each}
            </ul>
          {/if}
        </section>
      </div>
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<svelte:window onkeydown={(e) => chart && e.key === 'Escape' && (chart = null)} />

{#if chartMetric}
  {@const m = chartMetric}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div class="overlay" onclick={(e) => e.target === e.currentTarget && (chart = null)}>
    <div class="big {tileTone(m)}" role="dialog" aria-modal="true" aria-label={m.label}>
      <div class="big-head">
        <div class="now">
          <span class="muted small">{m.label}{#if liveActive} · <span class="live">{t('chunks.live')}</span>{/if}</span>
          <strong>{value(m)}</strong>
          <span class="muted small">{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}</span>
        </div>
        <div class="switch" role="group" aria-label={t('chunks.history')}>
          {#each RANGES as r}
            <button class:active={range === r} onclick={() => chooseRange(r)}>{r}</button>
          {/each}
        </div>
      </div>
      {#if !history.points.length && !phases.length}
        <p class="empty small">{t('chunks.historyEmpty')}</p>
      {/if}
      <LineChart title={m.label} points={shownPoints} live={livePoint} smooth bands={phases} from={rangeStart} to={now} height={chartHeight}
        states={{ points: shownPoints.map((p) => linePoint(p, m)), phases, live: livePoint && linePoint(livePoint, m) }}
        seconds={range === '1m' || range === '10m'} format={(v) => (m.rate ? v.toFixed(1) + '/s' : String(Math.round(v)))}
        refs={[{ value: m.warn, label: t('chunks.warnLimit'), color: 'var(--value-warn)' }, { value: m.lock, label: t('chunks.lockLimit'), color: 'var(--bad)' }].filter((r) => r.value > 0)}
        series={[{ key: m.key, label: m.label, color: 'var(--tn)' }]} onClose={() => (chart = null)} />
    </div>
  </div>
{/if}

<style>
  .page {
    --cl: var(--accent);
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--cl);
    padding: 4px 0;
  }

  .head {
    --st: var(--muted);
    display: grid;
    gap: 16px;
    padding: 20px 22px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--cl) 28%, var(--border));
    border-left: 4px solid var(--st);
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--cl) 18%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .head.good {
    --st: var(--good);
  }

  .head.warn {
    --st: var(--warn);
  }

  .head.bad {
    --st: var(--bad);
  }

  .head.info {
    --st: var(--accent);
  }

  .who {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .who img {
    border-radius: 12px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .names {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .status {
    font-size: 0.74rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
    overflow-wrap: anywhere;
  }

  h2 {
    font-size: 1rem;
    margin: 0;
  }

  .plink {
    background: transparent;
    color: var(--cl);
    padding: 0;
    font-weight: 600;
  }

  .lock {
    padding: 10px 14px;
    border-radius: 10px;
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 10%, var(--surface));
    border: 1px solid color-mix(in srgb, var(--bad) 35%, var(--border));
  }

  .lock.shed {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 10%, var(--surface));
    border-color: color-mix(in srgb, var(--warn) 35%, var(--border));
  }

  .lock.suspended {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, var(--surface));
    border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 180px), 1fr));
  }

  .fact {
    display: grid;
    gap: 3px;
    align-content: start;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    min-width: 0;
  }

  .fact > span {
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 600;
  }

  .fact b {
    font-size: 0.92rem;
    overflow-wrap: anywhere;
  }

  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 8px;
  }

  .coords i {
    font-style: normal;
    white-space: nowrap;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .bar {
    height: 5px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    margin-top: 4px;
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    background: var(--cl);
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .actions.suspend {
    align-items: center;
  }

  /* One value large, over the page */
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    background: rgb(0 0 0 / 45%);
    display: grid;
    place-items: center;
    padding: 16px;
  }

  .big {
    --tn: var(--good);
    display: grid;
    gap: 10px;
    justify-items: start;
    width: min(1400px, 100%);
  }

  .big > :global(*:last-child) {
    justify-self: stretch;
  }

  /* Above the large chart: the value right now, in its colour, beside the range. */
  .big-head {
    justify-self: stretch;
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-radius: 14px;
    background: var(--surface);
    border-left: 4px solid var(--tn);
  }

  .now {
    display: grid;
    gap: 2px;
  }

  .now strong {
    font-size: 2rem;
    line-height: 1.1;
    color: var(--tn);
  }

  .empty {
    margin: 0;
    padding: 6px 12px;
    border-radius: 9px;
    color: var(--muted);
    background: var(--surface);
  }

  .switch {
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
    padding: 5px 11px;
    border-radius: 9px;
    font-size: 0.85rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .push {
    margin-left: auto;
  }

  /* Below: activity on the left, size and members on the right */
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
    align-items: start;
  }

  .side {
    display: grid;
    gap: 16px;
  }

  .c-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }

  .c-icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 10px;
    color: var(--cl);
    background: color-mix(in srgb, var(--cl) 14%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--cl) 30%, transparent);
  }

  .live {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--good);
  }

  .live::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--good);
  }

  .count {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: var(--surface-2);
  }

  .activity p {
    margin: 0 0 8px;
  }

  /* The state of one value: orange over its warn limit, red over its lock limit, dark red while the loader is locked. */
  .metrics,
  .overlay {
    --value-warn: #ee7f1b;
    --value-locked: color-mix(in srgb, var(--bad) 55%, #000);
  }

  .metrics {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
    margin-top: 6px;
  }

  .metric {
    --tn: var(--good);
    display: grid;
    /* It is a button: not centred, and the text of the tile instead of the button's. */
    justify-content: stretch;
    width: 100%;
    gap: 5px;
    /* No padding below: the curve closes the tile. */
    padding: 10px 12px 0;
    overflow: hidden;
    border-radius: 10px;
    color: var(--text);
    font: inherit;
    text-align: left;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-left: 3px solid var(--tn);
  }

  .metric:hover:not(.plain) {
    background: color-mix(in srgb, var(--tn) 8%, var(--surface-2));
  }

  .metric.plain {
    cursor: default;
  }

  /* From edge to edge of the tile, the same height in all of them. */
  .well {
    height: 40px;
    margin: 3px -12px 0;
    border-top: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    background: color-mix(in srgb, var(--tn) 7%, transparent);
  }

  .metric.off,
  .big.off {
    --tn: var(--muted);
  }

  .metric.locked,
  .big.locked {
    --tn: var(--value-locked);
  }

  .metric.warn,
  .big.warn {
    --tn: var(--value-warn);
  }

  .metric.bad,
  .big.bad {
    --tn: var(--bad);
  }

  .mrow {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.88rem;
  }

  .mrow strong {
    color: var(--tn);
  }

  .mrow small {
    font-weight: 600;
  }

  .mbar {
    position: relative;
    height: 7px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: visible;
  }

  .fill {
    height: 100%;
    border-radius: 4px;
    background: var(--tn);
  }

  .tick {
    position: absolute;
    top: -2px;
    bottom: -2px;
    width: 2px;
    border-radius: 1px;
    background: var(--value-warn);
  }

  .tiny {
    font-size: 0.72rem;
  }

  .members {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .members li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .members img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .m-body {
    display: grid;
    min-width: 0;
  }

  .good-text {
    color: var(--good);
  }

  .warn-text {
    color: var(--warn);
  }

  .bad-text {
    color: var(--bad);
  }

  @media (max-width: 860px) {
    .grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 640px) {
    .head {
      padding: 16px;
    }

    .push {
      margin-left: 0;
    }
  }
</style>
