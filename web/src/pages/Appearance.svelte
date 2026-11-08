<script>
  // Server -> Appearance (mradmin.appearance): name, tagline, accent colour, footer and links, logo and browser icon -
  // with a live preview (sign-in page / start page, light / dark). Saving writes config.yml (section branding) and
  // MRadmin reloads; pictures go to plugins/MRadmin/branding/ (BrandingRoutes.java).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { brand, load as loadBrand } from '../lib/branding.svelte.js';
  import Toast from '../components/Toast.svelte';
  import LandingEditor from '../components/LandingEditor.svelte';

  // general = name, colour, pictures, footer; landing = the start page (blocks)
  let part = $state('general');

  let form = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let view = $state('signin');
  let dark = $state(matchMedia('(prefers-color-scheme: dark)').matches);
  let logoInput = $state();
  let iconInput = $state();

  async function load() {
    await loadBrand();
    const links = [...brand.links];
    while (links.length < 4) links.push({ label: '', url: '' });
    form = { name: brand.ownName, tagline: brand.tagline, accent: brand.accent || '#2f6fed', footer: brand.footer, links };
  }
  load();

  // Text colour on the accent, like the server picks it (Branding.textOn).
  function textOn(hex) {
    const n = parseInt(hex.slice(1), 16);
    const l = (0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
    return l > 0.62 ? '#111111' : '#ffffff';
  }
  const validColor = $derived(!!form && /^#[0-9a-fA-F]{6}$/.test(form.accent));
  const accent = $derived(validColor ? form.accent : '#2f6fed');
  const shownName = $derived(form?.name.trim() || brand.name);
  const changed = $derived(!!form && (form.name !== brand.ownName || form.tagline !== brand.tagline || form.accent !== brand.accent
    || form.footer !== brand.footer || JSON.stringify(form.links.filter((l) => l.label || l.url)) !== JSON.stringify(brand.links)));

  async function save() {
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/branding', { ...form, links: form.links.filter((l) => l.label.trim() || l.url.trim()) });
      await load();
      message = t('appearance.saved');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function upload(kind, input) {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 512 * 1024) {
      error = t('error.image-too-large');
      return;
    }
    busy = true;
    error = '';
    try {
      const response = await fetch('/api/branding/' + kind, {
        method: 'POST', credentials: 'same-origin', body: file,
        headers: { 'X-MRadmin': '1', 'Content-Type': file.type || 'application/octet-stream' },
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw Object.assign(new Error(body.error), { key: body.error, status: response.status });
      }
      await loadBrand();
      message = t('appearance.uploaded');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function removeImage(kind) {
    busy = true;
    try {
      await api('DELETE', '/branding/' + kind);
      await loadBrand();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const logoSrc = $derived(brand.logo ? `/branding/logo?v=${brand.logo}` : '/icon.svg');
  const iconSrc = $derived(brand.icon ? `/branding/icon?v=${brand.icon}` : brand.logo ? `/branding/logo?v=${brand.logo}` : '/icon.svg');
</script>

<div class="page">
  <div class="parts" role="tablist">
    <button role="tab" class:on={part === 'general'} aria-selected={part === 'general'} onclick={() => (part = 'general')}>{t('appearance.partGeneral')}</button>
    <button role="tab" class:on={part === 'landing'} aria-selected={part === 'landing'} onclick={() => (part = 'landing')}>{t('appearance.partLanding')}</button>
  </div>

  {#if part === 'landing'}
    <LandingEditor />
  {:else}
  <Toast text={message} />
  {#if error}<div class="error">{error}</div>{/if}

  {#if form}
    <div class="layout">
      <!-- The settings -->
      <section class="card settings">
        <h2>{t('appearance.title')}</h2>
        <p class="muted small">{t('appearance.intro')}</p>

        <label class="field">
          <span>{t('appearance.name')}</span>
          <input bind:value={form.name} maxlength="40" placeholder={brand.name} />
        </label>
        <label class="field">
          <span>{t('appearance.tagline')}</span>
          <input bind:value={form.tagline} maxlength="120" placeholder={t('signin.subtitle')} />
        </label>
        <div class="field">
          <span>{t('appearance.accent')}</span>
          <div class="color">
            <input type="color" bind:value={form.accent} aria-label={t('appearance.accent')} />
            <input class="mono" bind:value={form.accent} maxlength="7" />
            {#if !validColor}<span class="bad-text small">{t('appearance.colorInvalid')}</span>{/if}
          </div>
        </div>

        <div class="images">
          {#each [['logo', logoSrc, 'logoInput'], ['icon', iconSrc, 'iconInput']] as [kind, src] (kind)}
            <div class="image">
              <img {src} alt="" />
              <div>
                <strong>{t('appearance.' + kind)}</strong>
                <span class="muted small">{t('appearance.' + kind + 'Hint')}</span>
                <div class="acts">
                  <button class="secondary" disabled={busy} onclick={() => (kind === 'logo' ? logoInput : iconInput).click()}>
                    {t('appearance.upload')}
                  </button>
                  {#if brand[kind]}
                    <button class="ghost" disabled={busy} onclick={() => removeImage(kind)}>{t('appearance.remove')}</button>
                  {/if}
                </div>
              </div>
            </div>
          {/each}
          <input class="hidden" type="file" accept="image/png,image/webp,image/svg+xml" bind:this={logoInput}
            onchange={() => upload('logo', logoInput)} />
          <input class="hidden" type="file" accept="image/png,image/webp,image/svg+xml" bind:this={iconInput}
            onchange={() => upload('icon', iconInput)} />
        </div>

        <label class="field">
          <span>{t('appearance.footer')}</span>
          <input bind:value={form.footer} maxlength="300" />
        </label>
        <div class="field">
          <span>{t('appearance.links')}</span>
          {#each form.links as link, i (i)}
            <div class="link">
              <input bind:value={link.label} maxlength="40" placeholder={t('appearance.linkLabel')} />
              <input bind:value={link.url} maxlength="300" placeholder="https://" />
            </div>
          {/each}
        </div>

        <div class="save">
          <button disabled={busy || !changed || !validColor} onclick={save}>{t('common.save')}</button>
          {#if changed}<span class="muted small">{t('appearance.unsaved')}</span>{/if}
        </div>
      </section>

      <!-- The preview -->
      <section class="card preview-card">
        <div class="preview-bar">
          <div class="seg">
            <button class:on={view === 'signin'} onclick={() => (view = 'signin')}>{t('appearance.previewSignIn')}</button>
            <button class:on={view === 'start'} onclick={() => (view = 'start')}>{t('appearance.previewStart')}</button>
          </div>
          <div class="seg">
            <button class:on={!dark} onclick={() => (dark = false)}>☀</button>
            <button class:on={dark} onclick={() => (dark = true)}>☾</button>
          </div>
        </div>
        <div class="preview" class:dark style="--p-accent: {accent}; --p-on: {textOn(accent)}">
          {#if view === 'signin'}
            <div class="p-signin">
              <div class="p-box">
                <div class="p-brand">
                  <img src={logoSrc} alt="" />
                  <div>
                    <b>{shownName}</b>
                    <small>{form.tagline || t('signin.subtitle')}</small>
                  </div>
                </div>
                <div class="p-input">123456</div>
                <div class="p-button">{t('signin.submit')}</div>
                <div class="p-alt">{t('signin.withPassword')}</div>
              </div>
            </div>
          {:else}
            <div class="p-start">
              <div class="p-top">
                <img src={iconSrc} alt="" /><b>{shownName}</b>
                <span class="p-nav on">{t('self.nav.home')}</span><span class="p-nav">{t('nav.game')}</span><span class="p-nav">{t('self.nav.map')}</span>
              </div>
              <div class="p-content">
                <div class="p-hero">{t('appearance.previewHello')}</div>
                <div class="p-tiles"><span></span><span></span><span></span></div>
                <div class="p-button small">{t('appearance.previewButton')}</div>
              </div>
            </div>
          {/if}
          <div class="p-footer">
            {#if form.footer}<span>{form.footer}</span>{/if}
            {#each form.links.filter((l) => l.label && l.url) as l (l.url)}<span class="p-link">{l.label}</span>{/each}
          </div>
        </div>
      </section>
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 14px;
  }

  .parts {
    display: inline-flex;
    justify-self: start;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .parts button {
    justify-content: flex-start;
    padding: 6px 14px;
    font-size: 0.88rem;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .parts button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 16px;
    align-items: start;
  }

  .settings {
    display: grid;
    gap: 14px;
    padding: 20px 22px;
  }

  h2 {
    margin: 0;
    font-size: 1.15rem;
  }

  .settings > p {
    margin: -8px 0 0;
  }

  .field {
    display: grid;
    gap: 6px;
    margin: 0;
  }

  .field > span {
    font-weight: 600;
    font-size: 0.88rem;
  }

  .color {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .color input[type='color'] {
    width: 44px;
    height: 36px;
    padding: 2px;
    flex: none;
  }

  .color .mono {
    width: 110px;
  }

  .images {
    display: grid;
    gap: 10px;
  }

  .image {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 10px;
    border-radius: 12px;
    background: var(--surface-2);
  }

  .image img {
    width: 52px;
    height: 52px;
    object-fit: contain;
    border-radius: 8px;
    background: var(--surface);
  }

  .image > div {
    display: grid;
    gap: 4px;
  }

  .acts {
    display: flex;
    gap: 6px;
  }

  .acts button {
    justify-content: flex-start;
    padding: 5px 12px;
    font-size: 0.84rem;
  }

  .ghost {
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
  }

  .hidden {
    display: none;
  }

  .link {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
    gap: 6px;
  }

  .save {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .preview-card {
    position: sticky;
    top: 70px;
    display: grid;
    gap: 12px;
    padding: 14px;
  }

  .preview-bar {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  .seg {
    display: inline-flex;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg button {
    justify-content: flex-start;
    padding: 4px 10px;
    font-size: 0.82rem;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .seg button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  /* The preview: its own light / dark colours, the chosen accent */
  .preview {
    --p-bg: #f5f6f8;
    --p-surface: #ffffff;
    --p-text: #1f2937;
    --p-muted: #6b7280;
    --p-border: #e5e7eb;
    display: grid;
    border-radius: 12px;
    overflow: hidden;
    border: 1px solid var(--border);
    background: var(--p-bg);
    color: var(--p-text);
    font-size: 0.86rem;
    min-height: 360px;
    grid-template-rows: 1fr auto;
  }

  .preview.dark {
    --p-bg: #0f1115;
    --p-surface: #181b22;
    --p-text: #e5e7eb;
    --p-muted: #9ca3af;
    --p-border: #2a2f3a;
  }

  .p-signin {
    display: grid;
    place-items: center;
    padding: 24px;
    background: radial-gradient(80% 60% at 50% 0%, color-mix(in srgb, var(--p-accent) 18%, transparent), transparent 60%);
  }

  .p-box {
    width: 100%;
    max-width: 260px;
    display: grid;
    gap: 10px;
    padding: 16px;
    border-radius: 12px;
    background: var(--p-surface);
    border: 1px solid var(--p-border);
    border-top: 3px solid var(--p-accent);
  }

  .p-brand {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .p-brand img {
    height: 32px;
    max-width: 80px;
    object-fit: contain;
  }

  .p-brand div {
    display: grid;
  }

  .p-brand small {
    color: var(--p-muted);
  }

  .p-input {
    padding: 8px;
    border-radius: 8px;
    border: 1px solid var(--p-border);
    color: var(--p-muted);
    text-align: center;
    letter-spacing: 0.3em;
  }

  .p-button {
    padding: 8px;
    border-radius: 8px;
    background: var(--p-accent);
    color: var(--p-on);
    text-align: center;
    font-weight: 600;
  }

  .p-button.small {
    justify-self: start;
    padding: 6px 14px;
  }

  .p-alt {
    padding: 7px;
    border-radius: 8px;
    border: 1px solid var(--p-border);
    text-align: center;
  }

  .p-start {
    display: grid;
    grid-template-rows: auto 1fr;
  }

  .p-top {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: var(--p-surface);
    border-bottom: 1px solid var(--p-border);
  }

  .p-top img {
    width: 20px;
    height: 20px;
    object-fit: contain;
  }

  .p-top b {
    margin-right: 6px;
  }

  .p-nav {
    padding: 3px 8px;
    border-radius: 6px;
    color: var(--p-muted);
  }

  .p-nav.on {
    color: var(--p-text);
    box-shadow: inset 0 -2px 0 var(--p-accent);
  }

  .p-content {
    display: grid;
    align-content: start;
    gap: 12px;
    padding: 14px;
  }

  .p-hero {
    padding: 16px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--p-accent) 18%, var(--p-surface));
    font-weight: 600;
  }

  .p-tiles {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .p-tiles span {
    height: 56px;
    border-radius: 10px;
    background: var(--p-surface);
    border: 1px solid var(--p-border);
    border-top: 3px solid var(--p-accent);
  }

  .p-footer {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 4px 12px;
    padding: 10px;
    color: var(--p-muted);
    font-size: 0.78rem;
  }

  .p-link {
    color: var(--p-text);
    text-decoration: underline;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .preview-card {
      position: static;
    }
  }
</style>
