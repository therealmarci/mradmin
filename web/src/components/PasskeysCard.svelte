<script>
  // The own passkeys: list, add (with a fresh 2FA code), remove.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime } from '../lib/format.js';
  import * as passkeys from '../lib/passkeys.js';
  import * as discord from '../lib/discord.js';

  let { me } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let adding = $state(false);
  let code = $state('');
  let name = $state(guessName());

  async function load() {
    try {
      data = await api('GET', '/passkeys');
      me.passkeys = data.passkeys.length;
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  // A name that tells devices apart, e.g. "iPhone" or "Windows" - the member can change it.
  function guessName() {
    const ua = navigator.userAgent;
    if (/iPhone/.test(ua)) return 'iPhone';
    if (/iPad/.test(ua)) return 'iPad';
    if (/Android/.test(ua)) return 'Android';
    if (/Mac OS X/.test(ua)) return 'Mac';
    if (/Windows/.test(ua)) return 'Windows';
    return 'Passkey';
  }

  // Without 2FA and without a passkey, Discord confirms. Back from there the form is open; the browser only creates a
  // passkey after a real click, so the member clicks "Continue" once more (the confirmation counts for 2 minutes).
  const viaDiscord = $derived(!me.totp && me.passkeys === 0 && !!me.discord);
  let discordReady = $state(false);
  if (discord.confirmed('passkey-add')) {
    adding = true;
    discordReady = true;
    message = t('passkey.discordReady');
  }

  async function add(event) {
    event.preventDefault();
    if (viaDiscord && !discordReady) {
      await discord.confirm('passkey-add');
      return;
    }
    busy = true;
    error = '';
    message = '';
    try {
      // Confirmed with the 2FA code - without 2FA with an existing passkey, or with Discord.
      const proof = me.totp ? { code: code.trim() } : discordReady ? discord.body : await passkeys.proof(api);
      discordReady = false;
      const start = await api('POST', '/passkeys/start', proof);
      code = '';
      const credential = await passkeys.create(start.options);
      await api('POST', '/passkeys/finish', { credential, name: name.trim() });
      message = t('passkey.added');
      adding = false;
      await load();
    } catch (e) {
      if (!passkeys.cancelled(e)) error = e?.name === 'SecurityError' ? t('passkey.wrongAddress') : errorText(e);
    } finally {
      busy = false;
    }
  }

  async function remove(p) {
    if (!confirm(t('passkey.removeConfirm', { name: p.name }))) return;
    try {
      await api('DELETE', '/passkeys/' + encodeURIComponent(p.id));
      await load();
    } catch (e) {
      error = errorText(e);
    }
  }
</script>

{#if data?.enabled}
  <section class="card stack">
    <div class="row">
      <h2>🔑 {t('passkey.title')}</h2>
      {#if passkeys.supported && !adding}
        <button class="secondary small-button" onclick={() => (adding = true)}>{t('passkey.add')}</button>
      {/if}
    </div>
    <p class="muted small">{t('passkey.hint')}</p>
    {#if error}<div class="error">{error}</div>{/if}
    {#if message}<div class="notice">{message}</div>{/if}

    {#if adding}
      <form class="add" onsubmit={add}>
        <div class="field">
          <label for="pk-name">{t('passkey.name')}</label>
          <input id="pk-name" bind:value={name} maxlength="40" />
        </div>
        {#if me.totp}
          <div class="field">
            <label for="pk-code">{t('passkey.code')}</label>
            <input id="pk-code" class="mono" bind:value={code} inputmode="numeric" autocomplete="one-time-code" maxlength="6" />
          </div>
        {:else if viaDiscord}
          <p class="muted small">{discordReady ? t('passkey.discordReady') : t('passkey.confirmWithDiscord')}</p>
        {:else}
          <p class="muted small">{t('passkey.confirmWithPasskey')}</p>
        {/if}
        <div class="buttons">
          <button type="submit" disabled={busy || (me.totp && code.trim().length < 6)}>{t('passkey.create')}</button>
          <button type="button" class="secondary" onclick={() => (adding = false)}>{t('common.cancel')}</button>
        </div>
      </form>
    {/if}

    {#if data.passkeys.length === 0}
      <p class="muted small">{passkeys.supported ? t('passkey.none') : t('passkey.unsupported')}</p>
    {:else}
      <ul class="list">
        {#each data.passkeys as p (p.id)}
          <li>
            <div>
              <div class="pname">{p.name}</div>
              <div class="muted small">
                {t('passkey.added_at', { time: dateTime(p.created) })} ·
                {p.lastUsed ? t('passkey.used', { time: ago(p.lastUsed) }) : t('passkey.neverUsed')}
              </div>
            </div>
            <button class="danger" onclick={() => remove(p)}>{t('passkey.remove')}</button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
{/if}

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

  .add {
    display: grid;
    gap: 10px;
    background: var(--surface-2);
    border-radius: 10px;
    padding: 14px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .list li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }

  .pname {
    font-weight: 600;
  }

  .small-button {
    padding: 6px 12px;
    font-size: 0.85rem;
  }
</style>
