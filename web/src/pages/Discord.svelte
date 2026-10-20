<script>
  // Back from Discord: finishes sign-in, link or confirmation (the browser sends its cookie now) and moves on.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { markConfirmed } from '../lib/discord.js';

  let { onSignedIn } = $props();

  let error = $state('');

  async function finish() {
    const params = new URLSearchParams(window.location.hash.slice(1));
    history.replaceState(null, '', '/discord');
    if (params.get('error')) {
      error = params.get('error') === 'access_denied' ? t('discord.cancelled') : t('discord.expired');
      return;
    }
    try {
      const result = await api('POST', '/discord/finish', { state: params.get('state'), code: params.get('code') });
      if (result.mode === 'confirm') markConfirmed();
      if (result.mode === 'link') {
        try {
          sessionStorage.setItem('mradmin.discord.linked', '1');
        } catch {
          // fine
        }
      }
      await onSignedIn();
      go(result.mode === 'link' ? '/account' : result.returnTo || '/', true);
    } catch (e) {
      error = errorText(e);
    }
  }
  finish();
</script>

<div class="page">
  <div class="card stack">
    {#if error}
      <h1>Discord</h1>
      <div class="error">{error}</div>
      <button onclick={() => go('/', true)}>{t('discord.back')}</button>
    {:else}
      <p class="muted">{t('discord.finishing')}</p>
    {/if}
  </div>
</div>

<style>
  .page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 20px 16px;
  }

  .card {
    max-width: 400px;
    width: 100%;
    text-align: center;
  }

  h1 {
    margin: 0;
  }
</style>
