<script>
  // One own ticket: the conversation with the team, answer, close (unless only the team may close it).
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { dateTime } from '../lib/format.js';

  let { id } = $props();

  let ticket = $state(null);
  let error = $state('');
  let text = $state('');
  let busy = $state(false);

  async function load() {
    try {
      ticket = await api('GET', '/self/tickets/' + id);
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    id;
    untrack(load);
    const timer = setInterval(load, 15000);
    return () => clearInterval(timer);
  });

  async function reply(event) {
    event.preventDefault();
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
</script>

<div class="page">
  <button class="back" onclick={() => go('/me/tickets')}>← {t('self.tickets.title')}</button>
  {#if error}<div class="error">{error}</div>{/if}
  {#if ticket}
    <section class="card">
      <div class="head">
        <h1>#{ticket.id} {ticket.summary}</h1>
        <span class="tag">{ticket.open ? t('self.tickets.open') : t('self.tickets.closed')}</span>
      </div>
      <p class="muted small">{ticket.category}</p>
    </section>

    <ol class="messages">
      {#each ticket.messages as m, i (i)}
        <li class:own={m.own} class:staff={m.staff} class:system={m.system}>
          <div class="meta small"><strong>{author(m)}</strong> <span class="muted">{dateTime(m.time)}</span></div>
          <div class="text">{m.text}</div>
        </li>
      {/each}
    </ol>

    {#if ticket.open}
      <form class="card reply" onsubmit={reply}>
        <textarea bind:value={text} rows="4" maxlength="2000" placeholder={t('self.tickets.replyPlaceholder')} required></textarea>
        <div class="buttons">
          <button type="submit" disabled={busy || !text.trim()}>{t('self.tickets.reply')}</button>
          {#if ticket.mayClose}
            <button type="button" class="secondary" disabled={busy} onclick={close}>{t('self.tickets.close')}</button>
          {/if}
        </div>
      </form>
    {:else}
      <p class="muted">{t('self.tickets.closedHint')}</p>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--accent);
    padding: 4px 0;
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  h1 {
    margin: 0;
    font-size: 1.2rem;
    overflow-wrap: anywhere;
  }

  p {
    margin: 4px 0 0;
  }

  .messages {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .messages li {
    max-width: min(100%, 680px);
    padding: 10px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .messages li.own {
    justify-self: end;
    background: color-mix(in srgb, var(--accent) 12%, var(--surface));
  }

  .messages li.system {
    border-style: dashed;
  }

  .text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    margin-top: 4px;
  }

  .reply {
    display: grid;
    gap: 10px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
</style>
