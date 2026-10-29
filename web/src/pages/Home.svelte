<script>
  // Start page of the team panel, in the look of the player area: greeting, the situation as a slideshow (what needs
  // attention, one slide each - or the key facts), one tile per area (server, players, tickets, the plugins, sleep, maintenance, versions) and the
  // players online.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, tpsTone, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';
  import { MODULE_ICONS, moduleHue } from '../lib/icons.js';
  import DashTile from '../components/DashTile.svelte';
  import Carousel from '../components/Carousel.svelte';

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
    versions: () => me.may.versions && '/versions',
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
    if (a.key === 'restartSoon') v.time = dateTime(v.time);
    if (a.key === 'tps' || a.key === 'mspt') v.value = Number(v.value).toFixed(1);
    return t('alert.' + a.key, v);
  }

  // "12 / 49" or "120 / 245 Chunks" in a plugin's fact -> fill of a bar (0..1); everything else -> null
  function meter(value) {
    const m = /^\s*(\d[\d.,]*)\s*\/\s*(\d[\d.,]*)/.exec(String(value ?? ''));
    if (!m) return null;
    const num = (s) => Number(s.replace(/[.,]/g, ''));
    const max = num(m[2]);
    return max > 0 ? Math.min(1, num(m[1]) / max) : null;
  }

  const icon = { bad: '✕', warn: '!', info: 'i', good: '✓' };
  const alerts = $derived(data?.alerts ?? []);
  const worst = $derived(alerts[0]?.tone ?? 'good');
  const phaseTone = { none: 'good', planned: 'info', upcoming: 'warn', active: 'bad' };
  const msptTone = (v) => (v <= 40 ? '' : v <= 50 ? 'warn' : 'bad');
  const share = (a, b) => (b > 0 ? a / b : 0);

  // The situation as slides: the server status first (with or without warnings), then one slide per warning.
  const slideTone = (tone) => (tone === 'info' ? 'accent' : tone);
  const slides = $derived([
    { key: 'status', kind: 'status', tone: slideTone(worst) },
    ...alerts.map((a) => ({ key: 'alert:' + a.key, kind: 'alert', tone: slideTone(a.tone), alert: a })),
  ]);

  // Which part of the server a warning is about.
  function areaOf(a) {
    if (a.module) return (data?.tiles ?? []).find((m) => m.id === a.module)?.label ?? a.module;
    if (a.key.startsWith('tickets')) return t('nav.tickets');
    if (a.key.startsWith('maintenance')) return t('nav.maintenance');
    if (a.key.startsWith('versions') || a.key === 'restartSoon') return t('nav.versions');
    return t('nav.health');
  }

  // "in 2 h 5 min" / "in 4 min" until a time
  function until(time) {
    const m = Math.max(0, Math.ceil((time - Date.now()) / 60000));
    const h = Math.floor(m / 60);
    return h ? t('self.hero.inHours', { h, m: m % 60 }) : t('self.hero.inMinutes', { m });
  }

  // The details of a warning from the rest of the overview: a line and a few facts.
  function detail(a) {
    const s = data?.server;
    const tk = data?.tickets;
    const m = data?.maintenance;
    const v = data?.versions;
    if (a.module) {
      const tile = (data?.tiles ?? []).find((x) => x.id === a.module);
      return { line: '', facts: tile ? [{ label: tile.label, value: `${tile.value} ${tile.unit ?? ''}`.trim(), tone: tile.tone }, ...tile.facts] : [] };
    }
    if (s && ['tps', 'mspt', 'cpu'].includes(a.key)) {
      return { line: t('home.d.load', { tps: s.tps.map((x) => x.toFixed(1)).join(' / '), mspt: s.mspt.toFixed(1), online: s.online }), facts: [] };
    }
    if (s && a.key === 'ram') return { line: t('home.d.ram', { used: mb(s.ramUsed), max: mb(s.ramMax) }), facts: [] };
    if (s && a.key === 'disk') return { line: t('home.d.disk', { free: mb(s.diskFree), total: mb(s.diskTotal) }), facts: [] };
    if (s && a.key === 'ping' && s.players.length) {
      const top = s.players.reduce((x, p) => (p.ping > x.ping ? p : x));
      return { line: t('home.d.ping', { online: s.online, max: top.ping, name: top.name }), facts: [] };
    }
    if (tk && a.key === 'ticketsUnread') return { line: t('home.d.tickets', { open: tk.open, unread: tk.unread }), facts: [] };
    if (tk && a.key === 'ticketsMine') return { line: t('home.d.ticketsMine', { mine: tk.mine, open: tk.open }), facts: [] };
    if (m && a.key.startsWith('maintenance')) {
      return {
        line: t('home.d.maintenance', { start: dateTime(m.start), end: m.end ? dateTime(m.end) : t('overview.openEnd') }),
        facts: [
          ...(m.title ? [{ label: t('overview.what'), value: m.title }] : []),
          ...(a.key === 'maintenanceSoon' && m.start > Date.now() ? [{ label: t('home.d.maintenanceIn', { in: until(m.start) }), value: '' }] : []),
        ],
      };
    }
    if (a.key === 'restartSoon') return { line: t('home.d.restart', { in: until(a.values.time) }), facts: [] };
    if (v && a.key.startsWith('versions')) return { line: t('home.d.versions', v), facts: [] };
    return { line: '', facts: [] };
  }
