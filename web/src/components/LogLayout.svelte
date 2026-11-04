<script>
  // Page Log: filter column on the left, the list on the right. Narrow (phone): the filters fold away behind a button
  // above the list. Used by the tabs Events (pages/Logs.svelte) and Server log (components/ServerLog.svelte).
  import { t } from '../lib/i18n.js';

  let { filters, active = 0, children } = $props();
  let open = $state(false);
</script>

<div class="wrap">
  <div class="layout">
    <button type="button" class="toggle" aria-expanded={open} onclick={() => (open = !open)}>
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true"><path d="M3 5h18M6 12h12M10 19h4" /></svg>
      {t('logs.filters')}
      {#if active}<span class="count">{active}</span>{/if}
    </button>
    <aside class:open>
      {@render filters()}
    </aside>
    <div class="main">
      {@render children()}
    </div>
  </div>
</div>

<style>
  .wrap {
    container-type: inline-size;
  }

  .layout {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 16px;
    align-items: start;
  }

  aside {
    position: sticky;
    top: 12px;
    display: grid;
    gap: 14px;
    padding: 12px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
  }

  .main {
    display: grid;
    gap: 10px;
    min-width: 0;
  }

  .toggle {
    display: none;
  }

  @container (max-width: 700px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
      gap: 10px;
    }

    .toggle {
      display: inline-flex;
      justify-self: start;
      align-items: center;
      gap: 8px;
      padding: 6px 12px;
      border-radius: 9px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--text);
      font-size: 0.86rem;
      font-weight: 600;
    }

    .count {
      min-width: 18px;
      padding: 0 5px;
      border-radius: 999px;
      background: var(--accent);
      color: #fff;
      font-size: 0.72rem;
      text-align: center;
    }

    aside {
      position: static;
      display: none;
    }

    aside.open {
      display: grid;
    }
  }
</style>
