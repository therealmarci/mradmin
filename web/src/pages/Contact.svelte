<script>
  // Contact form below the imprint: no account needed. Spam protection: honeypot field, proof of work, limits on the
  // server. The answer comes by mail (if set up) and on the status page the person is sent to.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { startProof } from '../lib/pow.js';

  let info = $state(null);
  let name = $state('');
  let email = $state('');
  let subject = $state('');
  let text = $state('');
  let website = $state('');
  let busy = $state(false);
  let error = $state('');

  api('GET', '/public/info').then((i) => (info = i)).catch(() => (info = { contact: false }));
  const proof = startProof();

  async function send(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const { challenge, nonce } = await proof();
      const r = await api('POST', '/public/contact', { name, email, subject, text, website, challenge, nonce });
      go('/anfrage/' + r.token, true);
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }
</script>

<div class="page">
  <button class="back" onclick={() => (history.length > 1 ? history.back() : go('/impressum'))}>← {t('legal.back')}</button>
  {#if info === null}
    <p class="muted">{t('common.loading')}</p>
  {:else if !info.contact}
    <section class="card"><p class="muted">{t('pub.contactOff')}</p></section>
  {:else}
    <form class="card stack" onsubmit={send}>
      <h1>{t('pub.contactTitle')}</h1>
      <p class="muted small">{t('pub.contactIntro')}</p>
      <div class="two">
        <div>
          <label for="name">{t('pub.name')}</label>
          <input id="name" bind:value={name} maxlength="32" autocomplete="name" required />
        </div>
        <div>
          <label for="email">{t('pub.email')}</label>
          <input id="email" type="email" bind:value={email} maxlength="254" autocomplete="email" required />
        </div>
      </div>
      <div>
        <label for="subject">{t('pub.subject')}</label>
        <input id="subject" bind:value={subject} maxlength="100" required />
      </div>
      <div>
        <label for="text">{t('pub.message')}</label>
        <textarea id="text" bind:value={text} rows="7" maxlength={info.maxText} required></textarea>
        <div class="hint">{t('pub.textHint', { min: info.minText })} · {text.trim().length}/{info.maxText}</div>
      </div>
      <!-- Honeypot: hidden from people (and screen readers), bots fill it in. -->
      <div class="hp" aria-hidden="true">
        <label for="website">Website</label>
        <input id="website" bind:value={website} tabindex="-1" autocomplete="off" />
      </div>
      <p class="muted small">{t('pub.privacyNote')}</p>
      {#if error}<div class="error">{error}</div>{/if}
      <button type="submit" disabled={busy || text.trim().length < info.minText || !name.trim() || !email.trim() || !subject.trim()}>
        {busy ? t('pub.sending') : t('pub.send')}
      </button>
    </form>
  {/if}
</div>

<style>
  .page {
    max-width: 760px;
    margin: 0 auto;
    padding: 20px 16px 40px;
  }

  .back {
    background: transparent;
    color: var(--accent);
    padding: 0 0 12px;
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
