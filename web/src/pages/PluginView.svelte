<script>
  // The page of one installed MRtechnology plugin: version, update, what is new. Data from GET /api/versions
  // (VersionRoutes.java).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { markdown } from '../lib/markdown.js';
  import { ICONS } from '../lib/icons.js';
  import { pluginIcon, pluginDescription, pluginState, pluginError } from '../lib/plugins.js';
  import Toast from '../components/Toast.svelte';
  import ConfigEditor from '../components/ConfigEditor.svelte';

  let { name, tab = null } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      data = await api('GET', '/versions');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  const p = $derived(data?.plugins.find((x) => x.name === name) ?? null);
  $effect(() => {
    load();
  });
  $effect(() => {
    const timer = setInterval(load, p?.downloading || data?.checking ? 2000 : 30000);
    return () => clearInterval(timer);
  });

  async function update() {
    if (!confirm(t('versions.downloadConfirm', { name: p.name, version: p.latest.version }))) return;
    busy = true;
    error = '';
    try {
      await api('POST', `/versions/${encodeURIComponent(p.name)}/download`, {});
      message = t('versions.downloadStarted', { name: p.name });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const st = $derived(p ? pluginState(p) : null);
  const showConfig = $derived(tab === 'config' && !!p?.config);
</script>

<div class="page">
  <a class="back" href="/plugins" onclick={(e) => { e.preventDefault(); go('/plugins'); }}>← {t('plugins.back')}</a>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if p}
    <header class="head {st.tone}">
      <div class="intro">
        <span class="icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
            stroke-linejoin="round"><path d={ICONS[pluginIcon(p.name)]} /></svg>
        </span>
        <div>
          <h1>{p.name} <span class="ver mono">{p.installed ?? ''}</span></h1>
          <p class="muted">{pluginDescription(p.name)}</p>
        </div>
      </div>
      <span class="state"><span class="dot"></span>{st.text}</span>
    </header>

    {#if p.config}
      <div class="tabs" role="tablist">
        <a role="tab" href={'/plugins/' + p.name} aria-selected={!showConfig} class:on={!showConfig}
          onclick={(e) => { e.preventDefault(); go('/plugins/' + p.name, true); }}>{t('plugins.tab.overview')}</a>
        <a role="tab" href={'/plugins/' + p.name + '/config'} aria-selected={showConfig} class:on={showConfig}
          onclick={(e) => { e.preventDefault(); go('/plugins/' + p.name + '/config', true); }}>{t('plugins.tab.config')}</a>
      </div>
    {/if}

    {#if showConfig}
      <ConfigEditor plugin={p.name} />
    {:else}
      <section class="box">
        <h2>{t('plugins.tab.version')}</h2>
        <div class="facts">
          <div><span>{t('plugins.installed')}</span>{p.installed ?? '–'}</div>
          <div><span>{t('versions.latest')}</span>{p.latest ? p.latest.version : '–'}{#if p.latest?.prerelease} <em class="pre">{t('plugins.prerelease')}</em>{/if}</div>
          <div><span>{t('versions.published')}</span>{p.latest?.published ? dateTime(p.latest.published) : '–'}</div>
          <div><span>Minecraft</span>{p.minecraft}</div>
          {#if p.mod}<div><span>{t('versions.mod')}</span>{p.mod.version}</div>{/if}
          {#if p.squaremap && (p.squaremap.version || p.squaremap.staged)}
            <div><span>{t('versions.squaremap')}</span>{p.squaremap.version ?? '–'}{#if p.squaremap.staged && p.squaremap.staged !== p.squaremap.version} → {p.squaremap.staged}{/if}</div>
          {/if}
        </div>
  
        {#if pluginError(p)}<div class="error small">{pluginError(p)}</div>{/if}
  
        <div class="row">
          {#if p.update && data.may.install}
            {#if p.checksum}
              <button disabled={busy || p.downloading} onclick={update}>⬇ {t('versions.download', { version: p.latest.version })}</button>
            {:else}
              <span class="muted small">{t('versions.noChecksum')}</span>
            {/if}
          {/if}
          {#if p.staged}<span class="muted small">{t('plugins.stagedHint')}</span>{/if}
          <span class="links">
            <a href={p.website} target="_blank" rel="noopener noreferrer">{t('versions.website')} ↗</a>
            {#if p.latest}<a href={p.latest.page} target="_blank" rel="noopener noreferrer">GitHub ↗</a>{/if}
          </span>
        </div>
  
        {#if p.latest?.notes}
          <details open={p.update || !!p.staged}>
            <summary>{t('versions.notes', { version: p.latest.version })}</summary>
            <div class="notes">{@html markdown(p.latest.notes)}</div>
          </details>
        {/if}
      </section>
    {/if}
  {:else if data}
    <p class="muted">{t('plugins.notFound')}</p>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    color: var(--accent);
    font-size: 0.9rem;
    text-decoration: none;
  }

  .good {
    --tn: var(--good);
  }

  .warn {
    --tn: var(--warn);
  }

  .info {
    --tn: var(--accent);
  }

  .bad {
    --tn: var(--bad);
  }

  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
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
    width: 60px;
    height: 60px;
    flex-shrink: 0;
    border-radius: 15px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.5rem;
  }

  .ver {
    font-size: 1rem;
    font-weight: 600;
    color: var(--muted);
  }

  .intro p {
    margin: 3px 0 0;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .small {
    font-size: 0.84rem;
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.86rem;
    font-weight: 700;
    color: var(--tn, var(--muted));
    background: color-mix(in srgb, var(--tn, var(--muted)) 12%, transparent);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--tn, var(--muted));
  }

  .tabs {
    display: flex;
    gap: 4px;
    padding: 4px;
    justify-self: start;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .tabs a {
    padding: 7px 16px;
    border-radius: 9px;
    color: var(--muted);
    font-weight: 600;
    font-size: 0.92rem;
    text-decoration: none;
  }

  .tabs a.on {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .box {
    display: grid;
    gap: 14px;
    padding: 18px 20px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 150px), 1fr));
  }

  .facts > div {
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 10px;
    padding: 8px 12px;
    font-weight: 700;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .facts span {
    display: block;
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 600;
  }

  .pre {
    font-style: normal;
    font-size: 0.7rem;
    padding: 1px 6px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 16px;
  }

  .links {
    display: flex;
    gap: 14px;
    margin-left: auto;
    font-size: 0.88rem;
  }

  .links a {
    color: var(--accent);
  }

  summary {
    cursor: pointer;
    color: var(--accent);
    font-size: 0.9rem;
    font-weight: 600;
  }

  .notes {
    max-height: 420px;
    overflow: auto;
    font-size: 0.9rem;
    margin-top: 8px;
    padding: 10px 14px;
    background: var(--surface-2);
    border-radius: 10px;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .links {
      margin-left: 0;
    }
  }
</style>
