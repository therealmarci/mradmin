<script>
  // MRpvp: running duels live (health of both, time), finished rounds, arenas with their copies, the alerts of the
  // last 24 hours and conspicuous rounds waiting for a decision, the kits. Everything about one round is on /pvp/<id>.
  // Since the sidebar (1.6) every tab has its own address: /admin/plugins/mrpvp[/games|arenas|alerts|reviews|kits].
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { head, dateTime, ago } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';
  import { hearts, clock } from '../lib/pvp.js';
  import { ICONS } from '../lib/icons.js';
  import Hearts from '../components/Hearts.svelte';
  import Toast from '../components/Toast.svelte';
  import PageHead from '../components/PageHead.svelte';
  import PageTabs from '../components/PageTabs.svelte';
  import FilterChips from '../components/FilterChips.svelte';
  import PvpKits from './PvpKits.svelte';

  // asked: the tab from the address (unknown or not allowed: Live)
  let { me, tab: asked = 'live' } = $props();
  let overview = $state(null);
  let games = $state(null);
  let reviews = $state(null);
  let filter = $state('all');
  let offset = $state(0);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  const PAGE = 30;

  async function load() {
    try {
      overview = await moduleData('mrpvp', 'overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  async function loadGames() {
    if (!me.may.mrpvpGames) return;
    try {
      games = await moduleData('mrpvp', 'games', { filter, offset });
    } catch (e) {
      error = errorText(e);
    }
  }

  // Conspicuous rounds waiting for a decision (MRpvp with reviews; an older one answers 404 - tab stays empty).
  async function loadReviews() {
    if (!me.may.mrpvpReview) return;
    try {
      reviews = await moduleData('mrpvp', 'reviews');
    } catch (e) {
      reviews = [];
      if (e.status !== 404) error = errorText(e);
    }
  }

  async function decide(r, action) {
    if (action === 'discard' && !confirm(t('pvp.review.discardConfirm', { id: r.game }))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrpvp', { action, id: String(r.game) });
      message = t('pvp.review.done.' + action, { id: r.game });
      await Promise.all([load(), loadReviews()]);
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Live: MRpvp says when a round changes (start, hits, end); every 5 s as a fallback.
  $effect(() => {
    untrack(load);
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });
  $effect(() => {
    if (live.plugin.plugin === 'mrpvp' && live.plugin.tick) {
      untrack(load);
      if (tab === 'reviews') untrack(loadReviews);
    }
  });
  $effect(() => {
    if (tab === 'reviews') untrack(loadReviews);
  });
  $effect(() => {
    if (tab === 'games') {
      filter;
      offset;
      untrack(loadGames);
    }
  });

  async function stop(game) {
    if (!confirm(t('pvp.stopConfirm', { id: game.id }))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrpvp', { action: 'stop', id: String(game.id) });
      message = t('pvp.done.stop', { id: game.id });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Slots of an arena that are being built right now (after a round or to fill the pool).
  const building = (id) => overview.slots.filter((s) => s.template === id && s.state === 'pasting').length;
  const inUse = $derived(overview ? overview.slots.filter((s) => s.state === 'in_use').length : 0);
  const ready = $derived(overview ? overview.slots.filter((s) => s.state === 'ready').length : 0);

  // Colour of a running round: fighting green, countdown/paused yellow, ending grey.
  const gameTone = (g) => (g.adminPause || g.state === 'paused' || g.state === 'countdown' ? 'warn' : g.state === 'running' ? 'good' : '');
  const resultTone = { win: 'good', draw: 'warn', aborted: '' };

  // The tabs this team member may see, with their counter.
  const BASE = '/admin/plugins/mrpvp';
  const tabs = $derived([
    { id: 'live', n: overview?.games.length },
    me.may.mrpvpGames && { id: 'games' },
    me.may.mrpvpReview && overview?.reviews != null && { id: 'reviews', n: overview.reviews, tone: overview.reviews ? 'warn' : '' },
    { id: 'alerts', n: overview?.alerts.length, tone: overview?.alerts.length ? 'warn' : '' },
    { id: 'arenas', n: overview?.arenas.length },
    me.may.mrpvpKits && { id: 'kits' },
  ].filter(Boolean).map((x) => ({ ...x, label: t('pvp.tab.' + x.id), path: x.id === 'live' ? BASE : BASE + '/' + x.id })));
  const tab = $derived(tabs.some((x) => x.id === asked) ? asked : 'live');
  // The key figures in one line below the title.
  const stats = $derived(!overview ? [] : [
    { label: t('pvp.running'), value: overview.games.length, tone: overview.games.length ? 'good' : '' },
    { label: t('pvp.invites'), value: overview.invites.length },
    { label: t('pvp.arenasInUse'), value: `${inUse} / ${inUse + ready}` },
    ...(overview.reviews != null ? [{ label: t('pvp.reviews'), value: overview.reviews, tone: overview.reviews ? 'warn' : '' }] : []),
    { label: t('pvp.alerts'), value: overview.alerts.length, tone: overview.alerts.length ? 'warn' : '' },
  ]);
</script>

<div class="page">
  <PageHead title={t('pvp.title')} {stats} />
  <PageTabs {tabs} current={tab} />

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if tab === 'kits'}
    <PvpKits embedded />
  {:else}
  {#if tab === 'games'}
    <FilterChips value={filter} onchange={(f) => { filter = f; offset = 0; }}
      options={['all', 'win', 'draw', 'aborted'].map((f) => ({ id: f, label: t('pvp.filter.' + f), tone: f === 'all' ? '' : resultTone[f] || 'off' }))} />
  {/if}
  <section class="card tile">
    {#if !overview}
      {#if !error}<p class="muted pad">{t('common.loading')}</p>{/if}
    {:else if tab === 'live'}
      {#if overview.games.length === 0}
        <div class="empty"><span aria-hidden="true">⚔️</span><p>{t('pvp.noGames')}</p></div>
      {:else}
        <ul class="list">
          {#each overview.games as g (g.id)}
            <li class="game {gameTone(g)}">
              <button class="open" onclick={() => go('/pvp/' + g.id)}>
                <span class="g-top">
                  <span class="status">{g.adminPause ? t('pvp.state.adminPaused', { name: g.adminPause }) : t('pvp.state.' + g.state)}</span>
                  <span class="chip mono">{clock(g.elapsed)}</span>
                  <span class="muted small">#{g.id}</span>
                </span>
                {#each g.players as p, i (p.uuid)}
                  {#if i > 0}<span class="vs">vs</span>{/if}
                  <span class="fighter">
                    <img src={head(p.uuid)} alt="" width="36" height="36" class:off={!p.online} />
                    <span class="who">
                      <strong>{p.name}{#if !p.online} <span class="muted small">({t('pvp.offline')})</span>{/if}</strong>
                      <Hearts health={p.health} max={p.maxHealth} absorption={p.absorption} size={12}
                        hurt={p.lastHurtAt && overview.now - p.lastHurtAt < 900} />
                    </span>
                    <span class="hp mono">{hearts(p.health)}❤</span>
                  </span>
                {/each}
                <span class="muted small">{g.kitName} · {g.arenaName}</span>
              </button>
              {#if me.may.mrpvpStop && g.state !== 'ending'}
                <div class="g-actions">
                  <button class="danger mini" disabled={busy} onclick={() => stop(g)}>{t('pvp.stop')}</button>
                </div>
              {/if}
            </li>
          {/each}
        </ul>
      {/if}
      {#if overview.invites.length}
        <h2 class="sub-head">{t('pvp.openInvites')} <span class="n">{overview.invites.length}</span></h2>
        <ul class="rows">
          {#each overview.invites as i}
            <li>
              <span class="pair">
                <img src={head(i.from.uuid)} alt="" width="24" height="24" /><strong>{i.from.name}</strong>
                <span class="muted">→</span>
                <img src={head(i.to.uuid)} alt="" width="24" height="24" /><strong>{i.to.name}</strong>
              </span>
              <span class="muted small">{i.kit}{i.arena ? ' · ' + i.arena : ''}</span>
            </li>
          {/each}
        </ul>
      {/if}
    {:else if tab === 'games'}
      {#if !games}
        <p class="muted pad">{t('common.loading')}</p>
      {:else if games.length === 0}
        <div class="empty"><span aria-hidden="true">🗂️</span><p>{t('pvp.noFinished')}</p></div>
      {:else}
        <ul class="list">
          {#each games as g (g.id)}
            <li>
              <button class="round {resultTone[g.result] ?? ''}" onclick={() => go('/pvp/' + g.id)}>
                <span class="g-top">
                  <span class="status">{t('pvp.result.' + g.result)}</span>
                  <span class="chip mono">{clock(g.duration)}</span>
                  <span class="muted small">#{g.id}</span>
                </span>
                <span class="duo">
                  {#each g.players as p, i}
                    {#if i > 0}<span class="vs">vs</span>{/if}
                    <span class="mini-fighter" class:winner={p.uuid === g.winner} class:loser={g.winner && p.uuid !== g.winner}>
                      <img src={head(p.uuid)} alt="" width="28" height="28" /><span>{p.name}</span>
                    </span>
                  {/each}
                </span>
                <span class="muted small">{t('pvp.reason.' + g.reason)} · {g.kitName}</span>
                <span class="muted small">{dateTime(g.startedAt)}</span>
              </button>
            </li>
          {/each}
        </ul>
        <div class="pager">
          <button class="secondary" disabled={offset === 0} onclick={() => (offset = Math.max(0, offset - PAGE))}>←</button>
          <span class="muted small">{offset / PAGE + 1}</span>
          <button class="secondary" disabled={games.length < PAGE} onclick={() => (offset += PAGE)}>→</button>
        </div>
      {/if}
    {:else if tab === 'arenas'}
      <ul class="list">
        {#each overview.arenas as a (a.id)}
          {@const b = building(a.id)}
          {@const total = Math.max(a.pool, a.ready + a.used + b, 1)}
          <li class="arena" class:good={a.enabled}>
            <span class="g-top">
              <span class="status">{a.enabled ? t('pvp.enabled') : t('pvp.disabled')}</span>
              <span class="chip mono">{a.size.join('×')}</span>
              {#if a.build}<span class="chip">{t('pvp.build')}</span>{/if}
            </span>
            <span class="a-name">
              <strong>{a.name}</strong>
              <span class="muted small mono">{a.id} · v{a.version}</span>
            </span>
            <span class="slots" title="{t('pvp.arena.used')} {a.used} · {t('pvp.arena.ready')} {a.ready}">
              <span class="seg used" style="width: {(a.used / total) * 100}%"></span>
              <span class="seg ready" style="width: {(a.ready / total) * 100}%"></span>
              <span class="seg building" style="width: {(b / total) * 100}%"></span>
            </span>
            <span class="legend small">
              <span><span class="dot info"></span>{t('pvp.arena.used')} <b>{a.used}</b></span>
              <span><span class="dot good"></span>{t('pvp.arena.ready')} <b>{a.ready}</b></span>
              {#if b}<span><span class="dot warn"></span>{t('pvp.arena.building')} <b>{b}</b></span>{/if}
              <span class="muted">{t('pvp.arena.pool')} {a.pool}</span>
            </span>
            <span class="muted small">{a.kits.length ? a.kits.join(', ') : t('pvp.allKits')}</span>
          </li>
        {/each}
      </ul>
    {:else if tab === 'reviews'}
      <p class="muted small pad">{t('pvp.review.explain')}</p>
      {#if !reviews}
        <p class="muted pad">{t('common.loading')}</p>
      {:else if reviews.length === 0}
        <div class="empty"><span aria-hidden="true">✅</span><p>{t('pvp.review.none')}</p></div>
      {:else}
        <ul class="list">
          {#each reviews as r (r.game)}
            <li class="game warn">
              <span class="g-top">
                <span class="status">{t('pvp.result.' + r.result)}</span>
                <span class="muted small">#{r.game} · {ago(r.endedAt)}</span>
              </span>
              <span class="duo">
                {#each [r.one, r.two] as p, i}
                  {#if i > 0}<span class="vs">vs</span>{/if}
                  <span class="mini-fighter" class:winner={p.uuid === r.winner} class:loser={r.winner && p.uuid !== r.winner}>
                    <img src={head(p.uuid)} alt="" width="28" height="28" /><span>{p.name}</span>
                  </span>
                {/each}
              </span>
              <span class="flags">
                {#each r.flags as f}<span class="flag">{t('pvp.flag.' + f)}</span>{/each}
              </span>
              <span class="muted small">{t('pvp.reason.' + r.reason)} · {r.kitName} · {dateTime(r.endedAt)}</span>
              <div class="g-actions">
                {#if me.may.mrpvpGames}
                  <button class="secondary mini" onclick={() => go('/pvp/' + r.game)}>{t('pvp.review.open')}</button>
                {/if}
                <button class="mini" disabled={busy} onclick={() => decide(r, 'count')}>{t('pvp.review.count')}</button>
                <button class="danger mini" disabled={busy} onclick={() => decide(r, 'discard')}>{t('pvp.review.discard')}</button>
              </div>
            </li>
          {/each}
        </ul>
      {/if}
    {:else if tab === 'alerts'}
      {#if overview.alerts.length === 0}
        <div class="empty"><span aria-hidden="true">✅</span><p>{t('pvp.noAlerts')}</p></div>
      {:else}
        <ul class="list">
          {#each overview.alerts as a}
            <li>
              <button class="round warn" disabled={!me.may.mrpvpGames} onclick={() => go('/pvp/' + a.game)}>
                <span class="g-top">
                  <span class="status">{t('pvp.alert.' + a.type)}</span>
                  <span class="muted small">{ago(a.time)}</span>
                </span>
                <strong>{a.one} <span class="muted">vs</span> {a.two}</strong>
                <span class="muted small">#{a.game} · {a.detail}</span>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    {/if}
  </section>
  {/if}
</div>

<style>
  .page {
    --pv: var(--accent);
    display: grid;
    gap: 16px;
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .dot {
    display: inline-block;
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

  .dot.info {
    background: var(--accent);
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
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  /* A card: the colour of its state on the left (fighting/win/active green, countdown/paused/draw/alert yellow,
     ended/aborted grey). */
  .game,
  .round,
  .arena {
    --st: var(--muted);
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
  }

  .round {
    width: 100%;
    height: 100%;
    justify-content: flex-start;
    align-items: stretch;
    transition: transform 0.15s;
  }

  .round:not(:disabled):hover {
    filter: none;
    transform: translateY(-2px);
  }

  .round:disabled {
    opacity: 1;
    cursor: default;
  }

  .good {
    --st: var(--good);
  }

  .warn {
    --st: var(--warn);
  }

  .open {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0;
    background: none;
    border: 0;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    align-items: stretch;
  }

  .open:hover {
    filter: none;
  }

  .g-top {
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

  .mono {
    font-family: ui-monospace, monospace;
  }

  /* Live: both fighters with their health */
  .fighter {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .fighter img,
  .mini-fighter img,
  .pair img {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .fighter img.off {
    filter: grayscale(1);
    opacity: 0.6;
  }

  .who {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .who strong {
    overflow-wrap: anywhere;
  }

  .hp {
    font-weight: 700;
    min-width: 48px;
    text-align: right;
  }

  .vs {
    align-self: center;
    font-size: 0.7rem;
    font-weight: 800;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--muted);
  }

  .g-actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  /* Finished rounds and reviews: both names in a row, the winner in front */
  .duo {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .mini-fighter {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    font-weight: 600;
  }

  .mini-fighter.winner {
    font-weight: 800;
    color: var(--good);
  }

  .mini-fighter.loser {
    color: var(--muted);
  }

  .mini-fighter.loser img {
    opacity: 0.7;
  }

  .flags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  .flag {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  /* Arenas: how the copies are used */
  .a-name {
    display: grid;
  }

  .a-name strong {
    font-size: 1.02rem;
    overflow-wrap: anywhere;
  }

  .slots {
    display: flex;
    height: 8px;
    border-radius: 4px;
    overflow: hidden;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .seg.used {
    background: var(--accent);
  }

  .seg.ready {
    background: var(--good);
  }

  .seg.building {
    background: var(--warn);
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
  }

  .legend > span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  /* Open invitations */
  .sub-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 20px 0 0;
    font-size: 0.95rem;
  }

  .rows {
    list-style: none;
    margin: 10px 0 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .rows li {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 6px 12px;
    padding: 8px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .pair {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
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

  .pager {
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: center;
    margin-top: 14px;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .round {
      transition: none;
    }
  }
</style>
