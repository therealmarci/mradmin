<script>
  // The web console: unlock with a fresh 2FA code, then the live log and (with mradmin.web.console.execute) commands.
  import { tick, untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import * as passkeys from '../lib/passkeys.js';

  let { me } = $props();

  let info = $state(null);
  let error = $state('');
  let code = $state('');
  let busy = $state(false);

  let lines = $state([]);
  let last = 0;
  let filter = $state('');
  let follow = $state(true);
  let command = $state('');
  let history = [];
  let historyIndex = -1;
  let box = $state();

  const KEEP = 1500;

  async function loadState() {
    try {
      info = await api('GET', '/console/state');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  async function poll() {
    if (!info?.unlocked) return;
    try {
      const result = await api('GET', '/console/log?after=' + last);
      if (result.lines.length) {
        lines = [...lines, ...result.lines].slice(-KEEP);
        last = result.last;
        if (follow) {
          await tick();
          box?.scrollTo({ top: box.scrollHeight });
        }
      } else if (result.last < last) {
        // The server was restarted - start again.
        lines = [];
        last = 0;
      }
    } catch (e) {
      if (e.key === 'console-locked') {
        info = { ...info, unlocked: false };
      } else {
        error = errorText(e);
      }
    }
  }

  $effect(() => {
    untrack(loadState);
  });

  // Once a second while unlocked.
  $effect(() => {
    if (!info?.unlocked) return;
    untrack(poll);
    const timer = setInterval(poll, 1000);
    return () => clearInterval(timer);
  });

  // With a 2FA code - or with a passkey (the only way when 2FA is off).
  async function unlock(event, withPasskey = false) {
    event?.preventDefault();
    busy = true;
    error = '';
    try {
      const body = withPasskey ? await passkeys.proof(api) : { code: code.trim() };
      await api('POST', '/console/unlock', body);
      code = '';
      await loadState();
    } catch (e) {
      if (!passkeys.cancelled(e)) error = errorText(e);
    } finally {
      busy = false;
    }
  }

  async function lock() {
    await api('POST', '/console/lock').catch(() => {});
    lines = [];
    last = 0;
    await loadState();
  }

  async function send(event) {
    event.preventDefault();
    const text = command.trim();
    if (!text) return;
    busy = true;
    error = '';
    try {
      await api('POST', '/console/command', { command: text });
      history = [text, ...history.filter((h) => h !== text)].slice(0, 50);
      historyIndex = -1;
      command = '';
      follow = true;
      setTimeout(poll, 300);
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Arrow keys walk through the commands sent before.
  function keys(event) {
    if (event.key === 'ArrowUp' && history.length) {
      historyIndex = Math.min(history.length - 1, historyIndex + 1);
      command = history[historyIndex];
      event.preventDefault();
    } else if (event.key === 'ArrowDown') {
      historyIndex = Math.max(-1, historyIndex - 1);
      command = historyIndex < 0 ? '' : history[historyIndex];
      event.preventDefault();
    }
  }

  // Stop following when scrolled up, follow again at the bottom.
  function scrolled() {
    if (!box) return;
    follow = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
  }

  const shown = $derived(
    filter.trim() ? lines.filter((l) => l.text.toLowerCase().includes(filter.trim().toLowerCase())) : lines,
  );
  const level = (text) => (/\b(ERROR|SEVERE)\b/.test(text) ? 'error' : /\bWARN(ING)?\b/.test(text) ? 'warn' : '');
  const untilText = $derived(info?.until ? new Date(info.until).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '');
</script>

<div class="stack">
  <div class="head">
    <div>
      <h1>{t('console.title')}</h1>
      <p class="muted small">{t('console.subtitle')}</p>
    </div>
    {#if info?.unlocked}
      <button class="secondary" onclick={lock}>🔒 {t('console.lock')}</button>
    {/if}
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if info && !info.unlocked}
    <form class="card unlock" onsubmit={unlock}>
      <h2>🔐 {t('console.unlockTitle')}</h2>
      <p class="muted small">{t('console.unlockHint')}</p>
      {#if info.lockedUntil}
        <p class="error small">{t('console.codeLocked', { time: new Date(info.lockedUntil).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) })}</p>
      {/if}
      {#if me.totp}
        <input inputmode="numeric" autocomplete="one-time-code" maxlength="6" bind:value={code}
          placeholder="123456" aria-label={t('login.code')} />
        <button type="submit" disabled={busy || code.trim().length < 6}>{t('console.unlock')}</button>
      {/if}
      {#if me.passkeys > 0 && passkeys.supported}
        <button type="button" class:secondary={me.totp} disabled={busy}
          onclick={() => unlock(null, true)}>🔑 {t('console.unlockPasskey')}</button>
      {/if}
    </form>
  {:else if info}
    <div class="bar">
      <input type="search" bind:value={filter} placeholder={t('console.filter')} />
      <span class="muted small">{t('console.openUntil', { time: untilText })}</span>
      {#if !follow}
        <button class="secondary mini" onclick={() => { follow = true; box?.scrollTo({ top: box.scrollHeight }); }}>↓ {t('console.follow')}</button>
      {/if}
    </div>

    <div class="log mono" bind:this={box} onscroll={scrolled} role="log" aria-live="off">
      {#each shown as l (l.seq)}
        <div class="line {level(l.text)}">{l.text}</div>
      {:else}
        <div class="muted">{t('console.empty')}</div>
      {/each}
    </div>

    {#if info.execute}
      <form class="cmd" onsubmit={send}>
        <span class="prompt mono">&gt;</span>
        <input class="mono" bind:value={command} onkeydown={keys} maxlength="500" autocomplete="off" autocapitalize="off"
          spellcheck="false" placeholder={t('console.commandPlaceholder')} aria-label={t('console.command')} />
        <button type="submit" disabled={busy || !command.trim()}>{t('console.run')}</button>
      </form>
      <p class="muted hint">{t('console.commandHint')}</p>
    {:else}
      <p class="muted small">{t('console.viewOnly')}</p>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .head {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: space-between;
    align-items: flex-start;
  }

  .head h1 {
    margin: 0;
  }

  /* Centred on the page like a dialog - the page itself stays as wide as all others. */
  .unlock {
    max-width: 440px;
    margin-inline: auto;
    display: grid;
    gap: 12px;
    text-align: center;
    padding: 28px 24px;
  }

  .unlock h2 {
    margin: 0;
    font-size: 1.1rem;
  }

  .unlock p {
    margin: 0;
  }

  .unlock input {
    font-size: 1.4rem;
    letter-spacing: 0.3em;
    text-align: center;
  }

  .bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 12px;
    align-items: center;
  }

  .bar input {
    flex: 1;
    min-width: 180px;
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  /* Always dark, like a terminal - easier to read long output. */
  .log {
    height: min(62vh, 640px);
    overflow: auto;
    background: #0f1115;
    color: #d6dae0;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    padding: 10px 12px;
    font-size: 0.8rem;
    line-height: 1.45;
  }

  .line {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .line.warn {
    color: #f0c674;
  }

  .line.error {
    color: #ff7b72;
  }

  .cmd {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .cmd input {
    flex: 1;
    min-width: 0;
  }

  .prompt {
    color: var(--muted);
    font-weight: 700;
  }

  /* Hint below the command line - with room to breathe. */
  .hint {
    font-size: 0.78rem;
    margin: 14px 2px 0;
    line-height: 1.5;
  }
</style>
