<script>
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  let overview = $state(null);
  let message = $state('');
  let busy = $state(false);

  // Server-wide switches: pause all loaders, staged shutdown (with hard cap) on/off.
  async function control(action, on, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    try {
      await moduleAction('mrchunks', { action, on });
      message = t(`chunks.control.${action}.${on ? 'on' : 'off'}.done`);
      error = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
  let loaders = $state(null);
  let error = $state('');
  let filter = $state(sessionStorage.getItem('mradmin.chunks.filter') ?? 'all');
  let search = $state('');

  async function load() {
    try {
      [overview, loaders] = await Promise.all([moduleData('mrchunks', 'overview'), moduleData('mrchunks', 'loaders')]);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Every 5 seconds - MRchunks measures its loaders every 30 s, toggles show up quickly.
  $effect(() => {
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  function setFilter(value) {
    filter = value;
    try {
      sessionStorage.setItem('mradmin.chunks.filter', value);
    } catch {
      // fine
    }
  }

  // Highest load first (the staged shutdown takes them in this order), then problems, owner and number.
  const order = { locked: 0, warn: 1, shed: 2, paused: 3, ok: 4, inactive: 5 };
  const shown = $derived(
    (loaders ?? [])
      .filter((l) => filter === 'all' || l.status === filter)
      .filter((l) => {
        const q = search.trim().toLowerCase();
        return !q || [l.name, l.ownerName, l.world, `${l.x} ${l.y} ${l.z}`].some((v) => v.toLowerCase().includes(q));
      })
      .sort((a, b) => (b.load ?? -1) - (a.load ?? -1) || order[a.status] - order[b.status] || a.ownerName.localeCompare(b.ownerName) || a.number - b.number),
  );
  const counts = $derived(
    (loaders ?? []).reduce((c, l) => ((c[l.status] = (c[l.status] ?? 0) + 1), c), {}),
  );
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.grid} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.grid} /></svg>
      </span>
      <div>
        <h1>{t('chunks.title')}</h1>
        <p class="muted">{t('chunks.subtitle')}</p>
      </div>
    </div>
    {#if overview}
      <div class="numbers">
        <span><b>{overview.loaders}</b> {t('chunks.loaders')}</span>
        <span><b class="good-text">{overview.active}</b> {t('chunks.active')}</span>
        {#if overview.locked + overview.warn}<span><b class="bad-text">{overview.locked + overview.warn}</b> {t('chunks.problems')}</span>{/if}
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if overview}
    {#if overview.paused}
      <div class="notice">⏸ {t('chunks.paused', { tps: overview.tps, resume: overview.resumeTps })}</div>
    {/if}
    {#if overview.shed}
      <div class="notice warn">{t('chunks.shedNotice', { n: overview.shed, mspt: overview.mspt })}</div>
    {:else if overview.msptWarning}
      <div class="notice warn">{t('chunks.msptNotice', { mspt: overview.mspt })}</div>
    {/if}
    {#if overview.adminPause}
      <div class="notice warn">⏸ {t('chunks.adminPaused', { by: overview.adminPause.by, since: dateTime(overview.adminPause.since) })}</div>
    {/if}

    <div class="kpis">
      <div class="kpi"><div class="label">{t('chunks.loaders')}</div><div class="value">{overview.loaders}</div>
        <div class="sub">{t('chunks.owners', { n: overview.owners })}</div></div>
      <div class="kpi good"><div class="label">{t('chunks.active')}</div><div class="value">{overview.active}</div>
        <div class="bar"><span style="width: {overview.loaders ? (overview.active / overview.loaders) * 100 : 0}%"></span></div></div>
      <div class="kpi"><div class="label">{t('chunks.chunks')}</div><div class="value">{overview.chunks}</div>
        <div class="sub">{overview.defaultBudget != null ? t('chunks.overviewBudget', { n: overview.defaultBudget }) : t('chunks.size', { n: overview.size })}</div></div>
      {#if overview.dummiesEnabled}
        <div class="kpi"><div class="label">{t('chunks.dummies')}</div><div class="value">{overview.dummies}</div></div>
      {/if}
      <div class="kpi {overview.locked ? 'bad' : overview.warn || overview.shed ? 'warn' : ''}"><div class="label">{t('chunks.problems')}</div>
        <div class="value">{overview.locked + overview.warn + (overview.shed ?? 0)}</div>
        <div class="sub">{overview.locked} {t('chunks.status.locked')} · {overview.warn} {t('chunks.status.warn')}{#if overview.shed} · {overview.shed} {t('chunks.status.shed')}{/if}</div></div>
      {#if me?.may?.mrchunksMonitoring && overview.monitoring}
        {@const mon = overview.monitoring}
        <!-- Team switches as key-figure tiles (each with its own permission). -->
        <div class="kpi {mon.active ? 'good' : mon.configured ? 'warn' : ''}"
          title={mon.active ? t('chunks.control.monitoring.activeHint', { warn: mon.warn, shutdown: mon.shutdown, cap: mon.hardCap || '–', mspt: overview.mspt })
            : mon.configured ? t('chunks.control.monitoring.offHint', { by: mon.offBy ?? '?', since: mon.offSince ? dateTime(mon.offSince) : '' }) : ''}>
          <div class="label k-head">{t('chunks.control.monitoring.title')}
            {#if mon.configured}
              <button class="sw" role="switch" aria-checked={mon.active} disabled={busy}
                aria-label={t(mon.active ? 'chunks.control.monitoring.disable' : 'chunks.control.monitoring.enable')}
                onclick={() => (mon.active ? control('monitoring', false, t('chunks.control.monitoring.confirm')) : control('monitoring', true))}>
                <span class="knob" class:on={mon.active}></span>
              </button>
            {/if}
          </div>
          <div class="value">{t(mon.active ? 'chunks.control.on' : 'chunks.control.off')}</div>
          <div class="sub">
            {#if !mon.configured}{t('chunks.control.monitoring.config')}
            {:else if mon.active}{t('chunks.control.monitoring.sub', { mspt: overview.mspt, shutdown: mon.shutdown })}
            {:else}{t('chunks.control.since', { by: mon.offBy ?? '?', since: mon.offSince ? dateTime(mon.offSince) : '' })}{/if}
          </div>
        </div>
      {/if}
      {#if me?.may?.mrchunksPause}
        {@const pause = overview.adminPause}
        <div class="kpi {pause ? 'warn' : ''}" title={t('chunks.control.pauseAll.hint')}>
          <div class="label k-head">{t('chunks.control.pauseAll.title')}
            <button class="sw" role="switch" aria-checked={!!pause} disabled={busy}
              aria-label={t(pause ? 'chunks.control.pauseAll.lift' : 'chunks.control.pauseAll.start')}
              onclick={() => (pause ? control('pauseAll', false) : control('pauseAll', true, t('chunks.control.pauseAll.confirm', { n: overview.active })))}>
              <span class="knob warn" class:on={!!pause}></span>
            </button>
          </div>
          <div class="value">{t(pause ? 'chunks.control.pauseAll.yes' : 'chunks.control.pauseAll.no')}</div>
          <div class="sub">{pause ? t('chunks.control.since', { by: pause.by, since: dateTime(pause.since) }) : t('chunks.control.pauseAll.sub')}</div>
        </div>
      {/if}
    </div>
  {/if}

  <section class="card tile">
    <div class="tile-head">
      <div class="switch" role="tablist">
        <button role="tab" aria-selected={filter === 'all'} class:active={filter === 'all'} onclick={() => setFilter('all')}>
          {t('tickets.all')}{#if loaders}<span class="n">{loaders.length}</span>{/if}
        </button>
        {#each Object.keys(STATUS) as s}
          {#if counts[s]}
            <button role="tab" aria-selected={filter === s} class:active={filter === s} onclick={() => setFilter(s)}>
              <span class="dot {STATUS[s].tone}"></span>{t('chunks.status.' + s)}<span class="n">{counts[s]}</span>
            </button>
          {/if}
        {/each}
      </div>
      <input class="search" type="search" bind:value={search} placeholder={t('chunks.search')} />
    </div>

    {#if !loaders}
      {#if !error}<p class="muted pad">{t('common.loading')}</p>{/if}
    {:else if shown.length === 0}
      <div class="empty">
        <span aria-hidden="true">🗂️</span>
        <p>{t('chunks.empty')}</p>
      </div>
    {:else}
      <ul class="list">
        {#each shown as l (l.id)}
          <li>
            <button class="loader {STATUS[l.status].tone}" onclick={() => go('/chunks/' + l.id)}>
              <span class="l-top">
                <span class="status">{#if STATUS[l.status].icon}{STATUS[l.status].icon} {/if}{t('chunks.status.' + l.status)}</span>
                {#if l.width}<span class="chip">{l.width}×{l.length}</span>{/if}
                {#if l.dummyPresent}<span class="chip" title={t('chunks.dummySits')}>🧍 {t('chunks.dummyBadge')}</span>{/if}
              </span>
              <span class="l-person">
                <img src={head(l.owner)} alt="" width="36" height="36" />
                <span class="l-who">
                  <strong>{l.name}</strong>
                  <span class="muted small">{l.ownerName}</span>
                </span>
              </span>
              <span class="muted small mono">{l.world} {l.x} {l.y} {l.z}</span>
              {#if l.lock}
                <span class="note bad-text">{t('chunks.lockedBy', { metric: l.lock.metric, value: Math.round(l.lock.value), limit: Math.round(l.lock.limit) })} · {ago(l.lock.since)}</span>
              {:else if l.worst && l.status === 'warn'}
                <span class="note warn-text">{l.worst.metric}: {Math.round(l.worst.value)} ({t('chunks.warnLimit')} {Math.round(l.worst.warn)})</span>
              {/if}
              {#if l.shed}
                <span class="note warn-text">{t('chunks.shedBack', { back: dateTime(l.shed.back), since: ago(l.shed.since) })}</span>
              {/if}
              <!-- Load (share of the lock limit of the worst metric): the staged shutdown takes the highest first. -->
              <span class="l-load {l.load >= 1 ? 'bad' : l.loadWarn ? 'warn' : 'good'}" title={t('chunks.loadHint')}>
                <span class="l-load-head"><span>{t('chunks.load')}</span><b>{l.load != null ? Math.round(l.load * 100) + ' %' : '–'}</b></span>
                <span class="bar"><span style="width: {Math.min(100, (l.load ?? 0) * 100)}%"></span></span>
              </span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .notice.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 12%, transparent);
  }

  .page {
    --cl: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--cl) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--cl) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--cl);
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
    color: var(--cl);
    background: color-mix(in srgb, var(--cl) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--cl) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .numbers {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .numbers span {
    padding: 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    font-size: 0.88rem;
  }

  .numbers b {
    font-size: 1.1rem;
    margin-right: 2px;
  }

  /* Switch in the head of a key-figure tile (team controls) */
  .k-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .sw {
    padding: 0;
    min-height: 0;
    background: none;
    border: 0;
    box-shadow: none;
  }

  .sw:hover {
    filter: none;
  }

  .knob {
    position: relative;
    display: block;
    width: 36px;
    height: 20px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
    transition: background 0.15s;
  }

  .knob::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
    transition: transform 0.15s;
  }

  .knob.on {
    background: var(--good);
  }

  .knob.warn.on {
    background: var(--warn);
  }

  .knob.on::after {
    transform: translateX(16px);
  }

  /* Key figures, like on the health page */
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .kpi {
    --tn: var(--cl);
    display: grid;
    gap: 4px;
    align-content: start;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--cl) 10%, transparent), transparent 55%),
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
    background: var(--tn);
  }

  /* The list */
  .tile-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
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
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 9px;
    font-size: 0.88rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--muted);
  }

  .dot.good {
    background: var(--good);
  }

  .dot.warn {
    background: var(--warn);
  }

  .dot.bad {
    background: var(--bad);
  }

  .dot.info {
    background: var(--accent);
  }

  .search {
    width: min(100%, 280px);
  }

  .pad {
    margin: 14px 0 0;
  }

  .list {
    list-style: none;
    margin: 14px 0 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  }

  /* A loader: the colour of its state on the left (active green, off grey, locked red, conspicuous yellow,
     paused blue). */
  .loader {
    --st: var(--muted);
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 16px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--st);
    border-radius: 12px;
    justify-content: flex-start;
    align-items: stretch;
    transition: transform 0.15s;
  }

  .loader:hover {
    filter: none;
    transform: translateY(-2px);
  }

  .loader.good {
    --st: var(--good);
  }

  .loader.warn {
    --st: var(--warn);
  }

  .loader.bad {
    --st: var(--bad);
    background: color-mix(in srgb, var(--bad) 6%, var(--surface-2));
  }

  .loader.info {
    --st: var(--accent);
  }

  .l-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
    margin-right: auto;
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .l-person {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .l-person img {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .l-who {
    display: grid;
    min-width: 0;
  }

  .l-who strong {
    font-size: 1.02rem;
    overflow-wrap: anywhere;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  /* Load bar in every loader card */
  .l-load {
    display: grid;
    gap: 2px;
    width: 100%;
    --tn: var(--good);
  }

  .l-load.warn {
    --tn: var(--warn);
  }

  .l-load.bad {
    --tn: var(--bad);
  }

  .l-load-head {
    display: flex;
    justify-content: space-between;
    font-size: 0.78rem;
    color: var(--muted);
  }

  .l-load-head b {
    color: var(--text);
  }

  .note {
    font-size: 0.82rem;
    font-weight: 600;
  }

  .empty {
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 30px 10px 14px;
    color: var(--muted);
  }

  .empty span {
    font-size: 2.2rem;
  }

  .empty p {
    margin: 0;
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

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .search {
      width: 100%;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .loader {
      transition: none;
    }
  }
</style>
