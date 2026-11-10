<script>
  // The own sign-ins (since 1.5): everyone gets in with /web; on top a password (name or confirmed e-mail), passkeys,
  // Discord and optionally 2FA. The first own sign-in is added freely; afterwards adding or changing one needs a
  // confirmation with an existing one (password, 2FA code or passkey). Data: GET /api/account/logins (AuthRoutes.java).
  import QRCode from 'qrcode';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago } from '../lib/format.js';
  import { route } from '../lib/router.svelte.js';
  import * as passkeys from '../lib/passkeys.js';
  import * as discord from '../lib/discord.js';
  import Toast from './Toast.svelte';

  // team: whether the viewer is in the team (then the card says what the administration needs)
  let { team = false, require2fa = false, onchange } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  // which form is open: password | email | passkey | totp | null
  let open = $state(null);
  let pw1 = $state('');
  let pw2 = $state('');
  let email = $state('');
  let keyName = $state(guessName());
  let totpSetup = $state(null);
  let qr = $state('');
  let totpCode = $state('');
  let codes = $state(null);
  // confirmation with an existing sign-in: {run(body)} while asked
  let asking = $state(null);
  let confirmPassword = $state('');
  let confirmCode = $state('');

  async function load() {
    try {
      data = await api('GET', '/account/logins');
      error = '';
      onchange?.(data);
    } catch (e) {
      error = errorText(e);
    }
  }
  load();


  function guessName() {
    const ua = navigator.userAgent;
    if (/iPhone/.test(ua)) return 'iPhone';
    if (/iPad/.test(ua)) return 'iPad';
    if (/Android/.test(ua)) return 'Android';
    if (/Mac OS X/.test(ua)) return 'Mac';
    if (/Windows/.test(ua)) return 'Windows';
    return 'Passkey';
  }

  function toggle(form) {
    open = open === form ? null : form;
    error = '';
    asking = null;
  }

  // Runs fn(body) - with a confirmation first if the account has an own sign-in already.
  function confirmed(fn) {
    if (!data.ownLogin) return fn({});
    asking = { run: fn };
    confirmPassword = '';
    confirmCode = '';
  }

  async function sendConfirm(event, how) {
    event?.preventDefault();
    const job = asking;
    let body;
    try {
      body = how === 'passkey' ? await passkeys.proof(api) : how === 'code' ? { code: confirmCode.trim() } : { password: confirmPassword };
    } catch (e) {
      if (!passkeys.cancelled(e)) error = errorText(e);
      return;
    }
    asking = null;
    await job.run(body);
  }

  async function step(fn, done) {
    busy = true;
    error = '';
    message = '';
    try {
      await fn();
      if (done) message = done;
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function savePassword(event) {
    event.preventDefault();
    if (pw1 !== pw2) {
      error = t('logins.passwordMismatch');
      return;
    }
    confirmed((body) => step(async () => {
      await api('POST', '/account/password', { ...body, password: pw1 });
      pw1 = '';
      pw2 = '';
      open = null;
    }, t('logins.passwordSaved')));
  }

  function removePassword() {
    if (!window.confirm(t('logins.passwordRemoveConfirm'))) return;
    confirmed((body) => step(() => api('DELETE', '/account/password', body), t('logins.passwordRemoved')));
  }

  function saveEmail(event) {
    event.preventDefault();
    step(async () => {
      await api('POST', '/account/email', { email: email.trim() });
      open = null;
    }, t('logins.emailSent', { email: email.trim() }));
  }

  function removeEmail() {
    if (!window.confirm(t('logins.emailRemoveConfirm'))) return;
    step(() => api('DELETE', '/account/email'), t('logins.emailRemoved'));
  }

  function addPasskey(event) {
    event.preventDefault();
    confirmed((body) => step(async () => {
      const start = await api('POST', '/passkeys/start', body);
      const credential = await passkeys.create(start.options);
      await api('POST', '/passkeys/finish', { credential, name: keyName.trim() || 'Passkey' });
      open = null;
    }, t('logins.passkeyAdded')));
  }

  function removePasskey(key) {
    if (!window.confirm(t('logins.passkeyRemoveConfirm', { name: key.name }))) return;
    step(() => api('DELETE', `/passkeys/${encodeURIComponent(key.id)}`), t('logins.passkeyRemoved'));
  }

  async function linkDiscord() {
    busy = true;
    try {
      await discord.start('link', route.path);
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }

  function unlinkDiscord() {
    if (!window.confirm(t('logins.discordUnlinkConfirm'))) return;
    step(() => api('POST', '/discord/unlink'), t('logins.discordUnlinked'));
  }

  function startTotp() {
    confirmed((body) => step(async () => {
      totpSetup = await api('POST', '/account/totp/start', body);
      qr = await QRCode.toDataURL(totpSetup.uri, { margin: 1, width: 200 });
      open = 'totp';
    }));
  }

  function finishTotp(event) {
    event.preventDefault();
    step(async () => {
      const result = await api('POST', '/account/totp/finish', { code: totpCode.trim() });
      codes = result.recoveryCodes;
      totpSetup = null;
      totpCode = '';
      open = null;
    }, t('logins.totpOn'));
  }

  function disableTotp() {
    if (!window.confirm(t('logins.totpOffConfirm'))) return;
    confirmed((body) => step(() => api('POST', '/account/totp/disable', body), t('logins.totpOff')));
  }

  const ready = $derived(!!data && data.ownLogin && (!require2fa || data.totp));
</script>

<section class="card logins">
  <h2>{t('logins.title')}</h2>
  <p class="muted small intro">{t('logins.intro')}</p>
  <Toast text={message} />
  {#if error}<div class="error">{error}</div>{/if}

  {#if team && data}
    <div class="team" class:ok={ready}>
      {ready ? t('logins.teamReady') : t(require2fa ? 'logins.teamNeeds2fa' : 'logins.teamNeeds')}
    </div>
  {/if}

  {#if asking}
    <!-- Confirmation with an existing own sign-in -->
    <div class="confirm">
      <strong>{t('logins.confirmTitle')}</strong>
      {#if data.password}
        <form class="inline" onsubmit={(e) => sendConfirm(e, 'password')}>
          <input type="password" bind:value={confirmPassword} placeholder={t('login.password')} autocomplete="current-password" />
          <button type="submit" disabled={!confirmPassword}>{t('logins.confirm')}</button>
        </form>
      {/if}
      {#if data.totp}
        <form class="inline" onsubmit={(e) => sendConfirm(e, 'code')}>
          <input class="mono" bind:value={confirmCode} inputmode="numeric" maxlength="6" placeholder="123456" aria-label={t('login.code')} />
          <button type="submit" disabled={confirmCode.trim().length < 6}>{t('logins.confirm')}</button>
        </form>
      {/if}
      {#if data.passkeys.length && passkeys.supported}
        <button type="button" class="secondary" onclick={() => sendConfirm(null, 'passkey')}>🔑 {t('logins.confirmPasskey')}</button>
      {/if}
      {#if !data.password && !data.totp && !data.passkeys.length}
        <p class="muted small">{t('logins.confirmDiscordOnly')}</p>
      {/if}
      <button type="button" class="link" onclick={() => (asking = null)}>{t('common.cancel')}</button>
    </div>
  {/if}

  {#if data}
    <div class="rows">
      <!-- Password -->
      {#if data.available.password}
        <div class="row">
          <div class="what">
            <span class="dot" class:on={data.password}></span>
            <div>
              <strong>{t('logins.password')}</strong>
              <span class="muted small">{data.password ? t('logins.passwordOn', { name: data.name }) : t('logins.passwordOff')}</span>
            </div>
          </div>
          <div class="acts">
            <button class="secondary" disabled={busy} onclick={() => toggle('password')}>{data.password ? t('logins.change') : t('logins.set')}</button>
            {#if data.password}<button class="ghost danger" disabled={busy} onclick={removePassword}>{t('logins.remove')}</button>{/if}
          </div>
        </div>
        {#if open === 'password'}
          <form class="sub" onsubmit={savePassword}>
            <input type="password" bind:value={pw1} autocomplete="new-password" minlength={data.minPassword}
              placeholder={t('logins.newPassword', { n: data.minPassword })} />
            <input type="password" bind:value={pw2} autocomplete="new-password" placeholder={t('logins.repeatPassword')} />
            <button type="submit" disabled={busy || pw1.length < data.minPassword || !pw2}>{t('common.save')}</button>
          </form>
        {/if}

        <!-- E-mail (sign-in with the address, "forgot password") -->
        <div class="row">
          <div class="what">
            <span class="dot" class:on={!!data.email}></span>
            <div>
              <strong>{t('logins.email')}</strong>
              <span class="muted small">{data.email ?? (data.available.mail ? t('logins.emailOff') : t('logins.emailNoMail'))}</span>
            </div>
          </div>
          <div class="acts">
            {#if data.available.mail}
              <button class="secondary" disabled={busy} onclick={() => toggle('email')}>{data.email ? t('logins.change') : t('logins.add')}</button>
            {/if}
            {#if data.email}<button class="ghost danger" disabled={busy} onclick={removeEmail}>{t('logins.remove')}</button>{/if}
          </div>
        </div>
        {#if open === 'email'}
          <form class="sub" onsubmit={saveEmail}>
            <input type="email" bind:value={email} autocomplete="email" placeholder="name@example.com" maxlength="254" />
            <button type="submit" disabled={busy || !email.includes('@')}>{t('logins.emailSend')}</button>
            <span class="muted small full">{t('logins.emailHint')}</span>
          </form>
        {/if}
      {/if}

      <!-- Passkeys -->
      {#if data.available.passkeys}
        <div class="row">
          <div class="what">
            <span class="dot" class:on={data.passkeys.length > 0}></span>
            <div>
              <strong>{t('logins.passkeys')}</strong>
              <span class="muted small">{data.passkeys.length ? t('logins.passkeysOn', { n: data.passkeys.length }) : t('logins.passkeysOff')}</span>
            </div>
          </div>
          <div class="acts">
            {#if passkeys.supported}<button class="secondary" disabled={busy} onclick={() => toggle('passkey')}>{t('logins.add')}</button>{/if}
          </div>
        </div>
        {#each data.passkeys as key (key.id)}
          <div class="item">
            <span>🔑 {key.name}</span>
            <span class="muted small">{key.lastUsed ? t('logins.used', { time: ago(key.lastUsed) }) : t('logins.neverUsed')}</span>
            <button class="ghost danger small-btn" disabled={busy} onclick={() => removePasskey(key)}>{t('logins.remove')}</button>
          </div>
        {/each}
        {#if open === 'passkey'}
          <form class="sub" onsubmit={addPasskey}>
            <input bind:value={keyName} maxlength="40" placeholder={t('logins.passkeyName')} />
            <button type="submit" disabled={busy}>{t('logins.passkeyCreate')}</button>
          </form>
        {/if}
      {/if}

      <!-- Discord -->
      {#if data.available.discord}
        <div class="row">
          <div class="what">
            <span class="dot" class:on={!!data.discord}></span>
            <div>
              <strong>Discord</strong>
              <span class="muted small">{data.discord ? t('logins.discordOn', { name: data.discord }) : t('logins.discordOff')}</span>
            </div>
          </div>
          <div class="acts">
            {#if data.discord}
              <button class="ghost danger" disabled={busy} onclick={unlinkDiscord}>{t('logins.unlink')}</button>
            {:else}
              <button class="secondary" disabled={busy} onclick={linkDiscord}>{t('logins.link')}</button>
            {/if}
          </div>
        </div>
      {/if}

      <!-- 2FA -->
      <div class="row">
        <div class="what">
          <span class="dot" class:on={data.totp}></span>
          <div>
            <strong>{t('logins.totp')}</strong>
            <span class="muted small">{data.totp ? t('logins.totpOnText', { n: data.recoveryLeft }) : t('logins.totpOffText')}</span>
          </div>
        </div>
        <div class="acts">
          {#if data.totp}
            <button class="ghost danger" disabled={busy} onclick={disableTotp}>{t('logins.switchOff')}</button>
          {:else}
            <button class="secondary" disabled={busy} onclick={startTotp}>{t('logins.switchOn')}</button>
          {/if}
        </div>
      </div>
      {#if open === 'totp' && totpSetup}
        <form class="sub totp" onsubmit={finishTotp}>
          {#if qr}<img src={qr} alt="" width="200" height="200" />{/if}
          <div class="totp-side">
            <span class="muted small">{t('logins.totpScan')}</span>
            <code class="secret">{totpSetup.secret}</code>
            <input class="mono" bind:value={totpCode} inputmode="numeric" maxlength="6" placeholder="123456" aria-label={t('login.code')} />
            <button type="submit" disabled={busy || totpCode.trim().length < 6}>{t('logins.totpFinish')}</button>
          </div>
        </form>
      {/if}
      {#if codes}
        <div class="codes">
          <strong>{t('logins.recoveryTitle')}</strong>
          <span class="muted small">{t('logins.recoveryHint')}</span>
          <div class="grid mono">{#each codes as c (c)}<span>{c}</span>{/each}</div>
          <button class="secondary" onclick={() => (codes = null)}>{t('logins.recoveryDone')}</button>
        </div>
      {/if}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</section>

<style>
  .logins {
    display: grid;
    gap: 14px;
    padding: 20px 22px;
  }

  h2 {
    margin: 0;
    font-size: 1.15rem;
  }

  .intro {
    margin: -6px 0 0;
  }

  .team {
    padding: 10px 12px;
    border-radius: 10px;
    font-size: 0.9rem;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 12%, transparent);
  }

  .team.ok {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 12%, transparent);
  }

  .confirm {
    display: grid;
    gap: 8px;
    padding: 12px;
    border-radius: 12px;
    border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border));
    background: color-mix(in srgb, var(--accent) 6%, transparent);
  }

  .confirm p {
    margin: 0;
  }

  .inline {
    display: flex;
    gap: 8px;
  }

  .inline input {
    flex: 1;
    min-width: 0;
    width: auto;
  }

  .rows {
    display: grid;
    gap: 2px;
  }

  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 0;
    border-top: 1px solid var(--border);
  }

  .what {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }

  .what div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .what .small {
    overflow-wrap: anywhere;
  }

  .dot {
    width: 10px;
    height: 10px;
    flex: none;
    border-radius: 50%;
    background: var(--muted);
  }

  .dot.on {
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 22%, transparent);
  }

  .acts {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
  }

  .acts button,
  .small-btn {
    justify-content: flex-start;
    padding: 6px 12px;
    font-size: 0.86rem;
  }

  .ghost {
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
  }

  .ghost.danger {
    color: var(--bad);
    border-color: color-mix(in srgb, var(--bad) 45%, var(--border));
  }

  .link {
    justify-self: start;
    background: transparent;
    color: var(--accent);
    border: 0;
    padding: 2px 0;
  }

  .sub {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 0 0 12px 22px;
  }

  .sub input {
    flex: 1 1 180px;
    width: auto;
  }

  .full {
    flex-basis: 100%;
  }

  .item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
    gap: 10px;
    padding: 4px 0 8px 22px;
    font-size: 0.9rem;
  }

  .totp {
    align-items: flex-start;
  }

  .totp img {
    border-radius: 8px;
    background: #fff;
  }

  .totp-side {
    display: grid;
    gap: 8px;
    flex: 1 1 200px;
  }

  .secret {
    font-size: 0.8rem;
    overflow-wrap: anywhere;
  }

  .codes {
    display: grid;
    gap: 8px;
    padding: 12px;
    border-radius: 12px;
    background: var(--surface-2);
  }

  .codes .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px 12px;
  }

  @media (max-width: 560px) {
    .row {
      align-items: flex-start;
      flex-direction: column;
    }

    .acts {
      justify-content: flex-start;
    }
  }
</style>
