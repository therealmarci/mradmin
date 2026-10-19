<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import Footer from '../components/Footer.svelte';
  import * as passkeys from '../lib/passkeys.js';

  let { onSignedIn } = $props();

  let name = $state(localStorage.getItem('mradmin.name') ?? '');
  let password = $state('');
  let code = $state('');
  let busy = $state(false);
  let error = $state('');

  async function submit(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/login', { name: name.trim(), password, code: code.trim() });
      try {
        localStorage.setItem('mradmin.name', name.trim());
      } catch {
        // private mode - fine
      }
      password = '';
      code = '';
      onSignedIn();
    } catch (e) {
      error = errorText(e);
      code = '';
    } finally {
      busy = false;
    }
  }

  // Sign-in with a passkey: no name, no password, no code - the passkey knows the account.
  async function withPasskey() {
    busy = true;
    error = '';
    try {
      const start = await api('POST', '/login/passkey/start');
      const credential = await passkeys.get(start.options);
      await api('POST', '/login/passkey/finish', { request: start.request, credential });
      onSignedIn();
    } catch (e) {
      if (!passkeys.cancelled(e)) error = e?.name === 'SecurityError' ? t('passkey.wrongAddress') : errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page">
  <form class="card stack" onsubmit={submit}>
    <div class="logo">
      <img src="/icon.svg" alt="" width="44" height="44" />
      <div>
        <h1>{t('login.title')}</h1>
        <p class="muted small">{t('login.subtitle')}</p>
      </div>
    </div>

    {#if error}
      <div class="error" role="alert">{error}</div>
    {/if}

    {#if passkeys.supported}
      <button class="block passkey" type="button" onclick={withPasskey} disabled={busy}>🔑 {t('passkey.signIn')}</button>
      <div class="or"><span>{t('passkey.or')}</span></div>
    {/if}

    <div>
      <div class="field">
        <label for="name">{t('login.name')}</label>
        <input id="name" bind:value={name} autocomplete="username" autocapitalize="off" spellcheck="false" required maxlength="16" />
      </div>
      <div class="field">
        <label for="password">{t('login.password')}</label>
        <input id="password" type="password" bind:value={password} autocomplete="current-password" required />
      </div>
      <div class="field">
        <label for="code">{t('login.code')}</label>
        <input id="code" class="mono" bind:value={code} autocomplete="one-time-code" inputmode="text" required maxlength="12" />
        <div class="hint">{t('login.codeHint')}</div>
      </div>
    </div>

    <button class="block" type="submit" disabled={busy}>{busy ? t('common.loading') : t('login.submit')}</button>
    <p class="muted small">{t('login.noAccount')}</p>
  </form>
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

  form {
    width: 100%;
    max-width: 400px;
  }

  .logo {
    display: flex;
    gap: 14px;
    align-items: center;
  }

  .logo p {
    margin: 0;
  }

  .passkey {
    padding: 12px 16px;
  }

  /* "or" between passkey and password, with a line on both sides. */
  .or {
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--muted);
    font-size: 0.82rem;
  }

  .or::before,
  .or::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border);
  }
</style>
