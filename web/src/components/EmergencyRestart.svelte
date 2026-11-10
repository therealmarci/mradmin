<script>
  // Emergency restart / stop (shorter than the normal countdowns): a window that first asks for a fresh sign-in -
  // 2FA code or passkey, like unlocking the web console (POST /api/restart/confirm, valid 2 minutes) - and then for
  // the kind, the time and a reason. The server checks all of it again (VersionRoutes.java).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { duration } from '../lib/format.js';
  import * as passkeys from '../lib/passkeys.js';

  let { me, delays, canRestart, starterActive, reason: startReason = '', onclose, ondone } = $props();

  let dialog = $state();
  let step = $state('auth');
  let code = $state('');
  let kind = $state('restart');
  let delay = $state(null);
  let reason = $state('');
  let busy = $state(false);
  let error = $state('');

  export function open() {
    step = 'auth';
    code = '';
    kind = canRestart ? 'restart' : 'stop';
    delay = delays[0] ?? null;
    reason = startReason;
    error = '';
    dialog?.showModal();
  }

  function close() {
    dialog?.close();
  }

  const delayText = (ms) => (ms === 0 ? t('versions.restartNow') : t('versions.restartIn', { time: duration(ms) }));

  async function confirm(event, how = 'code') {
    event?.preventDefault();
    busy = true;
    error = '';
    try {
      const body = how === 'passkey' ? await passkeys.proof(api) : { code: code.trim() };
      await api('POST', '/restart/confirm', body);
      code = '';
      step = 'go';
    } catch (e) {
      if (!passkeys.cancelled(e)) error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function go(event) {
    event?.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/restart', { delay: String(delay), kind, reason: reason.trim() });
      close();
      ondone?.(t(kind === 'stop' ? 'server.stopStarted' : 'versions.restartStarted'));
    } catch (e) {
      error = errorText(e);
      // The confirmation is used up by every try - sign in again.
      step = 'auth';
    } finally {
      busy = false;
    }
  }
</script>

<dialog bind:this={dialog} onclose={() => onclose?.()} aria-labelledby="emergency-title">
  <div class="head">
    <h3 id="emergency-title">{t('server.emergencyTitle')}</h3>
    <button type="button" class="x" onclick={close} aria-label={t('common.close')}>✕</button>
  </div>
  <p class="warn small">{t('server.emergencyHint')}</p>

  {#if error}<div class="error">{error}</div>{/if}

  {#if step === 'auth'}
    <form class="auth" onsubmit={confirm}>
      <p class="muted small">{t('server.emergencyAuth')}</p>
      {#if me.totp}
        <div class="code">
          <input inputmode="numeric" autocomplete="one-time-code" maxlength="6" bind:value={code} placeholder="123456"
            aria-label={t('login.code')} />
          <button type="submit" disabled={busy || code.trim().length < 6}>{t('server.emergencyConfirmCode')}</button>
        </div>
      {/if}
      {#if me.passkeys > 0 && passkeys.supported}
        <button type="button" class="secondary" disabled={busy} onclick={() => confirm(null, 'passkey')}>
          🔑 {t('server.emergencyConfirmPasskey')}
        </button>
      {/if}
      {#if !me.totp && !(me.passkeys > 0 && passkeys.supported)}
        <p class="error small">{t('server.emergencyNoAuth')}</p>
      {/if}
    </form>
  {:else}
    <form class="go" onsubmit={go}>
      <div class="field">
        <span class="label">{t('server.emergencyKind')}</span>
        <div class="seg" role="radiogroup">
          <button type="button" role="radio" aria-checked={kind === 'restart'} class:on={kind === 'restart'} disabled={!canRestart}
            onclick={() => (kind = 'restart')}>{t('server.restart')}</button>
          <button type="button" role="radio" aria-checked={kind === 'stop'} class:on={kind === 'stop'}
            onclick={() => (kind = 'stop')}>{t('server.stop')}</button>
        </div>
      </div>
      <div class="field">
        <span class="label">{t('server.emergencyWhen')}</span>
        <div class="seg" role="radiogroup">
          {#each delays as d (d)}
            <button type="button" role="radio" aria-checked={delay === d} class:on={delay === d} onclick={() => (delay = d)}>
              {delayText(d)}
            </button>
          {/each}
        </div>
      </div>
      <label class="field">
        <span class="label">{t('server.reason')}</span>
        <input type="text" maxlength="200" bind:value={reason} placeholder={t('server.emergencyReasonPlaceholder')} />
      </label>
      {#if kind === 'stop' && !starterActive}<p class="muted small">{t('server.emergencyStopNoStarter')}</p>{/if}
      <div class="actions">
        <button type="button" class="ghost" onclick={close}>{t('common.cancel')}</button>
        <button type="submit" class="danger" disabled={busy || delay === null || !reason.trim()}>
          {t(kind === 'stop' ? 'server.emergencyGoStop' : 'server.emergencyGoRestart', { time: delay === null ? '' : delayText(delay) })}
        </button>
      </div>
    </form>
  {/if}
</dialog>

<style>
  dialog {
    width: min(460px, calc(100vw - 32px));
    max-height: calc(100dvh - 32px);
    border: 1px solid color-mix(in srgb, var(--bad) 45%, var(--border));
    border-top: 5px solid var(--bad);
    border-radius: var(--radius);
    background: var(--surface);
    color: var(--text);
    padding: 18px 20px 20px;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 50%);
  }

  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  h3 {
    margin: 0;
    color: var(--bad);
  }

  .x {
    background: transparent;
    color: var(--muted);
    border: 0;
    padding: 4px 8px;
  }

  .warn {
    margin: 6px 0 14px;
    color: var(--bad);
  }

  form {
    display: grid;
    gap: 14px;
  }

  form p {
    margin: 0;
  }

  .code {
    display: flex;
    gap: 8px;
  }

  .code input {
    flex: 1;
    min-width: 0;
    letter-spacing: 0.2em;
    font-size: 1.1rem;
  }

  .field {
    display: grid;
    gap: 6px;
    margin: 0;
  }

  .label {
    font-weight: 600;
    font-size: 0.88rem;
  }

  .seg {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .seg button {
    justify-content: flex-start;
    padding: 6px 12px;
    font-size: 0.86rem;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .seg button.on {
    color: var(--bad);
    border-color: var(--bad);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 8px;
  }

  .ghost {
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
  }

  .danger {
    background: var(--bad);
    color: #fff;
    border: 0;
  }
</style>
