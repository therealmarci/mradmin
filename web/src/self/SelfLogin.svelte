<script>
  // Sign-in to the player page: the 6-digit code from /web in game (valid a short time, once). The address of the page
  // never changes, so a bookmark keeps working - after player-web.session-days (or idle-days without use) the page
  // simply asks for a new code.
  import { api } from '../lib/api.js';
  import { t, lang, setLang, errorText } from '../lib/i18n.js';

  let { onSignedIn, error: outer = '' } = $props();

  let code = $state('');
  let error = $state('');
  let busy = $state(false);

  const digits = $derived(code.replace(/\D/g, ''));

  async function submit(event) {
    event.preventDefault();
    if (digits.length !== 6) return;
    busy = true;
    error = '';
    try {
      await api('POST', '/self/login', { code: digits });
      onSignedIn();
    } catch (e) {
      error = errorText(e);
      code = '';
    } finally {
      busy = false;
    }
  }
</script>

<div class="center">
  <form class="card box" onsubmit={submit}>
    <img src="/icon.svg" alt="" width="48" height="48" />
    <h1>{t('self.title')}</h1>
    {#if outer}<div class="error">{outer}</div>{/if}
    <p>{t('self.howTo')}</p>
    <label class="code">
      <span class="sr-only">{t('self.codeLabel')}</span>
      <!-- svelte-ignore a11y_autofocus -->
      <input bind:value={code} inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="000000"
        autofocus aria-describedby="code-hint" />
    </label>
    {#if error}<div class="error">{error}</div>{/if}
    <button type="submit" class="block" disabled={busy || digits.length !== 6}>{t('self.signIn')}</button>
    <p id="code-hint" class="muted small">{t('self.howToBookmark')}</p>
    <button type="button" class="linkish small" onclick={() => setLang(lang === 'de' ? 'en' : 'de')}>{lang === 'de' ? 'English' : 'Deutsch'}</button>
  </form>
</div>

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 16px;
  }

  .box {
    width: min(100%, 400px);
    text-align: center;
    display: grid;
    gap: 12px;
    justify-items: center;
  }

  h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  p {
    margin: 0;
  }

  .code {
    width: 100%;
    margin: 4px 0 0;
  }

  .code input {
    text-align: center;
    font-size: 1.8rem;
    font-weight: 800;
    letter-spacing: 0.35em;
    padding: 12px 8px 12px calc(8px + 0.35em);
    font-variant-numeric: tabular-nums;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }

  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 0;
  }

  .error {
    width: 100%;
  }
</style>
