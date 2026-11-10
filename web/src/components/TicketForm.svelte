<script>
  // A new ticket of the player area (like /report in game): the kind picked from tiles, then the fields it needs.
  // Used in the inbox. Data: GET /api/self/tickets (categories, limits), POST /api/self/tickets (SelfTickets.java).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';

  // oncreated(id): the ticket exists now; oncancel: back without a ticket
  let { oncreated, oncancel } = $props();

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);
  let category = $state(null);
  let target = $state('');
  let subject = $state('');
  let text = $state('');

  $effect(() => {
    (async () => {
      try {
        data = await api('GET', '/self/tickets');
        if (data.categories.length === 1) category = data.categories[0].id;
      } catch (e) {
        error = errorText(e);
      }
    })();
  });

  const chosen = $derived(data?.categories.find((c) => c.id === category));
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
      oncreated(r.id);
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<form class="create" onsubmit={send}>
  <h2>{t('self.tickets.new')}</h2>
  {#if error}<div class="error">{error}</div>{/if}

  {#if data && !data.categories.length}
    <p class="muted">{t('inbox.noCategories')}</p>
  {:else if data}
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
      </div>
    {/if}

    <div class="actions">
      <button type="submit" disabled={busy || !ready}>{t('self.tickets.send')} →</button>
      <button type="button" class="secondary" onclick={oncancel}>{t('common.cancel')}</button>
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</form>

<style>
  .create {
    display: grid;
    gap: 18px;
  }

  h2 {
    margin: 0;
    font-size: 1.15rem;
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
    background: var(--accent);
  }

  .categories {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 210px), 1fr));
  }

  .category {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    padding: 12px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
  }

  .category:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
  }

  .category.active {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, var(--surface-2));
  }

  .c-icon {
    font-size: 1.3rem;
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
    background: var(--accent);
    border-color: var(--accent);
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
    border-color: var(--accent);
  }

  .actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
</style>
