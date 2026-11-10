<script>
  // Links from mails (since 1.5): /email?token=… confirms an e-mail address, /reset?token=… sets a new password.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import Footer from '../components/Footer.svelte';
  import LangFlags from '../components/LangFlags.svelte';

  // kind: email | reset
  let { kind } = $props();

  const token = new URLSearchParams(window.location.search).get('token') ?? '';
  let state = $state('loading');
  let name = $state('');
  let error = $state('');
  let pw1 = $state('');
  let pw2 = $state('');
  let busy = $state(false);

  async function start() {
    try {
      if (kind === 'email') {
        name = (await api('POST', '/account/email/confirm', { token })).name;
        state = 'done';
      } else {
        state = (await api('POST', '/login/reset/check', { token })).valid ? 'form' : 'invalid';
      }
    } catch (e) {
      error = errorText(e);
      state = 'invalid';
    }
  }
  start();

  async function save(event) {
    event.preventDefault();
    if (pw1 !== pw2) {
      error = t('logins.passwordMismatch');
      return;
    }
    busy = true;
    error = '';
    try {
      await api('POST', '/login/reset', { token, password: pw1 });
      state = 'done';
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page">
  <div class="top"><LangFlags /></div>
  <main class="card box">
    <h1>{t(kind === 'email' ? 'maillink.emailTitle' : 'maillink.resetTitle')}</h1>
    {#if state === 'loading'}
      <p class="muted">{t('common.loading')}</p>
    {:else if state === 'invalid'}
      <div class="error">{error || t('maillink.invalid')}</div>
    {:else if state === 'form'}
      {#if error}<div class="error">{error}</div>{/if}
      <form onsubmit={save}>
        <input type="password" bind:value={pw1} autocomplete="new-password" placeholder={t('logins.newPassword', { n: 10 })} />
        <input type="password" bind:value={pw2} autocomplete="new-password" placeholder={t('logins.repeatPassword')} />
        <button type="submit" disabled={busy || !pw1 || !pw2}>{t('common.save')}</button>
      </form>
    {:else}
      <div class="notice">{kind === 'email' ? t('maillink.emailDone', { name }) : t('maillink.resetDone')}</div>
    {/if}
    {#if state !== 'loading' && state !== 'form'}
      <button class="secondary" onclick={() => go('/', true)}>{t('maillink.toSignIn')}</button>
    {/if}
  </main>
  <Footer />
</div>

<style>
  .page {
    min-height: 100vh;
    display: grid;
    grid-template-rows: auto 1fr auto;
    padding: 16px;
    background: var(--bg);
  }

  .top {
    display: flex;
    justify-content: flex-end;
  }

  .box {
    align-self: center;
    justify-self: center;
    width: 100%;
    max-width: 420px;
    display: grid;
    gap: 14px;
    padding: 24px;
    border-top: 4px solid var(--accent);
  }

  h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  form {
    display: grid;
    gap: 10px;
  }

  button {
    justify-content: center;
  }

  .notice {
    padding: 10px 12px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--good) 12%, transparent);
    color: var(--good);
  }
</style>
