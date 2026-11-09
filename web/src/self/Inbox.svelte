<script>
  // The inbox of the player area (letter at the top right) - everything on one page in the look of the plugin areas
  // (e.g. the chunk loaders): a head like the area head (what is new big, the counts beside it), then one tile each
  // for warnings, news, my tickets and changelogs with cards in their status colour (new = accent, open ticket = green,
  // closed / read = grey). A click opens the card over the page (read a post, a changelog, a ticket conversation,
  // write a new ticket). Addresses: /inbox#post-3, /inbox#ticket-12, /inbox#new.
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

  const SHORT = 6;

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

  // ------------------------------------------------------------------ the open card

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

  function keyOpen(event, next) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      show(next);
    }
  }

  const post = $derived(open?.kind === 'post' && news
    ? (news.news.find((p) => p.id === open.id) ?? news.changelog.find((p) => p.id === open.id) ?? null) : null);
  const openTicket = $derived(open?.kind === 'ticket' ? (tickets ?? []).find((x) => x.id === open.id) : null);
  const panelTone = $derived(open?.kind === 'new' ? 'info'
    : openTicket ? ticketTone(openTicket) : post ? (isNew(post) ? 'info' : 'none') : 'none');

  // ------------------------------------------------------------------ the tiles

  const isNew = (p) => seen != null && Math.max(p.created, p.updated || 0) > seen;
  const ticketTone = (x) => (!x.open ? 'none' : x.unread ? 'info' : 'good');
  const ticketBadge = (x) => (!x.open ? t('inbox.badge.closed') : x.unread ? t('inbox.badge.answer') : t('inbox.badge.open'));

  const openTickets = $derived((tickets ?? []).filter((x) => x.open)
    .sort((a, b) => (b.unread - a.unread) || (b.lastActivity - a.lastActivity)));
  const closedTickets = $derived((tickets ?? []).filter((x) => !x.open).sort((a, b) => b.lastActivity - a.lastActivity));
  const newsShown = $derived(!news ? [] : allNews ? news.news : news.news.slice(0, SHORT));
  const releasesShown = $derived(!news ? [] : allReleases ? news.changelog : news.changelog.slice(0, SHORT));
  const newNews = $derived(news ? news.news.filter(isNew).length : 0);
  const newReleases = $derived(news ? news.changelog.filter(isNew).length : 0);
  const answers = $derived((tickets ?? []).filter((x) => x.unread).length);
  const unread = $derived(newNews + newReleases + answers);
  const dateOnly = (ms) => new Date(ms).toLocaleDateString(lang === 'de' ? 'de-AT' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && show(null)} onhashchange={() => (open = readHash())} />

