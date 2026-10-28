<script>
  // Every post of the team (news, changelogs) in full, newest first, pinned ones on top. The slideshow on the start
  // page links here ("/me/news#post-3").
  import { tick } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let posts = $state(null);
  let error = $state('');

  async function load() {
    try {
      posts = (await api('GET', '/self/news?all=1&lang=' + lang)).slides;
      await tick();
      const target = location.hash && document.getElementById(location.hash.slice(1));
      target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      target?.classList.add('focus');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();
</script>

<div class="page">
  <h1>📣 {t('self.news.title')}</h1>
  {#if error}<div class="error">{error}</div>{/if}
  {#if posts}
    {#if posts.length === 0}
      <p class="muted">{t('self.news.none')}</p>
    {/if}
    {#each posts as p (p.id)}
      <article class="card post {p.type}" id="post-{p.id}">
        <div class="meta">
          <span class="tag">{t('self.hero.kind.' + p.type)}</span>
          {#if p.pinned}<span title={t('self.hero.pinned')}>📌</span>{/if}
          <span class="muted small">{dateTime(p.created)}{#if p.updated - p.created > 60_000} · {t('self.news.updated', { time: dateTime(p.updated) })}{/if}</span>
        </div>
        <h2>{p.title}</h2>
        <p class="text">{p.text}</p>
      </article>
    {/each}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
    max-width: 760px;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  h2 {
    margin: 6px 0 8px;
    font-size: 1.15rem;
    overflow-wrap: anywhere;
  }

  .post {
    --tone: var(--accent);
    border-left: 4px solid var(--tone);
    scroll-margin-top: 80px;
  }

  .post.changelog {
    --tone: var(--good);
  }

  .post:global(.focus) {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--tone) 45%, transparent), var(--shadow);
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .tag {
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--tone);
  }

  .text {
    margin: 0;
    white-space: pre-line;
    overflow-wrap: anywhere;
  }
</style>
