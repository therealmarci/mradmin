<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';

  let state = $state(sessionStorage.getItem('mradmin.tickets.state') ?? 'open');
  let category = $state('');
  let search = $state('');
  let tickets = $state(null);
  let categories = $state([]);
  let error = $state('');

  async function load() {
    try {
      tickets = await api('GET', '/tickets?state=' + state);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  api('GET', '/tickets/categories').then((c) => (categories = c)).catch(() => {});

  // Reload when the tab changes - and live, whenever any ticket changes.
  $effect(() => {
    state;
    live.ticket.tick;
    load();
  });

  function setState(value) {
    state = value;
    tickets = null;
    try {
      sessionStorage.setItem('mradmin.tickets.state', value);
    } catch {
      // fine
    }
  }

  const shown = $derived(
    (tickets ?? []).filter((x) => {
      if (category && x.category !== category) return false;
      if (!search.trim()) return true;
      const q = search.trim().toLowerCase();
      return [String(x.id), x.summary, x.creator, x.target, x.categoryName, x.last?.text]
        .some((v) => v && String(v).toLowerCase().includes(q));
    }),
  );

  const usedCategories = $derived(categories.filter((c) => (tickets ?? []).some((x) => x.category === c.id)));
</script>

<div class="stack">
  <div class="head">
    <h1>{t('tickets.title')}</h1>
    <div class="tabs">
      <button class:active={state === 'open'} onclick={() => setState('open')}>{t('tickets.open')}</button>
      <button class:active={state === 'closed'} onclick={() => setState('closed')}>{t('tickets.closed')}</button>
    </div>
  </div>

  <div class="filters">
    <input type="search" bind:value={search} placeholder={t('tickets.search')} />
    {#if usedCategories.length > 1}
      <div class="chips">
        <button class:active={category === ''} onclick={() => (category = '')}>{t('tickets.all')}</button>
        {#each usedCategories as c (c.id)}
          <button class:active={category === c.id} onclick={() => (category = c.id)}>{c.name}</button>
        {/each}
      </div>
    {/if}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if tickets === null}
    <p class="muted">{t('common.loading')}</p>
  {:else if shown.length === 0}
    <p class="muted">{state === 'open' && !search && !category ? t('tickets.emptyOpen') : t('tickets.empty')}</p>
  {:else}
    <div class="list">
      {#each shown as x (x.id)}
        <button class="card item" class:unread={x.unread} onclick={() => go('/tickets/' + x.id)}>
          <img class="avatar" src={head(x.report ? x.targetUuid : x.creatorUuid, 64)} alt="" width="40" height="40" />
          <div class="body">
            <div class="top">
              <span class="id">#{x.id}</span>
              <span class="cat" class:lock={x.source === 'mrchunks'}>{x.categoryName}</span>
              {#if x.unread}<span class="new">{t('tickets.unread')}</span>{/if}
              <span class="time muted small">{ago(x.lastActivity)}</span>
            </div>
            <div class="summary">{x.summary || x.categoryName}</div>
            <div class="meta muted small">
              {x.creator}{#if x.report} → {x.target}{/if}
              · {t('tickets.messages', { n: x.count })}
              {#if x.claimedBy}· {t('tickets.claimed', { name: x.claimedBy })}{/if}
            </div>
            {#if x.last}
              <div class="last small"><strong>{x.last.name}:</strong> {x.last.text}</div>
            {/if}
          </div>
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .head {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  .head h1 {
    margin: 0;
  }

  .tabs,
  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .tabs button,
  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 0.9rem;
  }

  .tabs button.active,
  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .filters {
    display: grid;
    gap: 10px;
  }

  .list {
    display: grid;
    gap: 10px;
  }

  .item {
    display: flex;
    gap: 14px;
    align-items: flex-start;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    padding: 14px 16px;
    width: 100%;
  }

  .item.unread {
    border-left: 4px solid var(--bad);
  }

  .avatar {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .body {
    min-width: 0;
    flex: 1;
  }

  .top {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .id {
    font-weight: 800;
  }

  .cat {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .cat.lock {
    background: color-mix(in srgb, var(--bad) 15%, transparent);
    color: var(--bad);
  }

  .new {
    font-size: 0.72rem;
    font-weight: 800;
    color: #fff;
    background: var(--bad);
    padding: 1px 7px;
    border-radius: 999px;
  }

  .time {
    margin-left: auto;
  }

  .summary {
    font-weight: 600;
    margin-top: 2px;
  }

  .last {
    margin-top: 4px;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
