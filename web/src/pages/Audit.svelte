<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let entries = $state(null);
  let error = $state('');

  api('GET', '/audit')
    .then((list) => (entries = list))
    .catch((e) => (error = errorText(e)));

  // Failed sign-ins and removals stand out.
  const tone = (action) =>
    action.endsWith('failed') ? 'bad' : action.includes('removed') || action.includes('revoked') ? 'warn' : '';
</script>

<div class="stack">
  <div>
    <h1>{t('audit.title')}</h1>
    <p class="muted">{t('audit.subtitle')}</p>
  </div>
  {#if error}
    <div class="error">{error}</div>
  {:else if !entries}
    <p class="muted">{t('common.loading')}</p>
  {:else if entries.length === 0}
    <p class="muted">{t('audit.empty')}</p>
  {:else}
    <div class="card list">
      {#each entries as e}
        <div class="entry">
          <div class="top">
            <span class="action {tone(e.action)}">{e.action}</span>
            <span class="muted small">{dateTime(e.time)}</span>
          </div>
          <div class="small">
            <strong>{e.name ?? '?'}</strong>
            <span class="muted">· {e.ip}</span>
            {#if e.detail}<span class="muted">· {e.detail}</span>{/if}
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .list {
    padding: 4px 20px;
  }

  .entry {
    padding: 12px 0;
    border-top: 1px solid var(--border);
  }

  .entry:first-child {
    border-top: none;
  }

  .top {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
  }

  .action {
    font-family: ui-monospace, Consolas, monospace;
    font-weight: 700;
  }

  .bad {
    color: var(--bad);
  }

  .warn {
    color: var(--warn);
  }
</style>
