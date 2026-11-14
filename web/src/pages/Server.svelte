<script>
  // Server -> Control (and the pages Plugins, Log, Console, which are drawn here without their own title). Since the
  // sidebar (1.6): main server and waiting room as two cards side by side, below one form to restart or stop (what,
  // when, why) with the emergency window beside it, then the last events. The plugins have their own page.
  // Data: /api/versions (VersionRoutes.java, field restart) and /api/logs?events=1.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { clock } from '../lib/plugins.js';
  import Toast from '../components/Toast.svelte';
  import EmergencyRestart from '../components/EmergencyRestart.svelte';
  import Plugins from './Plugins.svelte';
  import Logs from './Logs.svelte';
  import Console from './Console.svelte';
  import PageHead from '../components/PageHead.svelte';

  // bare: kept for older callers - the page never draws its own tabs any more (the sidebar has the entries)
  let { me, tab = null, bare = false } = $props();

  const tabs = $derived([
    ...(me.may.versions ? ['overview', 'plugins'] : []),
    ...(me.may.audit || me.may.console ? ['logs'] : []),
    ...(me.may.consoleExecute ? ['console'] : []),
  ]);
  const current = $derived(tabs.includes(tab ?? 'overview') ? (tab ?? 'overview') : tabs[0]);

  let data = $state(null);
  let events = $state(null);
  let error = $state('');
  let message = $state('');
  // Optional own reason for the next restart / stop (empty = MRadmin names one).
  let reason = $state('');
  let busy = $state(false);
  // The form: restart or stop, after which delay (first one of versions.restart-delays).
  let kind = $state('restart');
  let delay = $state(null);
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
  $effect(() => {
    if (r && delay == null && r.delays.length) delay = r.delays[0];
    if (r && data && !data.may.restart && kind === 'restart') kind = 'stop';
  });
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
  // The key figures below the title.
  const headStats = $derived(!r ? [] : [
    { label: t('server.stats.plugins'), value: installed.length },
    ...(updates.length ? [{ label: t('server.stats.updates'), value: updates.length, tone: 'info' }] : []),
    ...(waiting.length ? [{ label: t('server.stats.waiting'), value: waiting.length, tone: 'warn' }] : []),
  ]);
  const nextText = $derived(r && r.nextPlanned > 0
    ? new Date(r.nextPlanned).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' }) : '');
</script>

<div class="page">
  <PageHead title={t(current === 'overview' ? 'server.tab.control' : 'server.tab.' + current)} stats={current === 'overview' ? headStats : []} />

  {#if current === 'overview'}
    {#if error}<div class="error">{error}</div>{/if}
    <Toast text={message} />

    {#if r}
      <!-- A running countdown: across the whole width, with the button to cancel it -->
      {#if r.endsAt > 0}
        <section class="countdown {r.kind}">
          <span class="clock mono">{left > 0 ? clock(left) : t('versions.restartNow')}</span>
          <div class="cd-text">
            <strong>{t('server.countdown.' + r.kind)}</strong>
            <span class="muted small">{r.planned ? t('server.byPlan') : t('versions.restartBy', { name: r.by })}{#if r.reason}{' · ' + r.reason}{/if}</span>
          </div>
          {#if data.may.stop}
            <button class="secondary" disabled={busy} onclick={() => act('/restart/cancel', {}, t('server.cancelled'))}>
              {t(r.planned ? 'server.skipPlanned' : 'versions.restartCancel')}
            </button>
          {/if}
        </section>
      {/if}

      <!-- The network: main server and waiting room side by side, both the same way -->
      <div class="servers">
        <section class="srv">
          <header>
            <span class="dot {r.endsAt > 0 ? 'warn' : 'good'}"></span>
            <h2>{t('nav.serverMain')}</h2>
            <span class="pill {r.endsAt > 0 ? 'warn' : 'good'}">{r.endsAt > 0 ? t('server.countdown.' + r.kind) : t('server.room.online')}</span>
          </header>
          <dl>
            <dt>{t('server.uptime')}</dt>
            <dd title={t('server.since', { time: uptime })}>{uptime}</dd>
            <dt>{t('server.paper')}</dt>
            <dd title={paperTip}>{r.paper.minecraft} · #{r.paper.build}
              {#if paperStaged}<span class="note info">{t('server.paperStagedShort', { build: paperStaged })}</span>
              {:else if r.paper.latest}<span class="note warn">{t('server.paperLatestShort', { build: r.paper.latest })}</span>{/if}</dd>
            <dt>{t('server.starter')}</dt>
            <dd title={starterTip}><span class="dot {starterTone || 'good'}"></span>{starterText}</dd>
            <dt>{t('server.next')}</dt>
            <dd title={pausedUntil ? t('server.pausedBy', { name: r.schedule.pausedBy, time: when(pausedUntil) }) : ''}>
              {#if pausedUntil}{t('server.nextPaused')} <span class="note warn">{t('server.pausedUntil', { time: when(pausedUntil) })}</span>
              {:else}{nextText || t('server.nextNone')}{/if}</dd>
          </dl>
          {#if data.may.stop && r.schedule.times > 0}
            <footer>
              {#if pausedUntil}
                <button class="secondary small-btn" disabled={busy} onclick={() => act('/restart/schedule', { pause: 'false' }, t('server.resumeDone'))}>
                  {t('server.resume')}
                </button>
              {:else}
                <button class="secondary small-btn" disabled={busy}
                  onclick={() => act('/restart/schedule', { pause: 'true' }, t('server.pauseDone'), t('server.pauseConfirm'))}>
                  {t('server.pause')}
                </button>
              {/if}
            </footer>
          {/if}
        </section>

        {#if room}
          <section class="srv">
            <header>
              <span class="dot {roomTone}"></span>
              <h2>{t('nav.serverRoom')}</h2>
              <span class="pill {roomTone}">{roomState}</span>
            </header>
            <dl>
              <dt>{t('server.uptime')}</dt>
              <dd>{room.online && room.startedAt ? duration(Math.max(0, now - room.startedAt)) : '–'}</dd>
              <dt>{t('server.paper')}</dt>
              <dd title={t('server.room.paperTip')}>{room.online && room.paper ? '#' + room.paper : '–'}</dd>
              <dt>{t('server.room.waiting')}</dt>
              <dd>{room.online ? (room.players ?? 0) : '–'}</dd>
              <dt>{t('server.next')}</dt>
              <dd>{room.nextRestart ? when(room.nextRestart) : t('server.nextNone')}</dd>
            </dl>
            {#if data.may.stop}
              <footer>
                <button class="secondary small-btn" disabled={busy || !!roomLock} onclick={restartRoom}>{t('server.room.restart')}</button>
                {#if roomLock}<span class="small muted">{t('server.room.blocked.' + roomLock)}</span>{/if}
                {#if room.error}<span class="small bad-text">{t('server.room.blocked.' + room.error)}</span>{/if}
              </footer>
            {/if}
          </section>
        {/if}
      </div>

      <!-- Restart or stop the main server: what, when, why - one button. The emergency window beside it. -->
      {#if data.may.stop && r.endsAt <= 0}
        <section class="card action">
          <div class="a-head">
            <h2>{t('server.action.title')}</h2>
            {#if r.emergencyDelays.length}
              <button class="danger small-btn" disabled={busy} title={t('server.emergencyTip')}
                onclick={() => emergency.open()}>{t('server.emergencyRow')} …</button>
            {/if}
          </div>
          <div class="a-grid">
            <span class="a-label">{t('server.action.what')}</span>
            <div class="seg" role="radiogroup">
              <button role="radio" aria-checked={kind === 'restart'} class:on={kind === 'restart'} disabled={!data.may.restart}
                title={data.may.restart ? '' : t('server.restartOff')} onclick={() => (kind = 'restart')}>{t('server.restart')}</button>
              <button role="radio" aria-checked={kind === 'stop'} class:on={kind === 'stop'} onclick={() => (kind = 'stop')}>{t('server.stop')}</button>
            </div>
            <span class="a-label">{t('server.action.when')}</span>
            <div class="seg" role="radiogroup">
              {#each r.delays as d (d)}
                <button role="radio" aria-checked={delay === d} class:on={delay === d} onclick={() => (delay = d)}>{delayText(d)}</button>
              {/each}
            </div>
            <label class="a-label" for="restart-reason">{t('server.reason')}</label>
            <input id="restart-reason" class="reason-input" type="text" maxlength="200" bind:value={reason} placeholder={t('server.reasonPlaceholder')} />
          </div>
          <div class="a-go">
            {#if !data.may.restart}<span class="muted small">{t('server.restartOff')}</span>{/if}
            <button class:go-stop={kind === 'stop'} disabled={busy || delay == null || (kind === 'restart' && !data.may.restart)}
              onclick={() => (kind === 'stop' ? stop(delay) : restart(delay))}>
              {t(kind === 'stop' ? 'server.stop' : 'server.restart')} · {delay == null ? '' : delayText(delay)}
            </button>
          </div>
        </section>
      {/if}

      {#if data.may.stop && r.emergencyDelays.length}
        <EmergencyRestart bind:this={emergency} {me} delays={r.emergencyDelays} canRestart={data.may.restart}
          starterActive={r.starter.active} {reason} ondone={emergencyDone} />
      {/if}

      {#if me.may.audit}
        <section class="card events">
          <div class="a-head">
            <h2>{t('server.events')}</h2>
            <a href="/admin/server/logs" onclick={(e) => { e.preventDefault(); go('/admin/server/logs'); }}>{t('server.all')} →</a>
          </div>
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
        </section>
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
    gap: 16px;
  }

  .page > :global(.page-head) {
    margin-bottom: 0;
  }

  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }

  h2 {
    margin: 0;
    font-size: 1rem;
  }

  /* Countdown across the width */
  .countdown {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px 18px;
    padding: 14px 18px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--warn) 45%, var(--border));
    background: color-mix(in srgb, var(--warn) 9%, var(--surface));
  }

  .countdown.stop {
    border-color: color-mix(in srgb, var(--bad) 45%, var(--border));
    background: color-mix(in srgb, var(--bad) 8%, var(--surface));
  }

  .clock {
    font-size: 1.8rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .cd-text {
    display: grid;
    flex: 1;
    min-width: 200px;
  }

  /* The two servers */
  .servers {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 16px;
  }

  .srv {
    display: grid;
    grid-template-rows: auto 1fr auto;
    gap: 12px;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .srv header {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .srv h2 {
    flex: 1;
  }

  dl {
    display: grid;
    grid-template-columns: max-content 1fr;
    gap: 7px 16px;
    margin: 0;
    font-size: 0.92rem;
  }

  dt {
    color: var(--muted);
  }

  dd {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 8px;
    margin: 0;
    font-variant-numeric: tabular-nums;
  }

  .note {
    font-size: 0.8rem;
    font-weight: 600;
  }

  .note.warn {
    color: var(--warn);
  }

  .note.info {
    color: var(--accent);
  }

  .srv footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }

  .pill {
    padding: 2px 10px;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--muted);
    background: var(--surface-2);
  }

  .pill.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .pill.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .pill.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .pill.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .dot {
    flex: none;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--muted);
  }

  .dot.good {
    background: var(--good);
  }

  .dot.warn {
    background: var(--warn);
  }

  .dot.bad {
    background: var(--bad);
  }

  .dot.info {
    background: var(--accent);
  }

  .bad-text {
    color: var(--bad);
  }

  .small-btn {
    padding: 6px 12px;
    font-size: 0.85rem;
  }

  /* The button of the form while "stop" is chosen: red, so a stop is never pressed by mistake */
  .go-stop {
    color: #fff;
    background: var(--bad);
    border-color: var(--bad);
  }

  /* Restart / stop */
  .action,
  .events {
    display: grid;
    gap: 14px;
    padding: 16px 18px;
  }

  .a-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .a-head a {
    font-size: 0.88rem;
    font-weight: 600;
    text-decoration: none;
  }

  .a-grid {
    display: grid;
    grid-template-columns: max-content 1fr;
    align-items: center;
    gap: 10px 16px;
  }

  .a-label {
    margin: 0;
    color: var(--muted);
    font-weight: 400;
    font-size: 0.92rem;
  }

  .seg {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .seg button {
    justify-content: flex-start;
    padding: 6px 14px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text);
    font-size: 0.88rem;
  }

  .seg button.on {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
    background: color-mix(in srgb, var(--accent) 10%, transparent);
    font-weight: 600;
  }

  .reason-input {
    max-width: 420px;
    margin: 0;
  }

  .a-go {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
  }

  @media (max-width: 560px) {
    .a-grid {
      grid-template-columns: 1fr;
      gap: 6px;
    }
  }

  /* Last events */
  .events ul {
    display: grid;
    gap: 2px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .events li {
    display: grid;
    grid-template-columns: 4px 90px 1fr;
    align-items: center;
    gap: 12px;
    padding: 6px 0;
    font-size: 0.9rem;
    border-bottom: 1px solid var(--border);
  }

  .events li:last-child {
    border-bottom: 0;
  }

  .bar {
    width: 4px;
    height: 18px;
    border-radius: 2px;
  }

  .when {
    font-size: 0.82rem;
  }

  .what {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
