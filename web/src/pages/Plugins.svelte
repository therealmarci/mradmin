<script>
  // Page Plugins: every MRtechnology plugin as one row - installed version, channel (Stable / Beta / Dev), what the
  // channel offers and the action (update, install). Below the plugins that are not on the server. Restart with
  // countdown when something waits for it. Data from VersionRoutes.java.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import { pluginIcon, pluginDescription, pluginState, pluginError, clock, CHANNELS, versionParts } from '../lib/plugins.js';
  import ForeignSource from '../components/ForeignSource.svelte';
  import Toast from '../components/Toast.svelte';

  // embedded: inside the tab Plugins of the page Server - without its own title.
  // server: 'main' = only plugins of this server, 'room' = only those of the waiting room server, null = all
  let { embedded = false, server = null } = $props();

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
      t('versions.downloadConfirm', { name: p.name, version: offeredText(p) }));
  const install = (p) =>
    act(`/versions/${encodeURIComponent(p.name)}/install`, {}, t('plugins.installStarted', { name: p.name }),
      t('plugins.installConfirm', { name: p.name, version: offeredText(p) }));

  function setChannel(p, channel) {
    if (channel === p.channel && p.ownChannel) return;
    const sure = channel === 'dev' ? t('plugins.channel.devConfirm', { name: p.name })
      : channel === 'beta' ? t('plugins.channel.betaConfirm', { name: p.name }) : null;
    act(`/versions/${encodeURIComponent(p.name)}/channel`, { channel },
      t('plugins.channel.set', { name: p.name, channel: t('plugins.channel.' + channel) }), sure);
  }
  const resetChannel = (p) =>
    act(`/versions/${encodeURIComponent(p.name)}/channel`, { channel: 'default' },
      t('plugins.channel.set', { name: p.name, channel: t('plugins.channel.' + data.channel) }));

  /** What the channel offers, as text: "STA-1.4.0-bu002" or "Dev abc1234". */
  function offeredText(p) {
    if (p.channel === 'dev') return p.dev ? t('plugins.devBuild', { commit: p.dev.commit }) : '–';
    return p.latest ? p.latest.version : '–';
  }

  const buildText = (build) => t('plugins.build', { build: String(build).padStart(3, '0') });
  const delayText = (ms) => (ms === 0 ? t('versions.restartNow') : t('versions.restartIn', { time: duration(ms) }));
  const byName = (a, b) => a.name.localeCompare(b.name);
  const pool = $derived(!data ? [] : data.plugins.filter((p) => (server === 'room' ? !!p.remote : server === 'main' ? !p.remote : true)));
  // Most to do first: errors, updates (also blocked ones), then waiting for a restart, unknown, current at the bottom.
  const URGENCY = { bad: 0, warn: 1, info: 2, '': 3, good: 4 };
  const byUrgency = (a, b) => (URGENCY[pluginState(a).tone] ?? 3) - (URGENCY[pluginState(b).tone] ?? 3) || byName(a, b);
  const installed = $derived(pool.filter((p) => p.installed).sort(byUrgency));
  // The page of one plugin: under the server it runs on.
  const detail = (name) => (server === 'room' ? '/admin/server/room/plugins/' : '/admin/server/plugins/') + name;
  // The other plugins of this server (not from MRtechnology): only watched - with a GitHub repository stored, MRadmin
  // compares the installed version with its newest release (GET/POST /api/plugins/foreign).
  let foreign = $state(null);
  let minecraft = $state('');
  // The plugin whose source is being chosen (window ForeignSource).
  let choosing = $state(null);
  const foreignServer = $derived(server === 'room' ? 'room' : 'main');
  async function loadForeign() {
    if (server !== 'room' && server !== 'main') return;
    try {
      const answer = await api('GET', '/plugins/foreign?server=' + foreignServer);
      foreign = answer.plugins;
      minecraft = answer.minecraft ?? '';
    } catch {
      foreign = null;
    }
  }
  $effect(() => {
    foreignServer;
    loadForeign();
    // a release just asked for at GitHub shows up a moment later
    const timer = setInterval(() => foreign?.some((f) => f.state === 'checking') && loadForeign(), 3000);
    return () => clearInterval(timer);
  });
  // newer versions first, then the rest by name
  const FOREIGN_ORDER = { newer: 0, 'no-version': 1, unknown: 1, checking: 2, current: 3, none: 4 };
  const shownForeign = $derived((foreign ?? []).filter((f) => !installed.some((p) => p.name === f.name))
    .sort((x, y) => (FOREIGN_ORDER[x.state] ?? 4) - (FOREIGN_ORDER[y.state] ?? 4) || x.name.localeCompare(y.name)));
  const foreignNewer = $derived(shownForeign.filter((f) => f.state === 'newer').length);
  const others = $derived(server === 'room' ? [] : pool.filter((p) => !p.installed).sort(byName));
  // Waiting for a restart of this server (a plugin on the waiting room server restarts that one itself).
  const waiting = $derived(data ? data.plugins.filter((p) => !p.remote && (p.staged || (p.pending && !p.notLoaded))) : []);
  const updates = $derived(installed.filter((p) => p.update && !p.staged));
  // Badge next to the latest version of the channel: the installed one is it, or it is newer (not yet downloaded).
  const latestBadge = (p) => (!p.installed || p.staged || p.downloading || p.blocked ? null : p.update ? 'newer' : pluginState(p).tone === 'good' ? 'current' : null);
  // The dev build is the commit the installed JAR was built from (e.g. a beta released from the newest dev state).
  const sameCommit = (p) => !!(p.dev?.commit && p.installedInfo?.commit && p.dev.commit.startsWith(p.installedInfo.commit));
  const left = $derived(data && data.restart.endsAt > 0 ? Math.max(0, data.restart.endsAt - now) : 0);
