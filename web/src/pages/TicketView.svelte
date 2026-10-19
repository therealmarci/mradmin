<script>
  import { tick, untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live } from '../lib/live.svelte.js';

  let { id, me } = $props();

  let ticket = $state(null);
  let error = $state('');
  let text = $state('');
  let busy = $state(false);
  let chat = $state();

  async function load(scroll = false) {
    try {
      const before = ticket?.messages.length ?? 0;
      ticket = await api('GET', '/tickets/' + id);
      error = '';
      if (scroll || ticket.messages.length > before) {
        await tick();
        chat?.scrollTo({ top: chat.scrollHeight, behavior: before ? 'smooth' : 'instant' });
      }
    } catch (e) {
      error = errorText(e);
    }
  }

  // Load when the ticket id changes (load itself reads the ticket - that must not start it again).
  $effect(() => {
    id;
    untrack(() => load(true));
  });

  // Someone answered or changed this ticket (in game or in another browser): show it right away.
  $effect(() => {
    if (live.ticket.id === Number(id) && live.ticket.tick > 0) untrack(() => !busy && load());
  });

  async function action(path, body) {
    busy = true;
    error = '';
    try {
      await api('POST', `/tickets/${id}/${path}`, body);
      await load(true);
      return true;
    } catch (e) {
      error = errorText(e);
      return false;
    } finally {
      busy = false;
    }
  }

  async function send() {
    if (!text.trim()) return;
    if (await action('reply', { text })) text = '';
  }

  async function close() {
    if (!confirm(t('ticket.closeConfirm', { id }))) return;
    if (await action('close', { text })) text = '';
  }

  function keys(event) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      send();
    }
  }

  // Messages in a row by the same author within 5 minutes form one bubble.
  const groups = $derived.by(() => {
    const result = [];
    for (const m of ticket?.messages ?? []) {
      const last = result.at(-1);
      if (last && last.uuid === m.uuid && last.staff === m.staff && m.time - last.messages.at(-1).time < 300000) {
        last.messages.push(m);
      } else {
        result.push({ uuid: m.uuid, name: m.name, staff: m.staff, system: m.system, messages: [m] });
      }
    }
    return result;
  });

  const mineClaimed = $derived(ticket?.claimedBy === me.name);
</script>

<div class="page">
  <button class="back" onclick={() => go('/tickets')}>← {t('ticket.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}

  {#if !ticket}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else}
    <section class="card header">
      <div class="title-row">
        <h1>#{ticket.id} · {ticket.summary || ticket.categoryName}</h1>
        <span class="status" class:closed={!ticket.open}>{ticket.open ? t('ticket.statusOpen') : t('ticket.statusClosed')}</span>
      </div>
      <div class="facts">
        <div><span class="muted">{t('ticket.by')}</span>
          <img src={head(ticket.creatorUuid, 32)} alt="" width="18" height="18" /> {ticket.creator}</div>
        {#if ticket.report}
          <div><span class="muted">{t('ticket.reported')}</span>
            <img src={head(ticket.targetUuid, 32)} alt="" width="18" height="18" /> {ticket.target}</div>
        {/if}
        <div><span class="muted">{t('ticket.created')}</span> {dateTime(ticket.created)}</div>
        <div><span class="muted">{t('ticket.location')}</span> <span class="mono">{ticket.world} {ticket.x} {ticket.y} {ticket.z}</span></div>
        <div><span class="muted">{t('ticket.claimedBy')}</span> {ticket.claimedBy ?? t('ticket.nobody')}</div>
      </div>
      {#if ticket.source === 'mrchunks'}
        <div class="notice small">{t('ticket.lockHint')}</div>
      {/if}
    </section>

    <section class="chat" bind:this={chat}>
      {#each groups as g}
        {#if g.system}
          <div class="system">
            <div class="who small">{g.name} · {t('ticket.system')} · {ago(g.messages[0].time)}</div>
            {#each g.messages as m}<div class="text">{m.text}</div>{/each}
          </div>
        {:else}
          <div class="row" class:staff={g.staff}>
            <img src={head(g.uuid, 48)} alt="" width="32" height="32" />
            <div class="bubble">
              <div class="who small"><strong>{g.name}</strong> · <span title={dateTime(g.messages[0].time)}>{ago(g.messages[0].time)}</span></div>
              {#each g.messages as m}<div class="text">{m.text}</div>{/each}
            </div>
          </div>
        {/if}
      {/each}
      {#if !ticket.open}
        <div class="closed-note muted small">{t('ticket.closedBy', { name: ticket.closedBy, time: dateTime(ticket.closedAt) })}</div>
      {/if}
    </section>

    <section class="card composer">
      {#if ticket.open}
        <textarea bind:value={text} rows="3" placeholder={t('ticket.reply')} onkeydown={keys} maxlength="2000"></textarea>
        <div class="actions">
          <button onclick={send} disabled={busy || !text.trim()}>{t('ticket.send')}</button>
          <button class="secondary" onclick={() => action('claim')} disabled={busy}>
            {mineClaimed ? t('ticket.release') : t('ticket.claim')}
          </button>
          <button class="danger" onclick={close} disabled={busy}>
            {text.trim() ? t('ticket.closeWithText') : t('ticket.close')}
          </button>
          <span class="hint keys">{t('ticket.sendHint')}</span>
        </div>
      {:else}
        <button class="secondary" onclick={() => action('reopen')} disabled={busy}>{t('ticket.reopen')}</button>
      {/if}
    </section>
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

  .title-row {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    justify-content: space-between;
  }

  .title-row h1 {
    font-size: 1.25rem;
    word-break: break-word;
  }

  .status {
    flex-shrink: 0;
    font-size: 0.8rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--good) 15%, transparent);
    color: var(--good);
  }

  .status.closed {
    background: var(--surface-2);
    color: var(--muted);
  }

  .facts {
    display: grid;
    gap: 4px 20px;
    grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
    font-size: 0.9rem;
    margin: 8px 0;
  }

  .facts .muted {
    display: inline-block;
    min-width: 7.5em;
  }

  .facts img {
    vertical-align: -3px;
    border-radius: 3px;
    image-rendering: pixelated;
  }

  .chat {
    display: grid;
    gap: 12px;
    max-height: 60vh;
    overflow-y: auto;
    padding: 4px 2px;
  }

  .row {
    display: flex;
    gap: 10px;
    align-items: flex-end;
    max-width: 85%;
  }

  .row.staff {
    flex-direction: row-reverse;
    margin-left: auto;
  }

  .row img {
    border-radius: 6px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .bubble {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 14px 14px 14px 4px;
    padding: 8px 12px;
    min-width: 0;
  }

  .staff .bubble {
    background: color-mix(in srgb, var(--accent) 14%, var(--surface));
    border-color: color-mix(in srgb, var(--accent) 30%, var(--border));
    border-radius: 14px 14px 4px 14px;
  }

  .who {
    color: var(--muted);
    margin-bottom: 2px;
  }

  .text {
    white-space: pre-wrap;
    word-break: break-word;
  }

  .text + .text {
    margin-top: 4px;
  }

  .system {
    background: var(--surface-2);
    border-left: 4px solid var(--warn);
    border-radius: 10px;
    padding: 10px 14px;
    font-size: 0.92rem;
  }

  .closed-note {
    text-align: center;
  }

  .composer textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    resize: vertical;
    min-height: 80px;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-top: 10px;
  }

  .keys {
    margin-left: auto;
    display: none;
  }

  @media (min-width: 760px) {
    .keys {
      display: inline;
    }
  }
</style>
