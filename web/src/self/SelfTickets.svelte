<script>
  // The player's own tickets (like /report in game): a head with the numbers, the tickets as cards (open / closed /
  // all), and a new one: the kind picked from tiles instead of a drop-down, then the fields it needs.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { ago } from '../lib/format.js';

  let data = $state(null);
  let error = $state('');
  let creating = $state(false);
  let busy = $state(false);
  let filter = $state('open');
  let category = $state(null);
  let target = $state('');
  let subject = $state('');
  let text = $state('');

  async function load() {
    try {
      data = await api('GET', '/self/tickets');
      if (data.tickets.every((x) => !x.open) && data.tickets.length) filter = 'all';
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  function start() {
    creating = true;
    category = data.categories.length === 1 ? data.categories[0].id : null;
  }

  function cancel() {
    creating = false;
    category = null;
    target = subject = text = '';
  }

  const chosen = $derived(data?.categories.find((c) => c.id === category));
  const counts = $derived({
    open: data?.tickets.filter((x) => x.open).length ?? 0,
    unread: data?.tickets.filter((x) => x.unread).length ?? 0,
    closed: data?.tickets.filter((x) => !x.open).length ?? 0,
  });
  const shown = $derived(
    (data?.tickets ?? []).filter((x) => filter === 'all' || (filter === 'open' ? x.open : !x.open)),
  );
  const ready = $derived(
    chosen && text.trim().length >= Math.max(1, data.minText)
      && (chosen.player ? target.trim().length > 0 : subject.trim().length >= Math.max(1, data.minSubject)),
  );

  async function send(event) {
    event.preventDefault();
    busy = true;
    error = '';
    try {
      const r = await api('POST', '/self/tickets', { category, target: target.trim(), subject: subject.trim(), text });
      go('/me/tickets/' + r.id);
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page">
  <header class="head">
    <span class="watermark" aria-hidden="true">🎫</span>
    <div class="intro">
      <span class="icon" aria-hidden="true">🎫</span>
      <div>
        <h1>{t('self.tickets.title')}</h1>
        <p class="muted">{t('self.tickets.subtitle')}</p>
      </div>
    </div>
    {#if data}
      <div class="numbers">
        <span><b class="good-text">{counts.open}</b> {t('self.tickets.countOpen')}</span>
        <span><b class="info-text">{counts.unread}</b> {t('self.tickets.countUnread')}</span>
        <span><b>{counts.closed}</b> {t('self.tickets.countClosed')}</span>
      </div>
      {#if data.categories.length && !creating}
        <button class="new" onclick={start}>＋ {t('self.tickets.new')}</button>
      {/if}
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if creating && data}
    <form class="card create" onsubmit={send}>
      <div class="create-head">
        <h2>{t('self.tickets.new')}</h2>
        <button type="button" class="secondary small" onclick={cancel}>{t('common.cancel')}</button>
      </div>

      <div class="step">
        <span class="step-label"><span class="n">1</span> {t('self.tickets.pickCategory')}</span>
        <div class="categories" role="radiogroup" aria-label={t('self.tickets.category')}>
          {#each data.categories as c (c.id)}
            <button type="button" role="radio" aria-checked={category === c.id} class="category" class:active={category === c.id}
              onclick={() => (category = c.id)}>
              <span class="c-icon" aria-hidden="true">{c.player ? '🚩' : '💬'}</span>
              <span class="c-text">
                <strong>{c.name}</strong>
                <span class="muted small">{c.player ? t('self.tickets.aboutPlayer') : t('self.tickets.general')}</span>
              </span>
              <span class="check" aria-hidden="true">{category === c.id ? '✓' : ''}</span>
            </button>
          {/each}
        </div>
      </div>

      {#if chosen}
        <div class="step">
          <span class="step-label"><span class="n">2</span> {t('self.tickets.describe')}</span>
          {#if chosen.player}
            <label class="field">
              <span>{t('self.tickets.target')}</span>
              <input type="text" bind:value={target} maxlength="16" placeholder="Steve" autocomplete="off" />
            </label>
          {:else}
            <label class="field">
              <span>{t('self.tickets.subject')} <em class="count">{subject.trim().length}/{data.maxSubject}</em></span>
              <input type="text" bind:value={subject} maxlength={data.maxSubject} placeholder={t('self.tickets.subjectPlaceholder')} />
              {#if data.minSubject > 1}<small class="muted">{t('self.tickets.min', { n: data.minSubject })}</small>{/if}
            </label>
          {/if}
          <label class="field">
            <span>{t('self.tickets.text')} <em class="count">{text.length}/{data.maxText}</em></span>
            <textarea bind:value={text} rows="6" maxlength={data.maxText} placeholder={t('self.tickets.textPlaceholder')}></textarea>
            {#if data.minText > 1}<small class="muted">{t('self.tickets.min', { n: data.minText })}</small>{/if}
          </label>
          <button type="submit" class="send" disabled={busy || !ready}>{t('self.tickets.send')} →</button>
        </div>
      {/if}
    </form>
  {/if}

  {#if data}
    <section class="card tile">
      <div class="tile-head">
        <h2>{t('self.tickets.yours')}</h2>
        <div class="switch" role="tablist">
          {#each ['open', 'closed', 'all'] as f}
            <button role="tab" aria-selected={filter === f} class:active={filter === f} onclick={() => (filter = f)}>
              {t('self.tickets.filter.' + f)}
            </button>
          {/each}
        </div>
      </div>
      {#if shown.length === 0}
        <div class="empty">
          <span aria-hidden="true">{data.tickets.length ? '🗂️' : '✨'}</span>
          <p>{data.tickets.length ? t('self.tickets.noneHere') : t('self.tickets.none')}</p>
        </div>
      {:else}
        <ul class="list">
          {#each shown as x (x.id)}
            <li>
              <button class="ticket" class:unread={x.unread} class:closed={!x.open} onclick={() => go('/me/tickets/' + x.id)}>
                <span class="t-top">
                  <span class="id">#{x.id}</span>
                  <span class="chip">{x.category}</span>
                  {#if x.fromPlugin}<span class="chip auto">{t('self.tickets.auto')}</span>{/if}
                  <span class="status">{x.open ? t('self.tickets.open') : t('self.tickets.closed')}</span>
                </span>
                <span class="t-title">{x.summary}</span>
                <span class="t-bottom">
                  <span class="muted small">{t('self.tickets.lastActivity', { ago: ago(x.lastActivity) })}</span>
                  {#if x.unread}<span class="unread-badge">● {t('self.tickets.unread')}</span>{/if}
                </span>
              </button>
            </li>
          {/each}
        </ul>
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
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tk) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tk) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -10px;
    bottom: -34px;
    font-size: 9rem;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    font-size: 1.8rem;
    background: color-mix(in srgb, var(--tk) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--tk) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .numbers {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .numbers span {
    padding: 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    font-size: 0.88rem;
  }

  .numbers b {
    font-size: 1.1rem;
    margin-right: 2px;
  }

  .new {
    position: relative;
  }

  h2 {
    margin: 0;
    font-size: 1.1rem;
  }

  /* New ticket */
  .create {
    display: grid;
    gap: 18px;
    border-top: 4px solid var(--tk);
  }

  .create-head,
  .tile-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .step {
    display: grid;
    gap: 12px;
  }

  .step-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
  }

  .step-label .n {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    font-size: 0.8rem;
    color: #fff;
    background: var(--tk);
  }

  .categories {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr));
  }

  .category {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
    justify-content: flex-start;
    transition: border-color 0.15s, box-shadow 0.15s, transform 0.1s;
  }

  .category:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--tk) 50%, var(--border));
  }

  .category.active {
    border-color: var(--tk);
    background: color-mix(in srgb, var(--tk) 10%, var(--surface-2));
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--tk) 22%, transparent);
  }

  .c-icon {
    font-size: 1.5rem;
  }

  .c-text {
    display: grid;
    flex: 1;
    min-width: 0;
  }

  .check {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: 2px solid var(--border);
    color: #fff;
    font-size: 0.75rem;
    font-weight: 800;
  }

  .category.active .check {
    background: var(--tk);
    border-color: var(--tk);
  }

  .field {
    display: grid;
    gap: 6px;
    margin: 0;
  }

  .field > span {
    display: flex;
    justify-content: space-between;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--muted);
  }

  .count {
    font-style: normal;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
  }

  textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 11px 12px;
    resize: vertical;
    outline: none;
  }

  textarea:focus {
    border-color: var(--tk);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--tk) 25%, transparent);
  }

  .send {
    justify-self: start;
  }

  /* The list */
  .switch {
    display: inline-flex;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    background: transparent;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 9px;
    font-size: 0.88rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .list {
    list-style: none;
    margin: 14px 0 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  .ticket {
    --st: var(--good);
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 16px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--st);
    border-radius: 12px;
    justify-content: flex-start;
    align-items: stretch;
    transition: transform 0.15s, border-color 0.15s;
  }

  .ticket:hover {
    filter: none;
    transform: translateY(-2px);
  }

  .ticket.unread {
    --st: var(--accent);
    background: color-mix(in srgb, var(--accent) 7%, var(--surface-2));
  }

  .ticket.closed {
    --st: var(--muted);
  }

  .ticket.closed .t-title {
    color: var(--muted);
  }

  .t-top,
  .t-bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .id {
    font-weight: 800;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .chip.auto {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .status {
    margin-left: auto;
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
  }

  .t-title {
    font-weight: 700;
    font-size: 1.02rem;
    overflow-wrap: anywhere;
  }

  .t-bottom {
    margin-top: auto;
    justify-content: space-between;
  }

  .unread-badge {
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--accent);
  }

  .empty {
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 30px 10px 14px;
    color: var(--muted);
  }

  .empty span {
    font-size: 2.2rem;
  }

  .empty p {
    margin: 0;
  }

  .good-text {
    color: var(--good);
  }

  .info-text {
    color: var(--accent);
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .new {
      width: 100%;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .ticket,
    .category {
      transition: none;
    }
  }
</style>
