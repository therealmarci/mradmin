<script>
  // The inbox of the player area (letter at the top right) - everything on one page, laid out like a dashboard:
  // warnings on top, then the tiles News, My tickets and Changelogs. What came since the last visit is marked.
  // A click opens the entry in a panel over the page (read a post, a changelog, a ticket conversation, write a new
  // ticket), so the overview stays where it is. Addresses: /inbox#post-3, /inbox#ticket-12, /inbox#new.
  // Opening the page counts as "looked at the posts" (POST /api/self/inbox/seen); a ticket is read once opened.
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

  const NEWS_SHORT = 5;
  const RELEASES_SHORT = 4;

  let news = $state(null);
  let tickets = $state(null);
  let seen = $state(null);
  let error = $state('');
  let open = $state(readHash());
  let allNews = $state(false);
  let allReleases = $state(false);
  let showClosed = $state(false);

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

  // ------------------------------------------------------------------ the panel

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
    const hash = !next ? '' : next.kind === 'new' ? '#new' : `#${next.kind}-${next.id}`;
    history.replaceState(null, '', '/inbox' + hash);
  }

  const post = $derived(open?.kind === 'post' && news
    ? (news.news.find((p) => p.id === open.id) ?? news.changelog.find((p) => p.id === open.id) ?? null) : null);

  // ------------------------------------------------------------------ the tiles

  const isNew = (p) => seen != null && Math.max(p.created, p.updated || 0) > seen;
  const openTickets = $derived((tickets ?? []).filter((x) => x.open)
    .sort((a, b) => (b.unread - a.unread) || (b.lastActivity - a.lastActivity)));
  const closedTickets = $derived((tickets ?? []).filter((x) => !x.open).sort((a, b) => b.lastActivity - a.lastActivity));
  const newsShown = $derived(!news ? [] : allNews ? news.news : news.news.slice(0, NEWS_SHORT));
  const releasesShown = $derived(!news ? [] : allReleases ? news.changelog : news.changelog.slice(0, RELEASES_SHORT));
  const unread = $derived(!news || !tickets ? 0
    : news.news.filter(isNew).length + news.changelog.filter(isNew).length + tickets.filter((x) => x.unread).length);
  const dateOnly = (ms) => new Date(ms).toLocaleDateString(lang === 'de' ? 'de-AT' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && show(null)} onhashchange={() => (open = readHash())} />

