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

  // Unban requests: accept lifts the ban in LibertyBans; both close the ticket, the text goes to the player first.
  async function decide(accept) {
    if (!confirm(t(accept ? 'request.acceptConfirm' : 'request.rejectConfirm', { name: ticket.creator }))) return;
    if (await action('decide', { decision: accept ? 'accept' : 'reject', text })) text = '';
  }

  const request = $derived(ticket?.request ?? null);
  const mailto = $derived(request?.email
    ? `mailto:${request.email}?subject=${encodeURIComponent('Re: ' + (ticket.summary || ''))}` : null);

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
      <!-- The facts as small tiles: label on top, value below. -->
      <div class="facts">
        <div class="fact">
          <div class="flabel">{t('ticket.by')}</div>
          {#if request?.kind === 'contact'}
            <div class="fvalue">{ticket.creator}</div>
          {:else}
            <button class="fvalue person" disabled={!me.may.players} onclick={() => go('/players/' + ticket.creatorUuid)}>
              <img src={head(ticket.creatorUuid, 32)} alt="" width="20" height="20" /> {ticket.creator}</button>
          {/if}
        </div>
        {#if request?.email}
          <div class="fact">
            <div class="flabel">{t('request.email')}</div>
            <a class="fvalue" href={mailto}>{request.email}</a>
          </div>
        {/if}
        {#if ticket.report}
          <div class="fact">
            <div class="flabel">{t('ticket.reported')}</div>
            <button class="fvalue person" disabled={!me.may.players} onclick={() => go('/players/' + ticket.targetUuid)}>
              <img src={head(ticket.targetUuid, 32)} alt="" width="20" height="20" /> {ticket.target}</button>
          </div>
        {/if}
        <div class="fact">
          <div class="flabel">{t('ticket.created')}</div>
          <div class="fvalue">{dateTime(ticket.created)}</div>
        </div>
        {#if !request}
          <div class="fact">
            <div class="flabel">{t('ticket.location')}</div>
            <!-- Wrapped only between the values - never inside one. -->
            <div class="fvalue mono coords"><span>{ticket.world}</span><span>X {ticket.x}</span><span>Y {ticket.y}</span><span>Z {ticket.z}</span></div>
          </div>
        {/if}
        <div class="fact">
          <div class="flabel">{t('ticket.claimedBy')}</div>
          <div class="fvalue">{ticket.claimedBy ?? t('ticket.nobody')}</div>
        </div>
      </div>
      {#if ticket.source}
        <div class="notice small">{t('ticket.lockHint')}</div>
      {/if}
      {#if request?.kind === 'unban'}
        <div class="ban">
          <div class="ban-top">
            <span class="muted small">{t('request.ban', { id: request.banId })}</span>
            <span class="tag">{request.stage === 'appeal' ? t('request.stageAppeal') : t('request.stageRequest')}</span>
            {#if request.decision}<span class="tag {request.decision}">{t('request.decision.' + request.decision)}</span>{/if}
          </div>
          <div class="reason">{request.banReason}</div>
          <div class="muted small">{t('request.banFacts', { by: request.banOperator, since: dateTime(request.banStart),
            until: request.banEnd ? dateTime(request.banEnd) : t('pub.permanent') })}</div>
        </div>
        <div class="notice small">{request.stage === 'appeal' ? t('request.appealHint') : t('request.unbanHint')}</div>
      {:else if request?.kind === 'contact'}
        <div class="notice small">
          {me.may.mail ? t('request.contactMail') : t('request.contactNoMail')}
          {#if !me.may.mail && mailto}<a href={mailto}>{t('request.writeMail')}</a>{/if}
        </div>
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
      {#if ticket.open && me.may.ticketsReply}
        <textarea bind:value={text} rows="3" placeholder={t('ticket.reply')} onkeydown={keys} maxlength="2000"></textarea>
        <div class="actions">
          <button onclick={send} disabled={busy || !text.trim()}>{t('ticket.send')}</button>
          {#if me.may.ticketsClaim}
            <button class="secondary" onclick={() => action('claim')} disabled={busy}>
              {mineClaimed ? t('ticket.release') : t('ticket.claim')}
            </button>
          {/if}
          {#if me.may.ticketsClose && request?.kind === 'unban'}
            {#if me.may.unban}
              <button class="good" onclick={() => decide(true)} disabled={busy}>{t('request.accept')}</button>
            {/if}
            <button class="danger" onclick={() => decide(false)} disabled={busy}>{t('request.reject')}</button>
          {:else if me.may.ticketsClose}
            <button class="danger" onclick={close} disabled={busy}>
              {text.trim() ? t('ticket.closeWithText') : t('ticket.close')}
            </button>
          {/if}
          <span class="hint keys">{t('ticket.sendHint')}</span>
        </div>
      {:else if ticket.open && me.may.ticketsClose}
        <button class="danger" onclick={close} disabled={busy}>{t('ticket.close')}</button>
      {:else if !ticket.open && me.may.ticketsReopen}
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
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 170px), 1fr));
    margin: 12px 0 8px;
  }

  .fact {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    min-width: 0;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .fvalue {
    font-size: 0.92rem;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 6px;
    overflow-wrap: anywhere;
  }

  /* A person in the facts: a link to their player page (with mradmin.player.view). */
  .person {
    background: transparent;
    color: var(--accent);
    padding: 0;
    justify-content: flex-start;
    text-align: left;
  }

  .person:disabled {
    color: var(--text);
    opacity: 1;
  }

  .coords {
    flex-wrap: wrap;
    gap: 0 10px;
  }

  .coords span {
    white-space: nowrap;
  }

  .fvalue img {
    border-radius: 4px;
    image-rendering: pixelated;
    flex-shrink: 0;
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

  .ban {
    background: var(--surface-2);
    border-left: 4px solid var(--bad);
    border-radius: 10px;
    padding: 10px 14px;
    display: grid;
    gap: 6px;
    margin-bottom: 8px;
  }

  .ban-top {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .reason {
    font-weight: 600;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    color: var(--accent);
  }

  .tag.accepted {
    background: color-mix(in srgb, var(--good) 15%, transparent);
    color: var(--good);
  }

  .tag.rejected {
    background: color-mix(in srgb, var(--bad) 12%, transparent);
    color: var(--bad);
  }

  button.good {
    background: var(--good);
    color: #fff;
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
