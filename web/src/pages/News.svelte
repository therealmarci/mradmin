<script>
  // News, changelogs and critical warnings for the players: shown on the player page (/web), optionally posted to the
  // community Discord, previewed once in game (warnings: chat and title). Texts in German and English - a language left
  // empty shows the other one. Warnings pinned on Uptime Kuma status pages are listed here too (read only).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  const KINDS = ['news', 'changelog', 'alert'];
  const TYPES = ['new', 'changed', 'fixed', 'removed'];

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let filter = $state('all');

  const empty = (kind = 'news') => ({
    id: 0, kind, pinned: false, expires: '', discord: false, version: '',
    title: { de: '', en: '' }, text: { de: '', en: '' }, entries: [],
  });
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

  function newOf(kind) {
    draft = empty(kind);
    if (kind === 'changelog') addEntry();
    tab = 'de';
    message = '';
  }

  function edit(p) {
    draft = {
      id: p.id,
      kind: p.kind,
      pinned: p.pinned,
      expires: toDay(p.expires),
      discord: false,
      version: p.version ?? '',
      title: { de: p.title.de ?? '', en: p.title.en ?? '' },
      text: { de: p.text.de ?? '', en: p.text.en ?? '' },
      entries: (p.entries ?? []).map((e) => ({ type: e.type, de: e.de ?? '', en: e.en ?? '' })),
      posted: p.discord,
    };
    tab = draft.title.de || draft.entries.some((e) => e.de) || !(draft.title.en || draft.entries.some((e) => e.en)) ? 'de' : 'en';
    message = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function addEntry(type = 'new') {
    draft.entries.push({ type, de: '', en: '' });
  }

  function move(i, by) {
    const j = i + by;
    if (j < 0 || j >= draft.entries.length) return;
    const list = [...draft.entries];
    [list[i], list[j]] = [list[j], list[i]];
    draft.entries = list;
  }

  async function save(event) {
    event.preventDefault();
    busy = true;
    error = '';
    message = '';
    try {
      const body = { ...draft, expires: fromDay(draft.expires), entries: draft.entries.filter((e) => e.de.trim() || e.en.trim()) };
      await api('POST', '/news', body);
      message = t(draft.id ? 'news.saved' : 'news.posted.' + draft.kind);
      newOf(draft.kind);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function act(p, action, confirmKey) {
    if (confirmKey && !confirm(t(confirmKey, { title: heading(p) }))) return;
    busy = true;
    try {
      await api('POST', `/news/${p.id}/${action}`);
      if (action === 'delete' && draft.id === p.id) newOf(draft.kind);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const heading = (p) => p.title.de || p.title.en || p.version || '?';
  const filled = (l) => (draft.kind === 'changelog' ? draft.entries.some((e) => e[l].trim()) : draft.title[l].trim() && draft.text[l].trim());
  const complete = $derived(
    draft.kind === 'changelog' ? draft.version.trim() && draft.entries.some((e) => e.de.trim() || e.en.trim()) : ['de', 'en'].some(filled),
  );
  const now = Date.now();
  const over = (p) => p.ended || (p.expires && p.expires < now);
  const posts = $derived((data?.posts ?? []).filter((p) => filter === 'all' || p.kind === filter));
  const activeAlerts = $derived((data?.posts ?? []).filter((p) => p.kind === 'alert' && !over(p)).length + (data?.incidents?.length ?? 0));
</script>

<div class="stack">
  <div>
    <h1>{t('news.title')}</h1>
    <p class="muted small">{t('news.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}
  {#if data && !data.playerWeb}<div class="notice">{t('news.playerWebOff')}</div>{/if}

  {#if data?.incidents?.length}
    <section class="card kuma">
      <h2>⚠ {t('news.kumaTitle')}</h2>
      <p class="muted small">{t('news.kumaHint')}</p>
      {#each data.incidents as i (i.key)}
        <div class="incident level-{i.level}">
          <strong>{i.title}</strong>
          <span class="muted tiny">{i.slug} · {dateTime(i.created)}</span>
          {#if i.content}<p class="small">{i.content}</p>{/if}
        </div>
      {/each}
    </section>
  {/if}

  <form class="card form kind-{draft.kind}" onsubmit={save}>
    <div class="kinds" role="tablist" aria-label={t('news.kind')}>
      {#each KINDS as k}
        <button type="button" role="tab" aria-selected={draft.kind === k} class="kind k-{k}" class:active={draft.kind === k}
          disabled={draft.id !== 0 && draft.kind !== k} onclick={() => newOf(k)}>
          <span class="kind-icon">{k === 'news' ? '📣' : k === 'changelog' ? '📝' : '⚠️'}</span>
          <span><strong>{t('news.kind.' + k)}</strong><span class="muted small">{t('news.kindHint.' + k)}</span></span>
        </button>
      {/each}
    </div>

    <div class="row">
      <h2>{draft.id ? t('news.edit') : t('news.new.' + draft.kind)}</h2>
      {#if draft.id}<button type="button" class="secondary small-btn" onclick={() => newOf(draft.kind)}>{t('news.cancel')}</button>{/if}
    </div>

    {#if draft.kind === 'changelog'}
      <div class="field">
        <label class="flabel" for="news-version">{t('news.version')}</label>
        <input id="news-version" bind:value={draft.version} maxlength="40" placeholder="1.4.0" />
      </div>
    {/if}

    <div class="langs" role="tablist">
      {#each ['de', 'en'] as l}
        <button type="button" role="tab" aria-selected={tab === l} class="lang" class:active={tab === l} onclick={() => (tab = l)}>
          {l === 'de' ? 'Deutsch' : 'English'}
          {#if filled(l)}<span class="dot" aria-hidden="true"></span>{/if}
        </button>
      {/each}
    </div>

    {#key tab}
      <div class="field">
        <label class="flabel" for="news-title">{t(draft.kind === 'changelog' ? 'news.titleOptional' : 'news.titleLabel')}</label>
        <input id="news-title" bind:value={draft.title[tab]} maxlength="120"
          placeholder={t('news.titlePlaceholder.' + draft.kind)} />
      </div>
      {#if draft.kind === 'changelog'}
        <div class="entries">
          <span class="flabel">{t('news.entries')}</span>
          {#each draft.entries as e, i (i)}
            <div class="entry t-{e.type}">
              <select bind:value={e.type} aria-label={t('news.entryType')}>
                {#each TYPES as type}<option value={type}>{t('news.type.' + type)}</option>{/each}
              </select>
              <input bind:value={e[tab]} maxlength="300" placeholder={t('news.entryPlaceholder')} aria-label={t('news.entries')} />
              <span class="entry-buttons">
                <button type="button" class="secondary icon-btn" title={t('news.up')} disabled={i === 0} onclick={() => move(i, -1)}>↑</button>
                <button type="button" class="secondary icon-btn" title={t('news.down')} disabled={i === draft.entries.length - 1} onclick={() => move(i, 1)}>↓</button>
                <button type="button" class="danger icon-btn" title={t('news.remove')} onclick={() => draft.entries.splice(i, 1)}>✕</button>
              </span>
            </div>
          {/each}
          <div class="add">
            {#each TYPES as type}
              <button type="button" class="secondary small-btn add-{type}" onclick={() => addEntry(type)}>+ {t('news.type.' + type)}</button>
            {/each}
          </div>
        </div>
        <div class="field">
          <label class="flabel" for="news-text">{t('news.changelogIntro')}</label>
          <textarea id="news-text" bind:value={draft.text[tab]} maxlength="4000" rows="2"></textarea>
        </div>
      {:else}
        <div class="field">
          <label class="flabel" for="news-text">{t('news.textLabel')}</label>
          <textarea id="news-text" bind:value={draft.text[tab]} maxlength="4000" rows={draft.kind === 'alert' ? 3 : 7}
            placeholder={t('news.textPlaceholder.' + draft.kind)}></textarea>
        </div>
      {/if}
    {/key}
    <p class="muted tiny">{t('news.langHint')}</p>

    <div class="options">
      {#if draft.kind !== 'alert'}
        <label class="check"><input type="checkbox" bind:checked={draft.pinned} /> {t('news.pinned')}</label>
      {/if}
      <label class="check" class:off={!data?.discord}>
        <input type="checkbox" bind:checked={draft.discord} disabled={!data?.discord || draft.posted} />
        {draft.posted ? t('news.discordPosted') : t('news.discord')}
      </label>
      <div class="expires">
        <label class="flabel" for="news-expires">{t(draft.kind === 'alert' ? 'news.alertUntil' : 'news.expires')}</label>
        <input id="news-expires" type="date" bind:value={draft.expires} />
      </div>
    </div>
    {#if data && !data.discord}<p class="muted tiny">{t('news.discordOff')}</p>{/if}
    {#if draft.kind === 'alert' && !draft.id}<p class="alert-hint small">{t('news.alertHint')}</p>{/if}
    <button type="submit" class:danger-fill={draft.kind === 'alert'} disabled={busy || !complete}>
      {draft.id ? t('news.save') : t('news.post.' + draft.kind)}
    </button>
  </form>

  {#if data}
    <section class="card">
      <div class="row">
        <h2>{t('news.all')}</h2>
        <div class="filters" role="tablist">
          {#each ['all', ...KINDS] as f}
            <button class="link-btn" class:active={filter === f} onclick={() => (filter = f)}>
              {t('news.filter.' + f)}{#if f === 'alert' && activeAlerts} <span class="count">{activeAlerts}</span>{/if}
            </button>
          {/each}
        </div>
      </div>
      {#if posts.length === 0}
        <p class="muted small">{t('news.none')}</p>
      {:else}
        <ul class="posts">
          {#each posts as p (p.id)}
            <li class="post k-{p.kind}" class:faded={over(p)}>
              <div class="meta">
                <span class="tag {p.kind}">{t('news.kind.' + p.kind)}</span>
                {#if p.version}<span class="tag version">{p.version}</span>{/if}
                {#if p.pinned}<span class="tag pin">📌 {t('news.pinnedShort')}</span>{/if}
                {#if p.discord}<span class="tag">Discord ✓</span>{/if}
                {#if p.kind === 'alert' && !over(p)}<span class="tag live">● {t('news.active')}</span>{/if}
                {#if p.ended}<span class="tag">{t('news.ended')}</span>{:else if over(p)}<span class="tag">{t('news.expired')}</span>{:else if p.expires}<span class="muted tiny">{t('news.until', { date: dateTime(p.expires) })}</span>{/if}
              </div>
              <strong>{heading(p)}</strong>
              {#if p.kind === 'changelog' && p.entries.length}
                <ul class="mini-entries">
                  {#each p.entries.slice(0, 4) as e}<li><span class="t t-{e.type}">{t('news.type.' + e.type)}</span> {e.de || e.en}</li>{/each}
                  {#if p.entries.length > 4}<li class="muted tiny">+ {p.entries.length - 4}</li>{/if}
                </ul>
              {:else}
                <p class="text muted small">{p.text.de || p.text.en}</p>
              {/if}
              <div class="foot">
                <span class="muted tiny">{p.author} · {dateTime(p.created)}</span>
                <span class="buttons">
                  {#if p.kind === 'alert' && !over(p)}
                    <button class="secondary small-btn" disabled={busy} onclick={() => act(p, 'end', 'news.endConfirm')}>{t('news.end')}</button>
                  {/if}
                  <button class="secondary small-btn" disabled={busy} onclick={() => edit(p)}>{t('news.editButton')}</button>
                  <button class="danger small-btn" disabled={busy} onclick={() => act(p, 'delete', 'news.deleteConfirm')}>{t('news.delete')}</button>
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
    gap: 14px;
    --kind: var(--accent);
    border-top: 4px solid var(--kind);
  }

  .form.kind-changelog {
    --kind: var(--good);
  }

  .form.kind-alert {
    --kind: var(--bad);
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
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  }

  .kind {
    display: flex;
    align-items: center;
    gap: 10px;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border-color: var(--border);
    font-weight: 400;
    justify-content: flex-start;
  }

  .kind > span:last-child {
    display: grid;
  }

  .kind-icon {
    font-size: 1.4rem;
  }

  .kind.active {
    border-color: var(--kind);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--kind) 25%, transparent);
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

  textarea,
  select {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    outline: none;
  }

  textarea {
    resize: vertical;
  }

  textarea:focus,
  select:focus {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 25%, transparent);
  }

  .entries {
    display: grid;
    gap: 8px;
  }

  .entry {
    display: grid;
    grid-template-columns: 140px 1fr auto;
    gap: 8px;
    align-items: center;
    padding-left: 10px;
    border-left: 3px solid var(--t);
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

  .entry-buttons {
    display: flex;
    gap: 4px;
  }

  .icon-btn {
    padding: 6px 9px;
    font-size: 0.85rem;
  }

  .add {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  @media (max-width: 640px) {
    .entry {
      grid-template-columns: 1fr;
    }
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

  .check.off {
    opacity: 0.55;
  }

  .check input {
    width: auto;
  }

  .tiny {
    font-size: 0.78rem;
    margin: 0;
  }

  .alert-hint {
    margin: 0;
    padding: 8px 12px;
    border-radius: 10px;
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 10%, transparent);
  }

  .danger-fill {
    background: var(--bad);
    color: #fff;
  }

  .small-btn {
    padding: 5px 12px;
    font-size: 0.85rem;
  }

  .filters {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  .link-btn {
    background: transparent;
    color: var(--muted);
    padding: 5px 10px;
    font-size: 0.85rem;
  }

  .link-btn.active {
    color: var(--text);
    background: var(--surface-2);
  }

  .count {
    background: var(--bad);
    color: #fff;
    border-radius: 999px;
    padding: 0 6px;
    font-size: 0.72rem;
  }

  .posts {
    list-style: none;
    margin: 12px 0 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .post {
    display: grid;
    gap: 6px;
    padding: 12px 14px;
    border-radius: 10px;
    background: var(--surface-2);
    border-left: 3px solid var(--accent);
  }

  .post.k-changelog {
    border-left-color: var(--good);
  }

  .post.k-alert {
    border-left-color: var(--bad);
  }

  .post.faded {
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

  .tag.changelog,
  .tag.version {
    background: color-mix(in srgb, var(--good) 15%, transparent);
    color: var(--good);
  }

  .tag.alert,
  .tag.live {
    background: color-mix(in srgb, var(--bad) 15%, transparent);
    color: var(--bad);
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

  .mini-entries {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 2px;
    font-size: 0.88rem;
  }

  .t {
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--t);
  }

  .buttons {
    display: flex;
    gap: 6px;
  }

  .kuma {
    border-left: 4px solid var(--bad);
    display: grid;
    gap: 8px;
  }

  .kuma p {
    margin: 0;
  }

  .incident {
    display: grid;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border-left: 3px solid var(--warn);
  }

  .incident.level-info {
    border-left-color: var(--accent);
  }

  .incident.level-critical {
    border-left-color: var(--bad);
  }
</style>
