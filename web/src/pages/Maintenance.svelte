<script>
  // Maintenance (start, plan, extend, end - manual and from Uptime Kuma), like the commands; the next and the last
  // three maintenances. Announcements are on the page "Communication".
  import { api } from '../lib/api.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import { dateTime, duration } from '../lib/format.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  // Ticks every second for the countdown and the progress.
  let now = $state(Date.now());

  // New maintenance
  let mode = $state('now');
  let inValue = $state('15m');
  let durationValue = $state('1h');
  let title = $state('');
  let description = $state('');

  async function load() {
    if (!me.may.maintenance) return;
    try {
      data = await api('GET', '/maintenance');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    load();
    const timer = setInterval(load, 5000);
    const clockTimer = setInterval(() => (now = Date.now()), 1000);
    return () => {
      clearInterval(timer);
      clearInterval(clockTimer);
    };
  });

  async function act(action, body = {}, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/maintenance/' + action, body);
      message = t('mt.done.' + action);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Uptime Kuma: the same actions, but on a maintenance in Kuma.
  async function kumaAct(id, action, body = {}, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', `/maintenance/kuma/${id}/${action}`, body);
      message = t('mt.kdone.' + action);
      setTimeout(load, 800);
    } catch (e) {
      error = kumaError(e);
    } finally {
      busy = false;
    }
  }

  // Errors Kuma itself gives come as "kuma:<text>" - shown with its own text.
  const kumaError = (e) => (e?.key?.startsWith('kuma:') ? t('error.kuma', { msg: e.key.slice(5) }) : errorText(e));

  function create(event) {
    event.preventDefault();
    const duration = durationValue === 'open' ? '' : durationValue;
    if (data.mode === 'kuma') {
      kumaCreate(duration);
      return;
    }
    if (mode === 'now') {
      act('start', { duration, title }, t('mt.startConfirm'));
    } else {
      act('schedule', { in: inValue, duration, title });
    }
  }

  async function kumaCreate(duration) {
    if (mode === 'now' && !confirm(t('mt.startConfirm'))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/maintenance/kuma/create', {
        mode, in: inValue, duration, title: title.trim() || t('overview.maintenance'), description,
      });
      message = t(mode === 'now' ? 'mt.done.start' : 'mt.done.schedule');
      title = '';
      description = '';
      setTimeout(load, 800);
    } catch (e) {
      error = kumaError(e);
    } finally {
      busy = false;
    }
  }

  const IN = ['5m', '10m', '15m', '30m', '1h'];
  const DURATIONS = ['15m', '30m', '1h', '2h', 'open'];
  const phaseTone = { none: 'good', planned: 'info', upcoming: 'warn', active: 'bad' };
  const running = $derived(data?.current);
  const tone = $derived(data ? phaseTone[data.phase] ?? '' : '');
  // The current maintenance in Kuma (for its actions).
  const kumaCurrent = $derived(
    running?.source === 'kuma' ? (data?.kumaInfo?.maintenances ?? []).find((m) => m.id === running.id) ?? null : null,
  );

  // The next three maintenances after the current one - MRadmin's windows (Kuma and manual) plus the ones Kuma has
  // scheduled that are not among them, by start.
  const upcoming = $derived.by(() => {
    if (!data) return [];
    const isCurrent = (w) => running && w.source === running.source && w.id === running.id && w.start === running.start;
    const own = (data.windows ?? []).filter((w) => !isCurrent(w) && w.start > now);
    const seen = new Set(own.filter((w) => w.source === 'kuma').map((w) => w.id));
    if (running?.source === 'kuma') seen.add(running.id);
    const kuma = (data.kumaInfo?.maintenances ?? [])
      .filter((m) => m.status === 'scheduled' && !seen.has(m.id) && m.start > now)
      .map((m) => ({ source: 'kuma', id: m.id, title: m.title, start: m.start, end: m.end }));
    return [...own, ...kuma].sort((a, b) => a.start - b.start).slice(0, 3);
  });

  // The last three maintenances that are over - MRadmin's own history (Kuma and manual) plus the ones Kuma reports as
  // ended that MRadmin did not see end, newest first.
  const history = $derived.by(() => {
    if (!data) return [];
    const own = (data.history ?? []).map((h) => ({ ...h, when: h.endedAt }));
    const seen = new Set(own.filter((h) => h.source === 'kuma').map((h) => h.id));
    const kuma = (data.kumaInfo?.maintenances ?? [])
      .filter((m) => m.status === 'ended' && !seen.has(m.id))
      .map((m) => ({ source: 'kuma', id: m.id, title: m.title, start: m.start, end: m.end, endedAt: m.end, when: m.end || m.start, cancelled: false }));
    return [...own, ...kuma].sort((a, b) => b.when - a.when).slice(0, 3);
  });

  /** "1:05:09" or "4:09" */
  function countdown(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
  }

  /** "15m", "1h30m", "2h" -> ms (like the commands); null if it cannot be read. */
  function parse(value) {
    const units = { s: 1000, m: 60000, h: 3600000, d: 86400000 };
    let total = 0;
    let found = false;
    for (const [, n, u] of String(value).toLowerCase().matchAll(/(\d+)\s*([smhd])/g)) {
      total += Number(n) * units[u];
      found = true;
    }
    return found ? total : null;
  }

  const time = (ms) => new Date(ms).toLocaleTimeString(lang === 'de' ? 'de-AT' : 'en-GB', { hour: '2-digit', minute: '2-digit' });

  // What the form would do: when it starts and ends.
  const preview = $derived.by(() => {
    const start = mode === 'now' ? now : (parse(inValue) == null ? null : now + parse(inValue));
    if (start == null) return null;
    const length = durationValue === 'open' ? null : parse(durationValue);
    const startText = mode === 'now' ? t('mt.preview.now') : t('mt.preview.at', { time: time(start) });
    const endText = durationValue === 'open' ? t('overview.openEnd') : length == null ? '?' : t('mt.preview.until', { time: time(start + length) });
    return `${startText} · ${endText}`;
  });