{#snippet icon(name, size = 18)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name]} /></svg>
{/snippet}

<div class="page">
  <header class="head">
    <div>
      <h1>{t('inbox.title')}</h1>
      <p class="muted">{news && tickets ? (unread ? t('inbox.unreadCount', { n: unread }) : t('inbox.allRead')) : t('inbox.subtitle')}</p>
    </div>
    {#if player.tickets}
      <button onclick={() => show({ kind: 'new' })}>＋ {t('self.tickets.new')}</button>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if news && tickets}
    {#each news.alerts as a (a.type + a.id)}
      <div class="alert level-{a.type === 'incident' ? a.level : 'critical'}">
        <span class="a-mark" aria-hidden="true">{a.level === 'info' ? 'i' : '!'}</span>
        <div class="a-body">
          <strong>{a.title}</strong>
          {#if a.text}<span>{a.text}</span>{/if}
          {#if a.link}<a href={a.link} target="_blank" rel="noopener noreferrer">{t('self.news.statusPage')} ↗</a>{/if}
        </div>
        <span class="muted small">{a.type === 'incident' ? t('self.news.fromStatus') : ago(a.created)}</span>
      </div>
    {/each}

    <div class="board">
      {#if player.news}
        <section class="card tile news">
          <div class="t-head">
            {@render icon('news')}
            <h2>{t('self.nav.news')}</h2>
            {#if news.news.filter(isNew).length}<span class="new-chip">{t('inbox.newCount', { n: news.news.filter(isNew).length })}</span>{/if}
          </div>
          {#if !news.news.length}
            <p class="empty muted">{t('self.news.noNews')}</p>
          {:else}
            <ul class="rows">
              {#each newsShown as p (p.id)}
                <li>
                  <button class="row" class:unread={isNew(p)} class:on={open?.kind === 'post' && open.id === p.id} onclick={() => show({ kind: 'post', id: p.id })}>
                    <span class="dot" aria-hidden="true"></span>
                    <span class="r-main">
                      <span class="r-title">{#if p.pinned}<span class="pin" title={t('self.hero.pinned')}>📌</span>{/if}{p.title}</span>
                      {#if p.text}<span class="r-sub muted">{p.text}</span>{/if}
                    </span>
                    <span class="r-when muted small" title={dateTime(p.created)}>{dateOnly(p.created)}</span>
                  </button>
                </li>
              {/each}
            </ul>
            {#if news.news.length > NEWS_SHORT}
              <button class="link more" onclick={() => (allNews = !allNews)}>
                {allNews ? t('inbox.less') : t('inbox.showAll', { n: news.news.length })}
              </button>
            {/if}
          {/if}
        </section>
      {/if}

      {#if player.tickets}
        <section class="card tile tickets">
          <div class="t-head">
            {@render icon('ticket')}
            <h2>{t('inbox.myTickets')}</h2>
            {#if tickets.filter((x) => x.unread).length}<span class="new-chip">{t('inbox.answerCount', { n: tickets.filter((x) => x.unread).length })}</span>{/if}
          </div>
          {#if !openTickets.length}
            <p class="empty muted">{t('inbox.noOpenTickets')}</p>
          {:else}
            <ul class="rows">
              {#each openTickets as x (x.id)}
                {@render ticketRow(x)}
              {/each}
            </ul>
          {/if}
          {#if closedTickets.length}
            <button class="link more" onclick={() => (showClosed = !showClosed)}>
              {showClosed ? t('inbox.hideClosed') : t('inbox.showClosed', { n: closedTickets.length })}
            </button>
            {#if showClosed}
              <ul class="rows closed">
                {#each closedTickets as x (x.id)}
                  {@render ticketRow(x)}
                {/each}
              </ul>
            {/if}
          {/if}
        </section>
      {/if}

      {#if player.news && news.changelog.length}
        <section class="card tile releases">
          <div class="t-head">
            {@render icon('box')}
            <h2>{t('self.news.areaChangelog')}</h2>
            {#if news.changelog.filter(isNew).length}<span class="new-chip">{t('inbox.newCount', { n: news.changelog.filter(isNew).length })}</span>{/if}
          </div>
          <ul class="rows">
            {#each releasesShown as r (r.id)}
              <li>
                <button class="row" class:unread={isNew(r)} class:on={open?.kind === 'post' && open.id === r.id} onclick={() => show({ kind: 'post', id: r.id })}>
                  <span class="dot" aria-hidden="true"></span>
                  <span class="r-main">
                    <span class="r-title">
                      {#if r.version}<span class="version">{r.version}</span>{/if}
                      {r.title && r.title !== r.version ? r.title : ''}
                    </span>
                    <span class="r-sub muted">{t('inbox.entries', { n: entryCount(r.entries) })}</span>
                  </span>
                  <span class="r-when muted small" title={dateTime(r.created)}>{dateOnly(r.created)}</span>
                </button>
              </li>
            {/each}
          </ul>
          {#if news.changelog.length > RELEASES_SHORT}
            <button class="link more" onclick={() => (allReleases = !allReleases)}>
              {allReleases ? t('inbox.less') : t('inbox.showAll', { n: news.changelog.length })}
            </button>
          {/if}
        </section>
      {/if}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

{#snippet ticketRow(x)}
  <li>
    <button class="row" class:unread={x.unread} class:on={open?.kind === 'ticket' && open.id === x.id} onclick={() => show({ kind: 'ticket', id: x.id })}>
      <span class="dot" aria-hidden="true"></span>
      <span class="r-main">
        <span class="r-title">{x.summary}</span>
        <span class="r-sub muted">#{x.id} · {x.category}{#if x.unread} · <b class="answer">{t('inbox.answered')}</b>{/if}</span>
      </span>
      <span class="r-when muted small">{ago(x.lastActivity)}</span>
    </button>
  </li>
{/snippet}

{#if open}
  <!-- The entry over the page; the overview stays behind it -->
  <div class="scrim" role="presentation" onclick={() => show(null)}></div>
  <div class="panel" role="dialog" aria-modal="true" aria-label={t('inbox.title')} tabindex="-1">
    <div class="p-bar">
      <span class="muted small">
        {open.kind === 'new' ? t('self.tickets.new') : open.kind === 'ticket' ? t('inbox.kind.ticket')
          : post ? t('inbox.kind.' + post.type) : ''}
      </span>
      <button class="secondary close" aria-label={t('common.close')} onclick={() => show(null)}>✕</button>
    </div>
    <div class="p-body">
      {#if open.kind === 'new'}
        <TicketForm oncreated={(id) => { load(); show({ kind: 'ticket', id }); }} oncancel={() => show(null)} />
      {:else if open.kind === 'ticket'}
        {#key open.id}
          <SelfTicket id={open.id} embedded onread={() => { load(); onseen?.(); }} />
        {/key}
      {:else if post}
        <article class="read">
          <span class="muted small">
            {#if post.pinned}📌 {t('self.hero.pinned')} · {/if}{dateTime(post.created)}
            {#if post.updated - post.created > 60_000} · {t('self.news.updated', { time: dateTime(post.updated) })}{/if}
          </span>
          {#if post.type === 'changelog'}
            <h2>{#if post.version}<span class="version big">{post.version}</span>{/if} {post.title && post.title !== post.version ? post.title : ''}</h2>
            {#if post.text}<p class="intro">{post.text}</p>{/if}
            <ChangelogDoc entries={post.entries} />
          {:else}
            <h2>{post.title}</h2>
            <p class="text">{post.text}</p>
          {/if}
        </article>
      {:else if news}
        <p class="muted">{t('inbox.gone')}</p>
      {/if}
    </div>
  </div>
{/if}

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  /* Plain head: title, what is new, new ticket */
  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    justify-content: space-between;
    gap: 12px;
  }

  h1 {
    margin: 0;
    font-size: 1.5rem;
  }

  .head p {
    margin: 2px 0 0;
  }

  /* Warnings: one line each, full width */
  .alert {
    --a: var(--warn);
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 16px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--a) 40%, var(--border));
    border-left: 4px solid var(--a);
    background: var(--surface);
  }

  .alert.level-critical,
  .alert.level-danger {
    --a: var(--bad);
  }

  .alert.level-info,
  .alert.level-primary {
    --a: var(--accent);
  }

  .a-mark {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 50%;
    font-size: 0.8rem;
    font-weight: 800;
    color: #fff;
    background: var(--a);
  }

  .a-body {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  /* The tiles: news wide, tickets beside it, changelogs below */
  .board {
    display: grid;
    grid-template-columns: repeat(12, minmax(0, 1fr));
    gap: 16px;
    align-items: start;
  }

  .tile {
    display: grid;
    gap: 6px;
    grid-column: span 12;
    padding: 16px 16px 10px;
  }

  @media (min-width: 860px) {
    .news {
      grid-column: span 7;
    }

    .tickets {
      grid-column: span 5;
    }

    /* without news the tickets take the whole row */
    .tickets:first-child {
      grid-column: span 12;
    }
  }

  .t-head {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--muted);
    padding-bottom: 4px;
  }

  h2 {
    margin: 0;
    font-size: 1rem;
    color: var(--text);
  }

  .new-chip {
    margin-left: auto;
    padding: 1px 9px;
    border-radius: 999px;
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .empty {
    margin: 6px 0 10px;
    font-size: 0.9rem;
  }

  .rows {
    list-style: none;
    margin: 0 -8px;
    padding: 0;
  }

  .rows.closed {
    opacity: 0.75;
  }

  /* One entry: dot for new, title + one line, date */
  .row {
    display: grid;
    grid-template-columns: 8px minmax(0, 1fr) auto;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    width: 100%;
    padding: 8px;
    border: 0;
    border-radius: 8px;
    text-align: left;
    font-weight: 400;
    color: var(--text);
    background: transparent;
  }

  .row:hover,
  .row.on {
    filter: none;
    background: var(--surface-2);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .row.unread .dot {
    background: var(--accent);
  }

  .r-main {
    display: grid;
    min-width: 0;
  }

  .r-title,
  .r-sub {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .r-title {
    font-weight: 500;
  }

  .row.unread .r-title {
    font-weight: 700;
  }

  .r-sub {
    font-size: 0.82rem;
  }

  .pin {
    margin-right: 4px;
    font-size: 0.8rem;
  }

  .answer {
    color: var(--accent);
    font-weight: 700;
  }

  .version {
    display: inline-block;
    margin-right: 6px;
    padding: 0 7px;
    border-radius: 6px;
    font-family: var(--mono, ui-monospace, monospace);
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .version.big {
    font-size: 0.95rem;
  }

  .more {
    justify-self: start;
    padding: 4px 0 6px;
    font-size: 0.85rem;
  }

  /* The panel with the open entry: from the right, the whole screen on a phone */
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 40;
    background: rgb(0 0 0 / 40%);
  }

  .panel {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 41;
    width: min(620px, 100%);
    display: flex;
    flex-direction: column;
    background: var(--bg, var(--surface));
    border-left: 1px solid var(--border);
    box-shadow: -12px 0 40px rgb(0 0 0 / 25%);
  }

  .p-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 10px 16px;
    border-bottom: 1px solid var(--border);
    background: var(--surface);
  }

  .close {
    width: 34px;
    height: 34px;
    padding: 0;
  }

  .p-body {
    flex: 1;
    overflow: auto;
    padding: 18px 16px 28px;
  }

  .read {
    display: grid;
    gap: 10px;
  }

  .read h2 {
    font-size: 1.3rem;
  }

  .text,
  .intro {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.55;
  }
</style>
