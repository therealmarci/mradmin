<script>
  // Communication: posts for the players and announcements in the game.
  // Posts - news, changelogs and critical warnings: shown on the player page (/web), optionally posted to the community
  // Discord, previewed once in game (warnings: chat and title). Texts in German and English - a language left empty
  // shows the other one. Changelogs have entries by type and headings in three levels. Warnings pinned on Uptime Kuma
  // status pages are listed too (read only).
  // Announcements - like /mradmin announce: a preset with values or an own text, shown on every screen.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { ICONS } from '../lib/icons.js';
  import { ENTRY_TYPES, HEADINGS, isHeading, levelOf, sections, entryCount } from '../lib/changelog.js';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  const KINDS = ['news', 'changelog', 'alert'];

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let filter = $state('all');
  let area = $state(me.may.news ? 'posts' : 'announce');

  const empty = (kind = 'news') => ({
    id: 0, kind, pinned: false, expires: '', discord: false, version: '',
    title: { de: '', en: '' }, text: { de: '', en: '' }, entries: [],
  });
  let draft = $state(empty());
  let tab = $state('de');

  // Announcement
  let presets = $state([]);
  let preset = $state('');
  let args = $state('');
  let text = $state('');
  let toDiscord = $state(true);

  async function load() {
    if (!me.may.news) return;
    try {
      data = await api('GET', '/news');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }
  load();
  if (me.may.announce) api('GET', '/announce').then((r) => (presets = r.presets)).catch(() => {});

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

  async function announce(event) {
    event.preventDefault();
    const body = preset ? { preset, args, discord: String(toDiscord) } : { text, discord: String(toDiscord) };
    if (!confirm(t('mt.announceConfirm'))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/announce', body);
      message = t('mt.announced');
      args = '';
      text = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const heading = (p) => p.title.de || p.title.en || p.version || '?';
  const realEntries = (l) => draft.entries.some((e) => !isHeading(e.type) && e[l].trim());
  const filled = (l) => (draft.kind === 'changelog' ? realEntries(l) : draft.title[l].trim() && draft.text[l].trim());
  const complete = $derived(
    draft.kind === 'changelog' ? draft.version.trim() && (realEntries('de') || realEntries('en')) : ['de', 'en'].some(filled),
  );
  const now = Date.now();
  const over = (p) => p.ended || (p.expires && p.expires < now);
  const posts = $derived((data?.posts ?? []).filter((p) => filter === 'all' || p.kind === filter));
  const activeAlerts = $derived((data?.posts ?? []).filter((p) => p.kind === 'alert' && !over(p)).length + (data?.incidents?.length ?? 0));
  const counts = $derived(KINDS.reduce((c, k) => ((c[k] = (data?.posts ?? []).filter((p) => p.kind === k).length), c), {}));
  const chosen = $derived(presets.find((p) => p.name === preset));

  // Indent of every changelog line in the editor: a heading by its level, an entry by the heading above it.
  const indents = $derived.by(() => {
    let level = 0;
    return draft.entries.map((e) => {
      if (isHeading(e.type)) {
        level = levelOf(e.type);
        return level - 1;
      }
      return level;
    });
  });
  const KIND_ICON = { news: 'news', changelog: 'chart', alert: 'pulse' };
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.news} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.news} /></svg>
      </span>
      <div>
        <h1>{t('news.title')}</h1>
        <p class="muted">{t('news.subtitle')}</p>
      </div>
    </div>
    {#if data}
      <div class="numbers">
        <span><b>{data.posts.length}</b> {t('news.postsCount')}</span>
        {#if activeAlerts}<span class="bad-text"><b>{activeAlerts}</b> {t('news.alertsActive')}</span>{/if}
      </div>
    {/if}
  </header>

  {#if me.may.news && me.may.announce}
    <div class="seg areas" role="tablist">
      <button role="tab" aria-selected={area === 'posts'} class:active={area === 'posts'} onclick={() => (area = 'posts')}>{t('news.area.posts')}</button>
      <button role="tab" aria-selected={area === 'announce'} class:active={area === 'announce'} onclick={() => (area = 'announce')}>{t('news.area.announce')}</button>
    </div>
  {/if}

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if area === 'posts' && me.may.news}
    {#if data && !data.playerWeb}<div class="notice">{t('news.playerWebOff')}</div>{/if}

    {#if data?.incidents?.length}
      <section class="card tile kuma">
        <h2>{t('news.kumaTitle')}<span class="n">{data.incidents.length}</span></h2>
        <p class="muted small">{t('news.kumaHint')}</p>
        <ul class="incidents">
          {#each data.incidents as i (i.key)}
            <li class="incident level-{i.level}">
              <strong>{i.title}</strong>
              <span class="muted tiny">{i.slug} · {dateTime(i.created)}</span>
              {#if i.content}<p class="small">{i.content}</p>{/if}
            </li>
          {/each}
        </ul>
      </section>
    {/if}

    <!-- Writing: the kind as tiles, then the texts per language -->
    <form class="card tile editor kind-{draft.kind}" onsubmit={save}>
      <div class="kinds" role="tablist" aria-label={t('news.kind')}>
        {#each KINDS as k}
          <button type="button" role="tab" aria-selected={draft.kind === k} class="kind k-{k}" class:active={draft.kind === k}
            disabled={draft.id !== 0 && draft.kind !== k} onclick={() => newOf(k)}>
            <span class="kind-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round"><path d={ICONS[KIND_ICON[k]]} /></svg>
            </span>
            <span class="kind-text"><strong>{t('news.kind.' + k)}</strong><span class="muted small">{t('news.kindHint.' + k)}</span></span>
          </button>
        {/each}
      </div>

      <div class="e-head">
        <h2>{draft.id ? t('news.edit') : t('news.new.' + draft.kind)}</h2>
        <div class="e-tools">
          <div class="seg small" role="tablist">
            {#each ['de', 'en'] as l}
              <button type="button" role="tab" aria-selected={tab === l} class:active={tab === l} onclick={() => (tab = l)}>
                {l.toUpperCase()}{#if filled(l)}<span class="ok-dot" aria-hidden="true"></span>{/if}
              </button>
            {/each}
          </div>
          {#if draft.id}<button type="button" class="secondary mini" onclick={() => newOf(draft.kind)}>{t('news.cancel')}</button>{/if}
        </div>
      </div>

      <div class="e-grid">
        {#if draft.kind === 'changelog'}
          <div class="field">
            <label class="flabel" for="news-version">{t('news.version')}</label>
            <input id="news-version" class="mono" bind:value={draft.version} maxlength="40" placeholder="1.4.0" />
          </div>
        {/if}
        {#key tab}
          <div class="field" class:wide={draft.kind !== 'changelog'}>
            <label class="flabel" for="news-title">{t(draft.kind === 'changelog' ? 'news.titleOptional' : 'news.titleLabel')}</label>
            <input id="news-title" bind:value={draft.title[tab]} maxlength="120" placeholder={t('news.titlePlaceholder.' + draft.kind)} />
          </div>
        {/key}
      </div>

      {#key tab}
        {#if draft.kind === 'changelog'}
          <!-- Changelog lines: headings (3 levels) and entries, indented by the heading above -->
          <div class="entries">
            <span class="flabel">{t('news.entries')}</span>
            {#each draft.entries as e, i (i)}
              {@const head = isHeading(e.type)}
              <div class="entry t-{e.type}" class:is-head={head} style="--indent: {indents[i]}">
                <select bind:value={e.type} aria-label={t('news.entryType')}>
                  <optgroup label={t('news.headings')}>
                    {#each HEADINGS as h}<option value={h}>{t('news.type.' + h)}</option>{/each}
                  </optgroup>
                  <optgroup label={t('news.entries')}>
                    {#each ENTRY_TYPES as type}<option value={type}>{t('news.type.' + type)}</option>{/each}
                  </optgroup>
                </select>
                <input bind:value={e[tab]} maxlength="300" placeholder={t(head ? 'news.headingPlaceholder' : 'news.entryPlaceholder')}
                  aria-label={t('news.entries')} />
                <span class="entry-buttons">
                  <button type="button" class="icon-btn" title={t('news.up')} disabled={i === 0} onclick={() => move(i, -1)}>↑</button>
                  <button type="button" class="icon-btn" title={t('news.down')} disabled={i === draft.entries.length - 1} onclick={() => move(i, 1)}>↓</button>
                  <button type="button" class="icon-btn remove" title={t('news.remove')} onclick={() => draft.entries.splice(i, 1)}>✕</button>
                </span>
              </div>
            {/each}
            <div class="add">
              <span class="add-group">
                {#each HEADINGS as h}
                  <button type="button" class="add-btn add-head" onclick={() => addEntry(h)}>+ {t('news.type.' + h)}</button>
                {/each}
              </span>
              <span class="add-group">
                {#each ENTRY_TYPES as type}
                  <button type="button" class="add-btn add-{type}" onclick={() => addEntry(type)}>+ {t('news.type.' + type)}</button>
                {/each}
              </span>
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
          <button type="button" class="toggle" role="switch" aria-checked={draft.pinned} onclick={() => (draft.pinned = !draft.pinned)}>
            <span class="knob" class:on={draft.pinned}></span>{t('news.pinned')}
          </button>
        {/if}
        <button type="button" class="toggle" role="switch" aria-checked={draft.discord} disabled={!data?.discord || draft.posted}
          onclick={() => (draft.discord = !draft.discord)}>
          <span class="knob" class:on={draft.discord || draft.posted}></span>{draft.posted ? t('news.discordPosted') : t('news.discord')}
        </button>
        <div class="expires">
          <label class="flabel" for="news-expires">{t(draft.kind === 'alert' ? 'news.alertUntil' : 'news.expires')}</label>
          <input id="news-expires" type="date" bind:value={draft.expires} />
        </div>
      </div>
      {#if data && !data.discord}<p class="muted tiny">{t('news.discordOff')}</p>{/if}
      {#if draft.kind === 'alert' && !draft.id}<p class="alert-hint small">{t('news.alertHint')}</p>{/if}
      <div class="submit-row">
        <button type="submit" class:danger={draft.kind === 'alert'} disabled={busy || !complete}>
          {draft.id ? t('news.save') : t('news.post.' + draft.kind)}
        </button>
      </div>
    </form>

    <!-- Everything written so far -->
    {#if data}
      <section class="card tile">
        <div class="t-head">
          <h2>{t('news.all')}</h2>
          <div class="seg small" role="tablist">
            {#each ['all', ...KINDS] as f}
              <button role="tab" aria-selected={filter === f} class:active={filter === f} onclick={() => (filter = f)}>
                {t('news.filter.' + f)}
                {#if f === 'all'}<span class="n">{data.posts.length}</span>{:else if f === 'alert' && activeAlerts}<span class="n bad">{activeAlerts}</span>{:else if counts[f]}<span class="n">{counts[f]}</span>{/if}
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
                  <span class="status">{t('news.kind.' + p.kind)}</span>
                  {#if p.version}<span class="chip mono">{p.version}</span>{/if}
                  {#if p.pinned}<span class="chip">{t('news.pinnedShort')}</span>{/if}
                  {#if p.discord}<span class="chip">Discord</span>{/if}
                  {#if p.kind === 'alert' && !over(p)}<span class="chip live">{t('news.active')}</span>{/if}
                  {#if p.ended}<span class="chip">{t('news.ended')}</span>{:else if over(p)}<span class="chip">{t('news.expired')}</span>{/if}
                </div>
                <strong class="p-title">{heading(p)}</strong>
                {#if p.kind === 'changelog' && p.entries.length}
                  <div class="mini-cl">
                    {#each sections(p.entries, (e) => e.de || e.en).slice(0, 3) as s}
                      {#if s.title != null}<span class="mini-head" style="--lvl: {s.level}">{s.title}</span>{/if}
                      {#each s.groups as g}
                        <span class="mini-line" style="--lvl: {s.level}"><span class="t t-{g.type}">{t('news.type.' + g.type)}</span> {g.items.length}</span>
                      {/each}
                    {/each}
                    <span class="muted tiny">{t('news.entryCount', { n: entryCount(p.entries) })}</span>
                  </div>
                {:else}
                  <p class="text muted small">{p.text.de || p.text.en}</p>
                {/if}
                <div class="foot">
                  <span class="muted tiny">
                    {p.author} · {dateTime(p.created)}{#if p.expires && !over(p)} · {t('news.until', { date: dateTime(p.expires) })}{/if}
                  </span>
                  <span class="buttons">
                    {#if p.kind === 'alert' && !over(p)}
                      <button class="secondary mini" disabled={busy} onclick={() => act(p, 'end', 'news.endConfirm')}>{t('news.end')}</button>
                    {/if}
                    <button class="secondary mini" disabled={busy} onclick={() => edit(p)}>{t('news.editButton')}</button>
                    <button class="danger mini" disabled={busy} onclick={() => act(p, 'delete', 'news.deleteConfirm')}>{t('news.delete')}</button>
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
  {:else if area === 'announce' && me.may.announce}
    <form class="card tile" onsubmit={announce}>
      <h2>{t('mt.announceTitle')}</h2>
      <p class="muted small">{t('mt.announceHint')}</p>
      <div class="presets">
        <button type="button" class="preset" class:active={preset === ''} onclick={() => (preset = '')}>
          <strong>{t('mt.freeText')}</strong><span class="muted small">{t('mt.freeTextHint')}</span>
        </button>
        {#each presets as p (p.name)}
          <button type="button" class="preset" class:active={preset === p.name} onclick={() => (preset = p.name)}>
            <strong>{p.name}{#if p.discord}<span class="chip">Discord</span>{/if}</strong><span class="muted small">{p.preview}</span>
          </button>
        {/each}
      </div>
      {#if preset === ''}
        <input bind:value={text} maxlength="300" placeholder={t('mt.textPlaceholder')} aria-label={t('mt.freeText')} />
      {:else if chosen?.values}
        <input bind:value={args} maxlength="100" placeholder={t('mt.argsPlaceholder')} aria-label={t('mt.args')} />
      {/if}
      <div class="submit-row">
        <button type="button" class="toggle" role="switch" aria-checked={toDiscord} onclick={() => (toDiscord = !toDiscord)}>
          <span class="knob" class:on={toDiscord}></span>{t('mt.toDiscord')}
        </button>
        <button type="submit" disabled={busy || (preset === '' && !text.trim())}>{t('mt.send')}</button>
      </div>
    </form>
  {/if}
</div>

<style>
  .page {
    --cm: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--cm) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--cm) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--cm);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--cm);
    background: color-mix(in srgb, var(--cm) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--cm) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .numbers {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .numbers span {
    padding: 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    font-size: 0.88rem;
  }

  .numbers b {
    font-size: 1.1rem;
    margin-right: 2px;
  }

  .bad-text {
    color: var(--bad);
  }

  /* Switches */
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 6px 14px;
    border-radius: 9px;
    font-size: 0.88rem;
  }

  .seg.small button {
    padding: 4px 10px;
    font-size: 0.82rem;
  }

  .seg button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .areas {
    justify-self: start;
  }

  .areas button {
    padding: 8px 18px;
    font-size: 0.95rem;
    font-weight: 700;
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .n.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .ok-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--good);
  }

  /* Tiles */
  .tile {
    display: grid;
    gap: 14px;
    align-content: start;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.05rem;
  }

  .t-head,
  .e-head,
  .foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .e-tools {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .tiny {
    font-size: 0.76rem;
    margin: 0;
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .chip.live {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  /* Kuma incidents */
  .kuma {
    border-left: 4px solid var(--warn);
  }

  .incidents {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .incident {
    --lv: var(--accent);
    display: grid;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border-left: 3px solid var(--lv);
  }

  .incident.level-danger {
    --lv: var(--bad);
  }

  .incident.level-warning {
    --lv: var(--warn);
  }

  .incident p {
    margin: 4px 0 0;
  }

  /* The editor: the colour of the kind on top */
  .editor {
    --kind: var(--accent);
    border-top: 4px solid var(--kind);
  }

  .editor.kind-changelog {
    --kind: var(--good);
  }

  .editor.kind-alert {
    --kind: var(--bad);
  }

  .kinds {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  }

  .kind {
    --kc: var(--accent);
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    padding: 12px 14px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
  }

  .kind.k-changelog {
    --kc: var(--good);
  }

  .kind.k-alert {
    --kc: var(--bad);
  }

  .kind:not(:disabled):hover {
    filter: none;
    border-color: color-mix(in srgb, var(--kc) 50%, var(--border));
  }

  .kind.active {
    border-color: var(--kc);
    background: color-mix(in srgb, var(--kc) 9%, var(--surface-2));
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--kc) 22%, transparent);
  }

  .kind-icon {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    flex-shrink: 0;
    border-radius: 10px;
    color: var(--kc);
    background: color-mix(in srgb, var(--kc) 14%, var(--surface));
  }

  .kind-text {
    display: grid;
    min-width: 0;
  }

  .e-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: minmax(120px, 200px) minmax(0, 1fr);
  }

  .e-grid .wide {
    grid-column: 1 / -1;
  }

  .field {
    display: grid;
    gap: 6px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  textarea,
  select {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 9px 10px;
  }

  /* Changelog lines: a heading by its level, an entry below the heading above it */
  .entries {
    display: grid;
    gap: 6px;
  }

  .entry {
    --t: var(--muted);
    display: grid;
    grid-template-columns: 150px minmax(0, 1fr) auto;
    align-items: center;
    gap: 8px;
    margin-left: calc(var(--indent) * 22px);
    padding: 4px 4px 4px 8px;
    border-radius: 10px;
    border-left: 3px solid var(--t);
    background: color-mix(in srgb, var(--t) 6%, transparent);
  }

  .entry select {
    padding: 6px 8px;
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--t);
  }

  .entry.is-head {
    --t: var(--text);
    margin-top: 6px;
    background: var(--surface-2);
  }

  .entry.is-head input {
    font-weight: 800;
  }

  .entry.t-h1 input {
    font-size: 1.05rem;
  }

  .entry.t-h3 {
    --t: var(--muted);
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
    gap: 2px;
  }

  .icon-btn {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 8px;
    color: var(--muted);
    background: transparent;
  }

  .icon-btn:not(:disabled):hover {
    filter: none;
    color: var(--text);
    background: var(--surface-2);
  }

  .icon-btn.remove {
    color: var(--bad);
  }

  .add {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    gap: 8px;
    margin-top: 4px;
  }

  .add-group {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .add-btn {
    --t: var(--muted);
    padding: 5px 12px;
    font-size: 0.82rem;
    border-radius: 999px;
    color: var(--t);
    background: var(--surface-2);
    border: 1px dashed color-mix(in srgb, var(--t) 50%, var(--border));
  }

  .add-btn:hover {
    filter: none;
    background: color-mix(in srgb, var(--t) 10%, var(--surface-2));
  }

  .add-head {
    --t: var(--text);
  }

  .add-new {
    --t: var(--good);
  }

  .add-changed {
    --t: var(--accent);
  }

  .add-fixed {
    --t: var(--warn);
  }

  .add-removed {
    --t: var(--bad);
  }

  .options {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }

  .expires {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .expires input {
    width: auto;
  }

  .alert-hint {
    margin: 0;
    padding: 10px 12px;
    border-radius: 10px;
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 8%, var(--surface-2));
  }

  .submit-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .submit-row button[type='submit'] {
    margin-left: auto;
  }

  /* Switch */
  .toggle {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 0;
    background: transparent;
    color: var(--text);
    font-weight: 500;
  }

  .toggle:hover {
    filter: none;
  }

  .knob {
    position: relative;
    width: 40px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
    transition: background 0.15s;
  }

  .knob::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
    transition: transform 0.15s;
  }

  .knob.on {
    background: var(--accent);
  }

  .knob.on::after {
    transform: translateX(18px);
  }

  /* All posts as cards in the colour of their kind */
  .posts {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  }

  .post {
    --kc: var(--accent);
    display: grid;
    gap: 6px;
    align-content: start;
    padding: 14px 16px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--kc);
    min-width: 0;
  }

  .post.k-changelog {
    --kc: var(--good);
  }

  .post.k-alert {
    --kc: var(--bad);
  }

  .post.faded {
    opacity: 0.6;
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--kc);
    margin-right: auto;
  }

  .p-title {
    overflow-wrap: anywhere;
  }

  .text {
    margin: 0;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .mini-cl {
    display: grid;
    gap: 2px;
    padding: 0;
    font-size: 0.82rem;
  }

  .mini-head {
    font-weight: 700;
    margin-left: calc((var(--lvl) - 1) * 12px);
  }

  .mini-line {
    margin-left: calc(var(--lvl) * 12px);
  }

  .t {
    font-weight: 700;
    color: var(--t);
  }

  .foot {
    margin-top: 4px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .buttons {
    display: flex;
    gap: 6px;
  }

  /* Announcement */
  .presets {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .preset {
    display: grid;
    gap: 2px;
    justify-content: stretch;
    justify-items: start;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-weight: 400;
    padding: 10px 12px;
  }

  .preset strong {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .preset:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .preset.active {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 8%, var(--surface-2));
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 25%, transparent);
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .e-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .entry {
      grid-template-columns: minmax(0, 1fr) auto;
      margin-left: calc(var(--indent) * 12px);
    }

    .entry select {
      grid-column: 1 / -1;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .knob,
    .knob::after {
      transition: none;
    }
  }
</style>
