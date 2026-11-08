<script>
  // Editor of the start page (Server -> Appearance -> Start page): the blocks in order - switch on/off, move, fill,
  // remove, add own ones; on the right the live preview (Landing.svelte with the draft). GET/POST /api/landing/edit
  // and /api/landing (BrandingRoutes.java, the server checks every block in Landing.java).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import Landing from '../pages/Landing.svelte';
  import Toast from './Toast.svelte';

  let enabled = $state(true);
  let blocks = $state(null);
  let saved = $state('');
  let template = [];
  let open = $state(null);
  let busy = $state(false);
  let error = $state('');
  let message = $state('');
  let adding = $state(false);

  const TYPES = ['hero', 'news', 'text', 'image', 'buttons', 'columns', 'signin'];

  async function load() {
    try {
      const data = await api('GET', '/landing/edit');
      enabled = data.enabled;
      blocks = data.blocks;
      template = data.template;
      saved = JSON.stringify({ enabled, blocks });
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  const changed = $derived(blocks !== null && JSON.stringify({ enabled, blocks }) !== saved);

  const newId = () => Math.random().toString(16).slice(2, 12);
  function empty(type) {
    const data = {
      hero: { title: '', text: '', address: '', online: true },
      news: { title: '', count: 3 },
      text: { title: '', text: '', role: '' },
      image: { title: '', image: '', alt: '', wide: false },
      buttons: { title: '', items: [{ label: '', url: '' }] },
      columns: { title: '', items: [{ title: '', text: '' }, { title: '', text: '' }] },
      signin: { title: '', text: '' },
    }[type];
    return { id: newId(), type, enabled: true, data };
  }

  function add(type) {
    const block = empty(type);
    blocks = [...blocks, block];
    open = block.id;
    adding = false;
  }

  function move(i, by) {
    const j = i + by;
    if (j < 0 || j >= blocks.length) return;
    const copy = [...blocks];
    [copy[i], copy[j]] = [copy[j], copy[i]];
    blocks = copy;
  }

  function remove(i) {
    if (!window.confirm(t('landingEdit.removeConfirm'))) return;
    blocks = blocks.filter((_, k) => k !== i);
  }

  function restore() {
    if (!window.confirm(t('landingEdit.restoreConfirm'))) return;
    blocks = structuredClone(template);
  }

  async function save() {
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/landing', { enabled, blocks });
      await load();
      message = t('landingEdit.saved');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function uploadImage(block, input) {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    busy = true;
    error = '';
    try {
      const response = await fetch('/api/landing/image', {
        method: 'POST', credentials: 'same-origin', body: file,
        headers: { 'X-MRadmin': '1', 'Content-Type': file.type || 'application/octet-stream' },
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw Object.assign(new Error(body.error), { key: body.error, status: response.status });
      block.data.image = body.id;
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const label = (b) => {
    const base = t('landingEdit.type.' + b.type);
    const role = b.type === 'text' && b.data.role ? ` (${t('landing.' + b.data.role)})` : '';
    return b.data.title ? `${base}: ${b.data.title}` : base + role;
  };
</script>

<Toast text={message} />
{#if error}<div class="error">{error}</div>{/if}

{#if blocks}
  <div class="layout">
    <section class="card editor">
      <div class="head">
        <h2>{t('landingEdit.title')}</h2>
        <label class="switch"><input type="checkbox" bind:checked={enabled} /> {t('landingEdit.enabled')}</label>
      </div>
      <p class="muted small">{t(enabled ? 'landingEdit.intro' : 'landingEdit.off')}</p>

      <div class="blocks">
        {#each blocks as b, i (b.id)}
          <div class="block" class:off={!b.enabled} class:open={open === b.id}>
            <div class="row">
              <div class="move">
                <button class="icon" disabled={i === 0} onclick={() => move(i, -1)} aria-label={t('landingEdit.up')}>▲</button>
                <button class="icon" disabled={i === blocks.length - 1} onclick={() => move(i, 1)} aria-label={t('landingEdit.down')}>▼</button>
              </div>
              <button class="name" onclick={() => (open = open === b.id ? null : b.id)}>{label(b)}</button>
              <label class="mini"><input type="checkbox" bind:checked={b.enabled} /> {t('landingEdit.show')}</label>
              <button class="icon danger" onclick={() => remove(i)} aria-label={t('landingEdit.remove')}>✕</button>
            </div>

            {#if open === b.id}
              <div class="fields">
                {#if b.type !== 'image'}
                  <label>{t('landingEdit.blockTitle')}<input bind:value={b.data.title} maxlength="80" placeholder={t('landingEdit.defaultTitle')} /></label>
                {/if}
                {#if b.type === 'hero'}
                  <label>{t('landingEdit.heroText')}<input bind:value={b.data.text} maxlength="300" placeholder={t('landingEdit.heroTextHint')} /></label>
                  <label>{t('landingEdit.address')}<input bind:value={b.data.address} maxlength="100" placeholder="play.example.com" /></label>
                  <label class="check"><input type="checkbox" bind:checked={b.data.online} /> {t('landingEdit.showOnline')}</label>
                {:else if b.type === 'news'}
                  <label>{t('landingEdit.count')}<input type="number" min="1" max="6" bind:value={b.data.count} /></label>
                {:else if b.type === 'text'}
                  <label>{t('landingEdit.text')}<textarea rows="6" bind:value={b.data.text} maxlength="4000"></textarea></label>
                  <span class="muted small">{t('landingEdit.markdown')}</span>
                {:else if b.type === 'image'}
                  <label>{t('landingEdit.blockTitle')}<input bind:value={b.data.title} maxlength="80" /></label>
                  <div class="img-row">
                    {#if b.data.image}<img src={'/branding/landing/' + b.data.image} alt="" />{/if}
                    <label class="upload">
                      {t(b.data.image ? 'landingEdit.replaceImage' : 'landingEdit.uploadImage')}
                      <input type="file" accept="image/png,image/webp,image/svg+xml" onchange={(e) => uploadImage(b, e.currentTarget)} />
                    </label>
                  </div>
                  <label>{t('landingEdit.alt')}<input bind:value={b.data.alt} maxlength="120" /></label>
                  <label class="check"><input type="checkbox" bind:checked={b.data.wide} /> {t('landingEdit.wide')}</label>
                {:else if b.type === 'buttons'}
                  <span class="muted small">{t('landingEdit.buttonsHint')}</span>
                  {#each b.data.items as item, k (k)}
                    <div class="pair">
                      <input bind:value={item.label} maxlength="40" placeholder={t('appearance.linkLabel')} />
                      <input bind:value={item.url} maxlength="300" placeholder="https://" />
                      <button class="icon" onclick={() => (b.data.items = b.data.items.filter((_, n) => n !== k))}>✕</button>
                    </div>
                  {/each}
                  {#if b.data.items.length < 6}
                    <button class="secondary small-btn" onclick={() => (b.data.items = [...b.data.items, { label: '', url: '' }])}>+ {t('landingEdit.addButton')}</button>
                  {/if}
                {:else if b.type === 'columns'}
                  {#each b.data.items as col, k (k)}
                    <div class="column">
                      <input bind:value={col.title} maxlength="80" placeholder={t('landingEdit.columnTitle', { n: k + 1 })} />
                      <textarea rows="4" bind:value={col.text} maxlength="1500"></textarea>
                      {#if b.data.items.length > 1}
                        <button class="icon" onclick={() => (b.data.items = b.data.items.filter((_, n) => n !== k))}>✕</button>
                      {/if}
                    </div>
                  {/each}
                  {#if b.data.items.length < 3}
                    <button class="secondary small-btn" onclick={() => (b.data.items = [...b.data.items, { title: '', text: '' }])}>+ {t('landingEdit.addColumn')}</button>
                  {/if}
                {:else if b.type === 'signin'}
                  <label>{t('landingEdit.text')}<input bind:value={b.data.text} maxlength="300" placeholder={t('landing.signinText')} /></label>
                {/if}
              </div>
            {/if}
          </div>
        {/each}
      </div>

      <div class="adds">
        {#if adding}
          {#each TYPES as type (type)}
            <button class="secondary small-btn" onclick={() => add(type)}>{t('landingEdit.type.' + type)}</button>
          {/each}
          <button class="link" onclick={() => (adding = false)}>{t('common.cancel')}</button>
        {:else}
          <button class="secondary" onclick={() => (adding = true)} disabled={blocks.length >= 30}>+ {t('landingEdit.add')}</button>
          <button class="link" onclick={restore}>{t('landingEdit.restore')}</button>
        {/if}
      </div>

      <div class="save">
        <button disabled={busy || !changed} onclick={save}>{t('common.save')}</button>
        {#if changed}<span class="muted small">{t('appearance.unsaved')}</span>{/if}
      </div>
    </section>

    <section class="card preview">
      <span class="muted small">{t('landingEdit.preview')}</span>
      <div class="frame">
        {#if enabled}
          <Landing preview={blocks} />
        {:else}
          <p class="muted small off-note">{t('landingEdit.off')}</p>
        {/if}
      </div>
    </section>
  </div>
{:else if !error}
  <p class="muted">{t('common.loading')}</p>
{/if}

<style>
  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
    gap: 16px;
    align-items: start;
  }

  .editor {
    display: grid;
    gap: 12px;
    padding: 20px 22px;
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }

  h2 {
    margin: 0;
    font-size: 1.15rem;
  }

  .editor > p {
    margin: -6px 0 0;
  }

  .switch,
  .mini,
  .check {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: 0.86rem;
    white-space: nowrap;
  }

  .switch input,
  .mini input,
  .check input {
    width: auto;
    margin: 0;
  }

  .blocks {
    display: grid;
    gap: 6px;
  }

  .block {
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--surface-2);
  }

  .block.off .name {
    opacity: 0.55;
  }

  .block.open {
    border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
  }

  .row {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto auto;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
  }

  .move {
    display: flex;
    gap: 2px;
  }

  .icon {
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    font-size: 0.75rem;
    background: transparent;
    color: var(--muted);
    border: 1px solid var(--border);
  }

  .icon.danger {
    color: var(--bad);
  }

  .name {
    justify-content: flex-start;
    padding: 4px 6px;
    background: transparent;
    color: var(--text);
    border: 0;
    font-weight: 600;
    text-align: left;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .fields {
    display: grid;
    gap: 10px;
    padding: 4px 12px 14px;
  }

  .fields label {
    display: grid;
    gap: 4px;
    margin: 0;
    font-size: 0.86rem;
    font-weight: 600;
  }

  .fields label.check {
    display: inline-flex;
    font-weight: 400;
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

  .column {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .column textarea {
    grid-column: 1;
  }

  .img-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .img-row img {
    max-height: 70px;
    max-width: 140px;
    border-radius: 8px;
  }

  .upload {
    display: inline-flex !important;
    padding: 6px 12px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface);
    cursor: pointer;
    font-weight: 600;
  }

  .upload input {
    display: none;
  }

  .small-btn {
    justify-self: start;
    justify-content: flex-start;
    padding: 5px 12px;
    font-size: 0.84rem;
  }

  .adds {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  .link {
    background: transparent;
    color: var(--accent);
    border: 0;
    padding: 4px 6px;
  }

  .save {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .preview {
    position: sticky;
    top: 70px;
    display: grid;
    gap: 8px;
    padding: 12px;
  }

  .frame {
    max-height: calc(100vh - 140px);
    overflow: auto;
    border-radius: 12px;
    border: 1px solid var(--border);
  }

  .off-note {
    padding: 30px;
    text-align: center;
  }

  @media (max-width: 1000px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .preview {
      position: static;
    }
  }
</style>
