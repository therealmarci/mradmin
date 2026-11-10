<script>
  // A request from the public forms as its author sees it: state, the ban (unban requests), the conversation, and
  // the answer box - or the one appeal after a rejection. Whitelist applications: once accepted, the applicant makes
  // their web account right here (password, then signed in). `auth` is {token} or {name, code}.
  import { tick } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let { status = $bindable(), auth, minText = 20, maxText = 3000 } = $props();

  let text = $state('');
  let busy = $state(false);
  let error = $state('');
  let appealing = $state(false);
  let chat = $state();

  const unban = $derived(status.kind === 'unban');
  const application = $derived(status.kind === 'application');
  const phase = $derived.by(() => {
    if (status.open) return status.stage === 'appeal' ? 'appeal' : 'open';
    if (application) return status.decision === 'accepted' ? 'appAccepted' : 'appRejected';
    if (!unban) return 'closed';
    if (status.decision === 'accepted' || !status.banActive) return 'accepted';
    return status.stage === 'appeal' ? 'final' : 'rejected';
  });

  async function post(path, body) {
    busy = true;
    error = '';
    try {
      status = await api('POST', '/public/status' + path, { ...auth, ...body });
      text = '';
      appealing = false;
      await tick();
      chat?.scrollTo({ top: chat.scrollHeight, behavior: 'smooth' });
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // The accepted applicant's own account (only with the status link)
  let password = $state('');
  let password2 = $state('');
  async function makeAccount(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      await api('POST', '/public/status/account', { token: auth.token, password });
      location.href = '/';
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }

  const reply = () => text.trim() && post('/reply', { text });
  const appeal = () => post('/appeal', { text });
</script>

<section class="card stack">
  <div class="head">
    <div>
      <div class="muted small">{unban ? t('pub.unbanTitle') : application ? t('apply.title') : t('pub.contactTitle')} · #{status.id}</div>
      <h1>{status.subject}</h1>
    </div>
    <span class="state {phase}">{t("pub.state." + phase)}</span>
  </div>
  <p class="muted small">{t("pub.stateHint." + phase)}</p>

  {#if unban}
    <div class="ban">
      <div><span class="muted small">{t('pub.banReason')}</span><div class="reason">{status.ban.reason}</div></div>
      <div class="ban-facts small">
        <span>{t('pub.banBy')}: <strong>{status.ban.operator}</strong></span>
        <span>{t('pub.banSince')}: <strong>{dateTime(status.ban.start)}</strong></span>
        <span>{t('pub.banUntil')}: <strong>{status.ban.end ? dateTime(status.ban.end) : t('pub.permanent')}</strong></span>
      </div>
    </div>
  {/if}

  {#if application && phase === 'appAccepted'}
    <!-- Welcome: server address and the own account for the web interface -->
    <div class="welcome">
      {#if status.address}
        <div><span class="muted small">{t('apply.address')}</span><div class="address">{status.address}</div></div>
      {/if}
      {#if status.hasAccount}
        <p class="small">{t('apply.hasAccount')}</p>
        <a class="button" href="/login">{t('nav.login')}</a>
      {:else if auth.token}
        <form class="account" onsubmit={makeAccount}>
          <strong>{t('apply.accountTitle', { player: status.player })}</strong>
          <span class="muted small">{t('apply.accountHint')}</span>
          <input type="password" bind:value={password} autocomplete="new-password" minlength="10" maxlength="200"
            placeholder={t('apply.password')} aria-label={t('apply.password')} />
          <input type="password" bind:value={password2} autocomplete="new-password" maxlength="200"
            placeholder={t('apply.password2')} aria-label={t('apply.password2')} />
          {#if password2 && password !== password2}<span class="bad-text small">{t('apply.passwordMismatch')}</span>{/if}
          <button type="submit" disabled={busy || password.length < 10 || password !== password2}>{t('apply.accountCreate')}</button>
        </form>
      {/if}
    </div>
  {/if}

  <div class="chat" bind:this={chat}>
    {#each status.messages as m}
      {#if m.system}
        <div class="system"><div class="who small">{m.name} · {dateTime(m.time)}</div><div class="text">{m.text}</div></div>
      {:else}
        <div class="msg" class:mine={m.mine}>
          <div class="who small"><strong>{m.mine ? t('pub.you') : m.name}</strong> · {dateTime(m.time)}</div>
          <div class="text">{m.text}</div>
        </div>
      {/if}
    {/each}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if status.canReply}
    <div>
      <textarea bind:value={text} rows="3" maxlength={maxText} placeholder={t('pub.replyPlaceholder')}></textarea>
      <div class="actions"><button onclick={reply} disabled={busy || !text.trim()}>{t('pub.send')}</button></div>
    </div>
  {:else if status.canAppeal}
    {#if appealing}
      <div>
        <label for="appeal">{t('pub.appealLabel')}</label>
        <textarea id="appeal" bind:value={text} rows="6" maxlength={maxText} placeholder={t('pub.appealPlaceholder')}></textarea>
        <div class="hint">{t('pub.appealHint', { min: minText })} · {text.trim().length}/{maxText}</div>
        <div class="actions">
          <button onclick={appeal} disabled={busy || text.trim().length < minText}>{t('pub.appealSend')}</button>
          <button class="secondary" onclick={() => (appealing = false)} disabled={busy}>{t('pub.cancel')}</button>
        </div>
      </div>
    {:else}
      <button class="secondary" onclick={() => (appealing = true)}>{t('pub.appealStart')}</button>
    {/if}
  {/if}
  {#if status.email}<p class="muted small">{t('pub.mailNote', { email: status.email })}</p>{/if}
</section>

<style>
  .welcome {
    display: grid;
    gap: 12px;
    padding: 14px 16px;
    border-radius: 12px;
    border: 1px solid color-mix(in srgb, var(--good) 40%, var(--border));
    background: color-mix(in srgb, var(--good) 8%, var(--surface-2));
  }

  .welcome p {
    margin: 0;
  }

  .address {
    font-family: var(--mono, ui-monospace, monospace);
    font-size: 1.1rem;
    font-weight: 700;
  }

  .account {
    display: grid;
    gap: 8px;
  }

  .welcome .button {
    justify-self: start;
    padding: 8px 16px;
    border-radius: 10px;
    background: var(--accent);
    color: var(--accent-text, #fff);
    font-weight: 600;
    text-decoration: none;
  }

  .bad-text {
    color: var(--bad);
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
  }

  h1 {
    font-size: 1.2rem;
    margin: 2px 0 0;
    word-break: break-word;
  }

  p {
    margin: 0;
  }

  .state {
    flex-shrink: 0;
    font-size: 0.8rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    color: var(--accent);
  }

  .state.accepted,
  .state.appAccepted {
    background: color-mix(in srgb, var(--good) 15%, transparent);
    color: var(--good);
  }

  .state.rejected,
  .state.appRejected,
  .state.final {
    background: color-mix(in srgb, var(--bad) 12%, transparent);
    color: var(--bad);
  }

  .state.closed {
    background: var(--surface-2);
    color: var(--muted);
  }

  .ban {
    background: var(--surface-2);
    border-left: 4px solid var(--bad);
    border-radius: 10px;
    padding: 12px 14px;
    display: grid;
    gap: 8px;
  }

  .reason {
    font-weight: 600;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .ban-facts {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 16px;
    color: var(--muted);
  }

  .chat {
    display: grid;
    gap: 10px;
    max-height: 55vh;
    overflow-y: auto;
  }

  .msg {
    max-width: 88%;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 14px 14px 14px 4px;
    padding: 8px 12px;
  }

  .msg.mine {
    margin-left: auto;
    background: color-mix(in srgb, var(--accent) 14%, var(--surface));
    border-color: color-mix(in srgb, var(--accent) 30%, var(--border));
    border-radius: 14px 14px 4px 14px;
  }

  .system {
    background: var(--surface-2);
    border-left: 4px solid var(--warn);
    border-radius: 10px;
    padding: 8px 12px;
    font-size: 0.92rem;
  }

  .who {
    color: var(--muted);
    margin-bottom: 2px;
  }

  .text {
    white-space: pre-wrap;
    word-break: break-word;
  }

  textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    resize: vertical;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }
</style>
