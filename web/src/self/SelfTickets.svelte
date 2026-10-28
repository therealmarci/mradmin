<script>
  // The player's own tickets (like /report in game): the list and the form for a new one.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { ago } from '../lib/format.js';

  let data = $state(null);
  let error = $state('');
  let creating = $state(false);
  let busy = $state(false);
  let category = $state('');
  let target = $state('');
  let subject = $state('');
  let text = $state('');

  async function load() {
    try {
      data = await api('GET', '/self/tickets');
      if (!category && data.categories.length) category = data.categories[0].id;
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  const chosen = $derived(data?.categories.find((c) => c.id === category));

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
  <div class="head">
    <h1>🎫 {t('self.tickets.title')}</h1>
    {#if data?.categories.length && !creating}
      <button class="small" onclick={() => (creating = true)}>{t('self.tickets.new')}</button>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}

  {#if creating && data}
    <form class="card form" onsubmit={send}>
      <label>
        <span>{t('self.tickets.category')}</span>
        <select bind:value={category}>
          {#each data.categories as c}<option value={c.id}>{c.name}</option>{/each}
        </select>
      </label>
      {#if chosen?.player}
        <label>
          <span>{t('self.tickets.target')}</span>
          <input type="text" bind:value={target} maxlength="16" required />
        </label>
      {:else}
        <label>
          <span>{t('self.tickets.subject')}</span>
          <input type="text" bind:value={subject} minlength={data.minSubject} maxlength={data.maxSubject} required />
        </label>
      {/if}
      <label>
        <span>{t('self.tickets.text')}</span>
        <textarea bind:value={text} rows="6" minlength={Math.max(1, data.minText)} maxlength={data.maxText} required></textarea>
      </label>
      <div class="buttons">
        <button type="submit" disabled={busy}>{t('self.tickets.send')}</button>
        <button type="button" class="secondary" onclick={() => (creating = false)}>{t('common.cancel')}</button>
      </div>
    </form>
  {/if}

  {#if data}
    {#if data.tickets.length === 0}
      <p class="muted">{t('self.tickets.none')}</p>
    {:else}
      <ul>
        {#each data.tickets as x (x.id)}
          <li>
            <button class="card row" onclick={() => go('/me/tickets/' + x.id)}>
              <span class="id">#{x.id}</span>
              <span class="body">
                <strong>{x.summary}</strong>
                <span class="muted small">{x.category} · {ago(x.lastActivity)}</span>
              </span>
              {#if x.unread}<span class="tag new">{t('self.tickets.unread')}</span>{/if}
              <span class="tag {x.open ? 'open' : ''}">{x.open ? t('self.tickets.open') : t('self.tickets.closed')}</span>
            </button>
          </li>
        {/each}
      </ul>
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

  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .form {
    display: grid;
    gap: 12px;
  }

  label {
    display: grid;
    gap: 6px;
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    text-align: left;
    color: inherit;
    font: inherit;
  }

  .id {
    color: var(--muted);
    font-weight: 700;
  }

  .body {
    display: grid;
    flex: 1;
    min-width: 0;
  }

  .tag.open {
    color: var(--good);
  }

  .tag.new {
    color: var(--accent);
  }
</style>
