<script>
  // Team notices: per kind (only those the member may get) a switch each for the inbox, push on the devices and the
  // line in the game (columns); push on/off for this device and the other devices. Alarm kinds (crash) always stay
  // in the inbox; kinds that never reach the game have no switch there. Works without push too (inbox, game).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import Toast from './Toast.svelte';

  let { me } = $props();

  const supported = typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  // iPadOS reports itself as a Mac - only the touch screen gives it away.
  const ios = /iPhone|iPad/.test(navigator.userAgent) || (/Macintosh/.test(navigator.userAgent) && navigator.maxTouchPoints > 1);
  const installed = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true;

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let endpoint = $state(null);
  let permission = $state(supported ? Notification.permission : 'denied');

  const CHANNELS = ['inbox', 'push', 'game'];

  async function load() {
    try {
      data = await api('GET', '/push');
      if (supported && data.key) {
        const reg = await navigator.serviceWorker.ready;
        const sub = await reg.pushManager.getSubscription();
        endpoint = sub && data.devices.some((d) => d.endpoint === sub.endpoint) ? sub.endpoint : null;
      }
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  const key = (text) => {
    const b64 = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(text.length / 4) * 4, '=');
    return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  };

  async function enable() {
    busy = true;
    error = '';
    message = '';
    try {
      permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        error = t('push.denied');
        return;
      }
      const reg = await navigator.serviceWorker.ready;
      let sub = await reg.pushManager.getSubscription();
      if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key(data.key) });
      const json = sub.toJSON();
      await api('POST', '/push/subscribe', { endpoint: sub.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth });
      message = t('push.enabled');
      await load();
    } catch (e) {
      error = e?.key ? errorText(e) : t('push.failed');
    } finally {
      busy = false;
    }
  }

  async function disable(other) {
    busy = true;
    error = '';
    try {
      if (!other && supported) {
        const reg = await navigator.serviceWorker.ready;
        const sub = await reg.pushManager.getSubscription();
        await sub?.unsubscribe();
      }
      await api('POST', '/push/unsubscribe', { endpoint: other ?? endpoint });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function choose(kind, channel, on) {
    data.kinds[kind][channel] = on;
    try {
      await api('POST', '/push/choose', { kind, channel, on: String(on) });
    } catch (e) {
      error = errorText(e);
      data.kinds[kind][channel] = !on;
    }
  }

  async function test() {
    error = '';
    try {
      const r = await api('POST', '/push/test');
      const failed = r.failed ?? [];
      message = failed.length < r.devices ? t('push.testSent', { n: r.devices - failed.length }) : '';
      // The push service's answer, e.g. Apple's "BadJwtToken" - the only way to see why nothing arrives.
      if (failed.length) {
        error = failed
          .map((f) => t('push.testFailed', { device: f.device, reason: (f.status ? 'HTTP ' + f.status + ' ' : '') + (f.reason || '?') })
            + (String(f.reason).includes('BadJwtToken') ? ' ' + t('push.badJwt') : ''))
          .join('\n');
      }
    } catch (e) {
      error = errorText(e);
    }
  }

  // Kinds grouped by the plugin that sends them - MRadmin first, then the others by name.
  const groups = $derived.by(() => {
    const map = new Map();
    for (const [kind, c] of Object.entries(data?.kinds ?? {})) {
      const name = c.plugin ?? 'MRadmin';
      if (!map.has(name)) map.set(name, []);
      map.get(name).push([kind, c]);
    }
    return [...map].sort(([a], [b]) => (a === 'MRadmin' ? -1 : b === 'MRadmin' ? 1 : a.localeCompare(b)));
  });

  const others = $derived((data?.devices ?? []).filter((d) => d.endpoint !== endpoint));
</script>

<section class="card stack">
  <div class="row">
    <h2>{t('push.title')}</h2>
    {#if endpoint}<span class="tag on">✓ {t('push.thisOn')}</span>{:else}<span class="tag">○ {t('push.thisOff')}</span>{/if}
  </div>
  <p class="muted small">{t('push.hint')}</p>
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if data && !data.key}
    <p class="muted small">{t('push.serverOff')}</p>
  {:else if !supported}
    <p class="warn small">{ios && !installed ? t('push.iosInstall') : t('push.unsupported')}</p>
  {:else if data}
    <div class="device-box">
      <div>
        <strong>{t('push.thisDevice')}</strong>
        <div class="muted small">{permission === 'denied' ? t('push.blocked') : endpoint ? t('push.thisOnHint') : t('push.thisOffHint')}</div>
      </div>
      {#if endpoint}
        <div class="buttons">
          <button class="secondary small" onclick={test} disabled={busy}>{t('push.test')}</button>
          <button class="secondary small" onclick={() => disable(null)} disabled={busy}>{t('push.disable')}</button>
        </div>
      {:else}
        <button class="secondary small" onclick={enable} disabled={busy || permission === 'denied'}>{t('push.enable')}</button>
      {/if}
    </div>
  {/if}

  {#if data}
    <h3>{t('push.kinds')}</h3>
    {#if Object.keys(data.kinds).length === 0}
      <p class="muted small">{t('push.noKinds')}</p>
    {:else}
      <div class="kinds" role="table" aria-label={t('push.kinds')}>
        {#each groups as [name, entries] (name)}
          <div class="krow ghead" role="row">
            <span class="gname" role="columnheader">{name}</span>
            {#each CHANNELS as c}<span class="col" role="columnheader">{t('push.col.' + c)}</span>{/each}
          </div>
          {#each entries as [kind, c] (kind)}
            {@const label = data.labels?.[kind]?.label ?? t('push.kind.' + kind)}
            {@const hint = data.labels?.[kind]?.hint ?? t('push.kindHint.' + kind)}
            <div class="krow" role="row">
              <span class="kname" role="cell" title={hint}>
                <span class="ktitle">{label}</span>
                <span class="khint">{hint}</span>
              </span>
              {#each CHANNELS as ch}
                <span class="col" role="cell">
                  {#if c[ch] === undefined}
                    <span class="none" title={t('push.noGame')}>–</span>
                  {:else if ch === 'inbox' && c.alarm}
                    <input type="checkbox" class="switch" checked disabled title={t('push.alwaysInbox')}
                      aria-label={`${label}: ${t('push.col.' + ch)}`} />
                  {:else}
                    <input type="checkbox" class="switch" checked={c[ch]} aria-label={`${label}: ${t('push.col.' + ch)}`}
                      onchange={(e) => choose(kind, ch, e.currentTarget.checked)} />
                  {/if}
                </span>
              {/each}
            </div>
          {/each}
        {/each}
      </div>
      <p class="muted small">{t('push.muteHint')}</p>
    {/if}

    {#if others.length}
      <h3>{t('push.otherDevices')}</h3>
      <ul class="devices">
        {#each others as d (d.endpoint)}
          <li>
            <span>{d.device}</span>
            <span class="muted small">{t('push.since', { time: dateTime(d.created) })}</span>
            <button class="danger mini" onclick={() => disable(d.endpoint)} disabled={busy}>{t('push.remove')}</button>
          </li>
        {/each}
      </ul>
    {/if}
  {/if}
</section>

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
  }

  h3 {
    font-size: 0.9rem;
    color: var(--muted);
    margin: 4px 0 0;
  }

  p {
    margin: 0;
  }

  .tag {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.on {
    background: color-mix(in srgb, var(--good) 14%, transparent);
    color: var(--good);
  }

  .warn {
    color: var(--warn);
  }

  .device-box {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    justify-content: space-between;
    align-items: center;
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 14px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .devices {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  /* One quiet table: a header row per plugin, thin lines between the kinds, the three channels as fixed columns
     so the switches stand in line. */
  .kinds {
    display: grid;
    grid-template-columns: minmax(0, 1fr) repeat(3, 4.8rem);
    border: 1px solid var(--border);
    border-radius: 10px;
    overflow: hidden;
  }

  .krow {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: subgrid;
    align-items: center;
    padding: 8px 4px 8px 14px;
    border-top: 1px solid color-mix(in srgb, var(--border) 55%, transparent);
  }

  .krow.ghead {
    padding-top: 7px;
    padding-bottom: 7px;
    background: var(--surface-2);
    border-top-color: var(--border);
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--muted);
  }

  .krow.ghead:first-child {
    border-top: 0;
  }

  .gname {
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.03em;
    color: var(--text);
  }

  .kname {
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  .ktitle {
    font-size: 0.9rem;
  }

  .khint {
    font-size: 0.76rem;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .col {
    display: flex;
    justify-content: center;
    text-align: center;
  }

  .none {
    color: var(--muted);
    opacity: 0.6;
  }

  /* A small switch instead of a plain checkbox. */
  .switch {
    appearance: none;
    width: 32px;
    height: 18px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
    position: relative;
    cursor: pointer;
    flex-shrink: 0;
    padding: 0;
    margin: 0;
    border: none;
    transition: background 0.15s;
  }

  .switch::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #fff;
    transition: transform 0.15s;
  }

  .switch:checked {
    background: var(--accent);
  }

  .switch:checked::after {
    transform: translateX(14px);
  }

  .switch:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }

  .switch:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  @media (max-width: 560px) {
    .kinds {
      grid-template-columns: minmax(0, 1fr) repeat(3, 3.6rem);
    }

    .krow {
      padding-left: 10px;
    }

    .khint {
      display: none;
    }
  }

  .devices li {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }

  .mini {
    margin-left: auto;
    padding: 4px 10px;
    font-size: 0.8rem;
  }
</style>
