<script>
  // Push notifications: switch on for this device, choose the kinds (only those the member may get), other devices.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

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

  const ICON = { 'ticket-new': '🎫', 'ticket-mine': '📌', 'ticket-message': '💬', 'chunks-locked': '🔒', lag: '🐢', maintenance: '🔧' };

  async function load() {
    try {
      data = await api('GET', '/push');
      if (supported) {
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

  async function choose(kind, on) {
    data.kinds[kind] = on;
    try {
      await api('POST', '/push/choose', { kind, on: String(on) });
    } catch (e) {
      error = errorText(e);
      data.kinds[kind] = !on;
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

  const others = $derived((data?.devices ?? []).filter((d) => d.endpoint !== endpoint));
</script>

<section class="card stack">
  <div class="row">
    <h2>🔔 {t('push.title')}</h2>
    {#if endpoint}<span class="tag on">✓ {t('push.thisOn')}</span>{:else}<span class="tag">○ {t('push.thisOff')}</span>{/if}
  </div>
  <p class="muted small">{t('push.hint')}</p>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if !supported}
    <p class="warn small">{ios && !installed ? t('push.iosInstall') : t('push.unsupported')}</p>
  {:else if data}
    <div class="device-box">
      <div>
        <strong>{t('push.thisDevice')}</strong>
        <div class="muted small">{permission === 'denied' ? t('push.blocked') : endpoint ? t('push.thisOnHint') : t('push.thisOffHint')}</div>
      </div>
      {#if endpoint}
        <div class="buttons">
          <button class="secondary" onclick={test} disabled={busy}>{t('push.test')}</button>
          <button class="danger" onclick={() => disable(null)} disabled={busy}>{t('push.disable')}</button>
        </div>
      {:else}
        <button onclick={enable} disabled={busy || permission === 'denied'}>{t('push.enable')}</button>
      {/if}
    </div>
  {/if}

  {#if data}
    <h3>{t('push.kinds')}</h3>
    {#if Object.keys(data.kinds).length === 0}
      <p class="muted small">{t('push.noKinds')}</p>
    {:else}
      <ul class="kinds">
        {#each Object.entries(data.kinds) as [kind, on] (kind)}
          <li>
            <label>
              <span class="kicon" aria-hidden="true">{ICON[kind] ?? '•'}</span>
              <span class="ktext">
                <strong>{t('push.kind.' + kind)}</strong>
                <span class="muted small">{t('push.kindHint.' + kind)}</span>
              </span>
              <input type="checkbox" class="switch" checked={on} onchange={(e) => choose(kind, e.currentTarget.checked)} />
            </label>
          </li>
        {/each}
      </ul>
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
    background: var(--surface-2);
    border-radius: 10px;
    padding: 12px 14px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .kinds,
  .devices {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .kinds label {
    display: flex;
    gap: 12px;
    align-items: center;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 10px;
    padding: 10px 12px;
    cursor: pointer;
  }

  .kicon {
    font-size: 1.2rem;
  }

  .ktext {
    flex: 1;
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  /* A switch instead of a plain checkbox. */
  .switch {
    appearance: none;
    width: 42px;
    height: 24px;
    border-radius: 999px;
    background: var(--border);
    position: relative;
    cursor: pointer;
    flex-shrink: 0;
    padding: 0;
    border: none;
    transition: background 0.15s;
  }

  .switch::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: #fff;
    transition: transform 0.15s;
  }

  .switch:checked {
    background: var(--accent);
  }

  .switch:checked::after {
    transform: translateX(18px);
  }

  .switch:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
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
