<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, ago } from '../lib/format.js';

  let { me } = $props();

  let sessions = $state(null);
  let error = $state('');
  let message = $state('');
  let code = $state('');
  let newCodes = $state(null);
  let busy = $state(false);

  async function load() {
    try {
      sessions = await api('GET', '/sessions');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  async function revoke(id) {
    await api('DELETE', '/sessions/' + encodeURIComponent(id));
    load();
  }

  async function revokeOthers() {
    const result = await api('POST', '/sessions/others');
    message = t('account.revokedOthers', { n: result.count });
    load();
  }

  async function recovery(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const result = await api('POST', '/account/recovery', { code: code.trim() });
      newCodes = result.recoveryCodes;
      me.recoveryLeft = newCodes.length;
    } catch (e) {
      error = errorText(e);
    } finally {
      code = '';
      busy = false;
    }
  }
</script>

<div class="stack">
  <h1>{t('account.title')}</h1>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  <section class="card stack">
    <div class="row">
      <h2>{t('account.devices')}</h2>
      {#if sessions && sessions.length > 1}
        <button class="secondary small-button" onclick={revokeOthers}>{t('account.revokeOthers')}</button>
      {/if}
    </div>
    {#if !sessions}
      <p class="muted">{t('common.loading')}</p>
    {:else}
      <ul class="sessions">
        {#each sessions as s (s.id)}
          <li>
            <div>
              <div class="device">{s.device} {#if s.current}<span class="badge">{t('account.current')}</span>{/if}</div>
              <div class="muted small">
                {t('account.lastSeen')}: {ago(s.lastSeen)} · {s.ip}<br />
                {t('account.since')}: {dateTime(s.created)}
              </div>
            </div>
            {#if !s.current}
              <button class="danger" onclick={() => revoke(s.id)}>{t('account.revoke')}</button>
            {/if}
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section class="card stack">
    <h2>{t('account.recoveryTitle')}</h2>
    <p class="muted">{t('account.recoveryLeft', { n: me.recoveryLeft })}</p>
    {#if newCodes}
      <div class="codes mono">{#each newCodes as c}<span>{c}</span>{/each}</div>
    {:else}
      <form class="inline" onsubmit={recovery}>
        <input class="mono" bind:value={code} inputmode="numeric" maxlength="6" placeholder="123456" required autocomplete="one-time-code" />
        <button type="submit" disabled={busy}>{t('account.recoveryNew')}</button>
      </form>
      <p class="hint">{t('account.recoveryHint')}</p>
    {/if}
  </section>

  <p class="muted small">{t('account.reset')}</p>
</div>

<style>
  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
  }

  .row h2 {
    margin: 0;
  }

  .small-button {
    padding: 6px 12px;
    font-size: 0.85rem;
  }

  .sessions {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .sessions li {
    display: flex;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
    padding: 12px 0;
    border-top: 1px solid var(--border);
  }

  .sessions li:first-child {
    border-top: none;
  }

  .device {
    font-weight: 600;
  }

  .inline {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .inline input {
    flex: 1;
    min-width: 140px;
  }

  .codes {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    background: var(--surface-2);
    border-radius: 10px;
    padding: 14px;
    user-select: all;
  }
</style>
