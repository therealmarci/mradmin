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
  import PageHead from '../components/PageHead.svelte';
  import FilterChips from '../components/FilterChips.svelte';
  import SwipeRow from '../components/mobile/SwipeRow.svelte';
  import { device } from '../lib/mobile.svelte.js';

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

  // Phone: swipe a ticket to the left - take it over, close it.
  async function quick(x, path, body) {
    try {
      await api('POST', `/tickets/${x.id}/${path}`, body);
      error = '';
      load();
    } catch (e) {
      error = errorText(e);
    }
  }
  const swipes = (x) => state === 'closed' ? (me.may.ticketsReopen ? [{ label: t('ticket.reopen'), tone: 'accent', onclick: () => quick(x, 'reopen') }] : []) : [
    ...(me.may.ticketsClose ? [{ label: t('ticket.close'), tone: 'bad', onclick: () => quick(x, 'close', { text: '' }) }] : []),
    ...(me.may.ticketsClaim && !x.claimedBy ? [{ label: t('ticket.claim'), tone: 'good', onclick: () => quick(x, 'claim') }] : []),
  ];
</script>

<div class="page">
  <PageHead title={t('tickets.title')} stats={!counts ? [] : [
    { label: t('tickets.countOpen'), value: counts.open, tone: counts.open ? 'warn' : '' },
    { label: t('tickets.countUnread'), value: counts.unread, tone: counts.unread ? 'info' : '' },
    ...(me.may.ticketsClaim ? [{ label: t('tickets.countMine'), value: counts.mine, tone: counts.mine ? 'good' : '' }] : []),
  ]} />

  {#if error}<div class="error">{error}</div>{/if}

  <FilterChips value={state} onchange={setState} options={tabs.map((s) => ({ id: s, label: t('tickets.' + s) }))}
    search={search} onsearch={(v) => (search = v)} placeholder={t('tickets.search')} />
  {#if usedCategories.length > 1}
    <FilterChips value={category} onchange={(c) => (category = c)}
      options={[{ id: '', label: t('tickets.all') }, ...usedCategories.map((c) => ({ id: c.id, label: c.name }))]} />
  {/if}

  {#if device.mobile && tickets?.length && shown.length}
    <!-- Phone: one list, a row per ticket, swipe to the left for quick actions -->
    <ul class="rows">
      {#each shown as x (x.id)}
        <li>
          <SwipeRow actions={swipes(x)}>
            <button class="row {tone(x)}" onclick={() => go('/tickets/' + x.id)}>
              <img src={head(x.report ? x.targetUuid : x.creatorUuid, 64)} alt="" width="42" height="42" />
              <span class="r-main">
                <span class="r-line">
                  <span class="r-title">{x.summary || x.categoryName}</span>
                  <span class="r-time">{ago(x.lastActivity)}</span>
                </span>
                <span class="r-sub">
                  <span class="r-id">#{x.id}</span>
                  {x.creator}{#if x.report} → <b>{x.target}</b>{/if}
                  · <span class="r-cat" class:auto={!!x.source}>{x.categoryName}</span>
                </span>
                {#if x.last}<span class="r-last"><strong>{x.last.name}:</strong> {x.last.text}</span>{/if}
                <span class="r-state">
                  {#if state === 'closed'}{t('tickets.closed')}
                  {:else if x.unread}{t('tickets.unread')}
                  {:else if x.claimedBy}{t('tickets.claimed', { name: x.claimedBy })}
                  {:else}{t('tickets.waiting')}{/if}
                  · {t('tickets.messages', { n: x.count })}
                </span>
              </span>
            </button>
          </SwipeRow>
        </li>
      {/each}
    </ul>
  {:else}
  <section class="card tile">
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
  {/if}
</div>

<style>
  .page {
    --tk: var(--accent);
    display: grid;
    gap: 16px;
  }

  /* The list */
  .search {
    width: min(100%, 280px);
  }

  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    margin-top: 12px;
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
    .search {
      width: 100%;
    }
  }

  /* Phone: a list without tiles, a coloured dot for the state */
  .rows {
    margin: 0;
    padding: 0;
    list-style: none;
    border-radius: 18px;
    overflow: hidden;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .rows li + li {
    border-top: 1px solid var(--border);
  }

  .rows :global(.swipe),
  .rows :global(.front) {
    background: var(--surface);
  }

  .row {
    --st: var(--warn);
    position: relative;
    width: 100%;
    display: flex;
    align-items: flex-start;
    justify-content: flex-start;
    gap: 12px;
    padding: 13px 14px 13px 22px;
    border: 0;
    border-radius: 0;
    background: transparent;
    color: var(--text);
    font-weight: 400;
    text-align: left;
  }

  .row:active {
    transform: none;
    background: var(--surface-2);
  }

  .row::before {
    content: '';
    position: absolute;
    left: 8px;
    top: 30px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--st);
  }

  .row.unread {
    --st: var(--accent);
  }

  .row.claimed {
    --st: var(--good);
  }

  .row.closed {
    --st: transparent;
  }

  .row img {
    flex: none;
    border-radius: 10px;
    image-rendering: pixelated;
  }

  .r-main {
    flex: 1;
    min-width: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 1px;
  }

  .r-line {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .r-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 600;
  }

  .unread .r-title {
    font-weight: 800;
  }

  .r-time {
    flex: none;
    color: var(--muted);
    font-size: 0.8rem;
  }

  .unread .r-time {
    color: var(--accent);
    font-weight: 700;
  }

  .r-sub,
  .r-last,
  .r-state {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--muted);
    font-size: 0.86rem;
  }

  .r-id {
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .r-cat.auto {
    color: var(--bad);
  }

  .r-last strong {
    color: var(--text);
    font-weight: 600;
  }

  .r-state {
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--st);
  }

  .closed .r-state {
    color: var(--muted);
  }

  @media (prefers-reduced-motion: reduce) {
    .ticket {
      transition: none;
    }
  }
</style>
