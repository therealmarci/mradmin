<script>
  // Account -> Devices: the connected Stream Decks (MRadmin plugin for the Stream Deck), connecting one with the code
  // it shows (also from /link?code=…) and making a key to paste into it.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime } from '../lib/format.js';
  import Toast from './Toast.svelte';

  let { code: initial = '' } = $props();

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let code = $state(initial);
  let asking = $state(null);
  let keyName = $state('');
  let newKey = $state('');
  // May the Stream Deck also switch things (restart, admin mode, announcement, chunk loaders) - off unless chosen.
  let control = $state(false);
  let keyControl = $state(false);
  let busy = $state(false);

  async function load() {
    try {
      data = await api('GET', '/deck/devices');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  async function check(event) {
    event?.preventDefault();
    error = '';
    asking = null;
    if (!code.trim()) return;
    busy = true;
    try {
      asking = await api('GET', '/deck/link/info?code=' + encodeURIComponent(code.trim()));
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
  // Opened with /link?code=…: ask right away.
  if (initial) check();

  async function connect() {
    busy = true;
    error = '';
    try {
      const result = await api('POST', '/deck/link/confirm', { code: asking.code, control: control ? 'true' : 'false' });
      message = t('deck.connected', { name: result.name });
      asking = null;
      code = '';
      control = false;
      load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function makeKey(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const result = await api('POST', '/deck/keys', { name: keyName.trim(), control: keyControl ? 'true' : 'false' });
      newKey = result.key;
      keyName = '';
      keyControl = false;
      load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(newKey);
      message = t('deck.copied');
    } catch {
      // the key stays selectable
    }
  }

  async function setControl(d) {
    try {
      await api('POST', '/deck/devices/' + encodeURIComponent(d.id) + '/control', { on: d.control ? 'false' : 'true' });
      message = t(d.control ? 'deck.nowViewOnly' : 'deck.nowControl');
      load();
    } catch (e) {
      error = errorText(e);
    }
  }

  async function revoke(id) {
    if (!confirm(t('deck.revokeConfirm'))) return;
    try {
      await api('POST', '/deck/devices/' + encodeURIComponent(id) + '/revoke');
      message = t('deck.revoked');
      load();
    } catch (e) {
      error = errorText(e);
    }
  }

  const usable = $derived(data && data.enabled && data.allowed);
  // A Stream Deck: 3x2 keys.
  const ICON = 'M3 5h18v14H3zM7 9h2v2H7zM11 9h2v2h-2zM15 9h2v2h-2zM7 13h2v2H7zM11 13h2v2h-2zM15 13h2v2h-2z';
</script>

<section class="card tile">
  <div class="row">
    <h2>{t('deck.title')}{#if data}<span class="n">{data.devices.length}</span>{/if}</h2>
  </div>
  <p class="muted small">{t('deck.intro')}</p>
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if !data}
    <p class="muted">{t('common.loading')}</p>
  {:else}
    {#if data.devices.length}
      <ul class="devices">
        {#each data.devices as d (d.id)}
          <li class="dev">
            <span class="dev-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round"><path d={ICON} /></svg>
            </span>
            <div class="dev-body">
              <div class="dev-name">{d.name}</div>
              <div class="muted small">{t('deck.lastSeen')}: {ago(d.lastSeen)} · <span class="mono">{d.ip}</span></div>
              <div class="muted small">{t('deck.via.' + d.via)} · {dateTime(d.created)}</div>
              <div class="mode" class:on={d.control}>{t(d.control ? 'deck.mode.control' : 'deck.mode.view')}</div>
            </div>
            <div class="dev-actions">
              <button class="secondary mini" onclick={() => setControl(d)}>{t(d.control ? 'deck.makeViewOnly' : 'deck.allowControl')}</button>
              <button class="danger mini" onclick={() => revoke(d.id)}>{t('deck.revoke')}</button>
            </div>
          </li>
        {/each}
      </ul>
    {:else}
      <p class="muted small">{t('deck.none')}</p>
    {/if}

    {#if !data.enabled}
      <p class="muted small">{t('deck.off')}</p>
    {:else if !data.allowed}
      <p class="muted small">{t('deck.notAllowed')}</p>
    {/if}

    {#if usable}
      <div class="parts">
        <div class="part">
          <h3>{t('deck.linkTitle')}</h3>
          {#if asking}
            <div class="ask">
              <strong>{t('deck.ask', { name: asking.name })}</strong>
              <span class="muted small">{t('deck.askFrom', { ip: asking.ip, time: ago(asking.created) })}</span>
              <span class="muted small">{t('deck.askHint')}</span>
              <label class="check"><input type="checkbox" bind:checked={control} /> {t('deck.controlLabel')}</label>
              <div class="buttons">
                <button onclick={connect} disabled={busy}>{t('deck.connect')}</button>
                <button class="secondary" onclick={() => (asking = null)}>{t('deck.cancel')}</button>
              </div>
            </div>
          {:else}
            <form class="inline" onsubmit={check}>
              <input class="mono code" bind:value={code} maxlength="9" placeholder="K7Q-4MX" autocomplete="off"
                autocapitalize="characters" spellcheck="false" required />
              <button type="submit" disabled={busy}>{t('deck.check')}</button>
            </form>
            <p class="muted small">{t('deck.linkHint')}</p>
          {/if}
        </div>
        <div class="part">
          <h3>{t('deck.keyTitle')}</h3>
          {#if newKey}
            <div class="ask">
              <code class="mono key">{newKey}</code>
              <span class="muted small">{t('deck.keyOnce')}</span>
              <div class="buttons">
                <button onclick={copy}>{t('deck.copy')}</button>
                <button class="secondary" onclick={() => (newKey = '')}>{t('deck.done')}</button>
              </div>
            </div>
          {:else}
            <form class="inline" onsubmit={makeKey}>
              <input bind:value={keyName} maxlength="40" placeholder={t('deck.keyName')} />
              <button type="submit" disabled={busy}>{t('deck.keyCreate')}</button>
            </form>
            <label class="check"><input type="checkbox" bind:checked={keyControl} /> {t('deck.controlLabel')}</label>
            <p class="muted small">{t('deck.keyHint')}</p>
          {/if}
        </div>
      </div>
    {/if}
  {/if}
</section>

<style>
  .tile {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.05rem;
  }

  h3 {
    margin: 0;
    font-size: 0.95rem;
  }

  p {
    margin: 0;
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .devices {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .dev {
    display: flex;
    gap: 14px;
    align-items: center;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--accent);
  }

  .dev-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    border-radius: 12px;
    color: var(--accent);
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .dev-body {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .dev-name {
    font-weight: 700;
  }

  .parts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 12px;
  }

  .part {
    display: grid;
    gap: 8px;
    align-content: start;
    padding: 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
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

  .code {
    text-transform: uppercase;
    letter-spacing: 0.12em;
  }

  .ask {
    display: grid;
    gap: 6px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 4px;
  }

  .key {
    display: block;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface);
    border: 1px solid var(--border);
    overflow-wrap: anywhere;
    user-select: all;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .dev-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: flex-end;
  }

  .mode {
    justify-self: start;
    margin-top: 2px;
    font-size: 0.72rem;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .mode.on {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .check {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin: 0;
    font-size: 0.85rem;
    color: var(--text);
    cursor: pointer;
  }

  .check input {
    width: auto;
    margin: 2px 0 0;
    padding: 0;
    flex-shrink: 0;
  }

  .mini {
    padding: 5px 12px;
    font-size: 0.82rem;
  }
</style>
