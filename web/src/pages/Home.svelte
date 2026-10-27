<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, tpsTone, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';

  let { me } = $props();

  let data = $state(null);
  let error = $state('');

  async function load() {
    try {
      data = await api('GET', '/overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Every 2 seconds - the server keeps a snapshot (once per second), so this costs it nothing.
  $effect(() => {
    load();
    const timer = setInterval(load, 2000);
    return () => clearInterval(timer);
  });

  const mb = (v) => (v >= 1024 ? (v / 1024).toFixed(1) + ' GB' : Math.round(v ?? 0) + ' MB');

  function uptime(millis) {
    const m = Math.floor(millis / 60000);
    const d = Math.floor(m / 1440);
    const h = Math.floor((m % 1440) / 60);
    return d ? `${d} d ${h} h` : h ? `${h} h ${m % 60} min` : `${m} min`;
  }

  // Where an alert leads - only to pages the member may open (the server sends every alert only with permission
  // for its area, the page may still be closed to them, e.g. MSPT without health).
  const pages = {
    health: () => me.may.health && '/health',
    tickets: () => me.may.tickets && '/tickets',
    'tickets-mine': () => me.may.tickets && '/tickets',
    maintenance: () => me.may.maintenance && '/maintenance',
  };
  // Hints of modules (MRchunks, MRsleep ...) name the page themselves - linked if the member may see the module.
  const target = (a) => (a.module ? (me.may[a.module] && a.page) : a.page && pages[a.page]?.()) || null;

  function open(a) {
    if (a.page === 'tickets-mine') {
      try {
        sessionStorage.setItem('mradmin.tickets.state', 'mine');
      } catch {
        // fine
      }
    }
    go(target(a));
  }

  function alertText(a) {
    if (a.text) return a.text;
    const v = { ...a.values };
    if (a.key === 'disk') v.free = mb(v.free);
    if (a.key === 'maintenanceSoon') v.start = v.start ? dateTime(v.start) : '–';
    if (a.key === 'tps' || a.key === 'mspt') v.value = Number(v.value).toFixed(1);
    return t('alert.' + a.key, v);
  }

  const icon = { bad: '✕', warn: '!', info: 'i' };
  const alerts = $derived(data?.alerts ?? []);
  const worst = $derived(alerts[0]?.tone ?? 'good');
  const avgPing = $derived(
    data?.server?.players.length ? Math.round(data.server.players.reduce((s, p) => s + p.ping, 0) / data.server.players.length) : null,
  );
  const phaseTone = { none: 'good', planned: 'info', upcoming: 'warn', active: 'bad' };
</script>

<div class="stack">
  <div>
    <h1>{t('home.hello', { name: me.name })}</h1>
    <p class="muted small">{t('home.live')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    <!-- The situation: what needs attention right now - or, if nothing does, the most important facts. -->
    <section class="card hero tone-{worst}" aria-live="polite">
      {#if alerts.length}
        <div class="hero-head">
          <span class="badge {worst}">{icon[worst]}</span>
          <h2>{t(alerts.length === 1 ? 'home.attentionOne' : 'home.attention', { n: alerts.length })}</h2>
        </div>
        <ul class="alerts">
          {#each alerts as a (a.key)}
            <li>
              <span class="badge small-badge {a.tone}">{icon[a.tone]}</span>
              <span class="text">{alertText(a)}</span>
              {#if target(a)}
                <button class="secondary mini" onclick={() => open(a)}>{t('home.show')} →</button>
              {/if}
            </li>
          {/each}
        </ul>
      {:else}
        <div class="hero-head">
          <span class="badge good">✓</span>
          <h2>{t('home.allGood')}</h2>
        </div>
        <div class="facts-row">
          {#if data.server}
            <span><b>{data.server.tps[0].toFixed(1)}</b> TPS</span>
            <span><b>{data.server.online}</b> {t('home.online')}</span>
            <span>{t('home.upFor')} <b>{uptime(data.server.uptime)}</b></span>
          {/if}
          {#if data.tickets}<span><b>{data.tickets.open}</b> {t('home.ticketsOpen')}</span>{/if}
          {#if data.mrchunks}<span><b>{data.mrchunks.active}</b> {t('home.loadersActive')}</span>{/if}
          {#if data.maintenance}<span>{t('overview.phase.' + data.maintenance.phase)}</span>{/if}
        </div>
        {#if !data.server && !data.tickets && !data.mrchunks && !data.maintenance}
          <p class="muted small">{t('home.nothing')}</p>
        {/if}
      {/if}
    </section>

    <div class="grid">
      {#if data.server}
        {@const s = data.server}
        <button class="card tile" disabled={!me.may.health} onclick={() => go('/health')}>
          <div class="label">{t('overview.server')}</div>
          <div class="big {tpsTone(s.tps[0])}">{s.tps[0].toFixed(1)} <span class="unit">TPS</span></div>
          <div class="facts">
            <div><span class="muted">{t('overview.tps')}</span> {s.tps.map((v) => v.toFixed(1)).join(' / ')}</div>
            <div><span class="muted">{t('overview.mspt')}</span> {s.mspt.toFixed(1)} ms</div>
            <div><span class="muted">{t('overview.ram')}</span> {mb(s.ramUsed)} / {mb(s.ramMax)}
              <div class="bar"><div style="width: {Math.min(100, (s.ramUsed / s.ramMax) * 100)}%"></div></div></div>
          </div>
          <div class="small link">{#if me.may.health}{t('home.toHealth')} →{/if}</div>
        </button>

        <section class="card">
          <div class="label">{t('overview.players')}</div>
          <div class="big">{s.online} <span class="unit">/ {s.max}</span></div>
          <div class="facts">
            <div><span class="muted">{t('home.avgPing')}</span> {avgPing === null ? '–' : avgPing + ' ms'}</div>
            <div><span class="muted">{t('home.upFor')}</span> {uptime(s.uptime)}</div>
            <div><span class="muted">{t('health.disk')}</span> {mb(s.diskFree)} {t('health.free')}</div>
          </div>
          <div></div>
        </section>
      {/if}

      {#if data.tickets}
        {@const tk = data.tickets}
        <button class="card tile" onclick={() => go('/tickets')}>
          <div class="label">{t('overview.tickets')}</div>
          <div class="big {tk.unread > 0 ? 'bad' : tk.open > 0 ? 'warn' : 'good'}">
            {tk.open} <span class="unit">{t('overview.openTickets')}</span>
          </div>
          <div class="facts">
            <div><span class="muted">{t('overview.unread')}</span> <span class:bad={tk.unread > 0}>{tk.unread}</span></div>
            {#if me.may.ticketsClaim}<div><span class="muted">{t('tickets.mine')}</span> {tk.mine}</div>{/if}
          </div>
          <div class="small link">{t('overview.toTickets')} →</div>
        </button>
      {/if}

      {#if data.mrchunks}
        {@const c = data.mrchunks}
        <button class="card tile" onclick={() => go('/chunks')}>
          <div class="label">{t('nav.chunks')}</div>
          <div class="big {c.paused ? 'info' : c.locked > 0 ? 'bad' : c.warn > 0 ? 'warn' : 'good'}">
            {c.active} <span class="unit">/ {c.loaders} {t('home.active')}</span>
          </div>
          <div class="facts">
            {#if c.paused}<div class="info">{STATUS.paused.icon} {t('chunks.status.paused')}</div>{/if}
            <div><span class="muted">{STATUS.locked.icon} {t('chunks.status.locked')}</span> {c.locked}</div>
            <div><span class="muted">{STATUS.warn.icon} {t('chunks.status.warn')}</span> {c.warn}</div>
            <div><span class="muted">{t('chunks.chunks')}</span> {c.chunks}</div>
          </div>
          <div class="small link">{t('home.toChunks')} →</div>
        </button>
      {/if}

      {#if data.mrsleep}
        {@const sl = data.mrsleep}
        {@const managed = sl.worlds.filter((w) => w.managed)}
        <button class="card tile" onclick={() => go('/sleep')}>
          <div class="label">{t('nav.sleep')}</div>
          <div class="big small-big {sl.blocked > 0 ? 'warn' : 'good'}">
            {sl.blocked > 0 ? '⛔ ' + t('home.sleepBlocked', { n: sl.blocked }) : t('home.sleepFree')}
          </div>
          <div class="facts">
            {#each managed.slice(0, 3) as w (w.name)}
              <div><span class="muted">{w.name}</span> {w.thunder ? '⛈' : w.night ? '☽' : '☀'} {w.sleeping} / {w.needed}</div>
            {/each}
            {#if sl.bans}<div><span class="muted">{t('sleep.bans')}</span> {sl.bans}</div>{/if}
          </div>
          <div class="small link">{t('home.toSleep')} →</div>
        </button>
      {/if}

      {#if data.maintenance}
        {@const m = data.maintenance}
        <button class="card tile" onclick={() => go('/maintenance')}>
          <div class="label">{t('overview.maintenance')}</div>
          <div class="big small-big {phaseTone[m.phase]}">{t('overview.phase.' + m.phase)}</div>
          <div class="facts">
            {#if m.phase !== 'none'}
              {#if m.title}<div class="wide"><span class="muted">{t('overview.what')}</span> {m.title}</div>{/if}
              <div><span class="muted">{t('overview.from')}</span> {dateTime(m.start)}</div>
              <div><span class="muted">{t('overview.until')}</span> {m.end ? dateTime(m.end) : t('overview.openEnd')}</div>
            {/if}
          </div>
          <div class="small link">{t('home.toMaintenance')} →</div>
        </button>
      {/if}
    </div>

    {#if data.server}
      <section class="card">
        <div class="label">{t('overview.players')} · {data.server.online} / {data.server.max}</div>
        {#if data.server.players.length === 0}
          <p class="muted small">{t('overview.nobody')}</p>
        {:else}
          <div class="players">
            {#each data.server.players as p (p.uuid)}
              <div class="player">
                <img src={head(p.uuid)} alt="" width="32" height="32" />
                <div>
                  <div class="pname">{p.name}</div>
                  <div class="muted small">
                    {p.world} · {p.ping} ms
                    {#if p.admin}· <span class="warn">{t('overview.admin')}</span>{/if}
                    {#if p.frozen}· <span class="bad">{t('overview.frozen')}</span>{/if}
                  </div>
                </div>
              </div>
            {/each}
          </div>
        {/if}
      </section>
    {/if}

    {#if me.totp && me.recoveryLeft <= 2}
      <button class="card tile notice-tile" onclick={() => go('/account')}>
        <span class="warn">!</span> {t('home.recoveryLow')}
      </button>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .hero {
    border-left: 4px solid var(--good);
    padding: 16px 20px;
  }

  .hero.tone-warn {
    border-left-color: var(--warn);
  }

  .hero.tone-bad {
    border-left-color: var(--bad);
  }

  .hero.tone-info {
    border-left-color: var(--accent);
  }

  .hero-head {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .hero-head h2 {
    margin: 0;
    font-size: 1.15rem;
  }

  .badge {
    display: inline-grid;
    place-items: center;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    color: #fff;
    font-weight: 800;
    background: var(--good);
  }

  .small-badge {
    width: 20px;
    height: 20px;
    font-size: 0.75rem;
  }

  .badge.warn {
    background: var(--warn);
  }

  .badge.bad {
    background: var(--bad);
  }

  .badge.info {
    background: var(--accent);
  }

  .alerts {
    list-style: none;
    margin: 12px 0 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .alerts li {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 8px 10px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .alerts .text {
    flex: 1;
    min-width: 0;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
    flex-shrink: 0;
  }

  .facts-row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 12px;
    font-size: 0.92rem;
  }

  .facts-row > span {
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 8px;
    padding: 5px 10px;
  }

  /* Every tile has the same four rows (label, big value, sub-tiles, link) as a subgrid: in a row of tiles they
     all sit on the same level, however much each one holds. */
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }

  .grid > .card {
    display: grid;
    grid-row: span 4;
    grid-template-rows: subgrid;
    row-gap: 6px;
    align-content: start;
  }

  /* Clickable tiles look exactly like the others: buttons centre their content (also with display: block), so
     a column from the top, full width - the link at the bottom. */
  .tile {
    text-align: left;
    color: var(--text);
    font-weight: 400;
    justify-content: stretch;
    justify-items: stretch;
  }

  .tile:disabled {
    opacity: 1;
    cursor: default;
  }

  .label {
    color: var(--muted);
    font-size: 0.85rem;
    font-weight: 600;
  }

  /* Centred in its row - a short text ("Keine Wartung geplant") sits level with the big numbers. */
  .big {
    font-size: 2rem;
    font-weight: 800;
    line-height: 1.1;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0 6px;
    min-height: 2.4rem;
  }

  .small-big {
    font-size: 1.3rem;
  }

  .unit {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--muted);
  }

  /* Every value as a subtle sub-tile: label small on top, value below. */
  .facts {
    display: grid;
    gap: 6px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 118px), 1fr));
    align-content: start;
  }

  .facts > div {
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 8px;
    padding: 6px 10px;
    font-size: 0.92rem;
    font-weight: 700;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .facts .muted {
    display: block;
    font-size: 0.74rem;
    font-weight: 600;
    margin-bottom: 1px;
  }

  .bar {
    height: 5px;
    border-radius: 3px;
    background: var(--border);
    margin-top: 6px;
    overflow: hidden;
  }

  .bar div {
    height: 100%;
    background: var(--accent);
  }

  .good {
    color: var(--good);
  }

  .warn {
    color: var(--warn);
  }

  .bad {
    color: var(--bad);
  }

  .info {
    color: var(--accent);
  }

  .badge.good,
  .badge.warn,
  .badge.bad,
  .badge.info {
    color: #fff;
  }

  .link {
    color: var(--accent);
    align-self: end;
  }

  .facts .wide {
    grid-column: 1 / -1;
  }

  .players {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    margin-top: 10px;
  }

  .player {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .player img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .pname {
    font-weight: 600;
  }

  .notice-tile {
    padding: 12px 18px;
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }
</style>
