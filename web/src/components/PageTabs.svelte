<script>
  // The one style of tabs in the administration (since the sidebar, 1.6): a row with an underline, every tab has its own
  // address (back button, bookmarks and links land on it). Filters look different (FilterChips).
  //
  // tabs: [{id, label, path, n?, tone?}] - n = small counter, tone warn | bad colours it; current: id of the open tab.
  import { go } from '../lib/router.svelte.js';

  let { tabs, current } = $props();
</script>

{#if tabs.length > 1}
  <nav class="page-tabs" role="tablist">
    {#each tabs as tab (tab.id)}
      <a role="tab" href={tab.path} aria-selected={current === tab.id} class:on={current === tab.id}
        onclick={(e) => { e.preventDefault(); go(tab.path, true); }}>
        {tab.label}
        {#if tab.n}<span class="n {tab.tone ?? ''}">{tab.n}</span>{/if}
      </a>
    {/each}
  </nav>
{/if}

<style>
  .page-tabs {
    display: flex;
    gap: 4px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--border);
    overflow-x: auto;
    scrollbar-width: none;
  }

  a {
    flex: none;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px 10px;
    margin-bottom: -1px;
    border-bottom: 2px solid transparent;
    color: var(--muted);
    font-weight: 600;
    font-size: 0.92rem;
    text-decoration: none;
  }

  a:hover {
    color: var(--text);
  }

  a.on {
    color: var(--text);
    border-bottom-color: var(--accent);
  }

  .n {
    min-width: 18px;
    padding: 0 6px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    text-align: center;
    color: var(--muted);
    background: var(--surface-2);
  }

  .n.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .n.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 16%, transparent);
  }
</style>
