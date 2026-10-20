<script>
  // The own account: security (sign-in methods, 2FA, recovery codes), notifications, signed-in devices.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, ago, head } from '../lib/format.js';
  import PasskeysCard from '../components/PasskeysCard.svelte';
  import TwoFactorCard from '../components/TwoFactorCard.svelte';
  import NotificationsCard from '../components/NotificationsCard.svelte';
  import DiscordCard from '../components/DiscordCard.svelte';

  let { me } = $props();

  const TABS = [
    { key: 'security', icon: '🛡' },
    { key: 'notifications', icon: '🔔' },
    { key: 'devices', icon: '💻' },
  ];
  let tab = $state(sessionStorage.getItem('mradmin.account.tab') ?? 'security');

  let sessions = $state(null);
  let error = $state('');
  let message = $state('');
  let code = $state('');
  let newCodes = $state(null);
  let busy = $state(false);

  async function load() {
    try {
      sessions = await api('GET', '/sessions');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  function choose(key) {
    tab = key;
    try {
      sessionStorage.setItem('mradmin.account.tab', key);
    } catch {
      // fine
    }
  }

  async function revoke(id) {
    await api('DELETE', '/sessions/' + encodeURIComponent(id));
    load();
  }

  async function revokeOthers() {
    if (!confirm(t('account.revokeOthersConfirm'))) return;
    const result = await api('POST', '/sessions/others');
    message = t('account.revokedOthers', { n: result.count });
    load();
  }

  async function recovery(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const result = await api('POST', '/account/recovery', { code: code.trim() });
      newCodes = result.recoveryCodes;
      me.recoveryLeft = newCodes.length;
    } catch (e) {
      error = errorText(e);
    } finally {
      code = '';
      busy = false;
    }
  }

  const phone = (device) => /Mobile|Android|iPhone|iPad/.test(device);

  // How well the account is protected - symbol and word.
  const level = $derived(
    (me.passkeys > 0 || me.discord) && me.totp ? { tone: 'good', icon: '🛡', key: 'strong' }
      : me.passkeys > 0 || me.discord || me.totp ? { tone: 'good', icon: '✓', key: 'ok' }
      : { tone: 'warn', icon: '!', key: 'weak' },
  );
</script>

<div class="account">
  <section class="card hero">
    <img src={head(me.uuid)} alt="" width="64" height="64" />
    <div class="hero-body">
      <h1>{me.name}</h1>
      <div class="muted small">{t('home.lastLogin')}: {dateTime(me.lastLogin)}</div>
      <div class="chips">
        <span class="chip {level.tone}">{level.icon} {t('account.level.' + level.key)}</span>
        <span class="chip">{me.totp ? '📱 ' + t('account.chip.totpOn') : '○ ' + t('account.chip.totpOff')}</span>
        <span class="chip">🔑 {t('account.chip.passkeys', { n: me.passkeys })}</span>
        {#if me.discord}<span class="chip">💬 Discord: {me.discord}</span>{/if}
        {#if sessions}<span class="chip">💻 {t('account.chip.devices', { n: sessions.length })}</span>{/if}
      </div>
    </div>
  </section>

  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  <div class="layout">
    <nav class="sections" aria-label={t('nav.account')}>
      {#each TABS as s}
        <button class:active={tab === s.key} aria-current={tab === s.key ? 'page' : undefined} onclick={() => choose(s.key)}>
          <span class="icon" aria-hidden="true">{s.icon}</span>
          <span>{t('account.tab.' + s.key)}</span>
        </button>
      {/each}
    </nav>

    <div class="content">
      {#if tab === 'security'}
        <PasskeysCard {me} />
        <DiscordCard {me} />
        <TwoFactorCard {me} />
        {#if me.totp}
          <section class="card stack">
            <h2>🧾 {t('account.recoveryTitle')}</h2>
            <div class="facts">
              <div><span class="muted">{t('account.recoveryLeftLabel')}</span> <span class:warn={me.recoveryLeft <= 2}>{me.recoveryLeft} / 8</span></div>
            </div>
            {#if newCodes}
              <p class="small">{t('twofa.codesHint')}</p>
              <div class="codes mono">{#each newCodes as c}<span>{c}</span>{/each}</div>
            {:else}
              <form class="inline" onsubmit={recovery}>
                <input class="mono" bind:value={code} inputmode="numeric" maxlength="6" placeholder="123456" required autocomplete="one-time-code" />
                <button type="submit" disabled={busy}>{t('account.recoveryNew')}</button>
              </form>
              <p class="muted small">{t('account.recoveryHint')}</p>
            {/if}
          </section>
        {/if}
      {:else if tab === 'notifications'}
        <NotificationsCard {me} />
      {:else}
        <section class="card stack">
          <div class="row">
            <h2>💻 {t('account.devices')}</h2>
            {#if sessions && sessions.length > 1}
              <button class="secondary small-button" onclick={revokeOthers}>{t('account.revokeOthers')}</button>
            {/if}
          </div>
          {#if !sessions}
            <p class="muted">{t('common.loading')}</p>
          {:else}
            <ul class="sessions">
              {#each sessions as s (s.id)}
                <li class:current={s.current}>
                  <div class="dev-icon" aria-hidden="true">{phone(s.device) ? '📱' : '💻'}</div>
                  <div class="dev-body">
                    <div class="device">{s.device} {#if s.current}<span class="badge">{t('account.current')}</span>{/if}</div>
                    <div class="muted small">{t('account.lastSeen')}: {ago(s.lastSeen)} · {s.ip}</div>
                    <div class="muted small">{t('account.since')}: {dateTime(s.created)}</div>
                  </div>
                  {#if !s.current}
                    <button class="danger small-button" onclick={() => revoke(s.id)}>{t('account.revoke')}</button>
                  {/if}
                </li>
              {/each}
            </ul>
          {/if}
        </section>
      {/if}
      <p class="muted small foot">{t('account.reset')} · MRadmin {me.version}</p>
    </div>
  </div>
</div>

<style>
  .account {
    display: grid;
    gap: 16px;
  }

  .hero {
    display: flex;
    gap: 18px;
    align-items: center;
    flex-wrap: wrap;
  }

  .hero img {
    border-radius: 12px;
    image-rendering: pixelated;
    box-shadow: var(--shadow);
  }

  .hero-body {
    flex: 1;
    min-width: 0;
  }

  .hero h1 {
    margin: 0;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 10px;
  }

  .chip {
    font-size: 0.8rem;
    font-weight: 600;
    padding: 4px 10px;
    border-radius: 999px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
  }

  .chip.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 12%, transparent);
  }

  .chip.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  /* Phone: the sections as tabs on top; PC: a menu on the left. */
  .layout {
    display: grid;
    gap: 16px;
  }

  .sections {
    display: flex;
    gap: 6px;
    overflow-x: auto;
  }

  .sections button {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: flex-start;
    background: var(--surface-2);
    color: var(--muted);
    border: 1px solid var(--border);
    border-radius: 999px;
    padding: 8px 14px;
    white-space: nowrap;
  }

  .sections button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .content {
    display: grid;
    gap: 16px;
    min-width: 0;
  }

  @media (min-width: 900px) {
    .layout {
      grid-template-columns: 220px minmax(0, 1fr);
      align-items: start;
    }

    .sections {
      flex-direction: column;
      position: sticky;
      top: 84px;
    }

    .sections button {
      border-radius: 10px;
      background: transparent;
      border-color: transparent;
      padding: 10px 14px;
    }

    .sections button:hover {
      background: var(--surface-2);
    }

    .sections button.active {
      background: var(--surface);
      color: var(--text);
      border-color: var(--border);
      box-shadow: var(--shadow);
    }
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
  }

  h2 {
    margin: 0;
  }

  .small-button {
    padding: 6px 12px;
    font-size: 0.85rem;
  }

  .facts {
    display: grid;
    gap: 6px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 160px), 1fr));
  }

  .facts > div {
    background: var(--surface-2);
    border-radius: 8px;
    padding: 6px 10px;
    font-weight: 700;
  }

  .facts .muted {
    display: block;
    font-size: 0.74rem;
    font-weight: 600;
  }

  .warn {
    color: var(--warn);
  }

  .sessions {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .sessions li {
    display: flex;
    gap: 12px;
    align-items: center;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 10px;
    padding: 10px 12px;
  }

  .sessions li.current {
    border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  }

  .dev-icon {
    font-size: 1.4rem;
  }

  .dev-body {
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .device {
    font-weight: 600;
  }

  .badge {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--accent) 16%, transparent);
    color: var(--accent);
    margin-left: 4px;
  }

  .inline {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .inline input {
    flex: 1;
    min-width: 140px;
  }

  .codes {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    background: var(--surface-2);
    border-radius: 10px;
    padding: 14px;
    user-select: all;
  }

  .foot {
    margin: 0;
  }
</style>
