<script>
  // The inbox of the player area (letter at the top right): notifications, newest first with day separators - a new
  // news post, a new changelog, an answer of the team to an own ticket. Unread ones are bold with a coloured bar,
  // read ones grey. A click opens the content in a window over the page and marks it read; "mark all as read" clears
  // the counter. Each one (or all) can go into the archive (switch Inbox | Archive), from where it can come back;
  // something new on it (e.g. a new answer) brings it back by itself. The whole list of posts and tickets is in the entry News (/news, /news/changelog, /news/tickets).
  // Addresses: /inbox#post-3, /inbox#ticket-12 open that window (links from Discord, chat and push).
  // Data: GET /api/self/inbox (SelfInbox.java), contents from /api/self/news?all=1 (SelfNews.java) and the ticket.
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { go } from '../lib/router.svelte.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { ago, dateTime } from '../lib/format.js';
  import { ICONS } from '../lib/icons.js';
  import ChangelogDoc from '../components/ChangelogDoc.svelte';
  import SelfTicket from './SelfTicket.svelte';

  // player: GET /api/self (news / tickets switched on); onseen: the counter in the bar may load again
  let { player, onseen = null } = $props();

  let inbox = $state(null);
  let news = $state(null);
  let error = $state('');
  let open = $state(readHash());
  let view = $state('inbox');

  async function load() {
    try {
      const [i, n] = await Promise.all([
        api('GET', '/self/inbox?lang=' + lang),
        player.news ? api('GET', '/self/news?all=1&lang=' + lang) : { alerts: [], news: [], changelog: [] },
      ]);
      inbox = i;
      news = n;
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }
  load();
  $effect(() => poll(load, 30000));

  // A link with #post-3 / #ticket-12 opens the window once the data is there.
  $effect(() => {
    if (open && inbox) markRead(open);
  });

  function readHash() {
    const m = location.hash.match(/^#(post|ticket)-(\d+)$/);
    return m ? { kind: m[1], id: Number(m[2]) } : null;
  }

  function show(next) {
    open = next;
    history.replaceState(null, '', '/inbox' + (next ? `#${next.kind}-${next.id}` : ''));
  }

  async function markRead(target) {
    const item = inbox?.items.find((x) => x.key === `${target.kind}-${target.id}`);
    if (!item || item.read) return;
    item.read = true;
    inbox.unread = Math.max(0, inbox.unread - 1);
    if (target.kind === 'post') {
      try {
        await api('POST', '/self/inbox/read', { key: item.key });
      } catch {
        // stays unread on the next load
      }
    }
    onseen?.();
  }

  async function readAll() {
    try {
      await api('POST', '/self/inbox/read', { all: true });
      await load();
      onseen?.();
    } catch (e) {
      error = errorText(e);
    }
  }

  async function archive(n, all = false) {
    try {
      if (all) {
        await api('POST', '/self/inbox/archive', { all: true });
      } else {
        n.archived = true;
        await api('POST', '/self/inbox/archive', { key: n.key });
      }
      await load();
      onseen?.();
    } catch (e) {
      error = errorText(e);
    }
  }

  async function restore(n) {
    try {
      n.archived = false;
      await api('POST', '/self/inbox/restore', { key: n.key });
      await load();
      onseen?.();
    } catch (e) {
      error = errorText(e);
    }
  }

  const shown = $derived((inbox?.items ?? []).filter((n) => n.archived === (view === 'archive')));
  const archivedCount = $derived((inbox?.items ?? []).filter((n) => n.archived).length);

  function keyOpen(event, next) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      show(next);
    }
  }

  const post = $derived(open?.kind === 'post' && news
    ? (news.news.find((p) => p.id === open.id) ?? news.changelog.find((p) => p.id === open.id) ?? null) : null);

  // Day separators: today, yesterday, otherwise the date
  const locale = lang === 'de' ? 'de-AT' : 'en-GB';
  const startOf = (ms) => new Date(ms).setHours(0, 0, 0, 0);
  function dayLabel(ms) {
    const today = startOf(Date.now());
    if (startOf(ms) === today) return t('inbox.today');
    if (today - startOf(ms) <= 86_400_000 + 3_600_000) return t('inbox.yesterday');
    return new Date(ms).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });
  }
  const timeLabel = (ms) => startOf(ms) === startOf(Date.now())
    ? new Date(ms).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
    : new Date(ms).toLocaleDateString(locale, { day: 'numeric', month: 'short' });
  const ICON = { news: 'news', changelog: 'box', ticket: 'ticket' };
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && show(null)} onhashchange={() => (open = readHash())} />

