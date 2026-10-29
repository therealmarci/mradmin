<script>
  // The own account: security (sign-in methods, 2FA, recovery codes), notifications, signed-in devices.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, ago, head } from '../lib/format.js';
  import PasskeysCard from '../components/PasskeysCard.svelte';
  import TwoFactorCard from '../components/TwoFactorCard.svelte';
  import NotificationsCard from '../components/NotificationsCard.svelte';
  import DiscordCard from '../components/DiscordCard.svelte';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  const TABS = ['security', 'notifications', 'devices'];
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

  // How well the account is protected.
  const level = $derived(
    (me.passkeys > 0 || me.discord) && me.totp ? { tone: 'good', key: 'strong' }
      : me.passkeys > 0 || me.discord || me.totp ? { tone: 'good', key: 'ok' }
      : { tone: 'warn', key: 'weak' },
  );
  const others = $derived((sessions ?? []).filter((s) => !s.current));
  const current = $derived((sessions ?? []).find((s) => s.current));

  // Small outline icons (monitor, phone) for the devices.
  const DEVICE = {
    phone: 'M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2',
    desktop: 'M3 4h18v12H3zM8 20h8M12 16v4',
  };
</script>

<!-- A device as a card: its icon, browser, when it was last seen. -->
{#snippet device(s)}
  <li class="dev" class:current={s.current}>
    <span class="dev-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={phone(s.device) ? DEVICE.phone : DEVICE.desktop} /></svg>
    </span>
    <div class="dev-body">
      <div class="dev-name">{s.device}{#if s.current}<span class="badge">{t('account.current')}</span>{/if}</div>
      <div class="muted small">{t('account.lastSeen')}: {ago(s.lastSeen)} · <span class="mono">{s.ip}</span></div>
      <div class="muted small">{t('account.since')}: {dateTime(s.created)}</div>
    </div>
    {#if !s.current}
      <button class="danger mini" onclick={() => revoke(s.id)}>{t('account.revoke')}</button>
    {/if}
  </li>
{/snippet}

<div class="page">
  <header class="head">
    <img src={head(me.uuid)} alt="" width="64" height="64" />
    <div class="intro">
      <h1>{me.name}</h1>
      <span class="muted small">{t('home.lastLogin')}: {dateTime(me.lastLogin)}</span>
    </div>
    <span class="state {level.tone}"><span class="dot"></span>{t('account.level.' + level.key)}</span>
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  <div class="seg areas" role="tablist">
    {#each TABS as key}
      <button role="tab" aria-selected={tab === key} class:active={tab === key} onclick={() => choose(key)}>{t('account.tab.' + key)}</button>
    {/each}
  </div>

  <div class="content">
    {#if tab === 'security'}
      <PasskeysCard {me} />
      <DiscordCard {me} />
      <TwoFactorCard {me} />
      {#if me.totp}
        <section class="card tile">
          <div class="row">
            <h2>{t('account.recoveryTitle')}</h2>
            <span class="chip" class:warn={me.recoveryLeft <= 2}>{t('account.recoveryLeftLabel')}: {me.recoveryLeft} / 8</span>
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
      <section class="card tile">
        <div class="row">
          <h2>{t('account.devices')}{#if sessions}<span class="n">{sessions.length}</span>{/if}</h2>
          {#if others.length}
            <button class="secondary mini" onclick={revokeOthers}>{t('account.revokeOthers')}</button>
          {/if}
        </div>
        {#if !sessions}
          <p class="muted">{t('common.loading')}</p>
        {:else}
          <ul class="devices">
            {#if current}{@render device(current)}{/if}
            {#each others as s (s.id)}{@render device(s)}{/each}
          </ul>
        {/if}
      </section>
    {/if}
    <p class="muted small foot">{t('account.reset')} · MRadmin {me.version}</p>
  </div>
</div>

<style>
  .page {
    --ac: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 20px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--ac) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--ac) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .head img {
    border-radius: 14px;
    image-rendering: pixelated;
    border: 3px solid color-mix(in srgb, var(--ac) 45%, transparent);
  }

  .intro {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 200px;
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .good {
    --tn: var(--good);
  }

  .warn {
    --tn: var(--warn);
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    border-radius: 999px;
    font-weight: 700;
    color: var(--tn);
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--tn) 40%, var(--border));
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--tn);
  }

  /* Areas */
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    justify-self: start;
  }

  .seg button {
    background: transparent;
    color: var(--muted);
    padding: 8px 18px;
    border-radius: 9px;
    font-size: 0.95rem;
    font-weight: 700;
  }

  .seg button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .content {
    display: grid;
    gap: 16px;
    min-width: 0;
  }

  /* The headings of the cards (passkeys, 2FA ...) in the same size as the other tiles */
  .content :global(.card h2) {
    font-size: 1.05rem;
  }

  .tile {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.05rem;
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .chip {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .chip.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  .mini {
    padding: 5px 12px;
    font-size: 0.82rem;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  /* Devices: this one first and marked */
  .devices {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .dev {
    display: flex;
    gap: 14px;
    align-items: center;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--border);
  }

  .dev.current {
    border-left-color: var(--ac);
    background: color-mix(in srgb, var(--ac) 6%, var(--surface-2));
  }

  .dev-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    border-radius: 12px;
    color: var(--muted);
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .dev.current .dev-icon {
    color: var(--ac);
    border-color: color-mix(in srgb, var(--ac) 40%, var(--border));
  }

  .dev-body {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .dev-name {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    font-weight: 700;
  }

  .badge {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--ac) 16%, transparent);
    color: var(--ac);
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

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }
</style>
