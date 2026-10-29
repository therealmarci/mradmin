<script>
  // The players the server knows, in the look of the player area: a head with the numbers, then the players as cards
  // (all / online / flagged), searched on the server.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { FLAGS } from '../lib/players.js';
  import { ICONS } from '../lib/icons.js';

  let filter = $state(sessionStorage.getItem('mradmin.players.filter') ?? 'all');
  let search = $state('');
  let query = $state('');
  let players = $state([]);
  let total = $state(0);
  let page = $state(0);
  let loading = $state(true);
  let error = $state('');

  // The search goes to the server a moment after typing stops.
  let timer;
  function typed() {
    clearTimeout(timer);
    timer = setTimeout(() => (query = search.trim()), 300);
  }

  async function load(more = false) {
    loading = true;
    const next = more ? page + 1 : 0;
    try {
      const params = new URLSearchParams({ q: query, filter, page: String(next) });
      const result = await api('GET', '/players?' + params);
      players = more ? [...players, ...result.players] : result.players;
      total = result.total;
      page = next;
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      loading = false;
    }
  }

  // First page again whenever the search or the filter changes; the list refreshes every 15 seconds.
  $effect(() => {
    query;
    filter;
    load();
    const refresh = setInterval(() => page === 0 && load(), 15000);
    return () => clearInterval(refresh);
  });

  function setFilter(value) {
    filter = value;
    try {
      sessionStorage.setItem('mradmin.players.filter', value);
    } catch {
      // fine
    }
  }

  // How many are online, from the overview (the server keeps it anyway).
  let online = $state(null);
  $effect(() => {
    filter;
    query;
    api('GET', '/overview').then((o) => (online = o.server?.online ?? null)).catch(() => {});
  });
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.users} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.users} /></svg>
      </span>
      <div>
        <h1>{t('players.title')}</h1>
        <p class="muted">{t('players.subtitle')}</p>
      </div>
    </div>
    <div class="numbers">
      <span><b>{total}</b> {t('players.countShort')}</span>
      {#if online !== null}<span><b class="good-text">{online}</b> {t('players.online')}</span>{/if}
    </div>
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  <section class="card tile">
    <div class="tile-head">
      <div class="switch" role="tablist">
        {#each ['all', 'online', 'flagged'] as f}
          <button role="tab" aria-selected={filter === f} class:active={filter === f} onclick={() => setFilter(f)}>{t('players.filter.' + f)}</button>
        {/each}
      </div>
      <input class="search" type="search" bind:value={search} oninput={typed} placeholder={t('players.search')} />
    </div>

    {#if players.length === 0 && !loading}
      <div class="empty">
        <span aria-hidden="true">🔍</span>
        <p>{t('players.empty')}</p>
      </div>
    {:else}
      <ul class="list">
        {#each players as p (p.uuid)}
          <li>
            <button class="player" class:online={p.online} class:flagged={p.flags.length > 0} onclick={() => go('/players/' + p.uuid)}>
              <img src={head(p.uuid)} alt="" width="40" height="40" />
              <span class="p-body">
                <span class="p-top">
                  <strong>{p.name}</strong>
                  <span class="state" class:on={p.online}>{p.online ? '● ' + t('players.online') : ago(p.lastSeen)}</span>
                </span>
                {#if p.flags.length}
                  <span class="flags">
                    {#each p.flags as f}
                      {@const fl = FLAGS[f] ?? { icon: '•', tone: '' }}
                      <span class="tag {fl.tone}">{#if fl.icon}{fl.icon} {/if}{t('flag.' + f)}</span>
                    {/each}
                  </span>
                {/if}
              </span>
            </button>
          </li>
        {/each}
      </ul>
      {#if players.length < total}
        <button class="secondary more" onclick={() => load(true)} disabled={loading}>{t('players.more')}</button>
      {/if}
    {/if}
  </section>
</div>

<style>
  .page {
    --pl: var(--accent);
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
    border: 1px solid color-mix(in srgb, var(--pl) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pl) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--pl);
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
    color: var(--pl);
    background: color-mix(in srgb, var(--pl) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pl) 35%, transparent);
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

  .search {
    width: min(100%, 280px);
  }

  .list {
    list-style: none;
    margin: 14px 0 0;
    padding: 0;
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  }

  /* A player: online green on the left, with flags yellow, otherwise grey. */
  .player {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    padding: 12px 14px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--border);
    border-radius: 12px;
    transition: transform 0.15s, border-color 0.15s;
  }

  .player:hover {
    filter: none;
    transform: translateY(-2px);
  }

  .player.online {
    border-left-color: var(--good);
  }

  .player.flagged {
    border-left-color: var(--warn);
  }

  .player img {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .p-body {
    display: grid;
    gap: 6px;
    flex: 1;
    min-width: 0;
  }

  .p-top {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 8px;
  }

  .p-top strong {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .state {
    font-size: 0.8rem;
    color: var(--muted);
    white-space: nowrap;
  }

  .state.on {
    color: var(--good);
    font-weight: 700;
  }

  .flags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .tag {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .tag.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 15%, transparent);
  }

  .more {
    display: block;
    margin: 14px auto 0;
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

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .search {
      width: 100%;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .player {
      transition: none;
    }
  }
</style>
