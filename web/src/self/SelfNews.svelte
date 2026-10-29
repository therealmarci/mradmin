<script>
  // The page "News" of the player area: active warnings on top (written by the team or pinned in Uptime Kuma), then
  // two areas to switch between - news (the newest or pinned one big, the others as cards, read in a window) and the
  // changelog (a timeline of versions, each written out like a document with its headings). Links like /me/news#post-3 open that post,
  // /me/news#changelog the changelog.
  import { tick } from 'svelte';
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { lines } from '../lib/changelog.js';

  const NEWS_PAGE = 9;
  const RELEASES_PAGE = 5;

  let data = $state(null);
  let error = $state('');
  let area = $state(location.hash === '#changelog' ? 'changelog' : 'news');
  let reading = $state(null);
  let newsPage = $state(0);
  let releasePage = $state(0);
  let flash = $state(null);

  async function load() {
    try {
      data = await api('GET', '/self/news?all=1&lang=' + lang);
      await openFromHash();
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  // Warnings come and go (also from Uptime Kuma): every 30 seconds again while the tab is visible, without opening
  // anything.
  $effect(() => {
    return poll(async () => {
      try {
        data = await api('GET', '/self/news?all=1&lang=' + lang);
      } catch {
        // keep what is shown
      }
    }, 30_000);
  });

  // #post-3: the news opens in the window, a changelog is shown and marked.
  async function openFromHash() {
    const id = Number(location.hash.match(/^#post-(\d+)$/)?.[1]);
    if (!id) return;
    const n = data.news.find((p) => p.id === id);
    if (n) {
      area = 'news';
      reading = n;
      return;
    }
    const index = data.changelog.findIndex((p) => p.id === id);
    if (index >= 0) {
      area = 'changelog';
      releasePage = Math.floor(index / RELEASES_PAGE);
      flash = id;
      await tick();
      document.getElementById('post-' + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function show(which) {
    area = which;
    history.replaceState(null, '', which === 'changelog' ? '/me/news#changelog' : '/me/news');
  }

  function read(post) {
    reading = post;
    history.replaceState(null, '', '/me/news#post-' + post.id);
  }

  function close() {
    reading = null;
    history.replaceState(null, '', '/me/news');
  }

  const featured = $derived(data?.news[0] ?? null);
  const rest = $derived(data?.news.slice(1) ?? []);
  const newsPages = $derived(Math.max(1, Math.ceil(rest.length / NEWS_PAGE)));
  const newsShown = $derived(rest.slice(newsPage * NEWS_PAGE, (newsPage + 1) * NEWS_PAGE));
  const releasePages = $derived(Math.max(1, Math.ceil((data?.changelog.length ?? 0) / RELEASES_PAGE)));
  const releases = $derived(data?.changelog.slice(releasePage * RELEASES_PAGE, (releasePage + 1) * RELEASES_PAGE) ?? []);
  const dateOnly = (ms) => new Date(ms).toLocaleDateString(lang === 'de' ? 'de-AT' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
  const fresh = (ms) => Date.now() - ms < 3 * 86_400_000;
</script>

<svelte:window onkeydown={(e) => reading && e.key === 'Escape' && close()} />

{#snippet pager(page, pages, set)}
  {#if pages > 1}
    <div class="pager">
      <button class="secondary small" disabled={page === 0} onclick={() => set(page - 1)}>← {t('self.cards.prev')}</button>
      <span class="muted small">{t('self.cards.page', { n: page + 1, total: pages })}</span>
      <button class="secondary small" disabled={page >= pages - 1} onclick={() => set(page + 1)}>{t('self.cards.next')} →</button>
    </div>
  {/if}
{/snippet}

<div class="page">
  <header class="head">
    <span class="watermark" aria-hidden="true">📣</span>
    <div class="intro">
      <span class="icon" aria-hidden="true">📣</span>
      <div>
        <h1>{t('self.news.title')}</h1>
        <p class="muted">{t('self.news.subtitle')}</p>
      </div>
    </div>
    {#if data}
      <div class="switch" role="tablist" aria-label={t('self.news.title')}>
        <button role="tab" aria-selected={area === 'news'} class:active={area === 'news'} onclick={() => show('news')}>
          📣 {t('self.news.areaNews')} <span class="n">{data.news.length}</span>
        </button>
        <button role="tab" aria-selected={area === 'changelog'} class:active={area === 'changelog'} onclick={() => show('changelog')}>
          📝 {t('self.news.areaChangelog')} <span class="n">{data.changelog.length}</span>
        </button>
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    {#if data.alerts.length}
      <section class="alerts" aria-label={t('self.news.alerts')}>
        {#each data.alerts as a (a.type + a.id)}
          <article class="alert level-{a.type === 'incident' ? a.level : 'critical'}">
            <span class="alert-icon" aria-hidden="true">{a.level === 'info' ? 'i' : '!'}</span>
            <div class="alert-body">
              <div class="alert-head">
                <strong>{a.title}</strong>
                <span class="muted tiny">{a.type === 'incident' ? t('self.news.fromStatus') : dateTime(a.created)}</span>
              </div>
              {#if a.text}<p>{a.text}</p>{/if}
              {#if a.link}<a href={a.link} target="_blank" rel="noopener noreferrer">{t('self.news.statusPage')} ↗</a>{/if}
            </div>
          </article>
        {/each}
      </section>
    {/if}

    {#if area === 'news'}
      {#if !featured}
        <section class="card empty-state">
          <span aria-hidden="true">📭</span>
          <p>{t('self.news.noNews')}</p>
        </section>
      {:else}
        <button class="featured" onclick={() => read(featured)}>
          <span class="badges">
            {#if featured.pinned}<span class="badge pin">📌 {t('self.hero.pinned')}</span>{:else if fresh(featured.created)}<span class="badge new">{t('self.news.fresh')}</span>{/if}
            <span class="when">{dateOnly(featured.created)}</span>
          </span>
          <span class="f-title">{featured.title}</span>
          <span class="f-text">{featured.text}</span>
          <span class="more">{t('self.hero.readMore')} →</span>
        </button>

        {#if rest.length}
          <section class="card tile">
            <h2>{t('self.news.older')}</h2>
            <div class="grid">
              {#each newsShown as p (p.id)}
                <button class="news-card" onclick={() => read(p)}>
                  <span class="badges">
                    {#if p.pinned}<span class="badge pin">📌</span>{:else if fresh(p.created)}<span class="badge new">{t('self.news.fresh')}</span>{/if}
                    <span class="when">{dateOnly(p.created)}</span>
                  </span>
                  <span class="c-title">{p.title}</span>
                  <span class="c-text">{p.text}</span>
                  <span class="more">{t('self.hero.readMore')} →</span>
                </button>
              {/each}
            </div>
            {@render pager(newsPage, newsPages, (n) => (newsPage = n))}
          </section>
        {/if}
      {/if}
    {:else}
      {#if data.changelog.length === 0}
        <section class="card empty-state">
          <span aria-hidden="true">📝</span>
          <p>{t('self.news.noChangelog')}</p>
        </section>
      {:else}
        <section class="card tile">
          <ol class="timeline">
            {#each releases as r, i (r.id)}
              <li class="release" class:flash={flash === r.id} id="post-{r.id}">
                <span class="node" class:latest={releasePage === 0 && i === 0} aria-hidden="true"></span>
                <div class="r-head">
                  {#if r.version}<span class="version">{r.version}</span>{/if}
                  {#if releasePage === 0 && i === 0}<span class="badge new">{t('self.news.latest')}</span>{/if}
                  <span class="when">{dateOnly(r.created)}</span>
                </div>
                {#if r.title && r.title !== r.version}<h3>{r.title}</h3>{/if}
                {#if r.text}<p class="r-intro">{r.text}</p>{/if}
                <div class="doc">
                  {#each lines(r.entries) as l}
                    {#if l.heading}
                      <div class="d-head h{l.level}" style="--indent: {l.indent}">{l.text}</div>
                    {:else}
                      <div class="d-line t-{l.type}" style="--indent: {l.indent}">
                        <span class="tag">{t('news.type.' + l.type)}</span>
                        <span class="d-text">{l.text}</span>
                      </div>
                    {/if}
                  {/each}
                </div>
              </li>
            {/each}
          </ol>
          {@render pager(releasePage, releasePages, (n) => (releasePage = n))}
        </section>
      {/if}
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

{#if reading}
  <div class="overlay" role="presentation" onclick={close}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="reader card" role="dialog" aria-modal="true" aria-label={reading.title} tabindex="-1"
      onclick={(e) => e.stopPropagation()}>
      <div class="reader-head">
        <span class="badges">
          {#if reading.pinned}<span class="badge pin">📌 {t('self.hero.pinned')}</span>{/if}
          <span class="when">{dateTime(reading.created)}{#if reading.updated - reading.created > 60_000} · {t('self.news.updated', { time: dateTime(reading.updated) })}{/if}</span>
        </span>
        <button class="secondary close" aria-label={t('common.close')} onclick={close}>✕</button>
      </div>
      <h2>{reading.title}</h2>
      <p class="reader-text">{reading.text}</p>
    </div>
  </div>
{/if}

<style>
  .page {
    --news: var(--accent);
    display: grid;
    gap: 16px;
  }

  /* Head in the look of the tiles: icon, title, the switch between the two areas. */
  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 16px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--news) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--news) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -10px;
    bottom: -34px;
    font-size: 9rem;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    position: relative;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    font-size: 1.8rem;
    background: color-mix(in srgb, var(--news) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--news) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .switch {
    position: relative;
    display: inline-flex;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    background: transparent;
    color: var(--muted);
    padding: 8px 14px;
    border-radius: 9px;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 1px 7px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  /* Warnings: red, impossible to miss. */
  .alerts {
    display: grid;
    gap: 10px;
  }

  .alert {
    --a: var(--bad);
    display: flex;
    gap: 14px;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--a) 45%, var(--border));
    background: linear-gradient(120deg, color-mix(in srgb, var(--a) 16%, var(--surface)), var(--surface));
    box-shadow: var(--shadow);
  }

  .alert.level-warning {
    --a: var(--warn);
  }

  .alert.level-info {
    --a: var(--accent);
  }

  .alert-icon {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    flex-shrink: 0;
    border-radius: 50%;
    font-weight: 800;
    color: #fff;
    background: var(--a);
  }

  .alert-body {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  .alert-head {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    align-items: baseline;
  }

  .alert-head strong {
    font-size: 1.05rem;
    color: var(--a);
  }

  .alert p {
    margin: 0;
    white-space: pre-line;
    overflow-wrap: anywhere;
  }

  .alert a {
    color: var(--a);
    font-weight: 700;
    font-size: 0.88rem;
  }

  .tiny {
    font-size: 0.78rem;
  }

  /* News: the first one big. */
  .featured {
    position: relative;
    display: grid;
    gap: 10px;
    padding: 26px 28px 22px;
    text-align: left;
    color: var(--text);
    font: inherit;
    font-weight: 400;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--news) 35%, var(--border));
    background:
      radial-gradient(100% 160% at 0% 0%, color-mix(in srgb, var(--news) 22%, transparent), transparent 55%),
      radial-gradient(80% 120% at 100% 100%, color-mix(in srgb, #8b5cf6 14%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
    justify-content: stretch;
    transition: transform 0.15s, box-shadow 0.15s;
  }

  .featured:hover,
  .news-card:hover {
    filter: none;
    transform: translateY(-2px);
  }

  .badges {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .badge {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: 999px;
  }

  .badge.pin {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .badge.new {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 16%, transparent);
  }

  .when {
    font-size: 0.82rem;
    color: var(--muted);
  }

  .f-title {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.2;
    overflow-wrap: anywhere;
  }

  .f-text,
  .c-text {
    color: var(--muted);
    white-space: pre-line;
    overflow-wrap: anywhere;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .f-text {
    -webkit-line-clamp: 5;
    line-clamp: 5;
    font-size: 1.02rem;
  }

  .more {
    color: var(--news);
    font-weight: 700;
    font-size: 0.88rem;
    margin-top: auto;
  }

  .tile h2 {
    margin: 0 0 14px;
    font-size: 1.05rem;
  }

  .grid {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  }

  .news-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px;
    text-align: left;
    color: var(--text);
    font: inherit;
    font-weight: 400;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--news);
    background: var(--surface-2);
    justify-content: flex-start;
    align-items: stretch;
    transition: transform 0.15s;
  }

  .c-title {
    font-weight: 800;
    font-size: 1.05rem;
    overflow-wrap: anywhere;
  }

  .c-text {
    -webkit-line-clamp: 4;
    line-clamp: 4;
    font-size: 0.92rem;
  }

  .empty-state {
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 36px 20px;
    color: var(--muted);
  }

  .empty-state span {
    font-size: 2.4rem;
  }

  .empty-state p {
    margin: 0;
  }

  /* Changelog: a timeline of versions. */
  .timeline {
    list-style: none;
    margin: 0;
    padding: 0 0 0 26px;
    position: relative;
    display: grid;
    gap: 26px;
  }

  .timeline::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 6px;
    bottom: 6px;
    width: 2px;
    background: var(--border);
  }

  .release {
    position: relative;
    display: grid;
    gap: 10px;
    scroll-margin-top: 90px;
    border-radius: 12px;
    transition: box-shadow 0.4s;
  }

  .release.flash {
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 35%, transparent);
    padding: 8px;
    margin: -8px;
  }

  .node {
    position: absolute;
    left: -26px;
    top: 4px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--surface);
    border: 3px solid var(--border);
  }

  .node.latest {
    border-color: var(--good);
    box-shadow: 0 0 0 4px color-mix(in srgb, var(--good) 20%, transparent);
  }

  .r-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .version {
    font-weight: 800;
    font-size: 1.15rem;
    padding: 2px 12px;
    border-radius: 10px;
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
    font-variant-numeric: tabular-nums;
  }

  h3 {
    margin: 0;
    font-size: 1.05rem;
  }

  .r-intro {
    margin: 0;
    color: var(--muted);
    white-space: pre-line;
  }

  /* A changelog like a document: headings and lines as written; every step of indent shows a thin guide line on the
     left, so the entries below a heading stay visibly together. */
  .doc {
    display: grid;
  }

  .d-head,
  .d-line {
    --step: 20px;
    padding-left: calc(var(--indent) * var(--step));
    background: repeating-linear-gradient(90deg, var(--border) 0 2px, transparent 2px var(--step)) left top /
      calc(var(--indent) * var(--step)) 100% no-repeat;
  }

  .d-head {
    padding-top: 12px;
    padding-bottom: 4px;
    font-weight: 800;
    overflow-wrap: anywhere;
  }

  .d-head:first-child {
    padding-top: 0;
  }

  .d-head.h1 {
    font-size: 0.82rem;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--accent);
  }

  .d-head.h2 {
    font-size: 1rem;
  }

  .d-head.h3 {
    font-size: 0.9rem;
    color: var(--muted);
  }

  .d-line {
    --t: var(--muted);
    display: flex;
    align-items: baseline;
    gap: 10px;
    padding-top: 3px;
    padding-bottom: 3px;
  }

  .t-new {
    --t: var(--good);
  }

  .t-changed {
    --t: var(--accent);
  }

  .t-fixed {
    --t: var(--warn);
  }

  .t-removed {
    --t: var(--bad);
  }

  .tag {
    flex-shrink: 0;
    min-width: 76px;
    padding: 1px 8px;
    border-radius: 6px;
    text-align: center;
    font-size: 0.7rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--t);
    background: color-mix(in srgb, var(--t) 14%, transparent);
  }

  .d-text {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  @media (max-width: 520px) {
    .d-head,
    .d-line {
      --step: 12px;
    }

    .tag {
      min-width: 0;
    }
  }

  .pager {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 14px;
    margin-top: 18px;
  }

  /* Reading a news. */
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 50%);
    overflow-y: auto;
  }

  .reader {
    width: min(100%, 720px);
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    display: grid;
    gap: 12px;
    border-top: 4px solid var(--news);
  }

  .reader-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }

  .close {
    padding: 4px 10px;
  }

  .reader h2 {
    margin: 0;
    font-size: 1.5rem;
    overflow-wrap: anywhere;
  }

  .reader-text {
    margin: 0;
    white-space: pre-line;
    line-height: 1.6;
    overflow-wrap: anywhere;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .switch {
      width: 100%;
    }

    .switch button {
      flex: 1;
    }

    .featured {
      padding: 20px 18px;
    }

    .f-title {
      font-size: 1.3rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .featured,
    .news-card,
    .release {
      transition: none;
    }
  }
</style>