</script>

<!-- A row of choices like a switch (time until start, duration) with a free field for other values. -->
{#snippet choices(list, value, set, label, openLabel)}
  <div class="choice-row">
    <div class="seg" role="radiogroup" aria-label={label}>
      {#each list as v}
        <button type="button" role="radio" aria-checked={value === v} class:active={value === v} onclick={() => set(v)}>
          {v === 'open' ? openLabel : v}
        </button>
      {/each}
    </div>
    {#if value !== 'open'}
      <input class="short mono" value={value} oninput={(e) => set(e.currentTarget.value)} aria-label={label} />
    {/if}
  </div>
{/snippet}

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.tool} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.tool} /></svg>
      </span>
      <div>
        <h1>{t('mt.title')}</h1>
        <p class="muted">{t('mt.subtitle')}</p>
      </div>
    </div>
    {#if data}
      <span class="state {tone}"><span class="dot"></span>{t('overview.phase.' + data.phase)}</span>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if me.may.maintenance && data}
    <!-- The current maintenance: time left with a bar, or the countdown to its start -->
    <section class="now-tile {tone}">
      {#if running}
        {@const total = running.end ? running.end - running.start : null}
        {@const share = total ? Math.min(1, Math.max(0, (now - running.start) / total)) : null}
        <div class="now-main">
          <div class="now-text">
            <span class="status">{t('overview.phase.' + data.phase)}</span>
            <h2>{running.title || t('overview.maintenance')}</h2>
            <span class="muted small">
              {dateTime(running.start)} – {running.end ? dateTime(running.end) : t('overview.openEnd')} · {t('mt.source.' + running.source)}
            </span>
          </div>
          <div class="big-clock">
            {#if !running.active}
              <span class="label">{t('mt.startsIn')}</span>
              <span class="clock mono">{countdown(running.start - now)}</span>
            {:else if running.end}
              <span class="label">{t('mt.timeLeft')}</span>
              <span class="clock mono">{countdown(running.end - now)}</span>
            {:else}
              <span class="label">{t('mt.runningFor')}</span>
              <span class="clock mono">{countdown(now - running.start)}</span>
            {/if}
          </div>
        </div>
        {#if running.active && share != null}
          <div class="progress"><span style="width: {share * 100}%"></span></div>
          <div class="progress-legend muted small">
            <span>{t('mt.runningFor')} {duration(now - running.start)}</span>
            <span>{t('mt.total')} {duration(total)}</span>
          </div>
        {/if}
        {#if data.manage && data.mode === 'local'}
          <div class="now-actions">
            {#if running.source === 'manual'}
              <div class="seg" role="group" aria-label={t('mt.extend')}>
                {#each ['+15m', '+30m', '+1h', '-15m'] as by}
                  <button type="button" disabled={busy} onclick={() => act('extend', { by: by.replace('+', '') })}>{by}</button>
                {/each}
              </div>
            {/if}
            <button class="danger" disabled={busy}
              onclick={() => act('stop', {}, t(running.active ? 'mt.stopConfirm' : 'mt.cancelConfirm'))}>
              {running.active ? t('mt.stop') : t('mt.cancel')}
            </button>
          </div>
          {#if running.source === 'kuma'}<p class="muted tiny">{t('mt.kumaHint')}</p>{/if}
        {:else if data.manage && kumaCurrent && data.kumaInfo.state === 'connected'}
          {@const km = kumaCurrent}
          <div class="now-actions">
            {#if km.strategy === 'single' && km.status !== 'ended'}
              <div class="seg" role="group" aria-label={t('mt.extend')}>
                {#each ['+15m', '+30m', '+1h'] as by}
                  <button type="button" disabled={busy} onclick={() => kumaAct(km.id, 'extend', { by: by.replace('+', '') })}>{by}</button>
                {/each}
              </div>
            {/if}
            <span class="grow"></span>
            {#if km.status === 'inactive'}
              <button class="secondary" disabled={busy} onclick={() => kumaAct(km.id, 'resume')}>▶ {t('mt.resume')}</button>
            {:else if km.status !== 'ended'}
              <button class="secondary" disabled={busy}
                onclick={() => kumaAct(km.id, 'pause', {}, km.status === 'under-maintenance' ? t('mt.pauseConfirm') : null)}>
                {km.status === 'under-maintenance' ? t('mt.endNow') : t('mt.pause')}</button>
            {/if}
            <button class="danger" disabled={busy}
              onclick={() => kumaAct(km.id, 'delete', {}, t('mt.deleteConfirm', { title: km.title }))}>{t('mt.delete')}</button>
          </div>
        {/if}
      {:else}
        <div class="calm">
          <span class="calm-mark" aria-hidden="true">✓</span>
          <div>
            <h2>{t('overview.phase.none')}</h2>
            <span class="muted small">{t('mt.none')}</span>
          </div>
        </div>
      {/if}

      <div class="source">
        {#if data.mode === 'kuma'}
          <span class="src-chip {data.kuma === 'connected' ? 'good' : data.kuma === 'connecting' ? 'warn' : 'bad'}">
            <span class="dot"></span>Uptime Kuma: {t('mt.kuma.' + data.kuma)}{#if data.kumaInfo?.version} · {data.kumaInfo.version}{/if}
          </span>
          {#if data.kumaUpdate}<span class="muted small">{t('mt.kumaUpdated', { time: dateTime(data.kumaUpdate) })}</span>{/if}
          {#if data.manage}
            <button class="secondary mini" disabled={busy} onclick={() => act('refresh')}>{t('mt.refresh')}</button>
          {/if}
          {#if data.kumaInfo?.url}<a class="small" href={data.kumaInfo.url + '/maintenance'} target="_blank" rel="noopener">{t('mt.openKuma')} ↗</a>{/if}
        {:else}
          <span class="muted small">{t('mt.localMode')}</span>
        {/if}
      </div>
    </section>

    <!-- The next three maintenances, wherever they come from -->
    <section class="card tile">
      <h2>{t('mt.coming')}</h2>
      {#if upcoming.length === 0}
        <p class="muted small">{t('mt.comingNone')}</p>
      {:else}
        <ul class="history">
          {#each upcoming as w (w.source + w.id + w.start)}
            <li class="item info">
              <div class="i-top">
                <span class="status">{t('mt.startsIn')} {duration(w.start - now)}</span>
                <span class="chip origin {w.source}">{t('mt.origin.' + w.source)}</span>
              </div>
              <strong>{w.title || t('overview.maintenance')}</strong>
              <span class="muted small">{dateTime(w.start)} – {w.end ? dateTime(w.end) : t('overview.openEnd')}</span>
              <span class="chip">{w.end ? t('mt.hist.lasts', { d: duration(w.end - w.start) }) : t('overview.openEnd')}</span>
            </li>
          {/each}
        </ul>
      {/if}
    </section>

    <!-- The last three maintenances, wherever they came from -->
    <section class="card tile">
      <h2>{t('mt.history')}</h2>
      {#if history.length === 0}
        <p class="muted small">{t('mt.historyNone')}</p>
      {:else}
        <ul class="history">
          {#each history as h (h.source + h.id + h.when)}
            <li class="item {h.cancelled ? '' : 'good'}">
              <div class="i-top">
                <span class="status">{h.cancelled ? t('mt.hist.cancelled') : t('mt.hist.ended')}</span>
                <span class="chip origin {h.source}">{t('mt.origin.' + h.source)}</span>
              </div>
              <strong>{h.title || t('overview.maintenance')}</strong>
              <span class="muted small">
                {#if h.cancelled}
                  {t('mt.hist.planned', { time: h.start ? dateTime(h.start) : '-' })}
                {:else}
                  {h.start ? dateTime(h.start) : '?'} – {dateTime(h.endedAt)}
                {/if}
              </span>
              {#if !h.cancelled && h.start && h.endedAt > h.start}
                <span class="chip">{t('mt.hist.lasted', { d: duration(h.endedAt - h.start) })}</span>
              {/if}
            </li>
          {/each}
        </ul>
      {/if}
    </section>

    {#if data.mode === 'kuma' && data.kumaInfo.state !== 'connected'}
      <p class="error small">{t('mt.kumaNotConnected')}{#if data.kumaInfo.problem} ({data.kumaInfo.problem}){/if}</p>
    {/if}

    <!-- New maintenance: when on the left, what on the right -->
    {#if data.manage && (data.mode === 'kuma' ? data.kumaInfo.canCreate && data.kumaInfo.state === 'connected' : !data.manual)}
      <form class="card tile" onsubmit={create}>
        <h2>{t('mt.new')}{#if data.mode === 'kuma'}<span class="chip">Uptime Kuma</span>{/if}</h2>
        <div class="form-cols">
          <div class="form-col">
            <div class="seg wide" role="radiogroup">
              <button type="button" role="radio" aria-checked={mode === 'now'} class:active={mode === 'now'} onclick={() => (mode = 'now')}>{t('mt.now')}</button>
              <button type="button" role="radio" aria-checked={mode === 'plan'} class:active={mode === 'plan'} onclick={() => (mode = 'plan')}>{t('mt.plan')}</button>
            </div>
            {#if mode === 'plan'}
              <div class="field">
                <span class="flabel">{t('mt.in')}</span>
                {@render choices(IN, inValue, (v) => (inValue = v), t('mt.in'), '')}
              </div>
            {/if}
            <div class="field">
              <span class="flabel">{t('mt.duration')}</span>
              {@render choices(DURATIONS.filter((v) => !(v === 'open' && data.mode === 'kuma' && mode === 'plan')), durationValue,
                (v) => (durationValue = v), t('mt.duration'), t('overview.openEnd'))}
            </div>
            <p class="muted tiny">{t('mt.durationHint')}</p>
          </div>
          <div class="form-col">
            <div class="field">
              <label class="flabel" for="mt-title">{t('mt.titleLabel')}</label>
              <input id="mt-title" bind:value={title} maxlength="100" placeholder={t('mt.titlePlaceholder')} />
            </div>
            {#if data.mode === 'kuma'}
              <div class="field">
                <label class="flabel" for="mt-desc">{t('mt.description')}</label>
                <input id="mt-desc" bind:value={description} maxlength="300" placeholder={t('mt.descriptionPlaceholder')} />
              </div>
              <p class="muted tiny">{t('mt.kumaCreateHint')}</p>
            {/if}
            {#if preview}<div class="preview" class:bad={mode === 'now'}>{preview}</div>{/if}
            <button type="submit" class="submit" class:danger={mode === 'now'} disabled={busy}>{mode === 'now' ? t('mt.startNow') : t('mt.schedule')}</button>
          </div>
        </div>
      </form>
    {:else if data.mode === 'kuma' && data.manage && !data.kumaInfo.canCreate}
      <p class="muted tiny">{t('mt.kumaCannotCreate')}</p>
    {/if}
  {/if}
</div>

<style>
  .page {
    --mt: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--mt) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--mt) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--mt);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
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
    color: var(--mt);
    background: color-mix(in srgb, var(--mt) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--mt) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  /* Colours of the phases: none green, planned blue, countdown yellow, running red */
  .good {
    --tn: var(--good);
  }

  .info {
    --tn: var(--accent);
  }

  .warn {
    --tn: var(--warn);
  }

  .bad {
    --tn: var(--bad);
  }

  .state {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    border-radius: 999px;
    font-weight: 700;
    color: var(--tn, var(--muted));
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--tn, var(--muted)) 40%, var(--border));
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--tn, var(--muted));
  }

  /* The current maintenance */
  .now-tile {
    display: grid;
    gap: 14px;
    padding: 20px 22px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 5px solid var(--tn, var(--muted));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tn, var(--muted)) 12%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .now-main {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 14px 24px;
  }

  .now-text {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  .now-text h2 {
    font-size: 1.3rem;
    overflow-wrap: anywhere;
  }

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--tn, var(--muted));
  }

  .big-clock {
    display: grid;
    justify-items: end;
  }

  .label {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 700;
  }

  .clock {
    font-size: 2.3rem;
    font-weight: 800;
    line-height: 1.1;
    color: var(--tn, var(--text));
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .progress {
    height: 10px;
    border-radius: 5px;
    overflow: hidden;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .progress span {
    display: block;
    height: 100%;
    border-radius: 5px;
    background: var(--tn, var(--accent));
    transition: width 1s linear;
  }

  .progress-legend {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    margin-top: -8px;
  }

  .now-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }

  .calm {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .calm-mark {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    flex-shrink: 0;
    border-radius: 50%;
    font-size: 1.3rem;
    font-weight: 800;
    color: #fff;
    background: var(--good);
  }

  .calm h2 {
    font-size: 1.15rem;
  }

  .source {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .src-chip {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 0.82rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    color: var(--tn);
    background: color-mix(in srgb, var(--tn) 12%, transparent);
  }

  /* Tiles */
  .tile {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.05rem;
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  /* The last three maintenances next to each other (one below the other on phones) */
  .history {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .history .item {
    align-content: start;
  }

  .history .chip {
    justify-self: start;
  }

  .chip.origin.kuma {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  @media (max-width: 860px) {
    .history {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  /* New maintenance: two columns on wide screens */
  .form-cols {
    display: grid;
    gap: 16px 28px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
    align-items: start;
  }

  .form-col {
    display: grid;
    gap: 12px;
  }

  .grow {
    flex: 1;
  }

  /* A maintenance as a card with the colour of its state on the left */

  .item {
    display: grid;
    gap: 4px;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--tn, var(--muted));
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .i-top {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .i-top .status {
    margin-right: auto;
  }

  .i-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
    margin-top: 6px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  /* Switches */
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg.wide {
    display: flex;
  }

  .seg.wide button {
    flex: 1;
  }

  .seg button {
    background: transparent;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 9px;
    font-size: 0.86rem;
  }

  .seg button:not(:disabled):hover {
    filter: none;
    color: var(--text);
  }

  .seg button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .choice-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .short {
    width: 6.5em;
  }

  .field {
    display: grid;
    gap: 6px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  .preview {
    padding: 10px 12px;
    border-radius: 10px;
    font-weight: 700;
    font-size: 0.9rem;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
  }

  .preview.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 8%, var(--surface-2));
    border-color: color-mix(in srgb, var(--bad) 30%, var(--border));
  }

  .submit {
    justify-self: start;
  }

  .tiny {
    font-size: 0.76rem;
    margin: 0;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .big-clock {
      justify-items: start;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .progress span {
      transition: none;
    }
  }
</style>
