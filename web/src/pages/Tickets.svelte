<script>
  // The team's ticket list, in the look of the player area: a head with the numbers (open, unanswered, assigned to
  // me), then the tickets as cards - open / assigned to me / archive, search and categories. Live: reloads whenever
  // any ticket changes.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';
  import { ICONS } from '../lib/icons.js';

  let { me } = $props();

  // open | mine (open, claimed by me - only for those who may claim) | closed
  const stored = sessionStorage.getItem('mradmin.tickets.state') ?? 'open';
  let state = $state(untrack(() => (stored === 'mine' && !me.may.ticketsClaim ? 'open' : stored)));
  let category = $state('');
  let search = $state('');
  let tickets = $state(null);
  let categories = $state([]);
  let counts = $state(null);
  let error = $state('');

  async function load() {
    try {
      tickets = await api('GET', '/tickets?state=' + state);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
    // The numbers in the head come from the overview (the server keeps them anyway).
    api('GET', '/overview').then((o) => (counts = o.tickets ?? null)).catch(() => {});
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
    category = '';
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
  const tabs = $derived(['open', ...(me.may.ticketsClaim ? ['mine'] : []), 'closed']);

  // The colour of a card: new answer from the player (blue), taken by someone (green), open without anyone (yellow),
  // archive (grey).
  const tone = (x) => (state === 'closed' ? 'closed' : x.unread ? 'unread' : x.claimedBy ? 'claimed' : 'waiting');
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.ticket} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.ticket} /></svg>
      </span>
      <div>
        <h1>{t('tickets.title')}</h1>
        <p class="muted">{t('tickets.subtitle')}</p>
      </div>
    </div>
    {#if counts}
      <div class="numbers">
        <span><b class="warn-text">{counts.open}</b> {t('tickets.countOpen')}</span>
        <span><b class="info-text">{counts.unread}</b> {t('tickets.countUnread')}</span>
        {#if me.may.ticketsClaim}<span><b class="good-text">{counts.mine}</b> {t('tickets.countMine')}</span>{/if}
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  <section class="card tile">
    <div class="tile-head">
      <div class="switch" role="tablist">
        {#each tabs as s}
          <button role="tab" aria-selected={state === s} class:active={state === s} onclick={() => setState(s)}>{t('tickets.' + s)}</button>
        {/each}
      </div>
      <input class="search" type="search" bind:value={search} placeholder={t('tickets.search')} />
    </div>
    {#if usedCategories.length > 1}
      <div class="chips" role="group" aria-label={t('tickets.categories')}>
        <button class:active={category === ''} onclick={() => (category = '')}>{t('tickets.all')}</button>
        {#each usedCategories as c (c.id)}
          <button class:active={category === c.id} onclick={() => (category = c.id)}>{c.name}</button>
        {/each}
      </div>
    {/if}

    {#if tickets === null}
      <p class="muted pad">{t('common.loading')}</p>
    {:else if shown.length === 0}
      <div class="empty">
        <span aria-hidden="true">{!search && !category && state !== 'closed' ? '✨' : '🗂️'}</span>
        <p>{!search && !category && state !== 'closed' ? t(state === 'mine' ? 'tickets.emptyMine' : 'tickets.emptyOpen') : t('tickets.empty')}</p>
      </div>
    {:else}
      <ul class="list">
        {#each shown as x (x.id)}
          <li>
            <button class="ticket {tone(x)}" onclick={() => go('/tickets/' + x.id)}>
              <span class="t-top">
                <span class="id">#{x.id}</span>
                <span class="chip" class:auto={!!x.source}>{x.categoryName}</span>
                <span class="status">
                  {#if state === 'closed'}{t('tickets.closed')}
                  {:else if x.unread}● {t('tickets.unread')}
                  {:else if x.claimedBy}{t('tickets.claimed', { name: x.claimedBy })}
                  {:else}{t('tickets.waiting')}{/if}
                </span>
              </span>
              <span class="t-person">
                <img src={head(x.report ? x.targetUuid : x.creatorUuid, 64)} alt="" width="36" height="36" />
                <span class="t-who">
                  <span class="t-title">{x.summary || x.categoryName}</span>
                  <span class="muted small">{x.creator}{#if x.report} → <b>{x.target}</b>{/if}</span>
                </span>
              </span>
              {#if x.last}
                <span class="last small"><strong>{x.last.name}:</strong> {x.last.text}</span>
              {/if}
              <span class="t-bottom muted small">
                <span>{t('tickets.messages', { n: x.count })}</span>
                <span>{ago(x.lastActivity)}</span>
              </span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .page {
    --tk: var(--accent);
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
    border: 1px solid color-mix(in srgb, var(--tk) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tk) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--tk);
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
    color: var(--tk);
    background: color-mix(in srgb, var(--tk) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--tk) 35%, transparent);
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

  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    margin-top: 12px;
  }

  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border: 1px solid var(--border);
    padding: 4px 12px;
    border-radius: 999px;
    font-size: 0.82rem;
  }

  .chips button.active {
    color: var(--tk);
    border-color: var(--tk);
    background: color-mix(in srgb, var(--tk) 12%, var(--surface-2));
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

  .ticket {
    --st: var(--warn);
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 10px;
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
    transition: transform 0.15s, border-color 0.15s;
  }

  .ticket:hover {
    filter: none;
    transform: translateY(-2px);
  }

  .ticket.unread {
    --st: var(--accent);
    background: color-mix(in srgb, var(--accent) 7%, var(--surface-2));
  }

  .ticket.claimed {
    --st: var(--good);
  }

  .ticket.closed {
    --st: var(--muted);
  }

  .ticket.closed .t-title {
    color: var(--muted);
  }

  .t-top,
  .t-bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .id {
    font-weight: 800;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  /* Tickets a plugin opened (e.g. a chunk loader locked for lag). */
  .chip.auto {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .status {
    margin-left: auto;
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
  }

  .t-person {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .t-person img {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .t-who {
    display: grid;
    min-width: 0;
  }

  .t-title {
    font-weight: 700;
    font-size: 1.02rem;
    overflow-wrap: anywhere;
  }

  .last {
    color: var(--muted);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }

  .last strong {
    color: var(--text);
  }

  .t-bottom {
    margin-top: auto;
    justify-content: space-between;
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

  .info-text {
    color: var(--accent);
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
    .ticket {
      transition: none;
    }
  }
</style>
