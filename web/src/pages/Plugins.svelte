<script>
  // Page Plugins: every MRtechnology plugin - those on this server (open their page, load updates) and the others
  // (install with one click, loaded by Paper on the next start). Restart with countdown when something waits for it.
  // Data from VersionRoutes.java.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import { pluginIcon, pluginDescription, pluginState, pluginError, clock } from '../lib/plugins.js';
  import Toast from '../components/Toast.svelte';

  // embedded: inside the tab Plugins of the page Server - without its own title.
  let { embedded = false } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let now = $state(Date.now());

  async function load() {
    try {
      data = await api('GET', '/versions');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Faster while something is going on (check, download, countdown), otherwise every 30 seconds.
  const active = $derived(!!data && (data.checking || data.restart.endsAt > 0 || data.plugins.some((p) => p.downloading)));
  $effect(() => {
    load();
  });
  $effect(() => {
    const timer = setInterval(load, active ? 2000 : 30000);
    return () => clearInterval(timer);
  });
  $effect(() => {
    const timer = setInterval(() => (now = Date.now()), 1000);
    return () => clearInterval(timer);
  });

  async function act(path, body, done, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', path, body);
      if (done) message = done;
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const update = (p) =>
    act(`/versions/${encodeURIComponent(p.name)}/download`, {}, t('versions.downloadStarted', { name: p.name }),
      t('versions.downloadConfirm', { name: p.name, version: p.latest.version }));
  const install = (p) =>
    act(`/versions/${encodeURIComponent(p.name)}/install`, {}, t('plugins.installStarted', { name: p.name }),
      t('plugins.installConfirm', { name: p.name, version: p.latest.version }));

  const delayText = (ms) => (ms === 0 ? t('versions.restartNow') : t('versions.restartIn', { time: duration(ms) }));
  const byName = (a, b) => a.name.localeCompare(b.name);
  const installed = $derived(data ? data.plugins.filter((p) => p.installed).sort(byName) : []);
  const others = $derived(data ? data.plugins.filter((p) => !p.installed).sort(byName) : []);
  const waiting = $derived(data ? data.plugins.filter((p) => p.staged || (p.pending && !p.notLoaded)) : []);
  const updates = $derived(installed.filter((p) => p.update && !p.staged));
  const left = $derived(data && data.restart.endsAt > 0 ? Math.max(0, data.restart.endsAt - now) : 0);
</script>

{#snippet card(p)}
  {@const st = pluginState(p)}
  {@const err = pluginError(p)}
  <li class="card {st.tone}" class:missing={!p.installed}>
    <div class="c-head">
      <span class="c-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS[pluginIcon(p.name)]} /></svg>
      </span>
      <div class="c-title">
        {#if p.installed}
          <a class="c-link" href={'/plugins/' + p.name} onclick={(e) => { e.preventDefault(); go('/plugins/' + p.name); }}>{p.name}</a>
        {:else}
          <strong>{p.name}</strong>
        {/if}
        <span class="c-version mono">
          {#if p.installed}
            {p.installed}
            {#if p.update || p.staged}<span class="arrow">→</span><span class="new">{p.staged || p.latest.version}</span>{/if}
          {:else if p.latest}
            {t('plugins.newest')} {p.latest.version}{#if p.latest.prerelease}<span class="pre">{t('plugins.prerelease')}</span>{/if}
          {:else}
            –
          {/if}
        </span>
      </div>
      {#if p.installed}
        <svg class="chevron" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
      {/if}
    </div>

    <p class="c-desc">{pluginDescription(p.name)}</p>

    <div class="c-foot">
      <span class="state"><span class="dot"></span>{st.text}</span>
      {#if p.installed && p.update && p.checksum && data.may.install}
        <button class="act" disabled={busy || p.downloading} onclick={() => update(p)}>⬇ {t('plugins.update')}</button>
      {:else if p.installable && data.may.install}
        <button class="act" disabled={busy || p.downloading} onclick={() => install(p)}>{t('plugins.install')}</button>
      {:else if !p.installed}
        <a class="web" href={p.website} target="_blank" rel="noopener noreferrer">{t('versions.website')} ↗</a>
      {/if}
    </div>
    {#if p.notLoaded}<p class="c-note bad-text">{t('plugins.notLoadedHint')}</p>{/if}
    {#if err}<p class="c-note bad-text">{err}</p>{/if}
  </li>
{/snippet}

<div class="page">
  <header class="head" class:bare={embedded}>
    {#if !embedded}
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.box} /></svg>
      </span>
      <div>
        <h1>{t('plugins.title')}</h1>
        <p class="muted">{t('plugins.subtitle')}</p>
      </div>
    </div>
    {/if}
    {#if data}
      <div class="check">
        <span class="check-state" class:busy={data.checking}>
          <span class="dot"></span>
          {data.checking ? t('versions.checking') : data.checkedAt ? t('versions.checkedAt', { time: ago(data.checkedAt) }) : t('versions.neverChecked')}
        </span>
        <span class="chip">{t('versions.auto.' + data.auto)}</span>
        {#if data.may.install}
          <button class="ghost" disabled={busy || data.checking} onclick={() => act('/versions/check', {}, t('versions.checkStarted'))}>
            ⟳ {t('versions.check')}
          </button>
        {/if}
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if data}
    <div class="numbers">
      <div class="num"><b>{installed.length}</b><span>{t('plugins.installedOf', { total: data.plugins.length })}</span></div>
      <div class="num" class:warn={updates.length}><b>{updates.length}</b><span>{t('versions.updates')}</span></div>
      <div class="num" class:info={waiting.length}><b>{waiting.length}</b><span>{t('versions.waiting')}</span></div>
    </div>

    <!-- Restart: a running countdown, or - only when something waits for the next start - the restart buttons -->
    {#if data.restart.endsAt > 0}
      <section class="restart bad">
        <div class="r-text">
          <span class="status">{t('versions.restartTitle')}</span>
          <strong>{t('versions.restartBy', { name: data.restart.by })}</strong>
        </div>
        <span class="clock mono">{left > 0 ? clock(left) : t('versions.restartNow')}</span>
        {#if data.may.restart}
          <button class="ghost" disabled={busy} onclick={() => act('/restart/cancel', {}, t('versions.restartCancelled'))}>{t('versions.restartCancel')}</button>
        {/if}
      </section>
    {:else if waiting.length}
      <section class="restart info">
        <div class="r-text">
          <span class="status">{t('plugins.restartNeeded', { n: waiting.length })}</span>
          <span class="muted small">{waiting.map((p) => `${p.name} ${p.staged || p.pending}`).join(' · ')}</span>
          <span class="muted small">{data.may.restart ? t('versions.restartHint.' + (data.restart.starter?.active ? 'starter' : data.restart.method)) : t('versions.restartManual')}</span>
        </div>
        {#if data.may.restart}
          <div class="r-actions">
            {#each data.restart.delays as d}
              <button class="danger" disabled={busy}
                onclick={() => act('/restart', { delay: String(d) }, t('versions.restartStarted'), t('versions.restartConfirm', { time: delayText(d) }))}>{delayText(d)}</button>
            {/each}
          </div>
        {/if}
      </section>
    {/if}

    <section class="group">
      <h2>{t('plugins.onServer')}</h2>
      {#if installed.length}
        <ul class="grid">
          {#each installed as p (p.name)}{@render card(p)}{/each}
        </ul>
      {:else}
        <p class="muted">{t('versions.none')}</p>
      {/if}
    </section>

    {#if others.length}
      <section class="group">
        <h2>{t('plugins.more')}</h2>
        <p class="muted small hint">{t('plugins.moreHint')}</p>
        <ul class="grid">
          {#each others as p (p.name)}{@render card(p)}{/each}
        </ul>
      </section>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  /* Inside the page Server: no card of its own, only the controls (on the right) */
  .head.bare {
    padding: 0;
    border: 0;
    background: none;
    box-shadow: none;
    justify-content: flex-end;
  }

  .page {
    display: grid;
    gap: 18px;
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
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .check {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 10px;
  }

  .check-state {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 0.86rem;
    color: var(--muted);
  }

  .check-state .dot {
    background: var(--good);
  }

  .check-state.busy .dot {
    background: var(--accent);
    animation: pulse 1s ease-in-out infinite;
  }

  @keyframes pulse {
    50% {
      opacity: 0.3;
    }
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  button.ghost {
    padding: 5px 12px;
    font-size: 0.84rem;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  button.ghost:hover:not(:disabled) {
    border-color: var(--accent);
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .small {
    font-size: 0.84rem;
  }

  .bad-text {
    color: var(--bad);
  }

  /* Numbers */
  .numbers {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .num {
    display: grid;
    gap: 2px;
    padding: 12px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn, var(--border));
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .num b {
    font-size: 1.5rem;
    line-height: 1.1;
    color: var(--tn, var(--text));
  }

  .num span {
    color: var(--muted);
    font-size: 0.84rem;
  }

  /* Colours of a state: current green, update yellow, waiting blue, error red */
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

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--tn, var(--muted));
  }

  /* Restart */
  .restart {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 16px 20px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 5px solid var(--tn);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .r-text {
    display: grid;
    gap: 3px;
    flex: 1;
    min-width: 220px;
  }

  .clock {
    font-size: 2rem;
    font-weight: 800;
    color: var(--tn);
  }

  .r-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  /* Groups of cards */
  .group {
    display: grid;
    gap: 10px;
  }

  h2 {
    margin: 6px 0 0;
    font-size: 1.05rem;
  }

  .hint {
    margin: -6px 0 0;
  }

  .grid {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 290px), 1fr));
  }

  /* Card: name links to the plugin page and covers the whole card; the buttons lie above it. */
  .card {
    position: relative;
    display: grid;
    grid-template-rows: auto 1fr auto;
    gap: 10px;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
    min-width: 0;
    transition: border-color 0.15s, transform 0.15s;
  }

  .card:not(.missing):hover {
    border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
    transform: translateY(-1px);
  }

  .card.missing {
    border-style: dashed;
    background: color-mix(in srgb, var(--surface) 70%, transparent);
    box-shadow: none;
  }

  .c-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .c-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    border-radius: 11px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 13%, var(--surface-2));
  }

  .missing .c-icon {
    color: var(--muted);
    background: var(--surface-2);
  }

  .c-title {
    display: grid;
    min-width: 0;
    flex: 1;
  }

  .c-title strong,
  .c-link {
    font-size: 1.08rem;
    font-weight: 700;
    color: var(--text);
    text-decoration: none;
  }

  .c-link::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: var(--radius);
  }

  .c-version {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px;
    font-size: 0.86rem;
    color: var(--muted);
  }

  .arrow {
    font-size: 0.8rem;
  }

  .new {
    font-weight: 700;
    color: var(--tn);
  }

  .pre {
    font-family: inherit;
    font-size: 0.7rem;
    font-weight: 700;
    padding: 1px 6px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .chevron {
    color: var(--muted);
    flex-shrink: 0;
  }

  .c-desc {
    margin: 0;
    color: var(--muted);
    font-size: 0.9rem;
    line-height: 1.4;
  }

  .c-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    min-height: 34px;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 0.84rem;
    font-weight: 600;
    color: var(--tn, var(--muted));
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
    background: var(--tn, var(--muted));
  }

  button.act {
    position: relative;
    z-index: 1;
    padding: 5px 14px;
    font-size: 0.84rem;
  }

  .web {
    position: relative;
    z-index: 1;
    font-size: 0.84rem;
    color: var(--accent);
  }

  .c-note {
    margin: 0;
    font-size: 0.82rem;
    overflow-wrap: anywhere;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .numbers {
      gap: 8px;
    }

    .num {
      padding: 10px 12px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .check-state.busy .dot {
      animation: none;
    }

    .card {
      transition: none;
    }
  }
</style>