{#snippet icon(name, size = 18)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name]} /></svg>
{/snippet}

{#snippet stat(label, value, fresh)}
  <div class="stat">
    <span class="muted">{label}</span>
    <b>{value}{#if fresh}<span class="fresh">+{fresh}</span>{/if}</b>
  </div>
{/snippet}

<div class="page" style="--tint: var(--accent)">
  <!-- Head in the look of the area head of the plugins -->
  <header class="head">
    <span class="watermark" aria-hidden="true">{@render icon('mail', 150)}</span>
    <div class="intro">
      <span class="h-icon" aria-hidden="true">{@render icon('mail', 28)}</span>
      <div>
        <h1>{t('inbox.title')}</h1>
        {#if news && tickets}
          {#if unread}
            <div class="main"><strong>{unread}</strong> <span class="muted">{t('inbox.newLabel')}</span></div>
          {:else}
            <div class="main"><strong class="good-text">✓</strong> <span class="muted">{t('inbox.allRead')}</span></div>
          {/if}
        {:else}
          <p class="muted sub">{t('inbox.subtitle')}</p>
        {/if}
      </div>
    </div>
    {#if news && tickets}
      <div class="rest">
        {#if player.news}{@render stat(t('inbox.stat.news'), news.news.length, newNews)}{/if}
        {#if player.tickets}{@render stat(t('inbox.stat.tickets'), openTickets.length, answers)}{/if}
        {#if player.news}{@render stat(t('inbox.stat.releases'), news.changelog.length, newReleases)}{/if}
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if news && tickets}
    {#if news.alerts.length}
      <section class="card block">
        <div class="b-head"><h2>{t('inbox.alerts')}</h2></div>
        <div class="grid">
          {#each news.alerts as a (a.type + a.id)}
            {@const tone = a.type !== 'incident' || a.level === 'danger' ? 'bad' : a.level === 'warning' ? 'warn' : 'info'}
            <div class="item static tone-{tone}">
              <div class="top">
                <div class="names">
                  <strong class="title">{a.title}</strong>
                  <span class="subtitle">{a.type === 'incident' ? t('self.news.fromStatus') : ago(a.created)}</span>
                </div>
              </div>
              {#if a.text}<p class="excerpt full">{a.text}</p>{/if}
              {#if a.link}
                <div class="bottom"><a class="more" href={a.link} target="_blank" rel="noopener noreferrer">{t('self.news.statusPage')} ↗</a></div>
              {/if}
            </div>
          {/each}
        </div>
      </section>
    {/if}

    {#if player.tickets}
      <section class="card block">
        <div class="b-head">
          <h2>{t('inbox.myTickets')}</h2>
          <button class="small" onclick={() => show({ kind: 'new' })}>＋ {t('self.tickets.new')}</button>
        </div>
        {#if !openTickets.length && !showClosed}
          <p class="muted empty">{t('inbox.noOpenTickets')}</p>
        {:else}
          <div class="grid">
            {#each showClosed ? [...openTickets, ...closedTickets] : openTickets as x (x.id)}
              {@render ticketCard(x)}
            {/each}
          </div>
        {/if}
        {#if closedTickets.length}
          <button class="link toggle" onclick={() => (showClosed = !showClosed)}>
            {showClosed ? t('inbox.hideClosed') : t('inbox.showClosed', { n: closedTickets.length })}
          </button>
        {/if}
      </section>
    {/if}

    {#if player.news}
      <section class="card block">
        <div class="b-head"><h2>{t('self.nav.news')}</h2></div>
        {#if !news.news.length}
          <p class="muted empty">{t('self.news.noNews')}</p>
        {:else}
          <div class="grid">
            {#each newsShown as p (p.id)}
              {@const fresh = isNew(p)}
              <div class="item tone-{fresh ? 'info' : 'none'}" role="button" tabindex="0" onclick={() => show({ kind: 'post', id: p.id })}
                onkeydown={(e) => keyOpen(e, { kind: 'post', id: p.id })}>
                <div class="top">
                  <div class="names">
                    <strong class="title">{p.title}</strong>
                    <span class="subtitle" title={dateTime(p.created)}>{dateOnly(p.created)}</span>
                  </div>
                  {#if fresh}<span class="badge info">{t('inbox.badge.new')}</span>
                  {:else if p.pinned}<span class="badge">📌 {t('self.hero.pinned')}</span>{/if}
                </div>
                {#if p.text}<p class="excerpt">{p.text}</p>{/if}
                <div class="bottom"><span class="more">{t('inbox.open')} →</span></div>
              </div>
            {/each}
          </div>
          {#if news.news.length > SHORT}
            <button class="link toggle" onclick={() => (allNews = !allNews)}>
              {allNews ? t('inbox.less') : t('inbox.showAll', { n: news.news.length })}
            </button>
          {/if}
        {/if}
      </section>

      {#if news.changelog.length}
        <section class="card block">
          <div class="b-head"><h2>{t('self.news.areaChangelog')}</h2></div>
          <div class="grid">
            {#each releasesShown as r (r.id)}
              {@const fresh = isNew(r)}
              <div class="item tone-{fresh ? 'info' : 'none'}" role="button" tabindex="0" onclick={() => show({ kind: 'post', id: r.id })}
                onkeydown={(e) => keyOpen(e, { kind: 'post', id: r.id })}>
                <div class="top">
                  <div class="names">
                    <strong class="title">{r.version || r.title}</strong>
                    {#if r.version && r.title && r.title !== r.version}<span class="subtitle">{r.title}</span>{/if}
                  </div>
                  {#if fresh}<span class="badge info">{t('inbox.badge.new')}</span>{/if}
                </div>
                <div class="chips">
                  <span class="chip">{t('inbox.entries', { n: entryCount(r.entries) })}</span>
                  <span class="chip muted" title={dateTime(r.created)}>{dateOnly(r.created)}</span>
                </div>
                <div class="bottom"><span class="more">{t('inbox.open')} →</span></div>
              </div>
            {/each}
          </div>
          {#if news.changelog.length > SHORT}
            <button class="link toggle" onclick={() => (allReleases = !allReleases)}>
              {allReleases ? t('inbox.less') : t('inbox.showAll', { n: news.changelog.length })}
            </button>
          {/if}
        </section>
      {/if}
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

{#snippet ticketCard(x)}
  {@const tone = ticketTone(x)}
  <div class="item tone-{tone}" role="button" tabindex="0" onclick={() => show({ kind: 'ticket', id: x.id })}
    onkeydown={(e) => keyOpen(e, { kind: 'ticket', id: x.id })}>
    <div class="top">
      <div class="names">
        <strong class="title">{x.summary}</strong>
        <span class="subtitle">#{x.id} · {x.category}</span>
      </div>
      <span class="badge {tone}">{ticketBadge(x)}</span>
    </div>
    <div class="chips"><span class="chip muted">{ago(x.lastActivity)}</span></div>
    <div class="bottom"><span class="more">{t('inbox.open')} →</span></div>
  </div>
{/snippet}

{#if open}
  <!-- The open card over the page, like an open chunk loader -->
  <div class="overlay" role="presentation" onclick={() => show(null)}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="panel card tone-{panelTone}" role="dialog" aria-modal="true" aria-label={t('inbox.title')} tabindex="-1"
      onclick={(e) => e.stopPropagation()}>
      <div class="p-bar">
        <span class="muted small">
          {open.kind === 'new' ? t('self.tickets.new') : open.kind === 'ticket' ? t('inbox.kind.ticket')
            : post ? t('inbox.kind.' + post.type) : ''}
        </span>
        <button class="secondary close" aria-label={t('common.close')} onclick={() => show(null)}>✕</button>
      </div>
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
            <h2>{#if post.version}<span class="version">{post.version}</span>{/if} {post.title && post.title !== post.version ? post.title : ''}</h2>
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
    gap: 16px;
  }

  /* ---------------------------------------------------------------- head (like AreaHeader) */

  .head {
    position: relative;
    overflow: hidden;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
    gap: 18px 32px;
    align-items: center;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tint) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tint) 22%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -10px;
    bottom: -40px;
    line-height: 0;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .h-icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--tint);
    background: color-mix(in srgb, var(--tint) 18%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--tint) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .sub {
    margin: 2px 0 0;
  }

  .main {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 2px;
  }

  .main strong {
    font-size: 2rem;
    line-height: 1.1;
    font-weight: 800;
    color: var(--tint);
  }

  .rest {
    position: relative;
    display: grid;
    gap: 12px 20px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  }

  .stat {
    display: grid;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 12px;
    background: color-mix(in srgb, var(--surface-2) 70%, transparent);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
  }

  .stat span {
    font-size: 0.82rem;
  }

  .stat b {
    display: flex;
    align-items: baseline;
    gap: 6px;
    font-size: 1.35rem;
    font-weight: 800;
  }

  .fresh {
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--tint);
  }

  /* ---------------------------------------------------------------- tiles with cards (like CardGrid) */

  .b-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 14px;
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .empty {
    margin: 0;
  }

  .grid {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 270px), 1fr));
  }

  .toggle {
    margin-top: 12px;
    padding: 4px 0;
    font-size: 0.85rem;
  }

  .item {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 16px 16px 14px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background:
      linear-gradient(160deg, color-mix(in srgb, var(--tone) 9%, transparent), transparent 45%),
      var(--surface-2);
    cursor: pointer;
    transition: transform 0.15s, border-color 0.15s;
    min-width: 0;
  }

  .item.static {
    cursor: default;
  }

  .item::before {
    content: '';
    position: absolute;
    left: 14px;
    right: 14px;
    top: 0;
    height: 3px;
    border-radius: 0 0 3px 3px;
    background: var(--tone);
  }

  .item:not(.static):hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--tone) 55%, var(--border));
  }

  .item:focus-visible {
    outline: 2px solid var(--tone);
    outline-offset: 2px;
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

  .top {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .names {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .title {
    font-size: 1.05rem;
    overflow-wrap: anywhere;
  }

  .subtitle {
    color: var(--muted);
    font-size: 0.82rem;
    overflow-wrap: anywhere;
  }

  .badge {
    flex-shrink: 0;
    max-width: 55%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.72rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 14%, transparent);
  }

  .badge.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .badge.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 15%, transparent);
  }

  .excerpt {
    margin: 0;
    font-size: 0.9rem;
    color: var(--muted);
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    overflow-wrap: anywhere;
  }

  .excerpt.full {
    display: block;
    white-space: pre-wrap;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    font-size: 0.82rem;
    padding: 4px 9px;
    border-radius: 8px;
    background: var(--surface);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
  }

  .bottom {
    margin-top: auto;
    display: flex;
    justify-content: flex-end;
  }

  .more {
    font-size: 0.82rem;
    font-weight: 700;
    color: var(--tone);
  }

  /* ---------------------------------------------------------------- open card */

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
    border-top: 4px solid var(--tone);
  }

  .p-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .close {
    padding: 4px 10px;
  }

  .read {
    display: grid;
    gap: 10px;
  }

  .read h2 {
    font-size: 1.3rem;
  }

  .version {
    display: inline-block;
    margin-right: 6px;
    padding: 0 8px;
    border-radius: 6px;
    font-family: var(--mono, ui-monospace, monospace);
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .text {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.55;
  }

  .muted {
    color: var(--muted);
  }

  .good-text {
    color: var(--good);
  }

  @media (max-width: 720px) {
    .head {
      grid-template-columns: 1fr;
      padding: 18px 16px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .item {
      transition: none;
    }
  }
</style>
