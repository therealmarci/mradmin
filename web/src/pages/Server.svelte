<script>
  // Page Server: one entry in the bar for everything around the running server. Tabs (each only with its permission):
  // Overview (restart / stop / cancel, starter, Paper build, next planned restart, short view of plugins and events),
  // Plugins, Log and Console - the last three are the former pages, shown here without their own title.
  // Data: /api/versions (VersionRoutes.java, field restart) and /api/logs?events=1.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import { clock } from '../lib/plugins.js';
  import Toast from '../components/Toast.svelte';
  import EmergencyRestart from '../components/EmergencyRestart.svelte';
  import Plugins from './Plugins.svelte';
  import Logs from './Logs.svelte';
  import Console from './Console.svelte';

  // bare: inside the area Server of the administration - its tabs are there, here only the content
  let { me, tab = null, bare = false } = $props();

  const tabs = $derived([
    ...(me.may.versions ? ['overview', 'plugins'] : []),
    ...(me.may.audit || me.may.console ? ['logs'] : []),
    ...(me.may.consoleExecute ? ['console'] : []),
  ]);
  const current = $derived(tabs.includes(tab ?? 'overview') ? (tab ?? 'overview') : tabs[0]);
  const open = (id) => go(id === 'overview' ? '/server' : '/server/' + id, true);

  let data = $state(null);
  let events = $state(null);
  let error = $state('');
  let message = $state('');
  // Optional own reason for the next restart / stop (empty = MRadmin names one).
  let reason = $state('');
  let busy = $state(false);
  let now = $state(Date.now());

  async function load() {
    if (!me.may.versions) return;
    try {
      data = await api('GET', '/versions');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  async function loadEvents() {
    if (!me.may.audit) return;
    try {
      events = (await api('GET', '/logs?events=1&limit=6')).entries;
    } catch {
      events = [];
    }
  }

  const counting = $derived(!!data && data.restart.endsAt > 0);
  $effect(() => {
    if (current !== 'overview') return;
    load();
    loadEvents();
    const timer = setInterval(load, counting ? 2000 : 15000);
    const timer2 = setInterval(loadEvents, 30000);
    return () => {
      clearInterval(timer);
      clearInterval(timer2);
    };
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
      reason = '';
      if (done) message = done;
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const r = $derived(data?.restart);
  const left = $derived(r && r.endsAt > 0 ? Math.max(0, r.endsAt - now) : 0);
  const delayText = (ms) => (ms === 0 ? t('versions.restartNow') : t('versions.restartIn', { time: duration(ms) }));
  const restart = (d) => act('/restart', { delay: String(d), kind: 'restart', reason: reason.trim() }, t('versions.restartStarted'),
    t('versions.restartConfirm', { time: delayText(d) }));
  const stop = (d) => act('/restart', { delay: String(d), kind: 'stop', reason: reason.trim() }, t('server.stopStarted'),
    t(r.starter.active ? 'server.stopConfirm' : 'server.stopConfirmNoStarter', { time: delayText(d) }));
  // Emergency (shorter times): window with a fresh sign-in first.
  let emergency = $state();
  function emergencyDone(text) {
    reason = '';
    message = text;
    load();
  }
  const when = (ms) => new Date(ms).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' });

  // The waiting room server (MRwaitroom reports itself; its restart is an order it carries out when it may).
  const room = $derived(data?.waitroom);
  const roomTone = $derived(!room ? 'off' : room.restarting ? 'info' : !room.online ? 'bad' : room.ordered || room.pending ? 'info' : 'good');
  const roomState = $derived(!room ? '' : room.restarting ? t('server.room.restarting') : !room.online ? t('server.room.offline')
    : room.ordered || room.pending ? t('server.room.pending') : t('server.room.online'));
  const roomLock = $derived(!room ? null : room.restarting ? 'restarting' : !room.online ? 'remote-offline'
    : room.ordered ? 'ordered' : !room.starter ? 'no-starter'
    : room.blocked && room.blocked !== 'players-waiting' ? room.blocked
    : r?.endsAt > 0 ? 'main-restart-soon' : null);
  const restartRoom = () => act('/waitroom/restart', {}, t('server.room.ordered'), t('server.room.confirm'));

  // The values of the server card: one word each, the rest in the tooltip.
  const pausedUntil = $derived(r?.schedule?.pausedUntil > now ? r.schedule.pausedUntil : 0);
  const paperStaged = $derived(r && r.paper.staged >= 0 ? r.paper.staged : 0);
  const paperTone = $derived(!r ? 'off' : paperStaged ? 'info' : r.paper.latest ? 'warn' : r.paper.newest >= 0 ? '' : 'off');
  const paperTip = $derived(!r ? '' : [
    paperStaged ? t('server.paperStaged', { build: paperStaged })
      : r.paper.latest ? t('server.paperLatest', { build: r.paper.latest })
      : r.paper.newest >= 0 ? t('server.paperCurrent') : t(r.paper.failed ? 'server.paperFailed' : 'server.paperChecking'),
    r.paper.latest && !paperStaged ? (r.starter.active ? t('server.paperMode.' + r.paper.update) : t('server.paperManual')) : '',
    r.paper.checkedAt > 0 ? t('server.paperChecked', { time: ago(r.paper.checkedAt) }) : '',
  ].filter(Boolean).join(' · '));
  const starterTone = $derived(!r ? 'off' : r.starter.active ? '' : r.starter.ready ? 'warn' : 'off');
  const starterText = $derived(!r ? '' : t(r.starter.active ? 'server.starterOn' : r.starter.ready ? 'server.starterReady' : 'server.starterOff'));
  const starterTip = $derived(!r ? '' : r.starter.active ? (r.starter.mode ?? '')
    : t(r.starter.ready ? 'config.status.starter-ready' : 'server.starterHint'));

  const installed = $derived(data ? data.plugins.filter((p) => p.installed) : []);
  const updates = $derived(installed.filter((p) => p.update && !p.staged));
  const waiting = $derived(data ? data.plugins.filter((p) => p.staged || (p.pending && !p.notLoaded)) : []);
  const uptime = $derived(r ? duration(Math.max(0, now - r.startedAt)) : '');
  const nextText = $derived(r && r.nextPlanned > 0
    ? new Date(r.nextPlanned).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' }) : '');
</script>

<div class="page">
  {#if !bare}
  <header class="head">
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={ICONS.server} /></svg>
    </span>
    <div class="title">
      <h1>{t('server.title')}</h1>
      <p class="muted">{t('server.subtitle')}</p>
    </div>
  </header>

  {/if}

  {#if tabs.length > 1 && !bare}
    <div class="switch" role="tablist">
      {#each tabs as id (id)}
        <button role="tab" aria-selected={current === id} class:active={current === id} onclick={() => open(id)}>
          {t('server.tab.' + id)}
          {#if id === 'plugins' && updates.length}<span class="n">{updates.length}</span>{/if}
        </button>
      {/each}
    </div>
  {/if}

  {#if current === 'overview'}
    {#if error}<div class="error">{error}</div>{/if}
    <Toast text={message} />

    {#if r}
      <!-- The server: state on the left, restart / stop on the right (or the running countdown) -->
      <section class="card server" class:counting={r.endsAt > 0}>
        <!-- Four values in a row; details only in the tooltip -->
        <div class="stats">
          <div class="stat" title={t('server.since', { time: uptime })}>
            <span class="s-label">{t('server.uptime')}</span>
            <span class="s-value"><span class="dot" class:bad={r.endsAt > 0}></span>{uptime}</span>
          </div>
          <div class="stat" title={paperTip}>
            <span class="s-label">{t('server.paper')}</span>
            <span class="s-value"><span class="dot {paperTone}"></span>{r.paper.minecraft} · #{r.paper.build}</span>
            {#if paperStaged}<span class="s-sub info">{t('server.paperStagedShort', { build: paperStaged })}</span>
            {:else if r.paper.latest}<span class="s-sub warn">{t('server.paperLatestShort', { build: r.paper.latest })}</span>{/if}
          </div>
          <div class="stat" title={starterTip}>
            <span class="s-label">{t('server.starter')}</span>
            <span class="s-value"><span class="dot {starterTone}"></span>{starterText}</span>
          </div>
          <div class="stat" title={pausedUntil ? t('server.pausedBy', { name: r.schedule.pausedBy, time: when(pausedUntil) }) : ''}>
            <span class="s-label">{t('server.next')}</span>
            {#if pausedUntil}
              <span class="s-value"><span class="dot warn"></span>{t('server.nextPaused')}</span>
              <span class="s-sub">{t('server.pausedUntil', { time: when(pausedUntil) })}</span>
            {:else}
              <span class="s-value"><span class="dot" class:info={!!nextText} class:off={!nextText}></span>{nextText || t('server.nextNone')}</span>
            {/if}
          </div>
        </div>

        <div class="control">
          {#if r.endsAt > 0}
            <div class="countdown">
              <span class="status">{t('server.countdown.' + r.kind)}</span>
              <span class="clock mono">{left > 0 ? clock(left) : t('versions.restartNow')}</span>
              <span class="muted small">{r.planned ? t('server.byPlan') : t('versions.restartBy', { name: r.by })}</span>
              {#if r.reason}<span class="reason small">{t('server.reasonShown', { reason: r.reason })}</span>{/if}
            </div>
            {#if data.may.stop}
              <div class="actions">
                <button class="ghost" disabled={busy} onclick={() => act('/restart/cancel', {}, t('server.cancelled'))}>
                  {t(r.planned ? 'server.skipPlanned' : 'versions.restartCancel')}
                </button>
              </div>
            {/if}
          {:else if data.may.stop}
            <label class="row">
              <span class="label">{t('server.reason')}</span>
              <input class="reason-input" type="text" maxlength="200" bind:value={reason} placeholder={t('server.reasonPlaceholder')} />
            </label>
            <div class="row">
              <span class="label">{t('server.restart')}</span>
              <div class="choices">
                {#each r.delays as d (d)}
                  <button class="secondary" disabled={busy || !data.may.restart} title={data.may.restart ? '' : t('server.restartOff')}
                    onclick={() => restart(d)}>{delayText(d)}</button>
                {/each}
              </div>
            </div>
            <div class="row">
              <span class="label">{t('server.stop')}</span>
              <div class="choices">
                {#each r.delays as d (d)}
                  <button class="danger-ghost" disabled={busy} onclick={() => stop(d)}>{delayText(d)}</button>
                {/each}
              </div>
            </div>
            <!-- Own row: the emergency window can restart or stop, so it belongs to neither row above -->
            {#if r.emergencyDelays.length}
              <div class="row">
                <span class="label">{t('server.emergencyRow')}</span>
                <div class="choices">
                  <button class="danger" disabled={busy} title={t('server.emergencyTip')}
                    onclick={() => emergency.open()}>{t('server.emergencyButton')}</button>
                </div>
              </div>
            {/if}
            {#if !data.may.restart}<p class="muted small">{t('server.restartOff')}</p>{/if}
          {/if}

          {#if data.may.stop && r.schedule.times > 0}
            <div class="schedule">
              {#if pausedUntil}
                <span class="small">{t('server.paused', { time: when(pausedUntil) })}</span>
                <button class="ghost" disabled={busy} onclick={() => act('/restart/schedule', { pause: 'false' }, t('server.resumeDone'))}>
                  {t('server.resume')}
                </button>
              {:else}
                <button class="ghost" disabled={busy}
                  onclick={() => act('/restart/schedule', { pause: 'true' }, t('server.pauseDone'), t('server.pauseConfirm'))}>
                  {t('server.pause')}
                </button>
              {/if}
            </div>
          {/if}
        </div>
      </section>

      {#if data.may.stop && r.emergencyDelays.length}
        <EmergencyRestart bind:this={emergency} {me} delays={r.emergencyDelays} canRestart={data.may.restart}
          starterActive={r.starter.active} {reason} ondone={emergencyDone} />
      {/if}

      <div class="tiles" class:three={!!room}>
        {#if room}
          <div class="card tile room">
            <span class="t-head">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><path d={ICONS.restore} /></svg>
              {t('server.room.title')}
              <span class="pill {roomTone}"><span class="dot {roomTone}"></span>{roomState}</span>
            </span>
            {#if room.online}
              <div class="badges">
                <span class="badge" title={t('server.room.paperTip')}>Paper #{room.paper ?? '–'}</span>
                <span class="badge">{t('server.room.since', { time: room.startedAt ? ago(room.startedAt) : '–' })}</span>
                <span class="badge" class:info={room.players > 0}>{t('server.room.players', { n: room.players ?? 0 })}</span>
                <span class="badge">{room.nextRestart ? t('server.room.next', { time: when(room.nextRestart) }) : t('server.room.nextNone')}</span>
                <span class="badge" class:warn={!room.starter}>{t(room.starter ? 'server.room.starterOn' : 'server.room.starterOff')}</span>
              </div>
            {/if}
            {#if roomLock}<span class="lock small muted">{t('server.room.blocked.' + roomLock)}</span>{/if}
            {#if room.error}<span class="lock small bad">{t('server.room.blocked.' + room.error)}</span>{/if}
            {#if data.may.stop}
              <button class="secondary" disabled={busy || !!roomLock} title={roomLock ? t('server.room.blocked.' + roomLock) : ''}
                onclick={restartRoom}>{t('server.room.restart')}</button>
            {/if}
          </div>
        {/if}
        <button class="card tile" onclick={() => open('plugins')}>
          <span class="t-head">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" aria-hidden="true"><path d={ICONS.box} /></svg>
            {t('server.plugins')}
            <span class="go">→</span>
          </span>
          <span class="big">{t('server.pluginsInstalled', { n: installed.length })}</span>
          {#if updates.length}<span class="line warn">{t('server.pluginsUpdates', { n: updates.length })}</span>{/if}
          {#if waiting.length}<span class="line info">{t('server.pluginsWaiting', { n: waiting.length })}</span>{/if}
          {#if !updates.length && !waiting.length}<span class="line good">{t('server.pluginsCurrent')}</span>{/if}
        </button>

        {#if me.may.audit}
          <div class="card tile events">
            <span class="t-head">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><path d={ICONS.news} /></svg>
              {t('server.events')}
              <button class="link" onclick={() => open('logs')}>{t('server.all')} →</button>
            </span>
            {#if events && events.length}
              <ul>
                {#each events as e (e.time + e.event + e.text)}
                  <li>
                    <span class="bar" style="background: {e.color}"></span>
                    <span class="when muted">{ago(e.time)}</span>
                    <span class="what" title={e.text}>{e.text}</span>
                  </li>
                {/each}
              </ul>
            {:else if events}
              <p class="muted small">{t('server.eventsNone')}</p>
            {/if}
          </div>
        {/if}
      </div>
    {:else if !error}
      <p class="muted">{t('common.loading')}</p>
    {/if}
  {:else if current === 'plugins'}
    <Plugins embedded />
  {:else if current === 'logs'}
    <Logs {me} embedded />
  {:else if current === 'console'}
    <Console {me} embedded />
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 18px;
  }

  .head {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    flex: none;
    border-radius: 14px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.5rem;
  }

  .title p {
    margin: 2px 0 0;
  }

  /* Tabs: same look as on the duels page */
  .switch {
    display: flex;
    flex-wrap: wrap;
    justify-self: start;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 7px 14px;
    border-radius: 9px;
    font-size: 0.9rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 18%, transparent);
  }

  /* The server card */
  .server {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
    padding: 20px 24px;
    border-left: 5px solid var(--good);
  }

  .server.counting {
    border-left-color: var(--bad);
  }

  .stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
  }

  .stat {
    display: grid;
    align-content: start;
    gap: 4px;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    min-width: 0;
  }

  .s-label {
    color: var(--muted);
    font-size: 0.78rem;
  }

  .s-value {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
    font-size: 1.02rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .s-sub {
    font-size: 0.78rem;
    color: var(--muted);
  }

  .s-sub.info {
    color: var(--info, var(--accent));
  }

  .s-sub.warn {
    color: var(--warn);
  }

  .dot {
    width: 9px;
    height: 9px;
    flex: none;
    border-radius: 50%;
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 22%, transparent);
  }

  .dot.bad {
    background: var(--bad);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--bad) 22%, transparent);
  }

  .dot.warn {
    background: var(--warn);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--warn) 22%, transparent);
  }

  .dot.info {
    background: var(--accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
  }

  .dot.off {
    background: var(--muted);
    box-shadow: none;
  }

  .countdown {
    display: grid;
    gap: 6px;
  }

  .actions,
  .schedule {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .schedule {
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }

  .choices .danger {
    justify-content: flex-start;
    background: var(--bad);
    color: #fff;
    border: 0;
  }

  .control {
    display: grid;
    align-content: center;
    gap: 12px;
    max-width: 760px;
  }

  .row {
    display: grid;
    grid-template-columns: 7.5rem minmax(0, 1fr);
    align-items: center;
    gap: 10px;
  }

  .label {
    font-weight: 600;
  }

  label.row {
    display: grid;
    margin: 0;
  }

  .reason-input {
    width: 100%;
    padding: 6px 10px;
    font-size: 0.86rem;
  }

  .reason {
    color: var(--text);
    overflow-wrap: anywhere;
  }

  .choices {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .choices button {
    justify-content: flex-start;
    padding: 6px 12px;
    font-size: 0.86rem;
  }

  .danger-ghost {
    background: transparent;
    color: var(--bad);
    border: 1px solid color-mix(in srgb, var(--bad) 45%, var(--border));
  }

  .danger-ghost:hover:not(:disabled) {
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .status {
    color: var(--bad);
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    font-size: 0.8rem;
  }

  .clock {
    font-size: 2.2rem;
    font-weight: 700;
    line-height: 1;
  }

  .control .ghost {
    justify-self: start;
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
    padding: 6px 14px;
  }

  .control .ghost:hover:not(:disabled) {
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  /* Short view of the other tabs */
  .tiles {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
    gap: 14px;
  }

  .tiles.three {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 2fr);
  }

  .room .t-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .pill {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 2px 10px;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 700;
    border: 1px solid color-mix(in srgb, var(--good) 40%, transparent);
    background: color-mix(in srgb, var(--good) 12%, transparent);
  }

  .pill.info {
    border-color: color-mix(in srgb, var(--accent) 40%, transparent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .pill.bad {
    border-color: color-mix(in srgb, var(--bad) 40%, transparent);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .badges {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .badge {
    padding: 2px 9px;
    border-radius: 999px;
    font-size: 0.78rem;
    border: 1px solid var(--border);
    background: color-mix(in srgb, var(--text) 5%, transparent);
    color: var(--muted);
    white-space: nowrap;
  }

  .badge.info {
    color: var(--text);
    border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  }

  .badge.warn {
    color: var(--warn);
    border-color: color-mix(in srgb, var(--warn) 45%, transparent);
  }

  .lock.bad {
    color: var(--bad);
  }

  .room button {
    justify-self: start;
    margin-top: 4px;
  }

  .tile {
    display: grid;
    align-content: start;
    gap: 6px;
    padding: 16px 18px;
    text-align: left;
    justify-content: stretch;
    color: var(--text);
  }

  button.tile {
    cursor: pointer;
    border: 1px solid var(--border);
    background: var(--surface);
  }

  button.tile:hover {
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .t-head {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--muted);
    font-size: 0.88rem;
    font-weight: 600;
  }

  .go,
  .t-head .link {
    margin-left: auto;
  }

  .link {
    background: none;
    border: 0;
    padding: 0;
    color: var(--accent);
    font-size: 0.84rem;
    justify-content: flex-start;
  }

  .big {
    font-size: 1.25rem;
    font-weight: 700;
  }

  .line {
    font-size: 0.88rem;
  }

  .line.good {
    color: var(--good);
  }

  .line.warn {
    color: var(--warn);
  }

  .line.info {
    color: var(--accent);
  }

  .events ul {
    list-style: none;
    margin: 4px 0 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .events li {
    display: grid;
    grid-template-columns: 4px 6.5rem minmax(0, 1fr);
    align-items: center;
    gap: 10px;
    font-size: 0.88rem;
  }

  .events .bar {
    width: 4px;
    height: 1.2em;
    border-radius: 2px;
  }

  .when {
    white-space: nowrap;
  }

  .what {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .small {
    font-size: 0.84rem;
  }

  @media (max-width: 760px) {
    .stats {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .server,
    .tiles,
    .tiles.three {
      grid-template-columns: minmax(0, 1fr);
    }

    /* Tabs stay in one row and scroll sideways */
    .switch {
      flex-wrap: nowrap;
      overflow-x: auto;
      justify-self: stretch;
      scrollbar-width: none;
    }

    .switch button {
      flex: none;
      padding: 7px 12px;
    }

    .head {
      padding: 18px;
    }

    .row {
      grid-template-columns: minmax(0, 1fr);
      gap: 6px;
    }
  }
</style>
