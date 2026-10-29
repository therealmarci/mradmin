<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { FLAGS } from '../lib/players.js';

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
</script>

<div class="stack">
  <div>
    <h1>{t('players.title')}</h1>
    <p class="muted small">{t('players.subtitle')}</p>
  </div>

  <div class="filters">
    <div class="chips">
      {#each ['all', 'online', 'flagged'] as f}
        <button class:active={filter === f} onclick={() => setFilter(f)}>{t('players.filter.' + f)}</button>
      {/each}
    </div>
    <input type="search" bind:value={search} oninput={typed} placeholder={t('players.search')} />
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if players.length === 0 && !loading}
    <p class="muted">{t('players.empty')}</p>
  {:else}
    <p class="muted small">{t('players.count', { n: total })}</p>
    <div class="list">
      {#each players as p (p.uuid)}
        <button class="card item" onclick={() => go('/players/' + p.uuid)}>
          <img src={head(p.uuid)} alt="" width="36" height="36" />
          <div class="body">
            <div class="top">
              <strong>{p.name}</strong>
              <span class="state" class:on={p.online}>{p.online ? '● ' + t('players.online') : ago(p.lastSeen)}</span>
            </div>
            {#if p.flags.length}
              <div class="flags">
                {#each p.flags as f}
                  {@const fl = FLAGS[f] ?? { icon: '•', tone: '' }}
                  <span class="tag {fl.tone}">{#if fl.icon}{fl.icon} {/if}{t('flag.' + f)}</span>
                {/each}
              </div>
            {/if}
          </div>
        </button>
      {/each}
    </div>
    {#if players.length < total}
      <button class="secondary more" onclick={() => load(true)} disabled={loading}>{t('players.more')}</button>
    {/if}
  {/if}
</div>

<style>
  .filters {
    display: grid;
    gap: 10px;
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
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 0.9rem;
  }

  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .list {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  .item {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    justify-content: flex-start;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    padding: 12px 14px;
  }

  .item img {
    border-radius: 6px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .body {
    flex: 1;
    min-width: 0;
  }

  .top {
    display: flex;
    gap: 8px;
    justify-content: space-between;
    align-items: baseline;
    flex-wrap: wrap;
  }

  .state {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .state.on {
    color: var(--good);
    font-weight: 600;
  }

  .flags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .tag.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .more {
    justify-self: center;
  }
</style>