{#snippet icon(name, size = 16)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name]} /></svg>
{/snippet}

<div class="page">
  <header class="head">
    <div class="h-left">
      <h1>{t('inbox.title')}</h1>
      {#if inbox}
        <span class="muted">{inbox.unread ? t('inbox.newCount', { n: inbox.unread }) : t('inbox.allRead')}</span>
      {/if}
    </div>
    <div class="h-actions">
      {#if view === 'inbox' && inbox?.unread}
        <button class="secondary small" onclick={readAll}>✓ {t('inbox.readAll')}</button>
      {/if}
      {#if view === 'inbox' && shown.length}
        <button class="secondary small" onclick={() => archive(null, true)}>{@render icon('archive', 14)} {t('inbox.archiveAll')}</button>
      {/if}
    </div>
  </header>

  <div class="views" role="tablist">
    <button role="tab" aria-selected={view === 'inbox'} class:on={view === 'inbox'} onclick={() => (view = 'inbox')}>
      {t('inbox.title')}{#if inbox?.unread}<span class="count">{inbox.unread}</span>{/if}
    </button>
    <button role="tab" aria-selected={view === 'archive'} class:on={view === 'archive'} onclick={() => (view = 'archive')}>
      {t('inbox.archive')}{#if archivedCount}<span class="count muted-count">{archivedCount}</span>{/if}
    </button>
  </div>

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

  {#if inbox}
    <div class="list card" role="list">
      {#if !shown.length}
        <div class="empty muted">
          <span class="e-icon" aria-hidden="true">{@render icon(view === 'archive' ? 'archive' : 'mail', 40)}</span>
          <p>{view === 'archive' ? t('inbox.archiveEmpty') : t('inbox.nothing')}</p>
        </div>
      {/if}
      {#each shown as n, i (n.key)}
        {#if i === 0 || dayLabel(n.time) !== dayLabel(shown[i - 1].time)}
          <div class="day" role="presentation">{dayLabel(n.time)}</div>
        {/if}
        {@const target = { kind: n.kind === 'ticket' ? 'ticket' : 'post', id: n.id }}
        <div class="row" class:unread={!n.read && !n.archived} role="listitem">
          <div class="row-btn" role="button" tabindex="0" onclick={() => show(target)} onkeydown={(e) => keyOpen(e, target)}>
            <span class="r-icon" aria-hidden="true">{@render icon(ICON[n.kind])}</span>
            <div class="r-main">
              <span class="r-what">
                {n.kind === 'ticket' ? t('inbox.note.ticket', { id: n.id }) : t('inbox.note.' + n.kind)}
              </span>
              <strong class="r-title">{#if n.version}<span class="version">{n.version}</span>{/if}{n.title && n.title !== n.version ? n.title : ''}</strong>
            </div>
            <span class="r-time" title={dateTime(n.time)}>{timeLabel(n.time)}</span>
          </div>
          {#if n.archived}
            <button class="r-act" title={t('inbox.restore')} aria-label={t('inbox.restore')} onclick={() => restore(n)}>{@render icon('restore')}</button>
          {:else}
            <button class="r-act" title={t('inbox.archiveOne')} aria-label={t('inbox.archiveOne')} onclick={() => archive(n)}>{@render icon('archive')}</button>
          {/if}
        </div>
      {/each}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

{#if open}
  <!-- The notification opened in a window over the page -->
  <div class="overlay" role="presentation" onclick={() => show(null)}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="panel card" role="dialog" aria-modal="true" aria-label={t('inbox.title')} tabindex="-1"
      onclick={(e) => e.stopPropagation()}>
      <div class="p-bar">
        <span class="muted small">{open.kind === 'ticket' ? t('nav.tickets') : post ? t('inbox.kind.' + post.type) : ''}</span>
        <div class="p-actions">
          {#if open.kind === 'ticket'}
            <button class="link" onclick={() => go('/news/tickets')}>{t('inbox.toTickets')} →</button>
          {:else if post}
            <button class="link" onclick={() => go(post.type === 'changelog' ? '/news/changelog' : '/news')}>
              {post.type === 'changelog' ? t('inbox.toChangelogs') : t('inbox.toNews')} →
            </button>
          {/if}
          <button class="secondary close" aria-label={t('common.close')} onclick={() => show(null)}>✕</button>
        </div>
      </div>
      {#if open.kind === 'ticket'}
        {#key open.id}
          <SelfTicket id={open.id} embedded onread={() => { load(); onseen?.(); }} />
        {/key}
      {:else if post}
        <article class="read">
          <span class="muted small">
            {dateTime(post.created)}
            {#if post.updated - post.created > 60_000} · {t('self.news.updated', { time: dateTime(post.updated) })}{/if}
          </span>
          {#if post.type === 'changelog'}
            <h2>{post.version}{#if post.title && post.title !== post.version}{post.version ? ' · ' : ''}{post.title}{/if}</h2>
            {#if post.text}<p class="text">{post.text}</p>{/if}
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
    gap: 14px;
    max-width: 820px;
    margin: 0 auto;
    width: 100%;
  }

  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .h-left {
    display: flex;
    align-items: baseline;
    gap: 12px;
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

  .tone-warn {
    --tone: var(--warn);
  }

  .tone-bad {
    --tone: var(--bad);
  }

  .tone-info {
    --tone: var(--accent);
  }

  /* ---------------------------------------------------------------- the notifications */

  .list {
    padding: 6px 0 10px;
    overflow: hidden;
  }

  .empty {
    display: grid;
    place-items: center;
    gap: 6px;
    padding: 40px 16px;
    text-align: center;
  }

  .e-icon {
    opacity: 0.35;
  }

  .empty p {
    margin: 0;
  }

  .day {
    padding: 10px 18px 4px;
    font-size: 0.74rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--muted);
  }

  .row {
    --tone: var(--muted);
    position: relative;
    display: flex;
    align-items: center;
    padding: 2px 8px;
  }

  .row .row-btn {
    flex: 1;
  }

  /* Archive / restore: shows on hover (always on touch screens) */
  .r-act {
    position: absolute;
    right: 18px;
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border-radius: 8px;
    color: var(--muted);
    background: var(--surface);
    border: 1px solid var(--border);
    opacity: 0;
    transition: opacity 0.15s;
  }

  .row:hover .r-act,
  .r-act:focus-visible {
    opacity: 1;
  }

  .r-act:hover {
    color: var(--text);
    filter: none;
  }

  .row:hover .r-time {
    visibility: hidden;
  }

  @media (hover: none) {
    .r-act {
      position: static;
      opacity: 1;
      margin-left: 4px;
      border-color: transparent;
      background: transparent;
    }

    .row:hover .r-time {
      visibility: visible;
    }
  }

  .h-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .h-actions button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .views {
    display: flex;
    gap: 4px;
    justify-self: start;
    padding: 3px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .views button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    justify-content: flex-start;
    padding: 5px 14px;
    font-size: 0.86rem;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .views button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
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

  .muted-count {
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .row.unread {
    --tone: var(--accent);
  }

  .row-btn {
    position: relative;
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 10px 12px 10px 16px;
    border-radius: 10px;
    cursor: pointer;
    min-width: 0;
  }

  .row-btn::before {
    content: '';
    position: absolute;
    left: 5px;
    top: 10px;
    bottom: 10px;
    width: 3px;
    border-radius: 3px;
    background: var(--tone);
    opacity: 0;
  }

  .row.unread .row-btn::before {
    opacity: 1;
  }

  .row-btn:hover {
    background: var(--surface-2);
  }

  .row-btn:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 1px;
  }

  .r-icon {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    flex-shrink: 0;
    border-radius: 9px;
    color: var(--tone);
    background: color-mix(in srgb, var(--tone) 14%, var(--surface-2));
  }

  .r-main {
    display: grid;
    gap: 1px;
    flex: 1;
    min-width: 0;
  }

  .r-what {
    font-size: 0.8rem;
    color: var(--muted);
  }

  .row.unread .r-what {
    color: var(--accent);
    font-weight: 700;
  }

  .r-title {
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--muted);
  }

  .row.unread .r-title {
    font-weight: 800;
    color: var(--text);
  }

  .r-time {
    flex-shrink: 0;
    font-size: 0.78rem;
    color: var(--muted);
  }

  .row.unread .r-time {
    color: var(--accent);
    font-weight: 700;
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

  /* ---------------------------------------------------------------- the window */

  .overlay {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 45%);
    overflow-y: auto;
  }

  .panel {
    width: min(100%, 760px);
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    display: grid;
    gap: 14px;
    align-content: start;
    border-top: 4px solid var(--accent);
  }

  .p-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .p-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .p-actions .link {
    padding: 2px 0;
    background: transparent;
    border: 0;
    color: var(--accent);
    font-weight: 600;
    font-size: 0.86rem;
  }

  .close {
    padding: 4px 10px;
  }

  .read {
    display: grid;
    gap: 10px;
  }

  .read h2 {
    margin: 0;
    font-size: 1.3rem;
  }

  .text {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.55;
  }
</style>
