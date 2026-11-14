<script>
  // Window to choose where a plugin that is not from MRtechnology comes from: a Modrinth project (search) or a
  // GitHub repository. Shows before saving which version MRadmin would compare with (the newest one for this
  // server's Minecraft version). "Automatic" goes back to the lookup by the JAR's fingerprint, "Do not watch"
  // switches the lookup off for this plugin.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago } from '../lib/format.js';

  let { plugin, server, minecraft = '', onclose, ondone } = $props();

  let dialog = $state();
  let tab = $state(plugin.source?.type === 'github' ? 'github' : 'modrinth');
  let query = $state(plugin.source?.type === 'modrinth' ? plugin.source.title ?? plugin.name : plugin.name);
  let hits = $state(null);
  let searching = $state(false);
  let picked = $state(plugin.source?.type === 'modrinth' ? plugin.source.id : null);
  let repo = $state(plugin.source?.type === 'github' ? plugin.source.id : '');
  let preview = $state(null);
  let previewing = $state(false);
  let busy = $state(false);
  let error = $state('');

  $effect(() => {
    dialog?.showModal();
  });

  function close() {
    dialog?.close();
  }

  // Search as you type (a short pause first, so Modrinth is not asked for every letter).
  $effect(() => {
    if (tab !== 'modrinth') return;
    const q = query.trim();
    if (!q) {
      hits = [];
      return;
    }
    const timer = setTimeout(async () => {
      searching = true;
      try {
        hits = (await api('GET', `/plugins/foreign/search?server=${server}&q=${encodeURIComponent(q)}`)).hits;
        error = '';
      } catch (e) {
        error = errorText(e);
      } finally {
        searching = false;
      }
    }, 350);
    return () => clearTimeout(timer);
  });

  async function look(type, source) {
    preview = null;
    if (!source) return;
    previewing = true;
    try {
      preview = await api('GET', `/plugins/foreign/preview?server=${server}&type=${type}&source=${encodeURIComponent(source)}`);
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      previewing = false;
    }
  }

  function pick(hit) {
    picked = hit.id;
    look('modrinth', hit.id);
  }

  // A stored source shows its preview right away.
  $effect(() => {
    if (plugin.source?.type === 'modrinth' || plugin.source?.type === 'github') look(plugin.source.type, plugin.source.id);
  });

  function switchTab(next) {
    if (next === tab) return;
    tab = next;
    preview = null;
    error = '';
    if (next === 'modrinth' && picked) look('modrinth', picked);
    if (next === 'github' && repo.trim()) look('github', repo.trim());
  }

  async function save(type, source = '') {
    busy = true;
    error = '';
    try {
      await api('POST', '/plugins/foreign', { server, name: plugin.name, type, source });
      ondone?.();
      close();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const canSave = $derived(!busy && preview && (tab === 'modrinth' ? !!picked : !!repo.trim()));
  const fmt = (n) => (n >= 1_000_000 ? (n / 1_000_000).toFixed(1) + ' M' : n >= 1000 ? Math.round(n / 1000) + ' k' : String(n));
</script>

<dialog bind:this={dialog} onclose={() => onclose?.()} aria-labelledby="foreign-title">
  <div class="head">
    <div>
      <h3 id="foreign-title">{t('plugins.src.title', { name: plugin.name })}</h3>
      <p class="muted small">{t('plugins.src.hint', { mc: minecraft })}</p>
    </div>
    <button type="button" class="x" onclick={close} aria-label={t('common.close')}>✕</button>
  </div>

  {#if plugin.source && !plugin.manual && plugin.source.type === 'modrinth'}
    <p class="auto small">{t('plugins.src.autoFound', { title: plugin.source.title })}</p>
  {/if}

  <div class="tabs" role="tablist">
    <button role="tab" class="modrinth" class:on={tab === 'modrinth'} aria-selected={tab === 'modrinth'} onclick={() => switchTab('modrinth')}>
      <span class="logo" aria-hidden="true"></span>Modrinth
    </button>
    <button role="tab" class="github" class:on={tab === 'github'} aria-selected={tab === 'github'} onclick={() => switchTab('github')}>
      <span class="logo" aria-hidden="true"></span>GitHub
    </button>
  </div>

  {#if tab === 'modrinth'}
    <input type="search" bind:value={query} maxlength="80" placeholder={t('plugins.src.search')} aria-label={t('plugins.src.search')} />
    <ul class="hits">
      {#if searching && !hits?.length}
        <li class="muted small pad">{t('common.loading')}</li>
      {:else if hits && !hits.length && query.trim()}
        <li class="muted small pad">{t('plugins.src.noHits')}</li>
      {/if}
      {#each hits ?? [] as h (h.id)}
        <li>
          <button class="hit" class:on={picked === h.id} class:unfit={!h.fits} onclick={() => pick(h)}>
            {#if h.icon}<img src={h.icon} alt="" width="32" height="32" loading="lazy" />{:else}<span class="noicon"></span>{/if}
            <span class="h-body">
              <span class="h-title"><strong>{h.title}</strong> <span class="muted small">{t('plugins.src.by', { author: h.author })}</span></span>
              <span class="muted small h-desc">{h.description}</span>
            </span>
            <span class="h-side">
              <span class="fit" class:no={!h.fits}>{h.fits ? t('plugins.src.fits', { mc: minecraft }) : t('plugins.src.notFits', { mc: minecraft })}</span>
              <span class="muted small">↓ {fmt(h.downloads)}</span>
            </span>
          </button>
        </li>
      {/each}
    </ul>
  {:else}
    <form class="repo" onsubmit={(e) => { e.preventDefault(); look('github', repo.trim()); }}>
      <input type="text" bind:value={repo} maxlength="200" placeholder="owner/repo · https://github.com/…" aria-label="GitHub" />
      <button class="secondary" disabled={!repo.trim() || previewing}>{t('plugins.src.check')}</button>
    </form>
    <p class="muted small">{t('plugins.src.githubHint', { mc: minecraft })}</p>
  {/if}

  <div class="preview">
    {#if previewing}
      <span class="muted small">{t('plugins.foreignChecking')}</span>
    {:else if preview?.state === 'found'}
      {@const l = preview.latest}
      <span class="label">{t('plugins.src.newest', { mc: minecraft })}</span>
      <span class="p-line">
        <a class="mono strong" href={l.url} target="_blank" rel="noopener noreferrer">{l.version} ↗</a>
        {#if l.channel && l.channel !== 'release'}<span class="chan">{l.channel}</span>{/if}
        {#if l.published}<span class="muted small">{ago(l.published)}</span>{/if}
        <span class="muted small">· {t('plugins.src.installed', { version: plugin.version })}</span>
      </span>
      {#if l.mcUnknown}<span class="small warn-text">{t('plugins.src.mcUnknown', { mc: minecraft })}</span>{/if}
    {:else if preview?.state === 'no-version'}
      <span class="small warn-text">{t('plugins.src.noVersion', { mc: minecraft })}</span>
    {:else if preview?.state === 'unknown'}
      <span class="small bad-text">{t(preview.error === 'not-found' ? 'plugins.src.notFound' : preview.error === 'no-release' ? 'plugins.foreignNoRelease' : 'plugins.foreignFailed')}</span>
    {:else}
      <span class="muted small">{t(tab === 'modrinth' ? 'plugins.src.pickHint' : 'plugins.src.repoHint')}</span>
    {/if}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  <div class="actions">
    <div class="left">
      {#if plugin.manual}
        <button class="secondary" disabled={busy} onclick={() => save('auto')}>{t('plugins.src.auto')}</button>
      {/if}
      {#if plugin.source?.type !== 'off'}
        <button class="secondary" disabled={busy} onclick={() => save('off')}>{t('plugins.src.off')}</button>
      {/if}
    </div>
    <button disabled={!canSave} onclick={() => save(tab, tab === 'modrinth' ? picked : repo.trim())}>{t('common.save')}</button>
  </div>
</dialog>

<style>
  dialog {
    width: min(560px, calc(100vw - 32px));
    max-height: calc(100dvh - 32px);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    color: var(--text);
    padding: 18px 20px 20px;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 50%);
  }

  .head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  h3 {
    margin: 0;
  }

  .head p {
    margin: 4px 0 0;
  }

  .x {
    background: transparent;
    color: var(--muted);
    border: 0;
    padding: 4px 8px;
  }

  .auto {
    margin: 12px 0 0;
    padding: 8px 10px;
    border-radius: 8px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, transparent);
  }

  .tabs {
    display: flex;
    gap: 18px;
    margin: 14px 0 12px;
    border-bottom: 1px solid var(--border);
  }

  .tabs button {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 8px 2px;
    border: 0;
    border-bottom: 2px solid transparent;
    border-radius: 0;
    margin-bottom: -1px;
    background: transparent;
    color: var(--muted);
    font-weight: 600;
  }

  .tabs button.on {
    color: var(--text);
    border-bottom-color: var(--accent);
  }

  .logo {
    width: 10px;
    height: 10px;
    border-radius: 3px;
    background: var(--muted);
  }

  .modrinth .logo {
    background: #1bd96a;
  }

  .github .logo {
    border-radius: 50%;
    background: var(--text);
  }

  input[type='search'],
  .repo input {
    width: 100%;
  }

  .hits {
    list-style: none;
    margin: 8px 0 0;
    padding: 0;
    display: grid;
    gap: 4px;
    max-height: 290px;
    overflow-y: auto;
  }

  .pad {
    padding: 8px 4px;
  }

  .hit {
    display: grid;
    grid-template-columns: 32px 1fr auto;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 10px;
    border: 1px solid transparent;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    text-align: left;
    justify-content: flex-start;
  }

  .hit:hover {
    background: var(--surface-2);
  }

  .hit.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 8%, transparent);
  }

  .hit.unfit {
    opacity: 0.6;
  }

  .hit img,
  .noicon {
    width: 32px;
    height: 32px;
    border-radius: 7px;
    object-fit: cover;
    background: var(--surface-2);
  }

  .h-body {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .h-title,
  .h-desc {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .h-side {
    display: grid;
    justify-items: end;
    gap: 2px;
  }

  .fit {
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
    white-space: nowrap;
  }

  .fit.no {
    color: var(--muted);
    background: var(--surface-2);
  }

  .repo {
    display: flex;
    gap: 8px;
  }

  .repo + p {
    margin: 6px 0 0;
  }

  .preview {
    display: grid;
    gap: 4px;
    margin-top: 14px;
    padding: 10px 12px;
    border-radius: 8px;
    background: var(--surface-2);
    min-height: 44px;
    align-content: center;
  }

  .label {
    font-size: 0.75rem;
    color: var(--muted);
  }

  .p-line {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px;
  }

  .chan {
    padding: 0 7px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    margin-top: 16px;
  }

  .left {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .error {
    margin-top: 10px;
  }
</style>
