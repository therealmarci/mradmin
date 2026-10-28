<script>
  // The link from /web: /me/login#<code>. The code is in the fragment, so it never reaches a server log; it is sent
  // once and removed from the address bar right away (a bookmark of this page would be useless).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';

  let { onSignedIn } = $props();

  let error = $state('');
  const code = location.hash.slice(1);
  history.replaceState(null, '', '/me/login');

  async function redeem() {
    if (!code) {
      error = t('self.noCode');
      return;
    }
    try {
      await api('POST', '/self/login', { code });
      onSignedIn();
    } catch (e) {
      error = errorText(e);
    }
  }
  redeem();
</script>

<div class="center">
  <section class="card box">
    <img src="/icon.svg" alt="" width="48" height="48" />
    {#if error}
      <div class="error">{error}</div>
      <p>{t('self.howTo')}</p>
    {:else}
      <p class="muted">{t('self.signingIn')}</p>
    {/if}
  </section>
</div>

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 16px;
  }

  .box {
    max-width: 420px;
    text-align: center;
    display: grid;
    gap: 10px;
    justify-items: center;
  }

  p {
    margin: 0;
  }
</style>
