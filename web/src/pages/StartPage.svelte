<script>
  // The start pages (since 1.5): one for visitors, one for signed-in players - each made of widgets in a 12-column
  // grid. With mradmin.appearance the page has an edit mode right here (switch between the two pages):
  // drag to move, the corner handle to resize, + to insert from the catalog, the cog to set up, x to remove; save or
  // discard. Data: GET /api/landing, edit: GET/POST /api/landing/edit and /api/landing (BrandingRoutes.java).
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import Widget from '../components/Widget.svelte';
  import Toast from '../components/Toast.svelte';
  import { brand } from '../lib/branding.svelte.js';

  // player: the signed-in player (null = visitor); canEdit: mradmin.appearance; onsignin: opens the sign-in window
  let { player = null, canEdit = false, onsignin = () => {} } = $props();

  let data = $state(null);
  let editing = $state(false);
  let draft = $state({ guests: [], members: [] });
  // which page the edit mode works on
  let editPage = $state('members');
  let selected = $state(null);
  let catalog = $state(false);
  let busy = $state(false);
  let error = $state('');
  let message = $state('');
  let dragId = $state(null);
  let overId = $state(null);
  let gridEl = $state();

  async function load() {
    try {
      data = await api('GET', '/landing?lang=' + lang);
    } catch {
      data = { guests: [], members: [], news: [] };
    }
  }
  load();

  const ROW = 76;
  const GAP = 16;
  const GROUPS = [
    ['status', ['banner', 'hints', 'address', 'online']],
    ['personal', ['greeting', 'tiles', 'stats']],
    ['content', ['news', 'text', 'image', 'links', 'discord', 'login']],
  ];
  const SIZES = { banner: [12, 3], hints: [12, 2], address: [4, 2], online: [4, 2], greeting: [6, 2], tiles: [12, 3],
    stats: [4, 3], news: [8, 4], text: [6, 3], image: [6, 3], links: [4, 2], discord: [6, 2], login: [4, 2] };
  const PERSONAL = ['greeting', 'tiles', 'stats'];
  const page = $derived(player ? 'members' : 'guests');
  const DATA = {
    banner: () => ({ image: '', address: '', online: true }), address: () => ({ title: '', address: '' }),
    news: () => ({ title: '', count: 4 }), text: () => ({ title: '', text: '' }), image: () => ({ image: '', alt: '', fit: 'cover' }),
    links: () => ({ title: '', items: [] }), discord: () => ({ url: '', text: '' }), login: () => ({ title: '', text: '' }),
  };

  const widgets = $derived(editing ? draft[editPage] : (data?.[page] ?? []));
  // While editing the visitors' page it is shown as a visitor sees it.
  const viewer = $derived(editing && editPage === 'guests' ? null : player);
  // Outside the edit mode widgets without content stay away (no text, no picture, no links ...).
  function filled(w) {
    const d = w.data;
    if (w.type === "text") return !!d.text;
    if (w.type === "image") return !!d.image;
    if (w.type === "address") return !!d.address;
    if (w.type === "discord") return !!d.url;
    if (w.type === "links") return d.items.length > 0 || brand.links.length > 0;
    if (w.type === "tiles") return !!viewer?.areas?.length;
    if (w.type === "stats") return !!viewer?.personal?.length;
    return true;
  }
  const shown = $derived(editing ? widgets : widgets.filter(filled));
  const ctx = $derived({ player: viewer, news: data?.news ?? [], online: data?.online, editing, onsignin });
  const current = $derived(draft[editPage].find((w) => w.id === selected) ?? null);
  const changed = $derived(editing && JSON.stringify(draft) !== JSON.stringify({ guests: data?.guests ?? [], members: data?.members ?? [] }));
  // Personal widgets only on the players' page, the call to log in only on the visitors' page.
  const allowed = (type) => (editPage === 'guests' ? !PERSONAL.includes(type) : type !== 'login');

  function startEdit() {
    draft = structuredClone({ guests: $state.snapshot(data.guests), members: $state.snapshot(data.members) });
    editPage = page;
    editing = true;
    selected = null;
  }

  function discard() {
    if (changed && !window.confirm(t('widget.discardConfirm'))) return;
    editing = false;
    selected = null;
    catalog = false;
  }

  async function save() {
    busy = true;
    error = '';
    try {
      await api('POST', '/landing', draft);
      await load();
      editing = false;
      selected = null;
      message = t('widget.saved');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function insert(type) {
    const [w, h] = SIZES[type];
    const widget = { id: Math.random().toString(16).slice(2, 12), type, w, h, data: DATA[type]?.() ?? { title: '' } };
    const list = draft[editPage];
    const at = selected ? list.findIndex((x) => x.id === selected) + 1 : list.length;
    draft[editPage] = [...list.slice(0, at), widget, ...list.slice(at)];
    selected = widget.id;
    catalog = false;
  }

  function remove(id) {
    draft[editPage] = draft[editPage].filter((w) => w.id !== id);
    if (selected === id) selected = null;
  }

  // --- moving: drag a widget onto another one, it goes before it (or after it on its right half)
  function dragStart(e, id) {
    dragId = id;
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', id);
  }

  function dragOver(e, id) {
    if (!dragId || dragId === id) return;
    e.preventDefault();
    overId = id;
  }

  function drop(e, id) {
    e.preventDefault();
    if (!dragId || dragId === id) return reset();
    const rect = e.currentTarget.getBoundingClientRect();
    const after = e.clientX > rect.left + rect.width / 2;
    const moving = draft[editPage].find((w) => w.id === dragId);
    const rest = draft[editPage].filter((w) => w.id !== dragId);
    const at = rest.findIndex((w) => w.id === id) + (after ? 1 : 0);
    draft[editPage] = [...rest.slice(0, at), moving, ...rest.slice(at)];
    reset();
  }

  function reset() {
    dragId = null;
    overId = null;
  }

  // --- resizing with the corner handle: width in columns, height in rows of the grid
  function resizeStart(e, widget) {
    e.preventDefault();
    e.stopPropagation();
    const item = e.currentTarget.closest('.item');
    const box = item.getBoundingClientRect();
    const grid = gridEl.getBoundingClientRect();
    const column = (grid.width - GAP * 11) / 12;
    const target = draft[editPage].find((w) => w.id === widget.id);
    const move = (ev) => {
      target.w = Math.max(1, Math.min(12, Math.round((ev.clientX - box.left + GAP) / (column + GAP))));
      target.h = Math.max(1, Math.min(8, Math.round((ev.clientY - box.top + GAP) / (ROW + GAP))));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }

  // --- pictures (banner background, picture widget)
  async function upload(input) {
    const file = input.files?.[0];
    input.value = '';
    if (!file || !current) return;
    busy = true;
    error = '';
    try {
      const response = await fetch('/api/landing/image', {
        method: 'POST', credentials: 'same-origin', body: file,
        headers: { 'X-MRadmin': '1', 'Content-Type': file.type || 'application/octet-stream' },
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw Object.assign(new Error(body.error), { key: body.error, status: response.status });
      current.data.image = body.id;
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Leaving the page with unsaved changes asks first.
  $effect(() => {
    if (!changed) return;
    const warn = (e) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  });

  const size = (w) => (w.w <= 4 ? 's' : w.w <= 8 ? 'm' : 'l');
</script>

<Toast text={message} />
{#if error}<div class="error">{error}</div>{/if}

{#if canEdit}
  <div class="edit-bar" class:on={editing}>
    {#if editing}
      <span class="edit-title">✎ {t('widget.editing')}</span>
      <div class="pages" role="radiogroup">
        {#each ['guests', 'members'] as pg (pg)}
          <button role="radio" aria-checked={editPage === pg} class:on={editPage === pg}
            onclick={() => { editPage = pg; selected = null; }}>{t('widget.page.' + pg)}</button>
        {/each}
      </div>
      <button class="secondary" onclick={() => (catalog = true)}>+ {t('widget.insert')}</button>
      <button class="ghost" onclick={() => { if (window.confirm(t('widget.templateConfirm'))) api('GET', '/landing/edit').then((r) => (draft[editPage] = r.template[editPage])); }}>
        {t('widget.template')}
      </button>
      <span class="grow"></span>
      <button class="ghost" onclick={discard}>{t('widget.discard')}</button>
      <button disabled={busy || !changed} onclick={save}>{t('common.save')}</button>
    {:else}
      <button class="ghost" onclick={startEdit} disabled={!data}>✎ {t('widget.edit')}</button>
    {/if}
  </div>
{/if}

{#if data}
  <div class="grid" class:editing bind:this={gridEl} style="--row: {ROW}px; --gap: {GAP}px">
    {#each shown as w (w.id)}
      <div class="item size-{size(w)}" style="--w: {w.w}; --h: {w.h}"
        class:selected={editing && selected === w.id} class:over={overId === w.id}
        role="group" ondragover={(e) => dragOver(e, w.id)} ondrop={(e) => drop(e, w.id)} ondragleave={() => overId === w.id && (overId = null)}>
        <Widget widget={w} {ctx} />
        {#if editing}
          <div class="tools">
            <span class="grip" draggable="true" ondragstart={(e) => dragStart(e, w.id)} ondragend={reset} title={t('widget.move')}>⠿</span>
            <span class="kind">{t('widget.type.' + w.type)}</span>
            <button onclick={() => (selected = selected === w.id ? null : w.id)} title={t('widget.settings')}>⚙</button>
            <button onclick={() => remove(w.id)} title={t('widget.remove')}>✕</button>
          </div>
          <span class="resize" role="separator" aria-label={t('widget.resize')} onpointerdown={(e) => resizeStart(e, w)}></span>
        {/if}
      </div>
    {/each}
    {#if editing && !shown.length}
      <button class="empty" onclick={() => (catalog = true)}>+ {t('widget.insert')}</button>
    {/if}
  </div>
{:else}
  <p class="muted">{t('common.loading')}</p>
{/if}

<!-- Settings of the selected widget -->
{#if editing && current}
  <aside class="drawer">
    <div class="drawer-head">
      <strong>{t('widget.type.' + current.type)}</strong>
      <button class="x" onclick={() => (selected = null)} aria-label={t('common.close')}>✕</button>
    </div>
    <div class="row2">
      <label>{t('widget.width')}<input type="number" min="1" max="12" bind:value={current.w} /></label>
      <label>{t('widget.height')}<input type="number" min="1" max="8" bind:value={current.h} /></label>
    </div>

    {#if 'title' in current.data && current.type !== 'banner'}
      <label>{t('widget.title')}<input bind:value={current.data.title} maxlength="80" placeholder={t('landingEdit.defaultTitle')} /></label>
    {/if}
    {#if current.type === 'banner' || current.type === 'image'}
      <div class="pic">
        {#if current.data.image}<img src={'/branding/landing/' + current.data.image} alt="" />{/if}
        <label class="upload">{t(current.type === 'banner' ? 'landingEdit.background' : 'landingEdit.uploadImage')}
          <input type="file" accept="image/png,image/webp,image/svg+xml" onchange={(e) => upload(e.currentTarget)} />
        </label>
        {#if current.data.image}<button class="link" onclick={() => (current.data.image = '')}>{t('appearance.remove')}</button>{/if}
      </div>
    {/if}
    {#if current.type === 'banner' || current.type === 'address'}
      <label>{t('landingEdit.address')}<input bind:value={current.data.address} maxlength="100" placeholder="play.example.com" /></label>
    {/if}
    {#if current.type === 'banner'}
      <label class="check"><input type="checkbox" bind:checked={current.data.online} /> {t('landingEdit.showOnline')}</label>
      <p class="muted small">{t('widget.bannerHint')}</p>
    {/if}
    {#if current.type === 'news'}
      <label>{t('landingEdit.count')}<input type="number" min="1" max="8" bind:value={current.data.count} /></label>
    {/if}
    {#if current.type === 'text'}
      <label>{t('landingEdit.text')}<textarea rows="8" bind:value={current.data.text} maxlength="4000"></textarea></label>
      <span class="muted small">{t('landingEdit.markdown')}</span>
    {/if}
    {#if current.type === 'image'}
      <label>{t('landingEdit.alt')}<input bind:value={current.data.alt} maxlength="120" /></label>
      <label class="check"><input type="checkbox" checked={current.data.fit === 'contain'}
        onchange={(e) => (current.data.fit = e.currentTarget.checked ? 'contain' : 'cover')} /> {t('widget.fitContain')}</label>
    {/if}
    {#if current.type === 'links'}
      <span class="muted small">{t('landingEdit.buttonsHint')}</span>
      {#each current.data.items as item, k (k)}
        <div class="pair">
          <input bind:value={item.label} maxlength="40" placeholder={t('appearance.linkLabel')} />
          <input bind:value={item.url} maxlength="300" placeholder="https://" />
          <button class="link" onclick={() => (current.data.items = current.data.items.filter((_, n) => n !== k))}>✕</button>
        </div>
      {/each}
      {#if current.data.items.length < 8}
        <button class="secondary small-btn" onclick={() => (current.data.items = [...current.data.items, { label: '', url: '' }])}>+ {t('landingEdit.addButton')}</button>
      {/if}
    {/if}
    {#if current.type === 'discord'}
      <label>{t('widget.discordUrl')}<input bind:value={current.data.url} maxlength="300" placeholder="https://discord.gg/…" /></label>
      <label>{t('landingEdit.text')}<input bind:value={current.data.text} maxlength="300" placeholder={t('widget.discordText')} /></label>
    {/if}
    {#if current.type === 'login'}
      <label>{t('landingEdit.text')}<input bind:value={current.data.text} maxlength="300" placeholder={t('landing.signinText')} /></label>
    {/if}
  </aside>
{/if}

<!-- Catalog: insert a widget -->
{#if catalog}
  <div class="overlay" role="presentation" onclick={(e) => e.target === e.currentTarget && (catalog = false)}>
    <div class="catalog card" role="dialog" aria-modal="true" aria-label={t('widget.insert')}>
      <div class="drawer-head">
        <strong>{t('widget.insert')}</strong>
        <button class="x" onclick={() => (catalog = false)} aria-label={t('common.close')}>✕</button>
      </div>
      {#each GROUPS as [group, types] (group)}
        {#if types.some(allowed)}<span class="group">{t('widget.group.' + group)}</span>{/if}
        <div class="choices">
          {#each types.filter(allowed) as type (type)}
            <button class="choice" onclick={() => insert(type)}>
              <strong>{t('widget.type.' + type)}</strong>
              <span>{t('widget.desc.' + type)}</span>
            </button>
          {/each}
        </div>
      {/each}
    </div>
  </div>
{/if}

<style>
  .edit-bar {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .edit-bar.on {
    position: sticky;
    top: 62px;
    z-index: 20;
    padding: 10px 12px;
    border-radius: 12px;
    background: color-mix(in srgb, var(--surface) 92%, transparent);
    border: 1px solid color-mix(in srgb, var(--accent) 50%, var(--border));
    box-shadow: var(--shadow);
    backdrop-filter: blur(8px);
  }

  .edit-title {
    font-weight: 700;
    color: var(--accent);
  }

  .pages {
    display: inline-flex;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .edit-bar .pages button {
    padding: 5px 12px;
    font-size: 0.86rem;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .edit-bar .pages button.on {
    background: var(--accent);
    color: var(--accent-text);
  }

  .grow {
    flex: 1;
  }

  .edit-bar button {
    justify-content: flex-start;
    padding: 7px 14px;
  }

  .ghost {
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
  }

  /* The grid: 12 columns, rows of --row, widgets span --w columns and at least --h rows */
  .grid {
    display: grid;
    grid-template-columns: repeat(12, minmax(0, 1fr));
    grid-auto-rows: minmax(var(--row), auto);
    grid-auto-flow: dense;
    gap: var(--gap);
  }

  .item {
    position: relative;
    grid-column: span var(--w);
    grid-row: span var(--h);
    min-width: 0;
  }

  .editing .item {
    outline: 2px dashed color-mix(in srgb, var(--accent) 40%, transparent);
    outline-offset: 3px;
    border-radius: var(--radius);
  }

  .editing .item.selected {
    outline: 2px solid var(--accent);
  }

  .editing .item.over {
    outline: 3px solid var(--good);
  }

  .tools {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 5;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 3px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--surface) 94%, transparent);
    border: 1px solid var(--border);
    box-shadow: var(--shadow);
  }

  .tools button,
  .grip {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text);
    border: 0;
    font-size: 0.9rem;
  }

  .grip {
    cursor: grab;
    user-select: none;
  }

  .kind {
    padding: 0 6px;
    font-size: 0.76rem;
    color: var(--muted);
    white-space: nowrap;
  }

  .resize {
    position: absolute;
    right: -4px;
    bottom: -4px;
    z-index: 5;
    width: 18px;
    height: 18px;
    border-radius: 5px;
    background: var(--accent);
    border: 2px solid var(--surface);
    cursor: nwse-resize;
    touch-action: none;
  }

  .empty {
    grid-column: span 12;
    padding: 40px;
    border: 2px dashed var(--border);
    background: transparent;
    color: var(--muted);
    justify-content: center;
  }

  /* settings drawer */
  .drawer {
    position: fixed;
    top: 70px;
    right: 16px;
    bottom: 16px;
    z-index: 40;
    width: min(360px, calc(100vw - 32px));
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    overflow: auto;
    border-radius: var(--radius);
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: 0 10px 40px rgb(0 0 0 / 35%);
  }

  .drawer-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .x {
    padding: 4px 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .drawer label {
    display: grid;
    gap: 4px;
    margin: 0;
    font-size: 0.86rem;
    font-weight: 600;
  }

  .drawer label.check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 400;
  }

  .drawer label.check input {
    width: auto;
    margin: 0;
  }

  .drawer p {
    margin: 0;
  }

  .row2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  textarea {
    width: 100%;
    resize: vertical;
    font: inherit;
  }

  .pair {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(0, 3fr) auto;
    gap: 6px;
  }

  .pic {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .pic img {
    max-height: 60px;
    max-width: 120px;
    border-radius: 8px;
  }

  .upload {
    display: inline-flex !important;
    padding: 6px 12px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    cursor: pointer;
  }

  .upload input {
    display: none;
  }

  .link {
    background: transparent;
    color: var(--accent);
    border: 0;
    padding: 4px;
  }

  .small-btn {
    align-self: flex-start;
    justify-content: flex-start;
    padding: 5px 12px;
    font-size: 0.84rem;
  }

  /* catalog */
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 55%);
  }

  .catalog {
    width: min(720px, 100%);
    max-height: calc(100dvh - 32px);
    overflow: auto;
    display: grid;
    gap: 10px;
    padding: 18px;
  }

  .group {
    margin-top: 4px;
    font-size: 0.78rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
    font-weight: 700;
  }

  .choices {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 8px;
  }

  .choice {
    display: grid;
    justify-items: start;
    gap: 3px;
    padding: 10px 12px;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    border-radius: 10px;
  }

  .choice:hover {
    border-color: var(--accent);
  }

  .choice span {
    font-size: 0.8rem;
    color: var(--muted);
    font-weight: 400;
  }

  /* tablet: small widgets half width, others full; phone: all one below the other */
  @media (max-width: 1000px) {
    .item.size-s {
      grid-column: span 6;
    }

    .item.size-m,
    .item.size-l {
      grid-column: span 12;
    }
  }

  @media (max-width: 640px) {
    .item {
      grid-column: span 12 !important;
      grid-row: auto;
    }
  }
</style>
