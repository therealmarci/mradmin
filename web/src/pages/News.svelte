<script>
  // News and changelogs for the players' own page (/web): shown there in the slideshow on the start page and on the
  // page "News". Title and text in German and English - a language left empty shows the other one.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  // The post being written (id 0 = new)
  const empty = () => ({ id: 0, kind: 'news', pinned: false, expires: '', title: { de: '', en: '' }, text: { de: '', en: '' } });
  let draft = $state(empty());
  let tab = $state('de');

  async function load() {
    try {
      data = await api('GET', '/news');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  // <input type="date"> works with "2026-11-30"; stored is the end of that day (local time).
  const toDay = (ms) => {
    if (!ms) return '';
    const d = new Date(ms);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };
  const fromDay = (day) => (day ? new Date(day + 'T23:59:59').getTime() : 0);

  function edit(p) {
    draft = {
      id: p.id,
      kind: p.kind,
      pinned: p.pinned,
      expires: toDay(p.expires),
      title: { de: p.title.de ?? '', en: p.title.en ?? '' },
      text: { de: p.text.de ?? '', en: p.text.en ?? '' },
    };
    tab = draft.title.de || !draft.title.en ? 'de' : 'en';
    message = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function reset() {
    draft = empty();
    tab = 'de';
  }

  async function save(event) {
    event.preventDefault();
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/news', { ...draft, expires: fromDay(draft.expires) });
      message = t(draft.id ? 'news.saved' : 'news.posted');
      reset();
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function remove(p) {
    if (!confirm(t('news.deleteConfirm', { title: p.title.de || p.title.en }))) return;
    busy = true;
    try {
      await api('POST', `/news/${p.id}/delete`);
      if (draft.id === p.id) reset();
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const complete = $derived(['de', 'en'].some((l) => draft.title[l].trim() && draft.text[l].trim()));
  const expired = (p) => p.expires && p.expires < Date.now();
</script>

<div class="stack">
  <div>
    <h1>{t('news.title')}</h1>
    <p class="muted small">{t('news.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}
  {#if data && !data.playerWeb}<div class="notice">{t('news.playerWebOff')}</div>{/if}

  <form class="card form" onsubmit={save}>
    <div class="row">
      <h2>{draft.id ? t('news.edit') : t('news.new')}</h2>
      {#if draft.id}<button type="button" class="secondary small-btn" onclick={reset}>{t('news.cancel')}</button>{/if}
    </div>

    <div class="kinds" role="radiogroup" aria-label={t('news.kind')}>
      {#each ['news', 'changelog'] as k}
        <button type="button" role="radio" aria-checked={draft.kind === k} class="kind" class:active={draft.kind === k}
          onclick={() => (draft.kind = k)}>
          <strong>{t('news.kind.' + k)}</strong><span class="muted small">{t('news.kindHint.' + k)}</span>
        </button>
      {/each}
    </div>

    <div class="langs" role="tablist">
      {#each ['de', 'en'] as l}
        <button type="button" role="tab" aria-selected={tab === l} class="lang" class:active={tab === l} onclick={() => (tab = l)}>
          {l === 'de' ? 'Deutsch' : 'English'}
          {#if draft.title[l].trim() && draft.text[l].trim()}<span class="dot" aria-hidden="true"></span>{/if}
        </button>
      {/each}
    </div>
    {#key tab}
      <div class="field">
        <label class="flabel" for="news-title">{t('news.titleLabel')}</label>
        <input id="news-title" bind:value={draft.title[tab]} maxlength="120" placeholder={t('news.titlePlaceholder')} />
      </div>
      <div class="field">
        <label class="flabel" for="news-text">{t('news.textLabel')}</label>
        <textarea id="news-text" bind:value={draft.text[tab]} maxlength="4000" rows="7"
          placeholder={t(draft.kind === 'changelog' ? 'news.changelogPlaceholder' : 'news.textPlaceholder')}></textarea>
      </div>
    {/key}
    <p class="muted tiny">{t('news.langHint')}</p>

    <div class="options">
      <label class="check"><input type="checkbox" bind:checked={draft.pinned} /> {t('news.pinned')}</label>
      <div class="expires">
        <label class="flabel" for="news-expires">{t('news.expires')}</label>
        <input id="news-expires" type="date" bind:value={draft.expires} />
      </div>
    </div>
    <button type="submit" disabled={busy || !complete}>{draft.id ? t('news.save') : t('news.post')}</button>
  </form>

  {#if data}
    <section class="card">
      <h2>{t('news.all')}</h2>
      {#if data.posts.length === 0}
        <p class="muted small">{t('news.none')}</p>
      {:else}
        <ul class="posts">
          {#each data.posts as p (p.id)}
            <li class:faded={expired(p)}>
              <div class="meta">
                <span class="tag {p.kind}">{t('news.kind.' + p.kind)}</span>
                {#if p.pinned}<span class="tag pin">📌 {t('news.pinnedShort')}</span>{/if}
                {#if expired(p)}<span class="tag">{t('news.expired')}</span>{:else if p.expires}<span class="muted tiny">{t('news.until', { date: dateTime(p.expires) })}</span>{/if}
                {#if !p.title.de || !p.title.en}<span class="muted tiny">{t('news.onlyLang', { lang: p.title.de ? 'DE' : 'EN' })}</span>{/if}
              </div>
              <strong>{p.title.de || p.title.en}</strong>
              <p class="text muted small">{p.text.de || p.text.en}</p>
              <div class="foot">
                <span class="muted tiny">{p.author} · {dateTime(p.created)}</span>
                <span class="buttons">
                  <button class="secondary small-btn" disabled={busy} onclick={() => edit(p)}>{t('news.editButton')}</button>
                  <button class="danger small-btn" disabled={busy} onclick={() => remove(p)}>{t('news.delete')}</button>
                </span>
              </div>
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  h2 {
    margin: 0;
    font-size: 1.1rem;
  }

  .form {
    display: grid;
    gap: 12px;
  }

  .form .field + .field {
    margin-top: 0;
  }

  .row,
  .foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .kinds {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  }

  .kind {
    display: grid;
    justify-items: start;
    gap: 2px;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border-color: var(--border);
    font-weight: 400;
  }

  .kind.active {
    border-color: var(--accent);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 25%, transparent);
  }

  .langs {
    display: flex;
    gap: 4px;
    border-bottom: 1px solid var(--border);
  }

  .lang {
    background: transparent;
    color: var(--muted);
    border-radius: 8px 8px 0 0;
    padding: 6px 14px;
  }

  .lang.active {
    color: var(--text);
    background: var(--surface-2);
  }

  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--good);
  }

  .flabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
  }

  textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 11px 12px;
    resize: vertical;
    outline: none;
  }

  textarea:focus {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 25%, transparent);
  }

  .options {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 24px;
    align-items: end;
  }

  .expires {
    min-width: 180px;
  }

  .check {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 0.9rem;
    margin: 0;
  }

  .check input {
    width: auto;
  }

  .tiny {
    font-size: 0.78rem;
    margin: 0;
  }

  .small-btn {
    padding: 5px 12px;
    font-size: 0.85rem;
  }

  .posts {
    list-style: none;
    margin: 12px 0 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .posts li {
    display: grid;
    gap: 6px;
    padding: 12px 14px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .posts li.faded {
    opacity: 0.6;
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  .tag {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .tag.news {
    background: color-mix(in srgb, var(--accent) 15%, transparent);
    color: var(--accent);
  }

  .tag.changelog {
    background: color-mix(in srgb, var(--good) 15%, transparent);
    color: var(--good);
  }

  .text {
    margin: 0;
    white-space: pre-line;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .buttons {
    display: flex;
    gap: 6px;
  }
</style>
