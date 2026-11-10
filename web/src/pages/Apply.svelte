<script>
  // Whitelist application (public, /bewerben - also as a window from the start page widget): name, Minecraft name and
  // e-mail. After sending, a confirmation link goes to the address; opening it (/bewerben?code=...) turns the
  // application into a ticket for the team and leads to the status page (/anfrage/<token>). Spam protection like the
  // contact form: honeypot, proof of work, limits on the server.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { startProof } from '../lib/pow.js';
  import { brand } from '../lib/branding.svelte.js';

  // modal: shown in a window over the start page (onclose closes it)
  let { modal = false, onclose = null } = $props();

  const code = new URLSearchParams(location.search).get('code');

  let info = $state(null);
  let name = $state('');
  let player = $state('');
  let email = $state('');
  let website = $state('');
  let busy = $state(false);
  let error = $state('');
  let sentTo = $state('');

  api('GET', '/public/info').then((i) => (info = i)).catch(() => (info = { application: false }));
  const proof = code ? null : startProof();

  // The confirmation link: becomes a ticket, then to its status page.
  if (code) {
    api('POST', '/public/apply/confirm', { code })
      .then((r) => go('/anfrage/' + r.token, true))
      .catch((e) => (error = errorText(e)));
  }

  const validPlayer = $derived(/^[A-Za-z0-9_]{3,16}$/.test(player.trim()));

  async function send(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const { challenge, nonce } = await proof();
      const r = await api('POST', '/public/apply', { name, player: player.trim(), email, website, challenge, nonce });
      sentTo = r.email;
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page" class:modal>
  {#if !modal}
    <button class="back" onclick={() => (history.length > 1 ? history.back() : go('/'))}>← {t('apply.back')}</button>
  {/if}
  {#if code}
    <section class="card stack">
      <h1>{t('apply.title')}</h1>
      {#if error}<div class="error">{error}</div>{:else}<p class="muted">{t('apply.confirming')}</p>{/if}
    </section>
  {:else if info === null}
    <p class="muted">{t('common.loading')}</p>
  {:else if !info.application}
    <section class="card"><p class="muted">{t('apply.off')}</p></section>
  {:else if sentTo}
    <section class="card stack done">
      <span class="big" aria-hidden="true">✉</span>
      <h1>{t('apply.sentTitle')}</h1>
      <p>{t('apply.sentText', { email: sentTo })}</p>
      <p class="muted small">{t('apply.sentHint')}</p>
      {#if modal}<button class="secondary" onclick={onclose}>{t('common.close')}</button>{/if}
    </section>
  {:else}
    <form class="card stack" onsubmit={send}>
      <div class="f-head">
        <h1>{t('apply.title')}</h1>
        {#if modal}<button type="button" class="secondary close" aria-label={t('common.close')} onclick={onclose}>✕</button>{/if}
      </div>
      <p class="muted small">{t('apply.intro', { server: brand.name })}</p>
      <div>
        <label for="ap-name">{t('apply.name')}</label>
        <input id="ap-name" bind:value={name} maxlength="32" autocomplete="name" required />
      </div>
      <div>
        <label for="ap-player">{t('apply.player')}</label>
        <input id="ap-player" bind:value={player} maxlength="16" autocomplete="off" spellcheck="false" required />
        <div class="hint">{player && !validPlayer ? t('apply.playerInvalid') : t('apply.playerHint')}</div>
      </div>
      <div>
        <label for="ap-email">{t('apply.email')}</label>
        <input id="ap-email" type="email" bind:value={email} maxlength="254" autocomplete="email" required />
        <div class="hint">{t('apply.emailHint')}</div>
      </div>
      <!-- Honeypot: hidden from people (and screen readers), bots fill it in. -->
      <div class="hp" aria-hidden="true">
        <label for="ap-website">Website</label>
        <input id="ap-website" bind:value={website} tabindex="-1" autocomplete="off" />
      </div>
      <p class="muted small">{t('pub.privacyNote')}</p>
      {#if error}<div class="error">{error}</div>{/if}
      <button type="submit" disabled={busy || name.trim().length < 2 || !validPlayer || !email.trim()}>
        {busy ? t('pub.sending') : t('apply.send')}
      </button>
    </form>
  {/if}
</div>

<style>
  .page {
    max-width: 560px;
    margin: 0 auto;
    padding: 24px 16px 40px;
    display: grid;
    gap: 14px;
  }

  .page.modal {
    padding: 0;
    width: 100%;
  }

  .back {
    justify-self: start;
    padding: 4px 0;
    background: transparent;
    border: 0;
    color: var(--accent);
    font-weight: 600;
  }

  .stack {
    display: grid;
    gap: 14px;
  }

  .f-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  h1 {
    margin: 0;
    font-size: 1.35rem;
  }

  .close {
    padding: 4px 10px;
  }

  label {
    display: block;
    margin-bottom: 6px;
    font-weight: 600;
    font-size: 0.88rem;
  }

  .hint {
    margin-top: 4px;
    font-size: 0.8rem;
    color: var(--muted);
  }

  .hp {
    position: absolute;
    left: -10000px;
    width: 1px;
    height: 1px;
    overflow: hidden;
  }

  .done {
    text-align: center;
    justify-items: center;
  }

  .done p {
    margin: 0;
  }

  .big {
    font-size: 2.4rem;
    color: var(--accent);
  }

  .muted {
    color: var(--muted);
  }

  .small {
    font-size: 0.84rem;
  }
</style>
