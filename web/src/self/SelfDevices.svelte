<script>
  // The player's signed-in browsers: this one on top with how long the sign-in still lasts (the earlier of "at most
  // session-days after the code" and "idle-days without a visit"), the others below with "sign out". /web logout in game
  // signs out all of them.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let devices = $state(null);
  let error = $state('');
  let busy = $state(false);
  let now = $state(Date.now());

  async function load() {
    try {
      devices = await api('GET', '/self/devices');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  $effect(() => {
    const clock = setInterval(() => (now = Date.now()), 60_000);
    return () => clearInterval(clock);
  });

  async function revoke(id) {
    busy = true;
    try {
      await api('POST', '/self/devices/' + id + '/revoke');
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function revokeOthers() {
    if (!confirm(t('self.devices.othersConfirm'))) return;
    busy = true;
    try {
      for (const d of others) await api('POST', '/self/devices/' + d.id + '/revoke');
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // "Firefox on Windows" from the browser's description - only a hint for recognising the device.
  function browser(agent) {
    const a = agent || '';
    return /Edg\//.test(a) ? 'Edge' : /OPR\//.test(a) ? 'Opera' : /Firefox\//.test(a) ? 'Firefox'
      : /Chrome\//.test(a) ? 'Chrome' : /Safari\//.test(a) ? 'Safari' : t('self.browser');
  }
  function system(agent) {
    const a = agent || '';
    return /Android/.test(a) ? 'Android' : /iPhone|iPad/.test(a) ? 'iOS' : /Windows/.test(a) ? 'Windows'
      : /Mac OS X/.test(a) ? 'macOS' : /Linux/.test(a) ? 'Linux' : '';
  }
  const mobile = (agent) => /Android|iPhone|iPad|Mobile/.test(agent || '');
  const name = (d) => (system(d.agent) ? t('self.deviceOn', { browser: browser(d.agent), os: system(d.agent) }) : browser(d.agent));

  /** "4 days 3 h" / "5 h 10 min" / "8 min" until a time. */
  function left(until) {
    const m = Math.max(0, Math.round((until - now) / 60000));
    const d = Math.floor(m / 1440);
    const h = Math.floor((m % 1440) / 60);
    if (d) return t('self.devices.leftDays', { d, h });
    if (h) return t('self.devices.leftHours', { h, m: m % 60 });
    return t('self.devices.leftMinutes', { m });
  }

  const current = $derived(devices?.find((d) => d.current) ?? null);
  const others = $derived(devices?.filter((d) => !d.current) ?? []);
  // How much of the whole time (from the code to the latest end) is left - for the bar.
  const share = $derived(current ? Math.max(0, Math.min(1, (current.expires - now) / Math.max(1, current.maxUntil - current.verified))) : 0);
  const idleFirst = $derived(current ? current.idleUntil < current.maxUntil : false);
  const tone = $derived(share < 0.15 ? 'bad' : share < 0.35 ? 'warn' : 'good');
</script>

<div class="page">
  <header class="head">
    <span class="watermark" aria-hidden="true">📱</span>
    <div class="intro">
      <span class="icon" aria-hidden="true">📱</span>
      <div>
        <h1>{t('self.devicesTitle')}</h1>
        <p class="muted">{t('self.devices.subtitle')}</p>
      </div>
    </div>
    {#if devices}
      <div class="numbers"><span><b>{devices.length}</b> {t('self.devices.count')}</span></div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if devices}
    {#if current}
      <section class="card this tone-{tone}">
        <div class="this-head">
          <span class="d-icon" aria-hidden="true">{mobile(current.agent) ? '📱' : '💻'}</span>
          <div class="names">
            <strong>{name(current)}</strong>
            <span class="badge">{t('self.thisDevice')}</span>
          </div>
        </div>
        <div class="validity">
          <div class="v-line">
            <span class="muted">{t('self.devices.validFor')}</span>
            <b class="{tone}-text">{left(current.expires)}</b>
          </div>
          <div class="bar"><span class={tone} style="width: {share * 100}%"></span></div>
          <p class="muted small">
            {idleFirst ? t('self.devices.idleHint', { time: dateTime(current.idleUntil) }) : t('self.devices.maxHint', { time: dateTime(current.maxUntil) })}
          </p>
        </div>
        <div class="facts">
          <div><span class="muted">{t('self.devices.signedIn')}</span>{dateTime(current.verified)}</div>
          <div><span class="muted">{t('self.devices.latest')}</span>{dateTime(current.maxUntil)}</div>
          <div><span class="muted">{t('self.devices.idle')}</span>{dateTime(current.idleUntil)}</div>
        </div>
      </section>
    {/if}

    <section class="card tile">
      <div class="tile-head">
        <h2>{t('self.devices.others')}</h2>
        {#if others.length > 1}
          <button class="danger small" disabled={busy} onclick={revokeOthers}>{t('self.devices.signOutOthers')}</button>
        {/if}
      </div>
      {#if others.length === 0}
        <p class="muted small empty">{t('self.devices.noOthers')}</p>
      {:else}
        <ul class="list">
          {#each others as d (d.id)}
            <li class="device">
              <span class="d-icon" aria-hidden="true">{mobile(d.agent) ? '📱' : '💻'}</span>
              <div class="d-body">
                <strong>{name(d)}</strong>
                <span class="muted small">{t('self.lastSeen', { time: dateTime(d.lastSeen) })}</span>
                <span class="muted small">{t('self.devices.endsIn', { left: left(d.expires) })}</span>
              </div>
              <button class="secondary small" disabled={busy} onclick={() => revoke(d.id)}>{t('self.revoke')}</button>
            </li>
          {/each}
        </ul>
      {/if}
      <p class="muted small hint">{t('self.devicesHint')}</p>
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --dv: var(--accent);
    display: grid;
    gap: 16px;
    max-width: 900px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--dv) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--dv) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -10px;
    bottom: -34px;
    font-size: 9rem;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    position: relative;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    font-size: 1.8rem;
    background: color-mix(in srgb, var(--dv) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--dv) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .numbers {
    position: relative;
  }

  .numbers span {
    padding: 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    font-size: 0.88rem;
  }

  .numbers b {
    font-size: 1.1rem;
    margin-right: 2px;
  }

  /* This device and its sign-in */
  .this {
    --tn: var(--good);
    display: grid;
    gap: 16px;
    border-top: 4px solid var(--tn);
  }

  .this.tone-warn {
    --tn: var(--warn);
  }

  .this.tone-bad {
    --tn: var(--bad);
  }

  .this-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .d-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    border-radius: 12px;
    font-size: 1.4rem;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .names {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .names strong {
    font-size: 1.1rem;
  }

  .badge {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--dv);
    background: color-mix(in srgb, var(--dv) 15%, transparent);
  }

  .validity {
    display: grid;
    gap: 6px;
  }

  .v-line {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;
  }

  .v-line b {
    font-size: 1.5rem;
    font-weight: 800;
  }

  .validity p {
    margin: 0;
  }

  .bar {
    height: 8px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: var(--good);
    transition: width 0.4s;
  }

  .bar .warn {
    background: var(--warn);
  }

  .bar .bad {
    background: var(--bad);
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr));
  }

  .facts > div {
    display: grid;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    font-weight: 700;
    font-size: 0.92rem;
  }

  .facts .muted {
    font-size: 0.74rem;
    font-weight: 600;
  }

  /* Other devices */
  .tile-head {
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

  .list {
    list-style: none;
    margin: 14px 0 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .device {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .d-body {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .empty {
    margin: 12px 0 0;
  }

  .hint {
    margin: 14px 0 0;
  }

  .good-text {
    color: var(--good);
  }

  .warn-text {
    color: var(--warn);
  }

  .bad-text {
    color: var(--bad);
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }
</style>
