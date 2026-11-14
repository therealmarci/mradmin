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

  @media (prefers-reduced-motion: reduce) {
    .ticket {
      transition: none;
    }
  }
</style>
