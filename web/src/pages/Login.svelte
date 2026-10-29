<script>
  // Team sign-in in two steps: 1. Minecraft name and password (or instead a passkey / Discord), 2. the code from the
  // authenticator app (or a recovery code). Name, password and code are checked together at the end - a check of the
  // password alone would tell an attacker without the 2FA that it is right.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import Footer from '../components/Footer.svelte';
  import LangFlags from '../components/LangFlags.svelte';
  import * as passkeys from '../lib/passkeys.js';
  import * as discord from '../lib/discord.js';

  let { onSignedIn } = $props();

  let step = $state(1);
  let name = $state(localStorage.getItem('mradmin.name') ?? '');
  let password = $state('');
  let code = $state('');
  let busy = $state(false);
  let error = $state('');
  let codeInput = $state();

  // Which methods the server offers (Discord only when set up there), and whether there is a player page to go back to.
  let options = $state({ discord: false, passkeys: true, password: true, playerWeb: false });
  api('GET', '/login/options').then((o) => (options = { ...options, ...o })).catch(() => {});

  function next(event) {
    event.preventDefault();
    if (!name.trim() || !password) return;
    error = '';
    step = 2;
    queueMicrotask(() => codeInput?.focus());
  }

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
      // Only passkey / Discord on this account: back to the first step, where those are.
      if (e?.key === 'passkey-only' || e?.key === 'locked') {
        password = '';
        step = 1;
      }
    } finally {
      busy = false;
    }
  }

  function back() {
    step = 1;
    code = '';
    error = '';
  }

  async function withDiscord() {
    busy = true;
    error = '';
    try {
      await discord.start('login', '/');
    } catch (e) {
      error = errorText(e);
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

  const alternatives = $derived((passkeys.supported && options.passkeys) || options.discord);
</script>

<div class="page">
  <div class="top">
    {#if options.playerWeb}<button class="back" onclick={() => go('/')}>← {t('login.toChoice')}</button>{:else}<span></span>{/if}
    <LangFlags />
  </div>

  <main class="card box">
    <div class="brand">
      <img src="/icon.svg" alt="" width="44" height="44" />
      <div>
        <h1>{t('login.title')}</h1>
        <p class="muted small">{t('login.teamArea')}</p>
      </div>
    </div>

    {#if options.password}
      <!-- Where we are: 1 name and password, 2 code -->
      <ol class="steps" aria-label={t('login.title')}>
        <li class:on={step >= 1} class:done={step > 1}><span>1</span>{t('login.step1')}</li>
        <li class:on={step >= 2}><span>2</span>{t('login.step2')}</li>
      </ol>
    {/if}

    {#if error}<div class="error" role="alert">{error}</div>{/if}

    {#if step === 1}
      {#if options.password}
        <form class="form" onsubmit={next}>
          <div class="field">
            <label for="name">{t('login.name')}</label>
            <input id="name" bind:value={name} autocomplete="username" autocapitalize="off" spellcheck="false" required maxlength="16" />
          </div>
          <div class="field">
            <label for="password">{t('login.password')}</label>
            <input id="password" type="password" bind:value={password} autocomplete="current-password" required />
          </div>
          <button class="block" type="submit" disabled={busy || !name.trim() || !password}>{t('login.next')} →</button>
        </form>
      {/if}

      {#if alternatives}
        {#if options.password}<div class="or"><span>{t('login.orWith')}</span></div>{/if}
        <div class="alts">
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
    {:else}
      <form class="form" onsubmit={submit}>
        <div class="who">
          <span class="muted small">{t('login.as')}</span>
          <strong>{name.trim()}</strong>
          <button type="button" class="link" onclick={back}>{t('login.change')}</button>
        </div>
        <div class="field">
          <label for="code">{t('login.code')}</label>
          <input id="code" class="mono code" bind:this={codeInput} bind:value={code} autocomplete="one-time-code"
            inputmode="text" required maxlength="12" placeholder="123456" />
          <div class="hint">{t('login.codeHint')}</div>
        </div>
        <button class="block" type="submit" disabled={busy || !code.trim()}>{busy ? t('common.loading') : t('login.submit')}</button>
        <button type="button" class="secondary block" onclick={back} disabled={busy}>← {t('login.back')}</button>
      </form>
    {/if}

    <p class="muted small foot">{t('login.noAccount')}</p>
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
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }

  .back {
    background: transparent;
    color: var(--accent);
    padding: 4px 0;
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

  .brand h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .brand p {
    margin: 2px 0 0;
  }

  /* Steps 1 - 2 */
  .steps {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .steps li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-top: 8px;
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--muted);
    border-top: 3px solid var(--border);
  }

  .steps li.on {
    color: var(--text);
    border-top-color: var(--accent);
  }

  .steps span {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 50%;
    font-size: 0.75rem;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .steps li.on span {
    color: var(--accent-text);
    background: var(--accent);
    border-color: transparent;
  }

  .form {
    display: grid;
    gap: 12px;
  }

  .field {
    margin: 0;
  }

  .who {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px 10px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .link {
    margin-left: auto;
    padding: 0;
    background: transparent;
    color: var(--accent);
    font-size: 0.85rem;
  }

  .code {
    font-size: 1.4rem;
    letter-spacing: 0.25em;
    text-align: center;
  }

  /* "or" between password and the other ways, with a line on both sides. */
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

  .alts {
    display: grid;
    gap: 8px;
  }

  .alt {
    padding: 11px 16px;
    gap: 10px;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .alt.discord {
    background: #5865f2;
    color: #fff;
    border-color: transparent;
  }

  .foot {
    margin: 0;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }
</style>
