<script>
  // The one sign-in of the web interface (since 1.5): the code from /web in game - or an own sign-in: name or e-mail +
  // password (+ the 2FA code if the account has 2FA), a passkey or Discord. "Forgot password" sends a mail with a link.
  // onSignedIn() after success; the caller decides where to go (player area, administration).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { route } from '../lib/router.svelte.js';
  import Footer from './Footer.svelte';
  import LangFlags from './LangFlags.svelte';
  import * as passkeys from '../lib/passkeys.js';
  import * as discord from '../lib/discord.js';
  import { brand, picture } from '../lib/branding.svelte.js';

  // notice: why the player area refused (banned, no permission ...) - shown above the form
  let { onSignedIn, notice = '' } = $props();

  // code | password | forgot
  let mode = $state('code');
  let gameCode = $state('');
  let name = $state(remembered());
  let password = $state('');
  let code = $state('');
  let needCode = $state(false);
  let busy = $state(false);
  let error = $state('');
  let sent = $state(false);
  let codeInput = $state();

  let options = $state({ discord: false, passkeys: true, password: true, mail: false });
  api('GET', '/login/options').then((o) => (options = { ...options, ...o })).catch(() => {});

  function remembered() {
    try {
      return localStorage.getItem('mradmin.name') ?? '';
    } catch {
      return '';
    }
  }

  function remember() {
    try {
      localStorage.setItem('mradmin.name', name.trim());
    } catch {
      // private mode - fine
    }
  }

  function switchTo(next) {
    mode = next;
    error = '';
    sent = false;
    needCode = false;
    code = '';
  }

  async function withGameCode(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/self/login', { code: gameCode.replace(/\D/g, '') });
      gameCode = '';
      onSignedIn();
    } catch (e) {
      error = errorText(e);
      gameCode = '';
    } finally {
      busy = false;
    }
  }

  async function withPassword(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/login', { name: name.trim(), password, code: code.trim() });
      remember();
      password = '';
      code = '';
      onSignedIn();
    } catch (e) {
      if (e?.key === 'code-needed') {
        // Password right - the account has 2FA: ask for the code (the password stays in the form).
        needCode = true;
        queueMicrotask(() => codeInput?.focus());
      } else {
        error = errorText(e);
        code = '';
        if (needCode && e?.key !== 'locked') needCode = true;
      }
    } finally {
      busy = false;
    }
  }

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

  async function withDiscord() {
    busy = true;
    error = '';
    try {
      await discord.start('login', route.path);
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }

  async function forgot(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/login/forgot', { name: name.trim() });
      sent = true;
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const own = $derived(options.password || (passkeys.supported && options.passkeys) || options.discord);
</script>

<div class="page">
  <div class="top"><LangFlags /></div>

  <main class="card box">
    <div class="brand">
      <img src={picture('logo')} alt="" height="44" />
      <div>
        <h1>{brand.name}</h1>
        <p class="muted small">{brand.tagline || t('signin.subtitle')}</p>
      </div>
    </div>
    <h2 class="what">{t('signin.title')}</h2>

    {#if notice}<div class="error" role="alert">{notice}</div>{/if}
    {#if error}<div class="error" role="alert">{error}</div>{/if}

    {#if mode === 'code'}
      <form class="form" onsubmit={withGameCode}>
        <div class="field">
          <label for="game-code">{t('signin.gameCode')}</label>
          <input id="game-code" class="mono big" bind:value={gameCode} inputmode="numeric" autocomplete="one-time-code"
            maxlength="7" placeholder="123456" />
          <div class="hint">{t('signin.gameCodeHint')}</div>
        </div>
        <button class="block" type="submit" disabled={busy || gameCode.replace(/\D/g, '').length !== 6}>{t('signin.submit')}</button>
      </form>

      {#if own}
        <div class="or"><span>{t('signin.orOwn')}</span></div>
        <div class="alts">
          {#if options.password}
            <button class="block alt" type="button" onclick={() => switchTo('password')} disabled={busy}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
              {t('signin.withPassword')}
            </button>
          {/if}
          {#if passkeys.supported && options.passkeys}
            <button class="block alt" type="button" onclick={withPasskey} disabled={busy}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true"><path d="M15 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM11 11v10l2-2-2-2 2-2-2-2" /></svg>
              {t('passkey.signIn')}
            </button>
          {/if}
          {#if options.discord}
            <button class="block alt discord" type="button" onclick={withDiscord} disabled={busy}>
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M20.3 4.4A19.6 19.6 0 0 0 15.4 3l-.6 1.2a18 18 0 0 0-5.6 0L8.6 3a19.6 19.6 0 0 0-4.9 1.5C.6 9 .1 13.4.3 17.8a19.8 19.8 0 0 0 6 3l1.3-2a12.7 12.7 0 0 1-2-1l.5-.4a14.1 14.1 0 0 0 12 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.7 19.7 0 0 0 6-3c.5-5.1-.8-9.5-3.6-13.4ZM8.5 15.1c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Zm7 0c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Z"/></svg>
              {t('discord.signIn')}
            </button>
          {/if}
        </div>
      {/if}
    {:else if mode === 'password'}
      <form class="form" onsubmit={withPassword}>
        <div class="field">
          <label for="name">{t('signin.nameOrEmail')}</label>
          <input id="name" bind:value={name} autocomplete="username" autocapitalize="off" spellcheck="false" required maxlength="254" />
        </div>
        <div class="field">
          <label for="password">{t('login.password')}</label>
          <input id="password" type="password" bind:value={password} autocomplete="current-password" required />
        </div>
        {#if needCode}
          <div class="field">
            <label for="code">{t('login.code')}</label>
            <input id="code" class="mono" bind:this={codeInput} bind:value={code} autocomplete="one-time-code" maxlength="12"
              placeholder="123456" />
            <div class="hint">{t('login.codeHint')}</div>
          </div>
        {/if}
        <button class="block" type="submit" disabled={busy || !name.trim() || !password || (needCode && !code.trim())}>
          {busy ? t('common.loading') : t('signin.submit')}
        </button>
        <div class="links">
          <button type="button" class="link" onclick={() => switchTo('code')}>← {t('signin.back')}</button>
          {#if options.mail}<button type="button" class="link" onclick={() => switchTo('forgot')}>{t('signin.forgot')}</button>{/if}
        </div>
      </form>
    {:else}
      <form class="form" onsubmit={forgot}>
        <p class="muted small">{t('signin.forgotHint')}</p>
        {#if sent}
          <div class="notice">{t('signin.forgotSent')}</div>
        {:else}
          <div class="field">
            <label for="forgot-name">{t('signin.nameOrEmail')}</label>
            <input id="forgot-name" bind:value={name} autocomplete="username" autocapitalize="off" spellcheck="false" required maxlength="254" />
          </div>
          <button class="block" type="submit" disabled={busy || !name.trim()}>{t('signin.forgotSend')}</button>
        {/if}
        <div class="links">
          <button type="button" class="link" onclick={() => switchTo('password')}>← {t('signin.back')}</button>
        </div>
      </form>
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
    background:
      radial-gradient(80% 60% at 50% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--bg);
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
    gap: 18px;
    padding: 26px 24px;
    margin: 20px 0;
    border-top: 4px solid var(--accent);
  }

  .brand {
    display: flex;
    gap: 14px;
    align-items: center;
  }

  .brand img {
    max-width: 120px;
    object-fit: contain;
  }

  .brand h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .what {
    margin: 0;
    font-size: 1rem;
  }

  .brand p {
    margin: 2px 0 0;
  }

  .form {
    display: grid;
    gap: 14px;
  }

  .form p {
    margin: 0;
  }

  .field {
    display: grid;
    gap: 6px;
  }

  .field label {
    margin: 0;
    font-weight: 600;
    font-size: 0.9rem;
  }

  .big {
    font-size: 1.6rem;
    letter-spacing: 0.3em;
    text-align: center;
    padding: 10px;
  }

  .hint {
    color: var(--muted);
    font-size: 0.82rem;
  }

  .block {
    width: 100%;
    justify-content: center;
  }

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
    border-top: 1px solid var(--border);
  }

  .alts {
    display: grid;
    gap: 8px;
  }

  .alt {
    gap: 10px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .alt.discord {
    background: #5865f2;
    color: #fff;
    border-color: #5865f2;
  }

  .links {
    display: flex;
    justify-content: space-between;
    gap: 10px;
  }

  .link {
    background: transparent;
    color: var(--accent);
    padding: 2px 0;
    border: 0;
  }

  .notice {
    padding: 10px 12px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--good) 12%, transparent);
    color: var(--good);
    font-size: 0.9rem;
  }
</style>
