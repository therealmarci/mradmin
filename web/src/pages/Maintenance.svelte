<script>
  // Maintenance (start, plan, extend, end - manual and from Uptime Kuma) and announcements, like the commands.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  let data = $state(null);
  let presets = $state([]);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  // New maintenance
  let mode = $state('now');
  let inValue = $state('15m');
  let durationValue = $state('1h');
  let title = $state('');
  let description = $state('');

  // Announcement
  let preset = $state('');
  let args = $state('');
  let text = $state('');
  let discord = $state(true);

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
    if (me.may.announce) api('GET', '/announce').then((r) => (presets = r.presets)).catch(() => {});
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
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

  async function announce(event) {
    event.preventDefault();
    const body = preset ? { preset, args, discord: String(discord) } : { text, discord: String(discord) };
    if (!confirm(t('mt.announceConfirm'))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', '/announce', body);
      message = t('mt.announced');
      args = '';
      text = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const IN = ['5m', '10m', '15m', '30m', '1h'];
  const DURATIONS = ['15m', '30m', '1h', '2h', 'open'];
  // Kuma's status of a maintenance - symbol and word.
  const KSTATUS = {
    'under-maintenance': { icon: '🔧', tone: 'bad' },
    scheduled: { icon: '⏰', tone: 'info' },
    inactive: { icon: '⏸', tone: '' },
    ended: { icon: '✓', tone: 'good' },
  };
  const STRATEGIES = ['manual', 'single', 'cron', 'recurring-interval', 'recurring-weekday', 'recurring-day-of-month'];
  const phaseTone = { none: 'good', planned: 'info', upcoming: 'warn', active: 'bad' };
  const chosen = $derived(presets.find((p) => p.name === preset));
  const running = $derived(data?.current);
  const others = $derived((data?.windows ?? []).filter((w) => !running || w.start !== running.start || w.source !== running.source));
</script>

<div class="stack">
  <div>
    <h1>{t('mt.title')}</h1>
    <p class="muted small">{t('mt.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if me.may.maintenance && data}
    <section class="card">
      <div class="row">
        <h2>{t('overview.maintenance')}</h2>
        <span class="tag {phaseTone[data.phase]}">{t('overview.phase.' + data.phase)}</span>
      </div>

      {#if running}
        <div class="facts">
          {#if running.title}<div class="wide"><span class="muted">{t('overview.what')}</span>{running.title}</div>{/if}
          <div><span class="muted">{t('overview.from')}</span>{dateTime(running.start)}</div>
          <div><span class="muted">{t('overview.until')}</span>{running.end ? dateTime(running.end) : t('overview.openEnd')}</div>
          <div><span class="muted">{t('mt.source')}</span>{t('mt.source.' + running.source)}</div>
        </div>
        {#if data.manage && data.mode === 'local'}
          <div class="actions">
            {#if running.source === 'manual'}
              {#each ['+15m', '+30m', '+1h', '-15m'] as by}
                <button class="secondary" disabled={busy} onclick={() => act('extend', { by: by.replace('+', '') })}>{by}</button>
              {/each}
            {/if}
            <button class="danger" disabled={busy}
              onclick={() => act('stop', {}, t(running.active ? 'mt.stopConfirm' : 'mt.cancelConfirm'))}>
              {running.active ? t('mt.stop') : t('mt.cancel')}
            </button>
          </div>
          {#if running.source === 'kuma'}<p class="muted tiny">{t('mt.kumaHint')}</p>{/if}
        {/if}
      {:else}
        <p class="muted small">{t('mt.none')}</p>
      {/if}

      <div class="kuma muted small">
        {#if data.mode === 'kuma'}
          Uptime Kuma: {t('mt.kuma.' + data.kuma)}{#if data.kumaInfo?.version} ({data.kumaInfo.version}){/if}{#if data.kumaUpdate} · {t('mt.kumaUpdated', { time: dateTime(data.kumaUpdate) })}{/if}
          {#if data.manage}
            <button class="linkish" disabled={busy} onclick={() => act('refresh')}>{t('mt.refresh')}</button>
          {/if}
        {:else}
          {t('mt.localMode')}
        {/if}
      </div>
    </section>

    {#if data.mode === 'kuma'}
      {@const k = data.kumaInfo}
      <section class="card">
        <div class="row">
          <h2>{t('mt.kumaTitle')}</h2>
          {#if k.url}<a class="small" href={k.url + '/maintenance'} target="_blank" rel="noopener">{t('mt.openKuma')} ↗</a>{/if}
        </div>
        {#if k.state !== 'connected'}
          <p class="error small">{t('mt.kumaNotConnected')}{#if k.problem} ({k.problem}){/if}</p>
        {/if}
        {#if k.maintenances.length === 0}
          <p class="muted small">{t('mt.kumaNone')}</p>
        {:else}
          <ul class="list">
            {#each k.maintenances as m (m.id)}
              <li>
                <div class="krow">
                  <strong>{m.title || t('overview.maintenance')}</strong>
                  <span class="tag {KSTATUS[m.status]?.tone ?? ''}">{KSTATUS[m.status]?.icon ?? '•'} {t('mt.kstatus.' + (KSTATUS[m.status] ? m.status : 'unknown'))}</span>
                </div>
                <span class="muted small">
                  {t('mt.strategy.' + (STRATEGIES.includes(m.strategy) ? m.strategy : 'other'))}
                  {#if m.start} · {dateTime(m.start)} – {m.end ? dateTime(m.end) : t('overview.openEnd')}{/if}
                </span>
                {#if m.description}<span class="small">{m.description}</span>{/if}
                {#if data.manage && k.state === 'connected'}
                  <div class="actions small-actions">
                    {#if m.strategy === 'single' && m.status !== 'ended'}
                      {#each ['+15m', '+30m', '+1h'] as by}
                        <button class="secondary" disabled={busy} onclick={() => kumaAct(m.id, 'extend', { by: by.replace('+', '') })}>{by}</button>
                      {/each}
                    {/if}
                    {#if m.status === 'inactive'}
                      <button class="secondary" disabled={busy} onclick={() => kumaAct(m.id, 'resume')}>▶ {t('mt.resume')}</button>
                    {:else if m.status !== 'ended'}
                      <button class="secondary" disabled={busy}
                        onclick={() => kumaAct(m.id, 'pause', {}, m.status === 'under-maintenance' ? t('mt.pauseConfirm') : null)}>⏸ {m.status === 'under-maintenance' ? t('mt.endNow') : t('mt.pause')}</button>
                    {/if}
                    <button class="danger" disabled={busy}
                      onclick={() => kumaAct(m.id, 'delete', {}, t('mt.deleteConfirm', { title: m.title }))}>{t('mt.delete')}</button>
                  </div>
                {/if}
              </li>
            {/each}
          </ul>
        {/if}
        {#if !k.canCreate}<p class="muted tiny">{t('mt.kumaCannotCreate')}</p>{/if}
      </section>
    {/if}

    {#if data.mode === 'local' && others.length}
      <section class="card">
        <h2>{t('mt.upcoming')}</h2>
        <ul class="list">
          {#each others as w}
            <li>
              <strong>{w.title || t('overview.maintenance')}</strong>
              <span class="muted small">{dateTime(w.start)} – {w.end ? dateTime(w.end) : t('overview.openEnd')} · {t('mt.source.' + w.source)}</span>
            </li>
          {/each}
        </ul>
      </section>
    {/if}

    {#if data.manage && (data.mode === 'kuma' ? data.kumaInfo.canCreate && data.kumaInfo.state === 'connected' : !data.manual)}
      <form class="card form" onsubmit={create}>
        <h2>{t('mt.new')}{#if data.mode === 'kuma'} · Uptime Kuma{/if}</h2>
        <div class="chips" role="group">
          <button type="button" class:active={mode === 'now'} onclick={() => (mode = 'now')}>{t('mt.now')}</button>
          <button type="button" class:active={mode === 'plan'} onclick={() => (mode = 'plan')}>{t('mt.plan')}</button>
        </div>
        {#if mode === 'plan'}
          <div class="field">
            <span class="flabel">{t('mt.in')}</span>
            <div class="chips">
              {#each IN as v}<button type="button" class:active={inValue === v} onclick={() => (inValue = v)}>{v}</button>{/each}
              <input class="short" bind:value={inValue} aria-label={t('mt.in')} />
            </div>
          </div>
        {/if}
        <div class="field">
          <span class="flabel">{t('mt.duration')}</span>
          <div class="chips">
            {#each DURATIONS.filter((v) => !(v === 'open' && data.mode === 'kuma' && mode === 'plan')) as v}
              <button type="button" class:active={durationValue === v} onclick={() => (durationValue = v)}>{v === 'open' ? t('overview.openEnd') : v}</button>
            {/each}
            {#if durationValue !== 'open'}<input class="short" bind:value={durationValue} aria-label={t('mt.duration')} />{/if}
          </div>
        </div>
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
        <p class="muted tiny">{t('mt.durationHint')}</p>
        <button type="submit" class:danger={mode === 'now'} disabled={busy}>{mode === 'now' ? t('mt.startNow') : t('mt.schedule')}</button>
      </form>
    {/if}
  {/if}

  {#if me.may.announce}
    <form class="card form" onsubmit={announce}>
      <h2>📣 {t('mt.announceTitle')}</h2>
      <p class="muted small">{t('mt.announceHint')}</p>
      <div class="presets">
        <button type="button" class="preset" class:active={preset === ''} onclick={() => (preset = '')}>
          <strong>{t('mt.freeText')}</strong><span class="muted small">{t('mt.freeTextHint')}</span>
        </button>
        {#each presets as p (p.name)}
          <button type="button" class="preset" class:active={preset === p.name} onclick={() => (preset = p.name)}>
            <strong>{p.name}{#if p.discord} · Discord{/if}</strong><span class="muted small">{p.preview}</span>
          </button>
        {/each}
      </div>
      {#if preset === ''}
        <input bind:value={text} maxlength="300" placeholder={t('mt.textPlaceholder')} aria-label={t('mt.freeText')} />
      {:else if chosen?.values}
        <input bind:value={args} maxlength="100" placeholder={t('mt.argsPlaceholder')} aria-label={t('mt.args')} />
      {/if}
      <label class="check"><input type="checkbox" bind:checked={discord} /> {t('mt.toDiscord')}</label>
      <button type="submit" disabled={busy || (preset === '' && !text.trim())}>{t('mt.send')}</button>
    </form>
  {/if}
</div>

<style>
  .row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .tag {
    font-size: 0.8rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--surface-2);
  }

  .tag.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .tag.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .facts {
    display: grid;
    gap: 6px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 150px), 1fr));
    margin-top: 12px;
  }

  .facts > div {
    background: var(--surface-2);
    border-radius: 8px;
    padding: 6px 10px;
    font-weight: 700;
    font-size: 0.92rem;
    overflow-wrap: anywhere;
  }

  .facts .muted {
    display: block;
    font-size: 0.74rem;
    font-weight: 600;
  }

  .facts .wide {
    grid-column: 1 / -1;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 14px;
  }

  .kuma {
    margin-top: 14px;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    align-items: center;
  }

  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 0;
    font-size: 0.85rem;
  }

  .list {
    list-style: none;
    margin: 10px 0 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .list li {
    display: grid;
    gap: 4px;
    background: var(--surface-2);
    border-radius: 8px;
    padding: 10px 12px;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .krow {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .small-actions {
    margin-top: 6px;
  }

  .small-actions button {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .form {
    display: grid;
    gap: 12px;
  }

  .field {
    display: grid;
    gap: 6px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 0.88rem;
  }

  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .short {
    width: 7em;
  }

  .tiny {
    font-size: 0.76rem;
    margin: 0;
  }

  .presets {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .preset {
    display: grid;
    gap: 2px;
    justify-items: start;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-weight: 400;
    padding: 10px 12px;
  }

  .preset.active {
    border-color: var(--accent);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 30%, transparent);
  }

  .check {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 0.9rem;
  }

  .check input {
    width: auto;
  }
</style>
