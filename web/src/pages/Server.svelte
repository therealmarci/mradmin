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
  // part (since the sidebar, 1.6 - the servers each have their own page): 'main' = the main server (no waiting room
  // card), 'room' = only the waiting room card, null = both as before
  let { me, tab = null, bare = false, part = null } = $props();

  const tabs = $derived([
    ...(me.may.versions ? ['overview', 'plugins'] : []),
    ...(me.may.audit || me.may.console ? ['logs'] : []),
    ...(me.may.consoleExecute ? ['console'] : []),
  ]);
  const current = $derived(tabs.includes(tab ?? 'overview') ? (tab ?? 'overview') : tabs[0]);
  const open = (id) => go(id === 'overview' ? '/admin/server' : '/admin/server/' + id);

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
      events = (await api('GET', '/logs?events=1&limit=6' + (part === 'room' ? '&server=room' : ''))).entries;
    } catch {
      events = [];
    }
  }

  const counting = $derived(!!data && (data.restart.endsAt > 0 || data.waitroom?.dueAt > 0));
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

  // The full control of the waiting room (part room, since 1.6): like the main server, carried out by its agent.
  let roomReason = $state('');
  let roomEmergency = $state();
  let roomPlugins = $state(null);
  const roomLeft = $derived(room && room.dueAt > 0 ? Math.max(0, room.dueAt - now) : 0);
  const roomPaused = $derived(room?.pausedUntil > now ? room.pausedUntil : 0);
  const roomPaperNew = $derived(room && room.paperNewest > 0 && room.paper > 0 && room.paperNewest > room.paper ? room.paperNewest : 0);
  // A restart (not a stop) is refused at once for these reasons; waiting players only delay it.
  const roomRestartLock = $derived(!room ? null : !room.starter ? 'no-starter'
    : room.blocked && room.blocked !== 'players-waiting' ? room.blocked : null);
  /** A refusal of the waiting room: its own text (Neustart gesperrt: ...) if there is one. */
  function roomError(e) {
    const code = e?.key?.startsWith('waitroom-') ? e.key.slice(9) : null;
    const text = code ? t('server.room.blocked.' + code) : '';
    return code && !text.startsWith('server.room.') ? text : errorText(e);
  }
  async function roomAct(action, delay, done, question) {
    if (question && !confirm(question)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/waitroom/control', { action, delay: String(delay ?? 0), reason: roomReason.trim() });
      if (action === 'restart' || action === 'stop') roomReason = '';
      message = done;
      await load();
    } catch (e) {
      error = roomError(e);
    } finally {
      busy = false;
    }
  }
  const roomRestart = (d) => roomAct('restart', d, t('server.room.ordered'), t('server.room.confirmIn', { time: delayText(d) }));
  const roomStop = (d) => roomAct('stop', d, t('server.room.stopOrdered'), t('server.room.stopConfirm', { time: delayText(d) }));
  $effect(() => {
    if (part !== 'room' || !me.may.versions) return;
    api('GET', '/waitroom/plugins').then((d) => (roomPlugins = d.plugins)).catch(() => (roomPlugins = null));
  });

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

  {#if current === 'overview'}
    {#if error}<div class="error">{error}</div>{/if}
    <Toast text={message} />

    {#if r}
      {#if part !== 'room'}
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

      {/if}

      {#if part === 'room' && !room}
        <p class="muted">{t('server.room.none')}</p>
      {/if}
      {#if room && part === 'room'}
        <!-- The waiting room server: the same control as the main server (values, restart / stop, emergency, pause) -->
        <section class="card server" class:counting={room.dueAt > 0}>
          <div class="stats">
            <div class="stat" title={room.error ? t('server.room.blocked.' + room.error) : ''}>
              <span class="s-label">{t('server.room.state')}</span>
              <span class="s-value"><span class="dot {roomTone}"></span>{roomState}</span>
              <span class="s-sub">{t('server.room.players', { n: room.online ? (room.players ?? 0) : 0 })}</span>
            </div>
            <div class="stat">
              <span class="s-label">{t('server.uptime')}</span>
              <span class="s-value">{room.online && room.startedAt ? duration(Math.max(0, now - room.startedAt)) : '–'}</span>
            </div>
            <div class="stat" title={roomPaperNew ? t('server.room.paperNew', { build: roomPaperNew }) : t('server.room.paperTip')}>
              <span class="s-label">{t('server.paper')}</span>
              <span class="s-value"><span class="dot {roomPaperNew ? 'warn' : room.paper ? '' : 'off'}"></span>{room.minecraft ?? ''} · #{room.paper || '–'}</span>
              {#if roomPaperNew}<span class="s-sub warn">{t('server.paperLatestShort', { build: roomPaperNew })}</span>{/if}
            </div>
            <div class="stat">
              <span class="s-label">{t('server.starter')}</span>
              <span class="s-value"><span class="dot {room.starter ? '' : 'off'}"></span>{t(room.starter ? 'server.starterOn' : 'server.starterOff')}</span>
            </div>
            <div class="stat" title={roomPaused ? t('server.pausedBy', { name: room.pausedBy, time: when(roomPaused) }) : ''}>
              <span class="s-label">{t('server.next')}</span>
              {#if roomPaused}
                <span class="s-value"><span class="dot warn"></span>{t('server.nextPaused')}</span>
                <span class="s-sub">{t('server.pausedUntil', { time: when(roomPaused) })}</span>
              {:else}
                {@const next = room.nextPlanned || room.nextRestart}
                <span class="s-value"><span class="dot" class:info={!!next} class:off={!next}></span>{next ? when(next) : t('server.nextNone')}</span>
              {/if}
            </div>
          </div>

          <div class="control">
            {#if room.dueAt > 0}
              <div class="countdown">
                <span class="status">{t('server.countdown.' + room.dueKind)}{#if room.dueForce} · {t('server.emergencyRow')}{/if}</span>
                <span class="clock mono">{roomLeft > 0 ? clock(roomLeft) : t('server.room.dueWaiting')}</span>
                <span class="muted small">{t('versions.restartBy', { name: room.dueBy })}</span>
                {#if room.dueReason}<span class="reason small">{t('server.reasonShown', { reason: room.dueReason })}</span>{/if}
              </div>
              {#if data.may.stop}
                <div class="actions">
                  <button class="ghost" disabled={busy} onclick={() => roomAct('cancel', 0, t('server.cancelled'))}>{t('versions.restartCancel')}</button>
                </div>
              {/if}
            {:else if data.may.stop}
              <label class="row">
                <span class="label">{t('server.reason')}</span>
                <input class="reason-input" type="text" maxlength="200" bind:value={roomReason} placeholder={t('server.reasonPlaceholder')} />
              </label>
              <div class="row">
                <span class="label">{t('server.restart')}</span>
                <div class="choices">
                  {#each r.delays as d (d)}
                    <button class="secondary" disabled={busy || !room.online || !!roomRestartLock} onclick={() => roomRestart(d)}>{delayText(d)}</button>
                  {/each}
                </div>
              </div>
              {#if roomRestartLock}<p class="muted small">{t('server.room.blocked.' + roomRestartLock)}</p>
              {:else if room.blocked === 'players-waiting'}<p class="muted small">{t('server.room.blocked.players-waiting')}</p>{/if}
              <div class="row">
                <span class="label">{t('server.stop')}</span>
                <div class="choices">
                  {#each r.delays as d (d)}
                    <button class="danger-ghost" disabled={busy || !room.online} onclick={() => roomStop(d)}>{delayText(d)}</button>
                  {/each}
                </div>
              </div>
              {#if r.emergencyDelays.length}
                <div class="row">
                  <span class="label">{t('server.emergencyRow')}</span>
                  <div class="choices">
                    <button class="danger" disabled={busy || !room.online} title={t('server.emergencyTip')}
                      onclick={() => roomEmergency.open()}>{t('server.emergencyButton')}</button>
                  </div>
                </div>
              {/if}
              {#if !room.online}<p class="muted small">{t('server.room.blocked.remote-offline')}</p>{/if}
            {/if}

            {#if data.may.stop && room.restartTimes > 0}
              <div class="schedule">
                {#if roomPaused}
                  <span class="small">{t('server.paused', { time: when(roomPaused) })}</span>
                  <button class="ghost" disabled={busy} onclick={() => roomAct('resume', 0, t('server.resumeDone'))}>{t('server.resume')}</button>
                {:else}
                  <button class="ghost" disabled={busy}
                    onclick={() => roomAct('pause', 0, t('server.pauseDone'), t('server.room.pauseConfirm'))}>{t('server.pause')}</button>
                {/if}
              </div>
            {/if}
          </div>
        </section>

        {#if data.may.stop && r.emergencyDelays.length}
          <EmergencyRestart bind:this={roomEmergency} {me} delays={r.emergencyDelays} canRestart={room.starter}
            starterActive={room.starter} reason={roomReason} room ondone={(text) => { roomReason = ''; message = text; load(); }} />
        {/if}

        <div class="tiles">
          <button class="card tile" onclick={() => go('/admin/server/room/plugins')}>
            <span class="t-head">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><path d={ICONS.box} /></svg>
              {t('server.plugins')}
              <span class="go">→</span>
            </span>
            <span class="big">{roomPlugins ? t('server.pluginsInstalled', { n: roomPlugins.length }) : '–'}</span>
            {#if roomPlugins}<span class="line muted">{roomPlugins.map((p) => p.name).join(', ')}</span>{/if}
          </button>
          {#if me.may.audit}
            <div class="card tile events">
              <span class="t-head">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                  stroke-linejoin="round" aria-hidden="true"><path d={ICONS.news} /></svg>
                {t('server.events')}
                <button class="link" onclick={() => go('/admin/server/room/logs')}>{t('server.all')} →</button>
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
      {:else if room && part !== 'main'}
        <!-- The waiting room server: the four values that matter, the button on the right -->
        <section class="card room" class:off={roomTone === 'bad'} class:busy={roomTone === 'info'}>
          <div class="room-title">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round" aria-hidden="true"><path d={ICONS.restore} /></svg>
            <span>{t('server.room.title')}</span>
          </div>
          <div class="stats">
            <div class="stat">
              <span class="s-label">{t('server.room.state')}</span>
              <span class="s-value"><span class="dot {roomTone}"></span>{roomState}</span>
            </div>
            <div class="stat">
              <span class="s-label">{t('server.room.waiting')}</span>
              <span class="s-value">{room.online ? (room.players ?? 0) : '–'}</span>
            </div>
            <div class="stat" title={room.online && room.paper ? 'Paper #' + room.paper : ''}>
              <span class="s-label">{t('server.uptime')}</span>
              <span class="s-value">{room.online && room.startedAt ? duration(Math.max(0, now - room.startedAt)) : '–'}</span>
            </div>
            <div class="stat">
              <span class="s-label">{t('server.next')}</span>
              <span class="s-value">{room.nextRestart ? when(room.nextRestart) : t('server.nextNone')}</span>
            </div>
          </div>
          {#if data.may.stop}
            <div class="room-action">
              <button class="secondary" disabled={busy || !!roomLock} onclick={restartRoom}>{t('server.room.restart')}</button>
              {#if roomLock}<span class="small muted">{t('server.room.blocked.' + roomLock)}</span>{/if}
              {#if room.error}<span class="small bad-text">{t('server.room.blocked.' + room.error)}</span>{/if}
            </div>
          {/if}
        </section>
      {/if}

      {#if part !== 'room'}
      <div class="tiles">
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
      {/if}
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

  /* Tabs: same look as on the duels page */
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

  .room {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 18px;
    padding: 16px 24px;
    border-left: 5px solid var(--good);
  }

  .room.busy {
    border-left-color: var(--accent);
  }

  .room.off {
    border-left-color: var(--bad);
  }

  .room-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
    white-space: nowrap;
  }

  .room-action {
    display: grid;
    justify-items: end;
    gap: 4px;
    max-width: 240px;
    text-align: right;
  }

  .bad-text {
    color: var(--bad);
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
    .tiles {
      grid-template-columns: minmax(0, 1fr);
    }

    .room {
      grid-template-columns: minmax(0, 1fr);
    }

    .room-action {
      justify-items: start;
      text-align: left;
      max-width: none;
    }

    /* Tabs stay in one row and scroll sideways */
    .head {
      padding: 18px;
    }

    .row {
      grid-template-columns: minmax(0, 1fr);
      gap: 6px;
    }
  }
</style>
