<script>
  // One own ticket as a conversation: the head with status and "close", the messages as a chat (own ones on the right,
  // the team's on the left, notes of the system in the middle), and the answer below (Ctrl+Enter sends).
  import { untrack, tick } from 'svelte';
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { dateTime } from '../lib/format.js';

  let { id } = $props();

  let ticket = $state(null);
  let error = $state('');
  let text = $state('');
  let busy = $state(false);
  let list = $state();
  let seen = 0;

  async function load() {
    try {
      ticket = await api('GET', '/self/tickets/' + id);
      // New messages: scroll down to them.
      if (ticket.messages.length !== seen) {
        seen = ticket.messages.length;
        await tick();
        list?.lastElementChild?.scrollIntoView({ block: 'nearest' });
      }
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    id;
    untrack(load);
    // Every 10 seconds again while the tab is visible.
    return poll(load, 10_000);
  });

  async function reply(event) {
    event?.preventDefault();
    if (!text.trim() || busy) return;
    busy = true;
    error = '';
    try {
      await api('POST', `/self/tickets/${id}/reply`, { text });
      text = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function key(event) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) reply(event);
  }

  async function close() {
    if (!confirm(t('self.tickets.closeConfirm'))) return;
    busy = true;
    try {
      await api('POST', `/self/tickets/${id}/close`);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const author = (m) => (m.own ? t('self.tickets.you') : m.author ?? t('self.tickets.team'));
  // A day line whenever the day changes.
  const day = (ms) => new Date(ms).toDateString();
  const time = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
</script>

<div class="page">
  <button class="back" onclick={() => go('/me/tickets')}>← {t('self.tickets.title')}</button>
  {#if error}<div class="error">{error}</div>{/if}

  {#if ticket}
    <header class="head" class:closed={!ticket.open}>
      <div class="h-main">
        <span class="h-top">
          <span class="id">#{ticket.id}</span>
          <span class="chip">{ticket.category}</span>
          <span class="status">{ticket.open ? t('self.tickets.open') : t('self.tickets.closed')}</span>
        </span>
        <h1>{ticket.summary}</h1>
      </div>
      {#if ticket.open && ticket.mayClose}
        <button class="secondary small" disabled={busy} onclick={close}>✓ {t('self.tickets.close')}</button>
      {/if}
    </header>

    <section class="card chat">
      <ol class="messages" bind:this={list}>
        {#each ticket.messages as m, i (i)}
          {#if i === 0 || day(m.time) !== day(ticket.messages[i - 1].time)}
            <li class="day"><span>{new Date(m.time).toLocaleDateString()}</span></li>
          {/if}
          {#if m.system}
            <li class="system"><span>⚙️ {m.text}</span></li>
          {:else}
            <li class="msg" class:own={m.own} class:staff={m.staff && !m.own}>
              <span class="avatar" aria-hidden="true">{m.own ? '🙂' : m.staff ? '🛡️' : '👤'}</span>
              <div class="bubble">
                <div class="meta">
                  <strong>{author(m)}</strong>
                  {#if m.staff && !m.own}<span class="team">{t('self.tickets.team')}</span>{/if}
                  <span class="muted" title={dateTime(m.time)}>{time(m.time)}</span>
                </div>
                <div class="text">{m.text}</div>
              </div>
            </li>
          {/if}
        {/each}
      </ol>

      {#if ticket.open}
        <form class="composer" onsubmit={reply}>
          <textarea bind:value={text} rows="3" maxlength="2000" placeholder={t('self.tickets.replyPlaceholder')}
            onkeydown={key} aria-label={t('self.tickets.reply')}></textarea>
          <div class="c-foot">
            <span class="muted small">{t('self.tickets.sendHint')}</span>
            <button type="submit" disabled={busy || !text.trim()}>{t('self.tickets.reply')} ➤</button>
          </div>
        </form>
      {:else}
        <div class="closed-note">🔒 {t('self.tickets.closedHint')}</div>
      {/if}
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --tk: var(--accent);
    display: grid;
    gap: 14px;
    max-width: 900px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--tk);
    padding: 4px 0;
  }

  .head {
    --st: var(--good);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
    padding: 18px 22px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tk) 25%, var(--border));
    border-left: 4px solid var(--st);
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tk) 16%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .head.closed {
    --st: var(--muted);
  }

  .h-main {
    display: grid;
    gap: 6px;
    min-width: 0;
    flex: 1;
  }

  .h-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .id {
    font-weight: 800;
    color: var(--muted);
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
  }

  h1 {
    margin: 0;
    font-size: 1.3rem;
    overflow-wrap: anywhere;
  }

  .chat {
    display: grid;
    gap: 14px;
    padding: 18px;
  }

  .messages {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
  }

  .day,
  .system {
    display: flex;
    justify-content: center;
  }

  .day span {
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--muted);
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--surface-2);
  }

  .system span {
    font-size: 0.82rem;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 10px;
    border: 1px dashed var(--border);
    max-width: 80%;
    text-align: center;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .msg {
    display: flex;
    gap: 10px;
    align-items: flex-end;
    max-width: min(100%, 640px);
  }

  .msg.own {
    flex-direction: row-reverse;
    justify-self: end;
  }

  .avatar {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .bubble {
    padding: 10px 14px;
    border-radius: 14px 14px 14px 4px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    min-width: 0;
  }

  .msg.own .bubble {
    border-radius: 14px 14px 4px 14px;
    background: color-mix(in srgb, var(--tk) 14%, var(--surface));
    border-color: color-mix(in srgb, var(--tk) 35%, var(--border));
  }

  .msg.staff .bubble {
    border-color: color-mix(in srgb, #8b5cf6 40%, var(--border));
    background: color-mix(in srgb, #8b5cf6 8%, var(--surface-2));
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px;
    font-size: 0.82rem;
  }

  .team {
    font-size: 0.68rem;
    font-weight: 800;
    padding: 1px 7px;
    border-radius: 999px;
    color: #8b5cf6;
    background: color-mix(in srgb, #8b5cf6 15%, transparent);
  }

  .text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    margin-top: 4px;
    line-height: 1.5;
  }

  .composer {
    display: grid;
    gap: 8px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 11px 12px;
    resize: vertical;
    outline: none;
  }

  textarea:focus {
    border-color: var(--tk);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--tk) 25%, transparent);
  }

  .c-foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .closed-note {
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    color: var(--muted);
    text-align: center;
  }

  @media (max-width: 640px) {
    .head {
      padding: 16px;
    }

    .chat {
      padding: 12px;
    }

    .avatar {
      display: none;
    }
  }
</style>