</script>

<div class="page">
  <section class="card hello">
    <img src={head(me.uuid)} alt="" width="64" height="64" />
    <div>
      <h1>{t('home.hello', { name: me.name })}</h1>
      <p class="muted"><span class="dot" class:on={live.connected}></span>{t('home.live')}</p>
    </div>
  </section>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    <!-- The situation as a slideshow: with warnings an overview first, then one slide per warning with its details -
         otherwise "everything runs smoothly" with the key facts. -->
    <Carousel {slides} seconds={8} label={t('home.slides')}>
      {#snippet slide(s, i, active)}
        {#if s.kind === 'status'}
          <div class="s-head"><span class="badge big {worst}">{icon[worst]}</span>
            <h2>{alerts.length ? t('home.status') : t('home.allGood')}</h2>
          </div>
          <div class="chips">
            {#if data.server}
              <span><b>{data.server.tps[0].toFixed(1)}</b> TPS</span>
              <span><b>{data.server.online}</b> {t('home.online')}</span>
              <span>{t('home.upFor')} <b>{uptime(data.server.uptime)}</b></span>
            {/if}
            {#if data.tickets}<span><b>{data.tickets.open}</b> {t('home.ticketsOpen')}</span>{/if}
            {#each (data.tiles ?? []).filter((m) => m.headline) as m (m.id)}<span>{m.headline}</span>{/each}
            {#if data.maintenance}<span>{t('overview.phase.' + data.maintenance.phase)}</span>{/if}
          </div>
          {#if !data.server && !data.tickets && !data.tiles?.length && !data.maintenance}
            <p class="small">{t('home.nothing')}</p>
          {/if}
        {:else}
          {@const a = s.alert}
          {@const d = detail(a)}
          <div class="kind">
            <span class="badge small {a.tone}">{icon[a.tone]}</span>
            {t('home.sev.' + a.tone)} · {areaOf(a)}
            <span class="when">{i} / {alerts.length}</span>
          </div>
          <h2>{alertText(a)}</h2>
          {#if d.line}<p>{d.line}</p>{/if}
          {#if d.facts.length}
            <div class="chips">
              {#each d.facts as f}<span class={f.tone ? 'sev ' + f.tone : ''}>{f.label} <b>{f.value}</b></span>{/each}
            </div>
          {/if}
          {#if target(a)}
            <button class="more" tabindex={active ? 0 : -1} onclick={() => open(a)}>{t('home.show')} →</button>
          {/if}
        {/if}
      {/snippet}
    </Carousel>

    <div class="tiles">
      {#if data.server}
        {@const s = data.server}
        <DashTile icon="pulse" hue={210} title={t('overview.server')} value={s.tps[0].toFixed(1)} unit="TPS" tone={tpsTone(s.tps[0])}
          rows={[
            { label: t('overview.tps'), value: s.tps.map((v) => v.toFixed(1)).join(' / ') },
            { label: t('overview.mspt'), value: s.mspt.toFixed(1) + ' ms', tone: msptTone(s.mspt), fill: s.mspt / 50 },
            { label: t('overview.ram'), value: `${mb(s.ramUsed)} / ${mb(s.ramMax)}`, fill: share(s.ramUsed, s.ramMax),
              tone: share(s.ramUsed, s.ramMax) > 0.9 ? 'warn' : '' },
          ]}
          link={me.may.health ? t('home.toHealth') : ''} onclick={me.may.health ? () => go('/health') : null} />
      {/if}

      {#if data.tickets}
        {@const tk = data.tickets}
        <DashTile icon="ticket" hue={270} title={t('overview.tickets')} value={tk.open} unit={t('overview.openTickets')}
          tone={tk.unread > 0 ? 'bad' : tk.open > 0 ? 'warn' : 'good'}
          rows={[
            { label: t('overview.unread'), value: tk.unread, tone: tk.unread > 0 ? 'bad' : '' },
            ...(me.may.ticketsClaim ? [{ label: t('tickets.mine'), value: tk.mine }] : []),
          ]}
          link={t('overview.toTickets')} onclick={() => go('/tickets')} />
      {/if}

      <!-- Tiles of the modules (MRchunks ...): label, value and facts come from the plugin itself. -->
      {#each data.tiles ?? [] as m (m.id)}
        <DashTile icon={MODULE_ICONS[m.id] ?? 'box'} hue={moduleHue(m.id)} title={m.label} value={m.value} unit={m.unit} tone={m.tone}
          rows={m.facts.map((f) => ({ label: f.label, value: f.value, tone: f.tone, fill: meter(f.value) }))}
          link={m.link} onclick={m.page ? () => go(m.page) : null} />
      {/each}

      {#if data.mrsleep}
        {@const sl = data.mrsleep}
        {@const managed = sl.worlds.filter((w) => w.managed)}
        <DashTile icon="moon" hue={235} title={t('nav.sleep')} small
          value={sl.blocked > 0 ? '⛔ ' + t('home.sleepBlocked', { n: sl.blocked }) : t('home.sleepFree')}
          tone={sl.blocked > 0 ? 'warn' : 'good'}
          rows={[
            ...managed.slice(0, 3).map((w) => ({ label: w.name, value: `${w.thunder ? '⛈' : w.night ? '☽' : '☀'} ${w.sleeping} / ${w.needed}`,
              fill: share(w.sleeping, w.needed) })),
            ...(sl.bans ? [{ label: t('sleep.bans'), value: sl.bans }] : []),
          ]}
          link={t('home.toSleep')} onclick={() => go('/sleep')} />
      {/if}

      {#if data.maintenance}
        {@const m = data.maintenance}
        <DashTile icon="tool" hue={28} title={t('overview.maintenance')} small value={t('overview.phase.' + m.phase)} tone={phaseTone[m.phase]}
          rows={m.phase === 'none' ? [] : [
            ...(m.title ? [{ label: t('overview.what'), value: m.title }] : []),
            { label: t('overview.from'), value: dateTime(m.start) },
            { label: t('overview.until'), value: m.end ? dateTime(m.end) : t('overview.openEnd') },
          ]}
          link={t('home.toMaintenance')} onclick={() => go('/maintenance')} />
      {/if}

      {#if data.versions}
        {@const v = data.versions}
        <DashTile icon="box" hue={190} title={t('nav.versions')} small
          value={v.restartAt ? t('home.versionsRestart') : v.staged ? t('home.versionsStaged') : v.updates ? t('home.versionsUpdates', { n: v.updates }) : t('home.versionsCurrent')}
          tone={v.restartAt ? 'bad' : v.staged ? 'info' : v.updates ? 'warn' : 'good'}
          rows={[
            { label: t('home.versionsPlugins'), value: v.plugins },
            { label: t('home.versionsAvailable'), value: v.updates, tone: v.updates ? 'warn' : '' },
            { label: t('home.versionsWaiting'), value: v.staged },
          ]}
          link={t('home.toVersions')} onclick={() => go('/versions')} />
      {/if}
    </div>

    {#if data.server}
      <section class="card online">
        <div class="o-head">
          <span class="o-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c0-3.3 3.1-6 7-6s7 2.7 7 6M16 3.5a4 4 0 0 1 0 7M18 14c2.4.6 4 2.6 4 5" /></svg>
          </span>
          <h2>{t('overview.players')}</h2>
          <span class="count">{data.server.online} / {data.server.max}</span>
        </div>
        {#if data.server.players.length === 0}
          <p class="muted small">{t('overview.nobody')}</p>
        {:else}
          <div class="players">
            {#each data.server.players as p (p.uuid)}
              <svelte:element this={me.may.players ? 'button' : 'div'} class="player" onclick={me.may.players ? () => go('/players/' + p.uuid) : undefined}
                role={me.may.players ? undefined : 'group'}>
                <img src={head(p.uuid)} alt="" width="36" height="36" />
                <span class="p-body">
                  <span class="pname">{p.name}</span>
                  <span class="muted small">{p.world} · {p.ping} ms</span>
                </span>
                {#if p.admin}<span class="tag warn">{t('overview.admin')}</span>{/if}
                {#if p.frozen}<span class="tag bad">{t('overview.frozen')}</span>{/if}
              </svelte:element>
            {/each}
          </div>
        {/if}
      </section>
    {/if}

    {#if me.totp && me.recoveryLeft <= 2}
      <button class="notice" onclick={() => go('/account')}>
        <span class="badge small warn">!</span> {t('home.recoveryLow')} <span class="arrow" aria-hidden="true">→</span>
      </button>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .hello {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .hello img {
    border-radius: 10px;
    image-rendering: pixelated;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .hello p {
    margin: 4px 0 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
    background: var(--muted);
  }

  .dot.on {
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 25%, transparent);
  }

  /* Inside the slides of the situation (the slide itself comes from Carousel). */
  .kind .badge {
    margin-right: 2px;
  }

  .badge {
    display: inline-grid;
    place-items: center;
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    color: #fff;
    font-weight: 800;
    font-size: 0.8rem;
    letter-spacing: 0;
    background: var(--accent);
  }

  /* Server status: the big round mark next to the heading, like the situation tile before. */
  .s-head {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 6px;
  }

  .badge.big {
    width: 32px;
    height: 32px;
    font-size: 1rem;
  }

  .badge.small {
    width: 20px;
    height: 20px;
    font-size: 0.7rem;
  }

  .badge.good {
    background: var(--good);
  }

  .badge.warn {
    background: var(--warn);
  }

  .badge.bad {
    background: var(--bad);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 0.92rem;
  }

  .chips > span {
    background: color-mix(in srgb, var(--surface-2) 85%, transparent);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 999px;
    padding: 5px 12px;
  }

  .chips .sev.bad {
    color: var(--bad);
    border-color: color-mix(in srgb, var(--bad) 40%, transparent);
  }

  .chips .sev.warn {
    color: var(--warn);
    border-color: color-mix(in srgb, var(--warn) 40%, transparent);
  }

  .chips .sev.info {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  }

  .tiles {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  /* Players online */
  .online {
    display: grid;
    gap: 14px;
  }

  .o-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .o-icon {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    border-radius: 11px;
    color: hsl(150 70% 45%);
    background: color-mix(in srgb, hsl(150 70% 45%) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, hsl(150 70% 45%) 35%, transparent);
  }

  .o-head h2 {
    flex: 1;
  }

  .count {
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    font-size: 0.85rem;
  }

  .players {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr));
  }

  .player {
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: flex-start;
    padding: 10px 12px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    color: var(--text);
    font: inherit;
    font-weight: 400;
    text-align: left;
  }

  button.player:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .player img {
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .p-body {
    display: grid;
    flex: 1;
    min-width: 0;
  }

  .pname {
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .tag {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    flex-shrink: 0;
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .notice {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    padding: 12px 18px;
    border-radius: var(--radius);
    background: color-mix(in srgb, var(--warn) 10%, var(--surface));
    border: 1px solid color-mix(in srgb, var(--warn) 40%, var(--border));
    color: var(--text);
    font: inherit;
    font-weight: 600;
    text-align: left;
  }

  .notice .arrow {
    margin-left: auto;
    color: var(--warn);
  }

  .notice:hover {
    filter: none;
  }
</style>
