<script>
  // Discord as a sign-in method: link (only signed in - so nobody can take over someone else's account) or unlink.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import * as discord from '../lib/discord.js';

  let { me } = $props();

  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  // Just back from linking.
  try {
    if (sessionStorage.getItem('mradmin.discord.linked') === '1') {
      sessionStorage.removeItem('mradmin.discord.linked');
      message = t('discord.linked');
    }
  } catch {
    // fine
  }

  async function link() {
    busy = true;
    error = '';
    try {
      await discord.start('link', '/account');
    } catch (e) {
      error = errorText(e);
      busy = false;
    }
  }

  async function unlink() {
    if (!confirm(t('discord.unlinkConfirm'))) return;
    busy = true;
    error = '';
    try {
      await api('POST', '/discord/unlink');
      me.discord = null;
      message = t('discord.unlinked');
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

{#if me.discordAvailable}
  <section class="card stack">
    <div class="row">
      <h2><span class="logo" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M20.3 4.4A19.6 19.6 0 0 0 15.4 3l-.6 1.2a18 18 0 0 0-5.6 0L8.6 3a19.6 19.6 0 0 0-4.9 1.5C.6 9 .1 13.4.3 17.8a19.8 19.8 0 0 0 6 3l1.3-2a12.7 12.7 0 0 1-2-1l.5-.4a14.1 14.1 0 0 0 12 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.7 19.7 0 0 0 6-3c.5-5.1-.8-9.5-3.6-13.4ZM8.5 15.1c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Zm7 0c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Z"/></svg>
      </span> Discord</h2>
      {#if me.discord}<span class="tag on">✓ {t('discord.linkedAs', { name: me.discord })}</span>{:else}<span class="tag">○ {t('discord.notLinked')}</span>{/if}
    </div>
    <p class="muted small">{t('discord.hint')}</p>
    {#if error}<div class="error">{error}</div>{/if}
    {#if message}<div class="notice">{message}</div>{/if}
    <div>
      {#if me.discord}
        <button class="danger" disabled={busy} onclick={unlink}>{t('discord.unlink')}</button>
      {:else}
        <button class="discord" disabled={busy} onclick={link}>{t('discord.link')}</button>
      {/if}
    </div>
  </section>
{/if}

<style>
  .row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  h2 {
    margin: 0;
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .logo {
    color: #5865f2;
    display: inline-flex;
  }

  p {
    margin: 0;
  }

  .tag {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.on {
    background: color-mix(in srgb, var(--good) 14%, transparent);
    color: var(--good);
  }

  .discord {
    background: #5865f2;
    color: #fff;
  }
</style>
