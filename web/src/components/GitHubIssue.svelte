<script>
  // The GitHub issue a ticket was forwarded to (since 1.7): state, labels and comments for the team - read by MRadmin
  // from the public GitHub API (kept 10 minutes). A ticket is forwarded only once; a wrong link can be corrected.
  //
  // ticket: detail of /api/tickets/{id}   me: signed-in member   onlinked(url) after changing the link
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { markdown } from '../lib/markdown.js';
  import { dateTime, ago } from '../lib/format.js';

  let { ticket, me, onlinked } = $props();

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);
  let editing = $state(false);
  let link = $state('');

  async function load(refresh = false) {
    busy = true;
    try {
      data = await api('GET', `/tickets/${ticket.id}/github${refresh ? '?refresh=1' : ''}`);
      error = data.error ? errorText({ key: data.error }) : '';
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  $effect(() => {
    ticket.issue;
    load();
  });

  async function save() {
    busy = true;
    try {
      await api('POST', `/tickets/${ticket.id}/issue`, { url: link.trim() });
      editing = false;
      onlinked(link.trim());
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const closed = $derived(data?.state === 'closed');
</script>

<section class="card gh">
  <header>
    <div class="h-left">
      <svg viewBox="0 0 16 16" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.33c-2.23.48-2.7-1.07-2.7-1.07-.36-.92-.89-1.17-.89-1.17-.73-.5.06-.49.06-.49.8.06 1.23.83 1.23.83.72 1.23 1.88.87 2.34.67.07-.52.28-.87.5-1.07-1.78-.2-3.65-.89-3.65-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8 8 0 0 0 16 8c0-4.42-3.58-8-8-8Z" /></svg>
      <div>
        <a class="title" href={ticket.issue} target="_blank" rel="noopener noreferrer">{data?.title || t('github.issue')} #{data?.number ?? ticket.issue.split('/').pop()}</a>
        <div class="meta">
          {#if data?.state}
            <span class="state" class:closed>{closed ? t('github.closed') : t('github.open')}</span>
          {/if}
          {#each data?.labels ?? [] as l}<span class="label" style="--c: {l.color}">{l.name}</span>{/each}
        </div>
      </div>
    </div>
    <div class="h-actions">
      <button class="secondary small" onclick={() => load(true)} disabled={busy}>{busy ? t('common.loading') : t('github.refresh')}</button>
      {#if me.may.ticketsForward}
        <button class="secondary small" onclick={() => { editing = !editing; link = ticket.issue; }}>{t('github.changeLink')}</button>
      {/if}
    </div>
  </header>

  {#if editing}
    <div class="edit">
      <input bind:value={link} placeholder="https://github.com/therealmarci/…/issues/123" />
      <button class="secondary small" onclick={save} disabled={busy || !link.trim()}>{t('forward.save')}</button>
    </div>
  {/if}
  {#if error}<p class="muted small">{error}</p>{/if}

  {#if data?.comments}
    {#if data.comments.length}
      <ol class="comments">
        {#each data.comments as c}
          <li>
            <div class="c-meta">
              <strong>{c.user}</strong>
              {#if c.role === 'owner' || c.role === 'member' || c.role === 'collaborator'}<span class="role">MRtechnology</span>{/if}
              <a class="muted" href={c.url} target="_blank" rel="noopener noreferrer" title={dateTime(c.created)}>{ago(c.created)}</a>
            </div>
            <div class="body">{@html markdown(c.body)}</div>
          </li>
        {/each}
      </ol>
    {:else}
      <p class="muted small">{t('github.noComments')}</p>
    {/if}
    <p class="muted small">{t('github.fetched', { time: ago(data.fetched) })}</p>
  {/if}
</section>

<style>
  .gh {
    display: grid;
    gap: 12px;
  }

  header {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  .h-left {
    display: flex;
    gap: 10px;
    min-width: 0;
  }

  .h-left svg {
    flex: none;
    margin-top: 3px;
  }

  .title {
    font-weight: 700;
    color: var(--text);
    overflow-wrap: anywhere;
  }

  .meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 4px;
  }

  .state {
    font-size: 0.74rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .state.closed {
    color: #a371f7;
    background: color-mix(in srgb, #a371f7 15%, transparent);
  }

  .label {
    font-size: 0.72rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    border: 1px solid color-mix(in srgb, var(--c) 60%, transparent);
    background: color-mix(in srgb, var(--c) 18%, transparent);
  }

  .h-actions {
    display: flex;
    gap: 6px;
  }

  .edit {
    display: flex;
    gap: 8px;
  }

  .comments {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .comments li {
    padding: 10px 12px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: var(--surface-2);
  }

  .c-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    font-size: 0.82rem;
  }

  .role {
    font-size: 0.68rem;
    font-weight: 700;
    padding: 1px 7px;
    border-radius: 999px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .body {
    margin-top: 4px;
    font-size: 0.9rem;
    overflow-wrap: anywhere;
  }

  .body :global(p) {
    margin: 0.3em 0;
  }

  p {
    margin: 0;
  }
</style>
