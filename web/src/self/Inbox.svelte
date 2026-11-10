<script>
  // The inbox of the player area (letter at the top right), built like a mail inbox: warnings on top, then a filter
  // bar (all / news / changelogs / tickets) and the button for a new ticket; below on the left one list with
  // everything that came in - news, changelogs and the own tickets, mixed and newest first, with day separators -
  // and on the right the reading pane (read a post or a changelog, the ticket conversation, a new ticket).
  // Phone: only the list; a tap shows the entry over the whole width with a back button.
  // Addresses: /inbox#post-3, /inbox#ticket-12, /inbox#new.
  // Opening the page counts as "looked at the posts" (POST /api/self/inbox/seen); what was new stays marked until
  // it was opened or the page loads again. A ticket is read once opened.
  // Data: GET /api/self/inbox (SelfInbox.java), /api/self/news?all=1 (SelfNews.java), /api/self/tickets (SelfTickets.java).
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { ago, dateTime } from '../lib/format.js';
  import { entryCount } from '../lib/changelog.js';
  import { ICONS } from '../lib/icons.js';
  import ChangelogDoc from '../components/ChangelogDoc.svelte';
  import TicketForm from '../components/TicketForm.svelte';
  import SelfTicket from './SelfTicket.svelte';

  // player: GET /api/self (news / tickets switched on); onseen: the counter in the bar may load again
  let { player, onseen = null } = $props();

  let news = $state(null);
  let tickets = $state(null);
  let seen = $state(null);
  let error = $state('');
  let open = $state(readHash());
  let filter = $state('all');
  let readNow = $state(new Set(open && open.kind !== 'new' ? [`${open.kind}-${open.id}`] : []));

  async function load() {
    try {
      const [n, tk] = await Promise.all([
        player.news ? api('GET', '/self/news?all=1&lang=' + lang) : { alerts: [], news: [], changelog: [] },
        player.tickets ? api('GET', '/self/tickets') : { tickets: [] },
      ]);
      news = n;
      tickets = tk.tickets;
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // The last visit first (to mark what is new), then everything counts as seen.
  $effect(() => {
    (async () => {
      try {
        seen = (await api('GET', '/self/inbox')).seen;
        await load();
        await api('POST', '/self/inbox/seen', {});
        onseen?.();
      } catch (e) {
        error = errorText(e);
      }
    })();
  });
  $effect(() => poll(load, 30000));

  // ------------------------------------------------------------------ selection

  function readHash() {
    const h = location.hash;
    let m = h.match(/^#post-(\d+)$/);
    if (m) return { kind: 'post', id: Number(m[1]) };
    m = h.match(/^#ticket-(\d+)$/);
    if (m) return { kind: 'ticket', id: Number(m[1]) };
    return h === '#new' ? { kind: 'new' } : null;
  }

  function show(next) {
    open = next;
    if (next && next.kind !== 'new') readNow = new Set(readNow).add(`${next.kind}-${next.id}`);
    const hash = !next ? '' : next.kind === 'new' ? '#new' : `#${next.kind}-${next.id}`;
    history.replaceState(null, '', '/inbox' + hash);
  }

  function keyOpen(event, next) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      show(next);
    }
  }

  const isOpen = (kind, id) => open?.kind === kind && open.id === id;

  // ------------------------------------------------------------------ the list

  const postNew = (p) => seen != null && Math.max(p.created, p.updated || 0) > seen && !readNow.has('post-' + p.id);
  const ticketNew = (x) => x.unread && !readNow.has('ticket-' + x.id);
  const ticketTone = (x) => (!x.open ? 'none' : ticketNew(x) ? 'info' : 'good');
  const ticketState = (x) => (!x.open ? t('inbox.badge.closed') : ticketNew(x) ? t('inbox.badge.answer') : t('inbox.badge.open'));

  // Everything as one list, newest first: {kind, key, time, fresh, item}
  const rows = $derived.by(() => {
    if (!news || !tickets) return [];
    const all = [
      ...news.news.map((p) => ({ kind: 'news', key: 'post-' + p.id, time: Math.max(p.created, p.updated || 0), fresh: postNew(p), item: p })),
      ...news.changelog.map((p) => ({ kind: 'changelog', key: 'post-' + p.id, time: Math.max(p.created, p.updated || 0), fresh: postNew(p), item: p })),
      ...tickets.map((x) => ({ kind: 'ticket', key: 'ticket-' + x.id, time: x.lastActivity, fresh: ticketNew(x), item: x })),
    ];
    return all.sort((a, b) => b.time - a.time);
  });

  const counts = $derived({
    all: rows.filter((r) => r.fresh).length,
    news: rows.filter((r) => r.kind === 'news' && r.fresh).length,
    changelog: rows.filter((r) => r.kind === 'changelog' && r.fresh).length,
    ticket: rows.filter((r) => r.kind === 'ticket' && r.fresh).length,
  });

  const filters = $derived([
    { id: 'all', label: t('inbox.filter.all') },
    ...(player.news ? [{ id: 'news', label: t('inbox.filter.news') }, { id: 'changelog', label: t('inbox.filter.changelog') }] : []),
    ...(player.tickets ? [{ id: 'ticket', label: t('inbox.filter.tickets') }] : []),
  ]);

  const shown = $derived(filter === 'all' ? rows : rows.filter((r) => r.kind === filter));

  // Day separators: today, yesterday, otherwise the date
  const locale = lang === 'de' ? 'de-AT' : 'en-GB';
  function dayLabel(ms) {
    const day = new Date(ms).setHours(0, 0, 0, 0);
    const today = new Date().setHours(0, 0, 0, 0);
    if (day === today) return t('inbox.today');
    if (today - day <= 86_400_000 + 3_600_000) return t('inbox.yesterday');
    return new Date(ms).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });
  }
  const timeLabel = (ms) => {
    const day = new Date(ms).setHours(0, 0, 0, 0);
    return day === new Date().setHours(0, 0, 0, 0)
      ? new Date(ms).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
      : new Date(ms).toLocaleDateString(locale, { day: 'numeric', month: 'short' });
  };
  const preview = (text) => (text || '').replace(/\s+/g, ' ').trim();

  // ------------------------------------------------------------------ the reading pane

  const post = $derived(open?.kind === 'post' && news
    ? (news.news.find((p) => p.id === open.id) ?? news.changelog.find((p) => p.id === open.id) ?? null) : null);
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && show(null)} onhashchange={() => (open = readHash())} />

