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

  // Buttons in the head: a ticket of MRchunks (lock for lag) leads to its chunk loader, a ticket about a player to
  // them - the reported player, otherwise the one who wrote it (not for contact requests from the website).
  const loaderId = $derived(ticket?.source === 'mrchunks' && /^\d+$/.test(ticket?.sourceId ?? '') && me.may.mrchunks ? ticket.sourceId : null);
  const person = $derived(!ticket || !me.may.players || request?.kind === 'contact' ? null
    : ticket.report && ticket.targetUuid ? { uuid: ticket.targetUuid, name: ticket.target }
    : { uuid: ticket.creatorUuid, name: ticket.creator });

  // A day line whenever the day changes.
  const day = (ms) => new Date(ms).toDateString();
  const time = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
</script>

<div class="page">
  <button class="back" onclick={() => go('/tickets')}>← {t('ticket.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}

  {#if !ticket}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else}
    <header class="head" class:closed={!ticket.open} class:claimed={ticket.open && ticket.claimedBy}>
      <div class="h-main">
        <span class="h-top">
          <span class="id">#{ticket.id}</span>
          <span class="chip" class:auto={!!ticket.source}>{ticket.categoryName}</span>
          <span class="status">
            {ticket.open ? (ticket.claimedBy ? t('tickets.claimed', { name: ticket.claimedBy }) : t('ticket.statusOpen')) : t('ticket.statusClosed')}
          </span>
        </span>
        <h1>{ticket.summary || ticket.categoryName}</h1>
      </div>
      <div class="h-actions">
        {#if loaderId}
          <button class="secondary small" onclick={() => go('/chunks/' + loaderId)}>▦ {t('ticket.toLoader')}</button>
        {/if}
        {#if person}
          <button class="secondary small person-btn" onclick={() => go('/players/' + person.uuid)}>
            <img src={head(person.uuid, 32)} alt="" width="18" height="18" /> {t('ticket.toPlayer', { name: person.name })}
          </button>
        {/if}
        {#if ticket.open && me.may.ticketsClaim}
          <button class="secondary small" onclick={() => action('claim')} disabled={busy}>
            {mineClaimed ? t('ticket.release') : t('ticket.claim')}
          </button>
        {/if}
      </div>
    </header>

    <!-- The facts as small tiles: label on top, value below. -->
    <section class="card facts">
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
    </section>

    {#if ticket.source}
      <div class="notice small">{t('ticket.lockHint')}</div>
    {/if}
    {#if request?.kind === 'unban'}
      <section class="card ban">
        <div class="ban-top">
          <span class="muted small">{t('request.ban', { id: request.banId })}</span>
          <span class="tag">{request.stage === 'appeal' ? t('request.stageAppeal') : t('request.stageRequest')}</span>
          {#if request.decision}<span class="tag {request.decision}">{t('request.decision.' + request.decision)}</span>{/if}
        </div>
        <div class="reason">{request.banReason}</div>
        <div class="muted small">{t('request.banFacts', { by: request.banOperator, since: dateTime(request.banStart),
          until: request.banEnd ? dateTime(request.banEnd) : t('pub.permanent') })}</div>
        <div class="notice small">{request.stage === 'appeal' ? t('request.appealHint') : t('request.unbanHint')}</div>
      </section>
    {:else if request?.kind === 'contact'}
      <div class="notice small">
        {me.may.mail ? t('request.contactMail') : t('request.contactNoMail')}
        {#if !me.may.mail && mailto}<a href={mailto}>{t('request.writeMail')}</a>{/if}
      </div>
    {/if}

    <section class="card conversation">
      <ol class="messages" bind:this={chat}>
        {#each groups as g, i}
          {#if i === 0 || day(g.messages[0].time) !== day(groups[i - 1].messages[0].time)}
            <li class="day"><span>{new Date(g.messages[0].time).toLocaleDateString()}</span></li>
          {/if}
          {#if g.system}
            <li class="system">
              <span>
                <b>{g.name}</b> · {time(g.messages[0].time)}
                {#each g.messages as m}<span class="s-text">{m.text}</span>{/each}
              </span>
            </li>
          {:else}
            <li class="msg" class:staff={g.staff}>
              <img class="avatar" src={head(g.uuid, 48)} alt="" width="32" height="32" />
              <div class="bubble">
                <div class="meta">
                  <strong>{g.name}</strong>
                  {#if g.staff}<span class="team">{t('ticket.team')}</span>{/if}
                  <span class="muted" title={dateTime(g.messages[0].time)}>{time(g.messages[0].time)} · {ago(g.messages[0].time)}</span>
                </div>
                {#each g.messages as m}<div class="text">{m.text}</div>{/each}
              </div>
            </li>
          {/if}
        {/each}
      </ol>

      {#if !ticket.open}
        <div class="closed-note">{t('ticket.closedBy', { name: ticket.closedBy, time: dateTime(ticket.closedAt) })}</div>
      {/if}

      {#if ticket.open && me.may.ticketsReply}
        <div class="composer">
          <textarea bind:value={text} rows="3" placeholder={t('ticket.reply')} onkeydown={keys} maxlength="2000"></textarea>
          <div class="c-foot">
            <span class="muted small">{t('ticket.sendHint')}</span>
            <div class="actions">
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
              <button onclick={send} disabled={busy || !text.trim()}>{t('ticket.send')} ➤</button>
            </div>
          </div>
        </div>
      {:else if ticket.open && me.may.ticketsClose}
        <div class="composer"><button class="danger" onclick={close} disabled={busy}>{t('ticket.close')}</button></div>
      {:else if !ticket.open && me.may.ticketsReopen}
        <div class="composer"><button class="secondary" onclick={() => action('reopen')} disabled={busy}>{t('ticket.reopen')}</button></div>
      {/if}
    </section>
  {/if}
</div>

<style>
  .page {
    --tk: var(--accent);
    display: grid;
    gap: 14px;
    max-width: 960px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--tk);
    padding: 4px 0;
  }

  /* Head: number, category, status and title; the colour on the left as in the list. */
  .head {
    --st: var(--warn);
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

  .head.claimed {
    --st: var(--good);
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

  .h-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .person-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .person-btn img {
    border-radius: 4px;
    image-rendering: pixelated;
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

  .chip.auto {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
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

  /* Facts */
  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
    padding: 14px;
  }

  .fact {
    display: grid;
    gap: 3px;
    align-content: start;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    min-width: 0;
  }

  .flabel {
    font-size: 0.74rem;
    font-weight: 600;
    color: var(--muted);
  }

  .fvalue {
    font-weight: 700;
    font-size: 0.92rem;
    overflow-wrap: anywhere;
  }

  .person {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-weight: 700;
  }

  .person:not(:disabled):hover {
    filter: none;
    color: var(--tk);
  }

  .person:disabled {
    opacity: 1;
    cursor: default;
  }

  .person img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 8px;
  }

  .coords span {
    white-space: nowrap;
  }

  .mono {
    font-family: ui-monospace, monospace;
    font-size: 0.85rem;
  }

  .notice {
    padding: 10px 14px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--tk) 8%, var(--surface));
    border: 1px solid color-mix(in srgb, var(--tk) 25%, var(--border));
  }

  /* Unban request */
  .ban {
    display: grid;
    gap: 8px;
  }

  .ban-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .tag {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .tag.accepted {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .tag.rejected {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  button.good {
    background: var(--good);
    color: #fff;
  }

  .reason {
    font-weight: 700;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  /* Conversation: players on the left, the team on the right (violet), the system in the middle. */
  .conversation {
    display: grid;
    gap: 14px;
    padding: 18px;
  }

  .messages {
    list-style: none;
    margin: 0;
    padding: 0 4px 0 0;
    display: grid;
    gap: 12px;
    max-height: 60vh;
    overflow-y: auto;
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

  .system > span {
    display: grid;
    gap: 2px;
    font-size: 0.82rem;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 10px;
    border: 1px dashed var(--border);
    max-width: 80%;
    text-align: center;
    overflow-wrap: anywhere;
  }

  .s-text {
    white-space: pre-wrap;
  }

  .msg {
    display: flex;
    gap: 10px;
    align-items: flex-end;
    max-width: min(100%, 680px);
  }

  .msg.staff {
    flex-direction: row-reverse;
    justify-self: end;
  }

  .avatar {
    width: 32px;
    height: 32px;
    flex-shrink: 0;
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .bubble {
    padding: 10px 14px;
    border-radius: 14px 14px 14px 4px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    min-width: 0;
  }

  .msg.staff .bubble {
    border-radius: 14px 14px 4px 14px;
    border-color: color-mix(in srgb, #8b5cf6 40%, var(--border));
    background: color-mix(in srgb, #8b5cf6 9%, var(--surface-2));
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

  .closed-note {
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    color: var(--muted);
    text-align: center;
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

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-left: auto;
  }

  @media (max-width: 640px) {
    .head {
      padding: 16px;
    }

    .conversation {
      padding: 12px;
    }

    .avatar {
      display: none;
    }

    .actions,
    .actions button {
      width: 100%;
    }
  }
</style>