</script>

{#snippet channelSwitch(p)}
  <div class="channels" role="group" aria-label={t('plugins.col.channel')}>
    {#each CHANNELS.filter((c) => c !== 'dev' || data.devToken) as c}
      <!-- greyed out: nothing released in this channel yet (e.g. no stable of a 0.x plugin, no dev build) -->
      {@const empty = !p.channels?.[c] && p.channel !== c}
      <button class="ch {c}" class:on={p.channel === c} class:empty aria-pressed={p.channel === c}
        disabled={busy || !data.may.install || empty}
        title={empty ? t('plugins.channel.empty.' + c) : t('plugins.channel.hint.' + c)}
        onclick={() => setChannel(p, c)}>
        {t('plugins.channel.' + c)}
      </button>
    {/each}
  </div>
  <span class="ch-note small muted">
    {#if p.ownChannel}
      {t('plugins.channel.own')}
      {#if data.may.install}<button class="link" disabled={busy} onclick={() => resetChannel(p)}>{t('plugins.channel.reset')}</button>{/if}
    {:else}
      {t('plugins.channel.default')}
    {/if}
  </span>
{/snippet}

<!-- A version in parts: channel tag + number, below build / commit / age -->
{#snippet version(channel, number, notes, badge = null)}
  <span class="inst">
    <span class="tag {channel}">{t('plugins.channel.' + channel)}</span>
    <span class="mono strong">{number}</span>
    {#if badge}<span class="badge {badge}">{t('plugins.badge.' + badge)}</span>{/if}
  </span>
  <span class="small muted">{notes.filter(Boolean).join(' · ')}</span>
{/snippet}

<!-- A plugin of the waiting room that is not from MRtechnology: the same row, without channel and update -->
{#snippet srcLabel(p)}
  {#if p.source?.type === 'modrinth' || p.source?.type === 'github'}
    <span class="logo" aria-hidden="true"></span>
    <span class="src-name" title={p.source.title}>{p.source.title}</span>
    {#if !p.manual}<span class="src-auto">{t('plugins.src.autoShort')}</span>{/if}
    {#if p.channel === 'beta'}<span class="src-beta">{t('plugins.channel.beta')}</span>{/if}
  {:else if p.source?.type === 'off'}
    {t('plugins.src.offShort')}
  {:else if p.state === 'checking'}
    {t('plugins.foreignChecking')}
  {:else}
    {data.may.install ? '+ ' + t('plugins.src.add') : t('plugins.src.none')}
  {/if}
{/snippet}

{#snippet foreignRow(p)}
  <li class="row {p.state === 'newer' ? 'warn' : p.enabled ? 'good' : 'bad'}">
    <div class="cell plugin">
      <span class="r-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.box} /></svg>
      </span>
      <div class="r-name">
        <span class="name-line"><strong title={p.name}>{p.name}</strong>{#if !p.enabled}<span class="remote off">{t('plugins.foreignOff')}</span>{/if}</span>
      </div>
    </div>
    <div class="cell">
      <span class="label">{t('plugins.col.installed')}</span>
      <span class="mono strong">{p.version}</span>
    </div>
    <div class="cell">
      <span class="label">{t('plugins.col.source')}</span>
      <!-- Where it comes from: found on Modrinth by the JAR's fingerprint, or chosen by hand -->
      {#if data.may.install}
        <button class="src {p.source?.type ?? 'none'}" disabled={busy} title={t('plugins.src.change')} onclick={() => (choosing = p)}>{@render srcLabel(p)}</button>
      {:else}
        <span class="src {p.source?.type ?? 'none'}">{@render srcLabel(p)}</span>
      {/if}
    </div>
    <div class="cell">
      <span class="label">{t('plugins.col.latestRelease')}</span>
      {#if p.latest}
        <span class="inst"><a class="mono strong" href={p.latest.url} target="_blank" rel="noopener noreferrer">{p.latest.version}</a>
          {#if p.state === 'current'}<span class="badge current">{t('plugins.badge.current')}</span>{:else if p.state === 'newer'}<span class="badge newer">{t('plugins.badge.newer')}</span>{/if}</span>
        <span class="small muted">{[p.latest.published ? ago(p.latest.published) : '', p.latest.channel && p.latest.channel !== 'release' ? p.latest.channel : ''].filter(Boolean).join(' · ')}</span>
        {#if p.latest.mcUnknown}<span class="small warn-text">{t('plugins.src.mcUnknownShort')}</span>{/if}
      {:else if p.state === 'checking' && p.source}
        <span class="muted small">{t('plugins.foreignChecking')}</span>
      {:else if p.state === 'no-version'}
        <span class="small warn-text">{t(p.error === 'no-stable' ? 'plugins.src.onlyBeta' : 'plugins.src.noVersion', { mc: minecraft })}</span>
      {:else if p.state === 'unknown'}
        <span class="small bad-text" title={p.error}>{t(p.error === 'not-found' ? 'plugins.src.notFound' : p.error === 'no-release' ? 'plugins.foreignNoRelease' : 'plugins.foreignFailed')}</span>
      {:else}
        <span class="muted">–</span>
      {/if}
    </div>
    <div class="cell action">
      <span class="state"><span class="dot"></span>{p.state === 'newer' ? t('plugins.foreignNewer') : t(p.enabled ? 'plugins.foreignOn' : 'plugins.foreignOff')}</span>
    </div>
  </li>
{/snippet}

{#snippet row(p)}
  {@const st = pluginState(p)}
  {@const err = pluginError(p)}
  <li class="row {st.tone}" class:missing={!p.installed}>
    <div class="cell plugin">
      <span class="r-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS[pluginIcon(p.name)]} /></svg>
      </span>
      <div class="r-name">
        <span class="name-line">
          {#if p.installed}
            <a href={detail(p.name)} onclick={(e) => { e.preventDefault(); go(detail(p.name)); }}>{p.name}</a>
          {:else}
            <strong>{p.name}</strong>
          {/if}
          {#if p.remote}<span class="remote" class:off={!p.remote.online} title={t('plugins.remote.hint')}>{t('plugins.remote.where')}</span>{/if}
        </span>
        <span class="small muted desc">{pluginDescription(p.name)}</span>
      </div>
    </div>

    <div class="cell">
      <span class="label">{t('plugins.col.installed')}</span>
      {#if p.installed}
        {@const info = p.installedInfo ?? versionParts(p.installed)}
        {@render version(info.channel, info.number, [
          info.channel === 'dev' ? t('plugins.devState') : info.build != null ? buildText(info.build) : t('plugins.oldScheme'),
          info.commit ? t('plugins.commit', { commit: info.commit }) : null])}
      {:else if p.pending}
        <span class="mono muted">{p.pending}</span>
      {:else}
        <span class="muted">–</span>
      {/if}
    </div>

    <div class="cell">
      <span class="label">{t('plugins.col.channel')}</span>
      {@render channelSwitch(p)}
    </div>

    <div class="cell">
      <span class="label">{t('plugins.col.available')}</span>
      {#if p.channel === 'dev' && p.dev}
        {@render version('dev', p.dev.commit, [sameCommit(p) ? t('plugins.sameAsInstalled') : t('plugins.devState'), ago(p.dev.date)], latestBadge(p))}
      {:else if p.channel !== 'dev' && p.latest}
        {@const v = versionParts(p.latest.version)}
        {@render version(p.latest.prerelease ? 'beta' : v.channel, v.number, [v.build != null ? buildText(v.build) : t('plugins.oldScheme'),
          p.latest.published ? ago(p.latest.published) : null], latestBadge(p))}
      {:else}
        <span class="muted">–</span>
      {/if}
    </div>

    <div class="cell action">
      <!-- "up to date" already shows as the badge next to the latest version -->
      {#if st.tone !== 'good' || !p.installed}<span class="state"><span class="dot"></span>{st.text}</span>{/if}
      {#if p.installed && p.update && p.checksum && data.may.install}
        <button class="act" disabled={busy || p.downloading} onclick={() => update(p)}>⬇ {t('plugins.update')}</button>
      {:else if p.installable && data.may.install}
        <button class="act" disabled={busy || p.downloading} onclick={() => install(p)}>{t('plugins.install')}</button>
      {:else if !p.installed}
        <a class="web small" href={p.website} target="_blank" rel="noopener noreferrer">{t('versions.website')} ↗</a>
      {/if}
    </div>

    {#if p.blocked}<p class="note warn-text">{t('plugins.blocked.' + p.blocked.reason, { name: p.name, version: offeredText(p), have: p.blocked.have, offered: p.blocked.offered })}</p>{/if}
    {#if p.notLoaded}<p class="note bad-text">{t('plugins.notLoadedHint')}</p>{/if}
    {#if p.remote?.configPending}<p class="note small muted">{t('plugins.remote.configPending')}</p>{/if}
    {#if p.remote?.error}<p class="note bad-text">{t('plugins.remote.error', { error: p.remote.error })}</p>{/if}
    {#if err}<p class="note bad-text">{err}</p>{/if}
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
        <span class="chip" title={t('plugins.channel.defaultHint')}>{t('plugins.channel.defaultChip', { channel: t('plugins.channel.' + data.channel) })}</span>
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
      <div class="num"><b>{installed.length + shownForeign.length}</b><span>{t('plugins.installedHere')}</span></div>
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

    <!-- Two tiles: the MRtechnology plugins (managed here) and the other plugins of this server (only watched) -->
    <section class="group tile">
      <div class="tile-title">
        <h2>{t('plugins.mrTitle')}</h2>
        <span class="count">{installed.length}</span>
      </div>
      {#if installed.length}
        <ul class="table">
          <li class="row headrow" aria-hidden="true">
            <span>{t('plugins.col.plugin')}</span><span>{t('plugins.col.installed')}</span><span>{t('plugins.col.channel')}</span>
            <span>{t('plugins.col.available')}</span><span></span>
          </li>
          {#each installed as p (p.name)}{@render row(p)}{/each}
        </ul>
      {:else}
        <p class="muted">{t('versions.none')}</p>
      {/if}
      {#if others.length}
        <h3>{t('plugins.more')}</h3>
        <p class="muted small hint">{t('plugins.moreHint')}</p>
        <ul class="table">
          {#each others as p (p.name)}{@render row(p)}{/each}
        </ul>
      {/if}
    </section>

    {#if foreign !== null}
      <section class="group tile">
        <div class="tile-title">
          <h2>{t('plugins.otherTitle')}</h2>
          <span class="count">{shownForeign.length}</span>
          {#if foreignNewer}<span class="count warn">{t('plugins.otherNewer', { n: foreignNewer })}</span>{/if}
        </div>
        <p class="muted small hint">{t('plugins.otherHint')}</p>
        {#if shownForeign.length}
          <ul class="table">
            <li class="row headrow" aria-hidden="true">
              <span>{t('plugins.col.plugin')}</span><span>{t('plugins.col.installed')}</span><span>{t('plugins.col.source')}</span>
              <span>{t('plugins.col.latestRelease')}</span><span></span>
            </li>
            {#each shownForeign as f (f.name)}{@render foreignRow(f)}{/each}
          </ul>
        {:else}
          <p class="muted">{t('plugins.otherNone')}</p>
        {/if}
      </section>
    {/if}
    <p class="muted small legend">
      <b>{t('plugins.channel.stable')}</b> {t('plugins.channel.hint.stable')} ·
      <b>{t('plugins.channel.beta')}</b> {t('plugins.channel.hint.beta')}
      {#if data.devToken}· <b>{t('plugins.channel.dev')}</b> {t('plugins.channel.hint.dev')}{/if}
    </p>
    {#if data.devTokenState === 'invalid'}<p class="small bad-text legend">{t('plugins.channel.devInvalid')}</p>{/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

{#if choosing}
  <ForeignSource plugin={choosing} server={foreignServer} {minecraft} onclose={() => (choosing = null)} ondone={loadForeign} />
{/if}

<style>
  .row .cell :global(button.link) {
    padding: 0;
    font-size: 0.82rem;
    justify-content: flex-start;
  }

  .src {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding: 3px 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--text);
    background: var(--surface-2);
    justify-content: flex-start;
    justify-self: start;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
  }

  .src > * {
    flex-shrink: 0;
  }

  .src > .src-name {
    flex-shrink: 1;
  }

  button.src:hover {
    border-color: var(--accent);
  }

  .src.none,
  .src.off {
    color: var(--muted);
    background: transparent;
    border-style: dashed;
  }

  .src .logo {
    flex-shrink: 0;
    width: 9px;
    height: 9px;
    border-radius: 3px;
  }

  .src.modrinth .logo {
    background: #1bd96a;
  }

  .src.github .logo {
    border-radius: 50%;
    background: var(--text);
  }

  .src-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .src-beta {
    padding: 0 6px;
    border-radius: 999px;
    font-size: 0.68rem;
    font-weight: 700;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .src-auto {
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--muted);
  }

  .plain-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 6px 16px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .plain-list .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--bad);
  }

  .plain-list .dot.on {
    background: var(--good);
  }

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
    container-type: inline-size;
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

  button.link {
    display: inline;
    padding: 0;
    margin-left: 4px;
    border: 0;
    background: none;
    color: var(--accent);
    font-size: inherit;
    font-weight: 600;
    justify-content: flex-start;
  }

  .mono {
    font-family: ui-monospace, monospace;
    overflow-wrap: anywhere;
  }

  .small {
    font-size: 0.82rem;
  }

  .bad-text {
    color: var(--bad);
  }

  .warn-text {
    color: var(--warn);
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

  /* Groups */
  .group {
    display: grid;
    gap: 10px;
  }

  /* One tile each for the MRtechnology plugins and the other plugins */
  .tile {
    padding: 16px 18px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .tile-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .tile-title h2 {
    margin: 0;
  }

  .count {
    padding: 1px 9px;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--muted);
    background: var(--surface-2);
  }

  .count.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  h3 {
    margin: 8px 0 0;
    font-size: 0.95rem;
  }

  h2 {
    margin: 6px 0 0;
    font-size: 1.05rem;
  }

  .hint {
    margin: -6px 0 0;
  }

  /* Table: one row per plugin, the columns line up over all rows (subgrid). */
  .table {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    /* fixed widths, so that both tables (on the server / more plugins) line up */
    grid-template-columns: minmax(200px, 1.6fr) minmax(130px, 1fr) 200px minmax(150px, 1fr) 190px;
    gap: 10px;
  }

  /* every plugin is a tile; the columns still line up over all tiles (subgrid) */
  .row {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: subgrid;
    align-items: center;
    gap: 6px 18px;
    padding: 14px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 4px solid var(--tn, var(--border));
    background: var(--surface);
    box-shadow: var(--shadow);
    transition: border-color 0.15s;
  }

  .row:not(.headrow):hover {
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
    border-left-color: var(--tn, color-mix(in srgb, var(--accent) 45%, var(--border)));
  }

  .headrow {
    padding: 0 18px 0 22px;
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
    border: 0;
    background: none;
    box-shadow: none;
  }

  .row.missing {
    border-style: dashed;
    border-left-style: solid;
    background: color-mix(in srgb, var(--surface) 70%, transparent);
    box-shadow: none;
  }

  .cell {
    display: grid;
    gap: 3px;
    min-width: 0;
    justify-items: start;
  }

  .label {
    display: none;
    font-size: 0.7rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
  }

  .plugin {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .r-icon {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    flex-shrink: 0;
    border-radius: 10px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 13%, var(--surface-2));
  }

  .missing .r-icon {
    color: var(--muted);
    background: var(--surface-2);
  }

  .r-name {
    display: grid;
    min-width: 0;
  }

  .r-name a,
  .r-name strong {
    font-weight: 700;
    color: var(--text);
    text-decoration: none;
  }

  .r-name a:hover {
    color: var(--accent);
  }

  /* Long names (plugins of others) end with … instead of running into the next column; the full name on hover */
  .name-line strong {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .name-line > :not(strong) {
    flex-shrink: 0;
  }

  .cell .mono {
    max-width: 100%;
    overflow-wrap: anywhere;
  }

  .desc {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .tag {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 1px 7px;
    border-radius: 999px;
  }

  .tag.stable {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .tag.beta {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .tag.dev {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  /* Badge next to the latest version: outlined, so it does not look like a channel tag */
  .badge {
    font-size: 0.68rem;
    font-weight: 700;
    padding: 0 7px;
    border-radius: 999px;
    border: 1px solid currentColor;
    line-height: 1.5;
  }

  .badge.current {
    color: var(--good);
  }

  .badge.current::before {
    content: '✓ ';
  }

  .badge.newer {
    color: var(--warn);
  }

  .inst {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
  }

  .strong {
    font-weight: 700;
  }

  /* Channel switch: three segments; beta yellow, dev red when chosen */
  .channels {
    display: inline-flex;
    border: 1px solid var(--border);
    border-radius: 9px;
    overflow: hidden;
    background: var(--surface-2);
  }

  .ch {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 4px;
    padding: 4px 11px;
    border: 0;
    border-radius: 0;
    font-size: 0.8rem;
    font-weight: 600;
    background: transparent;
    color: var(--muted);
  }

  .ch + .ch {
    border-left: 1px solid var(--border);
  }

  .ch:hover:not(:disabled) {
    color: var(--text);
  }

  .ch.on {
    color: #fff;
    background: var(--accent);
  }

  .ch.beta.on {
    background: var(--warn);
    color: #1b1300;
  }

  .ch.dev.on {
    background: var(--bad);
  }

  .ch:disabled:not(.on) {
    opacity: 0.55;
  }

  /* nothing in this channel yet */
  .ch.empty {
    opacity: 0.35;
    text-decoration: line-through;
    cursor: not-allowed;
  }

  .ch:disabled.on {
    opacity: 1;
  }

  .ch-note {
    font-size: 0.74rem;
  }

  .action {
    justify-items: end;
    text-align: right;
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 0.82rem;
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
    padding: 5px 14px;
    font-size: 0.84rem;
  }

  .web {
    color: var(--accent);
  }

  .note {
    grid-column: 1 / -1;
    margin: 0;
    font-size: 0.82rem;
    overflow-wrap: anywhere;
  }

  .legend {
    margin: 0;
    line-height: 1.5;
  }

  /* Narrow: every row becomes a small card, the labels appear */
  @container (max-width: 860px) {
    .table {
      grid-template-columns: 1fr;
      gap: 12px;
    }

    .headrow {
      display: none;
    }

    .row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px 16px;
      padding: 14px 16px;
    }

    .plugin,
    .action {
      grid-column: 1 / -1;
    }

    .action {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 10px;
      border-top: 1px solid var(--border);
    }

    .label {
      display: block;
    }
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

    .row {
      transition: none;
    }
  }
  .name-line { display: flex; align-items: center; gap: 0.4rem; min-width: 0; }
  .remote { font-size: 0.7rem; font-weight: 700; padding: 1px 7px; border-radius: 999px; white-space: nowrap;
    border: 1px solid var(--edge); color: var(--muted); }
  .remote.off { color: var(--bad); border-color: var(--bad); }
</style>