{#snippet icon(name, size = 16)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name]} /></svg>
{/snippet}

<div class="page">
  <header class="head">
    <h1>{t('inbox.title')}</h1>
    {#if news && tickets}
      <span class="muted">{counts.all ? t('inbox.newCount', { n: counts.all }) : t('inbox.allRead')}</span>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if news?.alerts.length}
    <div class="alerts">
      {#each news.alerts as a (a.type + a.id)}
        {@const tone = a.type !== 'incident' || a.level === 'danger' ? 'bad' : a.level === 'warning' ? 'warn' : 'info'}
        <div class="alert tone-{tone}">
          <strong>{a.title}</strong>
          {#if a.text}<span class="a-text">{a.text}</span>{/if}
          <span class="a-meta muted">{a.type === 'incident' ? t('self.news.fromStatus') : ago(a.created)}</span>
          {#if a.link}<a href={a.link} target="_blank" rel="noopener noreferrer">{t('self.news.statusPage')} ↗</a>{/if}
        </div>
      {/each}
    </div>
  {/if}

  {#if news && tickets}
    <div class="box card" class:reading={open}>
      <div class="bar">
        <div class="filters" role="tablist">
          {#each filters as f (f.id)}
            <button role="tab" aria-selected={filter === f.id} class:on={filter === f.id} onclick={() => (filter = f.id)}>
              {f.label}{#if counts[f.id]}<span class="count">{counts[f.id]}</span>{/if}
            </button>
          {/each}
        </div>
        {#if player.tickets}
          <button class="small new" class:on={open?.kind === 'new'} onclick={() => show({ kind: 'new' })}
            aria-label={t('self.tickets.new')}>＋<span class="new-label"> {t('self.tickets.new')}</span></button>
        {/if}
      </div>

      <div class="split">
        <!-- The list -->
        <div class="list" role="list">
          {#if !shown.length}
            <p class="muted empty">{filter === 'ticket' ? t('inbox.noTickets') : t('inbox.empty')}</p>
          {/if}
          {#each shown as r, i (r.key)}
            {#if i === 0 || dayLabel(r.time) !== dayLabel(shown[i - 1].time)}
              <div class="day" role="presentation">{dayLabel(r.time)}</div>
            {/if}
            {@const target = r.kind === 'ticket' ? { kind: 'ticket', id: r.item.id } : { kind: 'post', id: r.item.id }}
            {@const tone = r.kind === 'ticket' ? ticketTone(r.item) : r.fresh ? 'info' : 'none'}
            <div class="row tone-{tone}" class:fresh={r.fresh} class:sel={isOpen(target.kind, target.id)} role="listitem">
              <div class="row-btn" role="button" tabindex="0" onclick={() => show(target)} onkeydown={(e) => keyOpen(e, target)}>
                <span class="r-icon" aria-hidden="true">{@render icon(r.kind === 'ticket' ? 'ticket' : r.kind === 'changelog' ? 'box' : 'news')}</span>
                <div class="r-main">
                  <div class="r-top">
                    {#if r.kind === 'changelog'}
                      <strong class="r-title">{#if r.item.version}<span class="version">{r.item.version}</span>{/if}{r.item.title && r.item.title !== r.item.version ? r.item.title : ''}</strong>
                    {:else if r.kind === 'ticket'}
                      <strong class="r-title">{r.item.summary}</strong>
                    {:else}
                      <strong class="r-title">{#if r.item.pinned}<span class="pin" title={t('self.hero.pinned')}>📌</span>{/if}{r.item.title}</strong>
                    {/if}
                    <span class="r-time" title={dateTime(r.time)}>{timeLabel(r.time)}</span>
                  </div>
                  <div class="r-sub">
                    {#if r.kind === 'ticket'}
                      <span class="state">{ticketState(r.item)}</span><span class="muted">#{r.item.id} · {r.item.category}</span>
                    {:else if r.kind === 'changelog'}
                      <span class="muted">{t('inbox.kind.changelog')} · {t('inbox.entries', { n: entryCount(r.item.entries) })}</span>
                    {:else}
                      <span class="muted ellipsis">{preview(r.item.text)}</span>
                    {/if}
                  </div>
                </div>
              </div>
            </div>
          {/each}
        </div>

        <!-- The reading pane -->
        <div class="pane">
          {#if open}
            <button class="link back" onclick={() => show(null)}>← {t('inbox.back')}</button>
          {/if}
          {#if open?.kind === 'new'}
            <h2 class="p-title">{t('self.tickets.new')}</h2>
            <TicketForm oncreated={(id) => { load(); filter = filter === 'all' ? 'all' : 'ticket'; show({ kind: 'ticket', id }); }} oncancel={() => show(null)} />
          {:else if open?.kind === 'ticket'}
            {#key open.id}
              <SelfTicket id={open.id} embedded onread={() => { load(); onseen?.(); }} />
            {/key}
          {:else if post}
            <article class="read">
              <span class="muted small">
                {t('inbox.kind.' + post.type)} · {#if post.pinned}📌 {t('self.hero.pinned')} · {/if}{dateTime(post.created)}
                {#if post.updated - post.created > 60_000} · {t('self.news.updated', { time: dateTime(post.updated) })}{/if}
              </span>
              {#if post.type === 'changelog'}
                <h2 class="p-title">{#if post.version}<span class="version">{post.version}</span>{/if} {post.title && post.title !== post.version ? post.title : ''}</h2>
                {#if post.text}<p class="text">{post.text}</p>{/if}
                <ChangelogDoc entries={post.entries} />
              {:else}
                <h2 class="p-title">{post.title}</h2>
                <p class="text">{post.text}</p>
              {/if}
            </article>
          {:else if open}
            <p class="muted">{t('inbox.gone')}</p>
          {:else}
            <div class="pick muted">
              <span class="pick-icon" aria-hidden="true">{@render icon('mail', 40)}</span>
              <p>{t('inbox.pick')}</p>
            </div>
          {/if}
        </div>
      </div>
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 14px;
  }

  .head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .muted {
    color: var(--muted);
  }

  .small {
    font-size: 0.82rem;
  }

  /* ---------------------------------------------------------------- warnings */

  .alerts {
    display: grid;
    gap: 8px;
  }

  .alert {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 12px;
    padding: 12px 16px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tone) 45%, var(--border));
    border-left: 4px solid var(--tone);
    background: color-mix(in srgb, var(--tone) 10%, var(--surface));
  }

  .a-text {
    flex: 1 1 260px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .a-meta {
    font-size: 0.82rem;
  }

  /* ---------------------------------------------------------------- the box: bar + list + pane */

  .box {
    padding: 0;
    overflow: hidden;
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 10px 12px;
    border-bottom: 1px solid var(--border);
  }

  .filters {
    display: flex;
    gap: 4px;
    flex: 1 1 auto;
    min-width: 0;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .filters button {
    flex-shrink: 0;
  }

  .filters button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    justify-content: flex-start;
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.86rem;
    background: transparent;
    color: var(--muted);
    border: 1px solid transparent;
  }

  .filters button:hover {
    filter: none;
    color: var(--text);
  }

  .filters button.on {
    color: var(--text);
    background: var(--surface-2);
    border-color: var(--border);
  }

  .count {
    min-width: 18px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 800;
    line-height: 18px;
    text-align: center;
    color: #fff;
    background: var(--accent);
  }

  .new.on {
    outline: 2px solid color-mix(in srgb, var(--accent) 50%, transparent);
  }

  .split {
    display: grid;
    grid-template-columns: minmax(280px, 380px) minmax(0, 1fr);
    height: max(460px, calc(100vh - 260px));
  }

  .list {
    overflow-y: auto;
    border-right: 1px solid var(--border);
    padding: 4px 0 12px;
  }

  .empty {
    padding: 16px;
    margin: 0;
  }

  .day {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: 8px 16px 4px;
    font-size: 0.74rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--muted);
    background: color-mix(in srgb, var(--surface) 92%, transparent);
    backdrop-filter: blur(6px);
  }

  /* One entry: a row tile with the status colour as a bar on the left */
  .row {
    padding: 2px 8px;
  }

  .row-btn {
    position: relative;
    display: flex;
    gap: 10px;
    align-items: flex-start;
    padding: 10px 12px 10px 14px;
    border-radius: 10px;
    border: 1px solid transparent;
    cursor: pointer;
    min-width: 0;
  }

  .row-btn::before {
    content: '';
    position: absolute;
    left: 4px;
    top: 10px;
    bottom: 10px;
    width: 3px;
    border-radius: 3px;
    background: var(--tone);
    opacity: 0.85;
  }

  .row.tone-none .row-btn::before {
    opacity: 0.3;
  }

  .row-btn:hover {
    background: var(--surface-2);
  }

  .row.sel .row-btn {
    background: color-mix(in srgb, var(--accent) 10%, var(--surface-2));
    border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
  }

  .row-btn:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 1px;
  }

  .tone-good {
    --tone: var(--good);
  }

  .tone-warn {
    --tone: var(--warn);
  }

  .tone-bad {
    --tone: var(--bad);
  }

  .tone-info {
    --tone: var(--accent);
  }

  .tone-none {
    --tone: var(--muted);
  }

  .r-icon {
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    flex-shrink: 0;
    border-radius: 8px;
    color: var(--tone);
    background: color-mix(in srgb, var(--tone) 14%, var(--surface-2));
  }

  .r-main {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .r-top {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .r-title {
    flex: 1;
    min-width: 0;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row.fresh .r-title {
    font-weight: 800;
  }

  .r-time {
    flex-shrink: 0;
    font-size: 0.78rem;
    color: var(--muted);
  }

  .row.fresh .r-time {
    color: var(--accent);
    font-weight: 700;
  }

  .r-sub {
    display: flex;
    gap: 8px;
    min-width: 0;
    font-size: 0.84rem;
  }

  .ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .state {
    flex-shrink: 0;
    font-weight: 700;
    color: var(--tone);
  }

  .pin {
    margin-right: 4px;
    font-size: 0.8rem;
  }

  .version {
    display: inline-block;
    margin-right: 6px;
    padding: 0 7px;
    border-radius: 6px;
    font-family: var(--mono, ui-monospace, monospace);
    font-size: 0.85em;
    font-weight: 700;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  /* ---------------------------------------------------------------- reading pane */

  .pane {
    overflow-y: auto;
    padding: 20px 24px;
    display: grid;
    align-content: start;
    gap: 12px;
    min-width: 0;
  }

  .back {
    display: none;
    justify-self: start;
    padding: 2px 0;
    background: transparent;
    border: 0;
    color: var(--accent);
    font-weight: 600;
  }

  .p-title {
    margin: 0;
    font-size: 1.3rem;
  }

  .read {
    display: grid;
    gap: 10px;
  }

  .text {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.55;
  }

  .pick {
    display: grid;
    place-items: center;
    gap: 6px;
    min-height: 320px;
    text-align: center;
  }

  .pick-icon {
    opacity: 0.35;
  }

  /* Phone / narrow: only the list; an open entry takes the whole width with a back button */
  @media (max-width: 760px) {
    .split {
      grid-template-columns: minmax(0, 1fr);
      height: auto;
    }

    .list {
      border-right: 0;
      overflow: visible;
    }

    .pane {
      display: none;
      padding: 14px 16px 18px;
    }

    .box.reading .list {
      display: none;
    }

    .box.reading .pane {
      display: grid;
    }

    .back {
      display: inline-block;
    }

    .box.reading .bar {
      display: none;
    }

    .new-label {
      display: none;
    }

    .new {
      flex-shrink: 0;
      min-width: 38px;
      font-size: 1.1rem;
    }
  }
</style>
