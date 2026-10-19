<script>
  import QRCode from 'qrcode';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import Footer from '../components/Footer.svelte';

  let { onDone } = $props();

  // The token is in the address after # - browsers never send that part to a server. Take it and remove it from
  // the address bar right away, so it does not stay in the history.
  const token = location.hash.slice(1);
  history.replaceState(null, '', '/setup');

  let info = $state(null);
  let invalid = $state(false);
  let qr = $state('');
  let password = $state('');
  let password2 = $state('');
  let code = $state('');
  let busy = $state(false);
  let error = $state('');
  let codes = $state(null);
  let saved = $state(false);
  let copied = $state(false);

  async function load() {
    if (!token) {
      invalid = true;
      return;
    }
    try {
      info = await api('POST', '/setup/info', { token });
      qr = await QRCode.toDataURL(info.uri, { margin: 1, width: 240 });
    } catch {
      invalid = true;
    }
  }
  load();

  async function submit(event) {
    event.preventDefault();
    error = '';
    if (password !== password2) {
      error = t('setup.mismatch');
      return;
    }
    busy = true;
    try {
      const result = await api('POST', '/setup/finish', { token, password, code: code.trim() });
      codes = result.recoveryCodes;
      password = password2 = '';
    } catch (e) {
      error = errorText(e);
      code = '';
      if (e.key === 'setup-invalid') invalid = true;
    } finally {
      busy = false;
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      copied = true;
    } catch {
      // clipboard not allowed - the codes can still be selected
    }
  }

  // The secret in groups of four, easier to type.
  const grouped = $derived(info ? info.secret.match(/.{1,4}/g).join(' ') : '');
</script>

<div class="page">
  <div class="card stack box">
    {#if invalid}
      <h1>{t('setup.title')}</h1>
      <div class="error">{t('setup.invalid')}</div>
    {:else if !info}
      <p class="muted">{t('common.loading')}</p>
    {:else if codes}
      <h1>{t('setup.done')}</h1>
      <h2>{t('setup.codesTitle')}</h2>
      <p class="muted small">{t('setup.codesHint')}</p>
      <div class="codes mono">
        {#each codes as c}<span>{c}</span>{/each}
      </div>
      <button class="secondary" onclick={copy}>{copied ? t('setup.copied') : t('setup.copy')}</button>
      <label class="check"><input type="checkbox" bind:checked={saved} /> {t('setup.saved')}</label>
      <button class="block" disabled={!saved} onclick={onDone}>{t('setup.continue')}</button>
    {:else}
      <div>
        <h1>{info.existing ? t('setup.reset') : t('setup.title')}</h1>
        <p class="muted">{t('setup.for')} <strong>{info.name}</strong></p>
      </div>
      {#if info.existing}
        <div class="notice">{t('setup.resetHint')}</div>
      {/if}
      {#if error}
        <div class="error" role="alert">{error}</div>
      {/if}

      <form class="stack" onsubmit={submit}>
        <section>
          <h2>{t('setup.step1')}</h2>
          <input type="text" autocomplete="username" value={info.name} hidden readonly />
          <div class="field">
            <label for="pw">{t('setup.password')}</label>
            <input id="pw" type="password" bind:value={password} autocomplete="new-password" minlength={info.minPassword} required />
            <div class="hint">{t('setup.passwordMin', { n: info.minPassword })}</div>
          </div>
          <div class="field">
            <label for="pw2">{t('setup.password2')}</label>
            <input id="pw2" type="password" bind:value={password2} autocomplete="new-password" minlength={info.minPassword} required />
          </div>
        </section>

        <section>
          <h2>{t('setup.step2')}</h2>
          <p class="muted small">{t('setup.scan')}</p>
          <div class="qr">
            {#if qr}<img src={qr} alt="QR" width="200" height="200" />{/if}
            <div>
              <div class="small muted">{t('setup.secret')}</div>
              <div class="mono secret">{grouped}</div>
            </div>
          </div>
        </section>

        <section>
          <h2>{t('setup.step3')}</h2>
          <input class="mono" bind:value={code} autocomplete="one-time-code" inputmode="numeric" required maxlength="6" placeholder="123456" />
        </section>

        <button class="block" type="submit" disabled={busy}>{busy ? t('common.loading') : t('setup.submit')}</button>
      </form>
    {/if}
  </div>
  <Footer />
</div>

<style>
  .page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    grid-template-rows: 1fr auto;
    padding: 20px 16px;
  }

  .box {
    width: 100%;
    max-width: 520px;
  }

  .qr {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    align-items: center;
  }

  .qr img {
    border-radius: 10px;
    background: #fff;
    padding: 6px;
  }

  .secret {
    font-size: 1.05rem;
    word-break: break-all;
    user-select: all;
  }

  .codes {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    background: var(--surface-2);
    border-radius: 10px;
    padding: 14px;
    font-size: 1.05rem;
    user-select: all;
  }

  .check {
    display: flex;
    gap: 10px;
    align-items: center;
    font-weight: 500;
  }

  .check input {
    width: auto;
  }
</style>
