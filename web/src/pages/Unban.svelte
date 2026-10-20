<script>
  // Unban request: name + the code from the ban message prove who it is. Then the ban is shown, and either the form
  // for the one request per ban - or the existing request with its conversation (and the one appeal).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { dateTime } from '../lib/format.js';
  import { startProof } from '../lib/pow.js';
  import RequestThread from '../components/RequestThread.svelte';

  let info = $state(null);
  let name = $state('');
  let code = $state('');
  let checked = $state(null);
  let text = $state('');
  let email = $state('');
  let website = $state('');
  let busy = $state(false);
  let error = $state('');
  let proof = null;

  api('GET', '/public/info').then((i) => (info = i)).catch(() => (info = { unban: false }));

  async function check(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      checked = await api('POST', '/public/unban/check', { name: name.trim(), code: code.trim() });
      // Start the spam protection while they write.
      if (checked.canRequest) proof = startProof();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function send(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const { challenge, nonce } = await proof();
      const r = await api('POST', '/public/unban', { name: name.trim(), code: code.trim(), text, email, website, challenge, nonce });
      go('/anfrage/' + r.token, true);
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }
</script>

<div class="page">
  {#if info === null}
    <p class="muted">{t('common.loading')}</p>
  {:else if !info.unban}
    <section class="card"><p class="muted">{t('pub.unbanOff')}</p></section>
  {:else if !checked}
    <form class="card stack" onsubmit={check}>
      <h1>{t('pub.unbanTitle')}</h1>
      <p class="muted small">{t('pub.unbanIntro')}</p>
      <div class="two">
        <div>
          <label for="name">{t('pub.mcName')}</label>
          <input id="name" bind:value={name} maxlength="16" autocomplete="username" autocapitalize="off" spellcheck="false" required />
        </div>
        <div>
          <label for="code">{t('pub.code')}</label>
          <input id="code" bind:value={code} maxlength="30" placeholder="123-ABCDEF" autocapitalize="characters" spellcheck="false" required />
        </div>
      </div>
      <p class="hint">{t('pub.codeHint')}</p>
      {#if error}<div class="error">{error}</div>{/if}
      <button type="submit" disabled={busy || !name.trim() || !code.trim()}>{t('pub.continue')}</button>
    </form>
  {:else if checked.request}
    <RequestThread bind:status={checked.request} auth={{ name: name.trim(), code: code.trim() }} minText={info.minText} maxText={info.maxText} />
  {:else if !checked.ban}
    <section class="card stack">
      <h1>{t('pub.unbanTitle')}</h1>
      <p>{t('pub.noBan', { name: checked.name })}</p>
    </section>
  {:else if checked.used}
    <section class="card stack">
      <h1>{t('pub.unbanTitle')}</h1>
      <p>{t('pub.alreadyUsed')}</p>
    </section>
  {:else}
    <form class="card stack" onsubmit={send}>
      <h1>{t('pub.unbanFor', { name: checked.name })}</h1>
      <div class="ban">
        <div><span class="muted small">{t('pub.banReason')}</span><div class="reason">{checked.ban.reason}</div></div>
        <div class="ban-facts small">
          <span>{t('pub.banBy')}: <strong>{checked.ban.operator}</strong></span>
          <span>{t('pub.banSince')}: <strong>{dateTime(checked.ban.start)}</strong></span>
          <span>{t('pub.banUntil')}: <strong>{checked.ban.end ? dateTime(checked.ban.end) : t('pub.permanent')}</strong></span>
        </div>
      </div>
      <div class="notice small">{t('pub.oneRequest')}</div>
      <div>
        <label for="text">{t('pub.justification')}</label>
        <textarea id="text" bind:value={text} rows="8" maxlength={info.maxText} placeholder={t('pub.justificationPlaceholder')} required></textarea>
        <div class="hint">{t('pub.textHint', { min: info.minText })} · {text.trim().length}/{info.maxText}</div>
      </div>
      <div>
        <label for="email">{t('pub.emailOptional')}</label>
        <input id="email" type="email" bind:value={email} maxlength="254" autocomplete="email" />
        <div class="hint">{info.mail ? t('pub.emailHint') : t('pub.emailHintNoMail')}</div>
      </div>
      <div class="hp" aria-hidden="true">
        <label for="website">Website</label>
        <input id="website" bind:value={website} tabindex="-1" autocomplete="off" />
      </div>
      {#if error}<div class="error">{error}</div>{/if}
      <button type="submit" disabled={busy || text.trim().length < info.minText}>{busy ? t('pub.sending') : t('pub.unbanSend')}</button>
    </form>
  {/if}
</div>

<style>
  .page {
    max-width: 760px;
    margin: 0 auto;
    padding: 20px 16px 40px;
  }

  h1 {
    font-size: 1.4rem;
    margin: 0;
  }

  p {
    margin: 0;
  }

  .two {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
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

  .hp {
    position: absolute;
    left: -10000px;
    width: 1px;
    height: 1px;
    overflow: hidden;
  }
</style>
