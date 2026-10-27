<script>
  // MRpvp: running duels live (health of both, time), finished rounds, arenas with their copies, the alerts of the
  // last 24 hours and conspicuous rounds waiting for a decision. Everything about one round is on /pvp/<id>.
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { head, dateTime, ago } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';
  import { hearts, clock } from '../lib/pvp.js';
  import Hearts from '../components/Hearts.svelte';

  let { me } = $props();

  let tab = $state('live');
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

  const outcomeOf = (g, p) => p?.outcome ?? g.result;

  // Slots of an arena that are being built right now (after a round or to fill the pool).
  const building = (id) => overview.slots.filter((s) => s.template === id && s.state === 'pasting').length;
</script>

<div class="stack">
  <div>
    <h1>{t('pvp.title')}</h1>
    <p class="muted small">{t('pvp.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if overview}
    <div class="kpis">
      <div class="card kpi"><div class="label">{t('pvp.running')}</div><div class="value">{overview.games.length}</div></div>
      <div class="card kpi"><div class="label">{t('pvp.invites')}</div><div class="value">{overview.invites.length}</div></div>
      <div class="card kpi"><div class="label">{t('pvp.arenasInUse')}</div>
        <div class="value">{overview.slots.filter((s) => s.state === 'in_use').length}</div>
        <div class="sub">{t('pvp.slotsReady', { n: overview.slots.filter((s) => s.state === 'ready').length })}</div></div>
      <div class="card kpi"><div class="label">{t('pvp.alerts')}</div><div class="value">{overview.alerts.length}</div>
        <div class="sub">{t('pvp.bans', { n: overview.bans })}</div></div>
      {#if overview.reviews != null}
        <div class="card kpi"><div class="label">{t('pvp.reviews')}</div>
          <div class="value" class:attention={overview.reviews > 0}>{overview.reviews}</div>
          <div class="sub">{t('pvp.reviewsSub')}</div></div>
      {/if}
    </div>
  {/if}

  <div class="chips">
    <button class:active={tab === 'live'} onclick={() => (tab = 'live')}>{t('pvp.tab.live')}</button>
    {#if me.may.mrpvpGames}
      <button class:active={tab === 'games'} onclick={() => (tab = 'games')}>{t('pvp.tab.games')}</button>
    {/if}
    <button class:active={tab === 'arenas'} onclick={() => (tab = 'arenas')}>{t('pvp.tab.arenas')}</button>
    <button class:active={tab === 'alerts'} onclick={() => (tab = 'alerts')}>{t('pvp.tab.alerts')}</button>
    {#if me.may.mrpvpReview && overview?.reviews != null}
      <button class:active={tab === 'reviews'} onclick={() => (tab = 'reviews')}>
        {t('pvp.tab.reviews')}{overview.reviews ? ` (${overview.reviews})` : ''}
      </button>
    {/if}
  </div>

  {#if !overview}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else if tab === 'live'}
    {#if overview.games.length === 0}
      <p class="muted">{t('pvp.noGames')}</p>
    {:else}
      <div class="list">
        {#each overview.games as g (g.id)}
          <div class="card game">
            <button class="open" onclick={() => go('/pvp/' + g.id)}>
              <div class="top">
                <strong>#{g.id}</strong>
                <span class="badge-status {g.state}">{t('pvp.state.' + g.state)}</span>
                <span class="muted small">{clock(g.elapsed)}</span>
              </div>
              {#each g.players as p (p.uuid)}
                <div class="fighter">
                  <img src={head(p.uuid)} alt="" width="28" height="28" />
                  <div class="who">
                    <div class="name">{p.name}{#if !p.online} <span class="muted small">({t('pvp.offline')})</span>{/if}</div>
                    <Hearts health={p.health} max={p.maxHealth} absorption={p.absorption} size={12}
                      hurt={p.lastHurtAt && overview.now - p.lastHurtAt < 900} />
                  </div>
                  <div class="hp mono">{hearts(p.health)}❤</div>
                </div>
              {/each}
              <div class="muted small">{g.kitName} · {g.arenaName}</div>
            </button>
            {#if me.may.mrpvpStop && g.state !== 'ending'}
              <button class="secondary small" disabled={busy} onclick={() => stop(g)}>{t('pvp.stop')}</button>
            {/if}
          </div>
        {/each}
      </div>
    {/if}
    {#if overview.invites.length}
      <h2>{t('pvp.openInvites')}</h2>
      <div class="card rows">
        {#each overview.invites as i}
          <div class="row small">
            <img src={head(i.from.uuid)} alt="" width="20" height="20" /> {i.from.name} → {i.to.name}
            <span class="muted">{i.kit}{i.arena ? ' · ' + i.arena : ''}</span>
          </div>
        {/each}
      </div>
    {/if}
  {:else if tab === 'games'}
    <div class="chips">
      {#each ['all', 'win', 'draw', 'aborted'] as f}
        <button class:active={filter === f} onclick={() => { filter = f; offset = 0; }}>{t('pvp.filter.' + f)}</button>
      {/each}
    </div>
    {#if !games}
      <p class="muted">{t('common.loading')}</p>
    {:else if games.length === 0}
      <p class="muted">{t('pvp.noFinished')}</p>
    {:else}
      <div class="list">
        {#each games as g (g.id)}
          <button class="card item {g.result}" onclick={() => go('/pvp/' + g.id)}>
            <div class="body">
              <div class="top">
                <strong>#{g.id}</strong>
                <span class="badge-status {g.result}">{t('pvp.result.' + g.result)}</span>
              </div>
              <div class="small">
                {#each g.players as p, i}
                  {#if i > 0}<span class="muted"> vs </span>{/if}
                  <span class:winner={p.uuid === g.winner}>{p.name}</span>
                {/each}
              </div>
              <div class="muted small">{t('pvp.reason.' + g.reason)} · {clock(g.duration)} · {g.kitName} · {dateTime(g.startedAt)}</div>
            </div>
          </button>
        {/each}
      </div>
      <div class="pager">
        <button class="secondary" disabled={offset === 0} onclick={() => (offset = Math.max(0, offset - PAGE))}>←</button>
        <span class="muted small">{offset / PAGE + 1}</span>
        <button class="secondary" disabled={games.length < PAGE} onclick={() => (offset += PAGE)}>→</button>
      </div>
    {/if}
  {:else if tab === 'arenas'}
    <div class="list">
      {#each overview.arenas as a (a.id)}
        <div class="card item {a.enabled ? 'good' : ''}">
          <div class="body">
            <div class="top">
              <strong>{a.name}</strong>
              <span class="badge-status">{a.enabled ? t('pvp.enabled') : t('pvp.disabled')}</span>
            </div>
            <div class="muted small mono">{a.id} · v{a.version} · {a.size.join('×')}</div>
            <div class="facts small">
              <span><span class="muted">{t('pvp.arena.pool')}</span> {a.pool}</span>
              <span><span class="muted">{t('pvp.arena.ready')}</span> {a.ready}</span>
              {#if building(a.id)}<span><span class="muted">{t('pvp.arena.building')}</span> {building(a.id)}</span>{/if}
              <span><span class="muted">{t('pvp.arena.used')}</span> {a.used}</span>
            </div>
            <div class="muted small">{a.kits.length ? a.kits.join(', ') : t('pvp.allKits')}{a.build ? ' · ' + t('pvp.build') : ''}</div>
          </div>
        </div>
      {/each}
    </div>
  {:else if tab === 'reviews'}
    <p class="muted small">{t('pvp.review.explain')}</p>
    {#if !reviews}
      <p class="muted">{t('common.loading')}</p>
    {:else if reviews.length === 0}
      <p class="muted">{t('pvp.review.none')}</p>
    {:else}
      <div class="list">
        {#each reviews as r (r.game)}
          <div class="card item warn">
            <div class="body">
              <div class="top">
                <strong>#{r.game}</strong>
                <span class="badge-status {r.result}">{t('pvp.result.' + r.result)}</span>
                <span class="muted small">{ago(r.endedAt)}</span>
              </div>
              <div class="small">
                <span class:winner={r.one.uuid === r.winner}>{r.one.name}</span>
                <span class="muted"> vs </span>
                <span class:winner={r.two.uuid === r.winner}>{r.two.name}</span>
              </div>
              <div class="flags">
                {#each r.flags as f}<span class="flag">{t('pvp.flag.' + f)}</span>{/each}
              </div>
              <div class="muted small">{t('pvp.reason.' + r.reason)} · {r.kitName} · {dateTime(r.endedAt)}</div>
              <div class="actions">
                {#if me.may.mrpvpGames}
                  <button class="secondary small" onclick={() => go('/pvp/' + r.game)}>{t('pvp.review.open')}</button>
                {/if}
                <button class="small" disabled={busy} onclick={() => decide(r, 'count')}>{t('pvp.review.count')}</button>
                <button class="secondary small" disabled={busy} onclick={() => decide(r, 'discard')}>{t('pvp.review.discard')}</button>
              </div>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  {:else if tab === 'alerts'}
    {#if overview.alerts.length === 0}
      <p class="muted">{t('pvp.noAlerts')}</p>
    {:else}
      <div class="list">
        {#each overview.alerts as a}
          <button class="card item warn" onclick={() => me.may.mrpvpGames && go('/pvp/' + a.game)}>
            <div class="body">
              <div class="top">
                <strong>{t('pvp.alert.' + a.type)}</strong>
                <span class="muted small">{ago(a.time)}</span>
              </div>
              <div class="small">#{a.game} · {a.one} vs {a.two}</div>
              <div class="muted small">{a.detail}</div>
            </div>
          </button>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
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
  }

  .sub {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.85rem;
  }

  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .list {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  }

  .game {
    display: grid;
    gap: 8px;
    padding: 12px 14px;
  }

  .open {
    display: grid;
    gap: 8px;
    background: none;
    border: 0;
    padding: 0;
    text-align: left;
    color: var(--text);
    font-weight: 400;
  }

  .item {
    display: flex;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    padding: 12px 14px;
    border-left: 4px solid var(--border);
  }

  .item.win, .item.good { border-left-color: var(--good); }
  .item.draw, .item.warn { border-left-color: var(--warn); }
  .item.aborted { border-left-color: var(--muted); }

  .facts {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
  }

  .body {
    min-width: 0;
    flex: 1;
    display: grid;
    gap: 2px;
  }

  .top {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .fighter {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .fighter img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .who {
    flex: 1;
    min-width: 0;
  }

  .name {
    font-weight: 600;
  }


  .hp {
    min-width: 48px;
    text-align: right;
  }

  .badge-status {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .badge-status.running, .badge-status.win, .badge-status.ready { color: var(--good); }
  .badge-status.countdown, .badge-status.paused, .badge-status.draw, .badge-status.pasting { color: var(--warn); }
  .badge-status.in_use { color: var(--accent); }

  .winner {
    font-weight: 700;
  }

  .attention {
    color: var(--warn);
  }

  .flags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
    margin: 4px 0;
  }

  .flag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--warn);
  }

  .actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    margin-top: 6px;
  }

  .rows {
    display: grid;
    gap: 6px;
    padding: 10px 14px;
  }

  .row {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .row img {
    border-radius: 4px;
  }

  .pager {
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: center;
  }
</style>
