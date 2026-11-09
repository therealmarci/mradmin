<script>
  // The inbox of the player area (letter at the top right): posts of the team (news, changelogs), warnings and the
  // viewer's own tickets in one list, newest first; what came since the last visit is marked. Opening the page counts
  // as "looked at the posts" (POST /api/self/inbox/seen) - a ticket counts as read once it is opened.
  // Data: GET /api/self/inbox (SelfInbox.java), /api/self/news?all=1 (SelfNews.java), /api/self/tickets (SelfTickets.java).
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { ago, dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';

  // player: GET /api/self (news / tickets switched on); onseen: the counter in the bar may load again
  let { player, onseen = null } = $props();

  let posts = $state(null);
  let tickets = $state(null);
  let alerts = $state([]);
  let seen = $state(null);
  let error = $state('');
  let filter = $state('all');

  async function load() {
    try {
      const [n, tk] = await Promise.all([
        player.news ? api('GET', '/self/news?all=1&lang=' + lang) : null,
        player.tickets ? api('GET', '/self/tickets') : null,
      ]);
      alerts = n?.alerts ?? [];
      posts = n ? [...n.news, ...n.changelog] : [];
      tickets = tk?.tickets ?? [];
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // The last visit first (to mark what is new), then mark everything as seen.
  $effect(() => {
    (async () => {
      try {
        const before = await api('GET', '/self/inbox');
        seen = before.seen;
        await load();
        await api('POST', '/self/inbox/seen', {});
        onseen?.();
      } catch (e) {
        error = errorText(e);
      }
    })();
  });
  $effect(() => poll(load, 30000));

  const items = $derived.by(() => {
    if (posts === null || tickets === null) return null;
    const list = [
      ...posts.map((p) => ({
        key: 'p' + p.id, kind: p.type, time: Math.max(p.created, p.updated || 0), title: p.title || p.version,
        sub: p.type === 'changelog' ? (p.version ? t('inbox.version', { version: p.version }) : '') : '',
        unread: seen != null && Math.max(p.created, p.updated || 0) > seen,
        open: () => go('/inbox/news#post-' + p.id),
      })),
      ...tickets.map((x) => ({
        key: 't' + x.id, kind: 'ticket', time: x.lastActivity, title: x.summary,
        sub: `#${x.id} · ${x.category}` + (x.open ? '' : ' · ' + t('inbox.closed')), unread: x.unread, closed: !x.open,
        open: () => go('/inbox/tickets/' + x.id),
      })),
    ];
    return list.sort((a, b) => b.time - a.time);
  });
  const shown = $derived(!items ? [] : filter === 'unread' ? items.filter((i) => i.unread) : items);
  const unreadCount = $derived(items ? items.filter((i) => i.unread).length : 0);

  const ICON = { news: 'news', changelog: 'box', ticket: 'ticket' };
</script>

<div class="page">
  <header class="head">
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.mail} /></svg>
      </span>
      <div>
        <h1>{t('inbox.title')}</h1>
        <p class="muted">{t('inbox.subtitle')}</p>
      </div>
    </div>
    {#if player.tickets}
      <button class="new" onclick={() => go('/inbox/tickets#new')}>＋ {t('self.tickets.new')}</button>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#each alerts as a (a.id)}
    <button class="alert" class:critical={a.critical || a.level === 'danger'} onclick={() => go('/inbox/news')}>
      <span class="a-mark" aria-hidden="true">!</span>
      <span class="a-text">
        <strong>{a.title}</strong>
        {#if a.text}<span class="small">{a.text}</span>{/if}
      </span>
    </button>
  {/each}

  <section class="card list">
    <div class="bar">
      <div class="filter" role="radiogroup" aria-label={t('inbox.filter')}>
        <button role="radio" aria-checked={filter === 'all'} class:on={filter === 'all'} onclick={() => (filter = 'all')}>
          {t('inbox.all')}
        </button>
        <button role="radio" aria-checked={filter === 'unread'} class:on={filter === 'unread'} onclick={() => (filter = 'unread')}>
          {t('inbox.unread')}{#if unreadCount}<span class="n">{unreadCount}</span>{/if}
        </button>
      </div>
    </div>

    {#if items === null}
      <p class="muted pad">{t('common.loading')}</p>
    {:else if !shown.length}
      <p class="muted pad empty">{t(filter === 'unread' ? 'inbox.noneUnread' : 'inbox.none')}</p>
    {:else}
      <ul>
        {#each shown as i (i.key)}
          <li>
            <button class="item {i.kind}" class:unread={i.unread} class:closed={i.closed} onclick={i.open}>
              <span class="i-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                  stroke-linejoin="round"><path d={ICONS[ICON[i.kind]]} /></svg>
              </span>
              <span class="i-text">
                <span class="i-top">
                  <span class="kind">{t('inbox.kind.' + i.kind)}</span>
                  {#if i.sub}<span class="muted small">{i.sub}</span>{/if}
                </span>
                <strong class="title">{i.title}</strong>
              </span>
              <span class="when muted small" title={dateTime(i.time)}>{ago(i.time)}</span>
              <span class="dot" aria-label={i.unread ? t('inbox.new') : undefined}></span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--accent) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 220px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  /* Warnings stay on top, whatever the filter */
  .alert {
    display: flex;
    align-items: flex-start;
    justify-content: flex-start;
    gap: 12px;
    width: 100%;
    text-align: left;
    padding: 12px 16px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--warn) 45%, var(--border));
    background: color-mix(in srgb, var(--warn) 10%, var(--surface));
    color: var(--text);
  }

  .alert.critical {
    border-color: color-mix(in srgb, var(--bad) 50%, var(--border));
    background: color-mix(in srgb, var(--bad) 10%, var(--surface));
  }

  .a-mark {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    flex-shrink: 0;
    border-radius: 50%;
    font-weight: 800;
    color: #fff;
    background: var(--warn);
  }

  .critical .a-mark {
    background: var(--bad);
  }

  .a-text {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .list {
    padding: 0;
    overflow: hidden;
  }

  .bar {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    border-bottom: 1px solid var(--border);
  }

  .filter {
    display: inline-flex;
    gap: 2px;
    padding: 3px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .filter button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border: 0;
    border-radius: 8px;
    font-size: 0.86rem;
    font-weight: 600;
    color: var(--muted);
    background: transparent;
  }

  .filter button.on {
    color: var(--text);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .n {
    min-width: 18px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: 0.72rem;
    color: #fff;
    background: var(--accent);
  }

  .pad {
    margin: 0;
    padding: 18px 16px;
  }

  .empty {
    text-align: center;
    padding: 34px 16px;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li + li {
    border-top: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
  }

  /* One line per entry: icon, kind + title, time, dot for new */
  .item {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto 10px;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    width: 100%;
    padding: 12px 16px;
    border: 0;
    border-radius: 0;
    text-align: left;
    color: var(--text);
    background: transparent;
  }

  .item:hover {
    background: var(--surface-2);
  }

  .i-icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    color: var(--muted);
    background: var(--surface-2);
  }

  .item.unread .i-icon {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, var(--surface-2));
  }

  .i-text {
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  .i-top {
    display: flex;
    gap: 8px;
    align-items: baseline;
    min-width: 0;
  }

  .kind {
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--muted);
  }

  .title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  .item.unread .title {
    font-weight: 700;
  }

  .item.closed .title {
    color: var(--muted);
  }

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }

  .item.unread .dot {
    background: var(--accent);
  }

  @media (max-width: 520px) {
    .item {
      grid-template-columns: auto minmax(0, 1fr) 10px;
    }

    .when {
      display: none;
    }
  }
</style>
