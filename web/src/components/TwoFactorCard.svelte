<script>
  // 2FA (authenticator app): switch off when a passkey exists - then sign-in only with passkeys, since password
  // sign-in always needs 2FA - and switch on again (new QR code, new recovery codes).
  import QRCode from 'qrcode';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import * as passkeys from '../lib/passkeys.js';
  import * as discord from '../lib/discord.js';

  let { me } = $props();

  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let code = $state('');
  let offOpen = $state(false);
  // Switching on: secret + QR, then the first code; afterwards the new recovery codes.
  let setup = $state(null);
  let qr = $state('');
  let newCodes = $state(null);

  // Confirmed with the code, a passkey or Discord (Discord leaves the page and comes back - see the end).
  async function disable(event, how = 'code', asked = false) {
    event?.preventDefault();
    if (!asked && !confirm(t('twofa.offConfirm'))) return;
    if (how === 'discord') {
      await discord.confirm('totp-off');
      return;
    }
    busy = true;
    error = '';
    try {
      const proof = how === 'passkey' ? await passkeys.proof(api) : how === 'discord-done' ? discord.body : { code: code.trim() };
      await api('POST', '/account/totp/disable', proof);
      me.totp = false;
      me.recoveryLeft = 0;
      code = '';
      offOpen = false;
      message = t('twofa.offDone');
    } catch (e) {
      if (!passkeys.cancelled(e)) error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function startOn(how = me.passkeys > 0 && passkeys.supported ? 'passkey' : 'discord') {
    if (how === 'discord') {
      await discord.confirm('totp-on');
      return;
    }
    busy = true;
    error = '';
    message = '';
    try {
      setup = await api('POST', '/account/totp/start', how === 'discord-done' ? discord.body : await passkeys.proof(api));
      qr = await QRCode.toDataURL(setup.uri, { margin: 1, width: 220 });
    } catch (e) {
      if (!passkeys.cancelled(e)) error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Back from Discord with a confirmation: carry on where the member was.
  if (discord.confirmed('totp-off')) disable(null, 'discord-done', true);
  if (discord.confirmed('totp-on')) startOn('discord-done');

  // Another way in besides 2FA: a passkey or Discord.
  const otherWay = $derived(me.passkeys > 0 || !!me.discord);

  async function finishOn(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const result = await api('POST', '/account/totp/finish', { code: code.trim() });
      newCodes = result.recoveryCodes;
      me.totp = true;
      me.recoveryLeft = newCodes.length;
      setup = null;
      code = '';
      message = t('twofa.onDone');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<section class="card stack">
  <div class="row">
    <h2>📱 {t('twofa.title')}</h2>
    <span class="tag" class:on={me.totp}>{me.totp ? '✓ ' + t('twofa.on') : '○ ' + t('twofa.off')}</span>
  </div>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if me.totp}
    <p class="muted small">{t('twofa.onHint')}</p>
    {#if otherWay}
      {#if offOpen}
        <form class="box" onsubmit={disable}>
          <p class="small">{t('twofa.offWarning')}</p>
          <div class="inline">
            <input class="mono" bind:value={code} inputmode="numeric" maxlength="6" placeholder="123456" autocomplete="one-time-code" />
            <button type="submit" class="danger" disabled={busy || code.trim().length < 6}>{t('twofa.turnOff')}</button>
          </div>
          <div class="links">
            {#if passkeys.supported && me.passkeys > 0}
              <button type="button" class="linkish" disabled={busy} onclick={() => disable(null, 'passkey')}>{t('twofa.withPasskey')}</button>
            {/if}
            {#if me.discord}
              <button type="button" class="linkish" disabled={busy} onclick={() => disable(null, 'discord')}>{t('twofa.withDiscord')}</button>
            {/if}
            <button type="button" class="linkish" onclick={() => (offOpen = false)}>{t('common.cancel')}</button>
          </div>
        </form>
      {:else}
        <button class="secondary" onclick={() => (offOpen = true)}>{t('twofa.turnOffOpen')}</button>
      {/if}
    {:else}
      <p class="muted small">{t('twofa.needPasskey')}</p>
    {/if}
  {:else}
    <p class="small warn-text">{t('twofa.offHint')}</p>
    {#if setup}
      <form class="box" onsubmit={finishOn}>
        <p class="small">{t('twofa.scan')}</p>
        {#if qr}<img src={qr} alt="" width="220" height="220" />{/if}
        <p class="mono small secret">{setup.secret}</p>
        <div class="inline">
          <input class="mono" bind:value={code} inputmode="numeric" maxlength="6" placeholder="123456" autocomplete="one-time-code" />
          <button type="submit" disabled={busy || code.trim().length < 6}>{t('twofa.turnOn')}</button>
        </div>
      </form>
    {:else}
      <div class="links">
        {#if passkeys.supported && me.passkeys > 0}
          <button class="secondary" disabled={busy} onclick={() => startOn('passkey')}>🔑 {t('twofa.turnOnStart')}</button>
        {/if}
        {#if me.discord}
          <button class="secondary" disabled={busy} onclick={() => startOn('discord')}>💬 {t('twofa.turnOnDiscord')}</button>
        {/if}
      </div>
    {/if}
  {/if}

  {#if newCodes}
    <p class="small">{t('twofa.codesHint')}</p>
    <div class="codes mono">{#each newCodes as c}<span>{c}</span>{/each}</div>
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

  p {
    margin: 0;
  }

  .tag {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 2px 10px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--warn) 16%, transparent);
    color: var(--warn);
  }

  .tag.on {
    background: color-mix(in srgb, var(--good) 14%, transparent);
    color: var(--good);
  }

  .warn-text {
    color: var(--warn);
  }

  .box {
    display: grid;
    gap: 10px;
    background: var(--surface-2);
    border-radius: 10px;
    padding: 14px;
    justify-items: start;
  }

  .box img {
    border-radius: 8px;
    background: #fff;
  }

  .secret {
    overflow-wrap: anywhere;
  }

  .inline {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    width: 100%;
  }

  .inline input {
    flex: 1;
    min-width: 120px;
  }

  .links {
    display: flex;
    gap: 14px;
  }

  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 2px 0;
    font-size: 0.88rem;
  }

  .codes {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 6px;
  }

  .codes span {
    background: var(--surface-2);
    border-radius: 6px;
    padding: 6px 8px;
    text-align: center;
  }
</style>
