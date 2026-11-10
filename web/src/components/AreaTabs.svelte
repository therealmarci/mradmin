<script>
  // The tabs of an area (player area and administration, since 1.5): one row, the current one highlighted. Shown only
  // with more than one tab. tabs: [{id, label, path, badge?}]; current: id of the open tab.
  import { go } from '../lib/router.svelte.js';

  let { tabs, current } = $props();
</script>

{#if tabs.length > 1}
  <div class="area-tabs" role="tablist">
    {#each tabs as tab (tab.id)}
      <button role="tab" aria-selected={current === tab.id} class:active={current === tab.id} onclick={() => go(tab.path)}>
        {tab.label}
        {#if tab.badge}<span class="n">{tab.badge}</span>{/if}
      </button>
    {/each}
  </div>
{/if}

<style>
  .area-tabs {
    display: flex;
    flex-wrap: wrap;
    justify-self: start;
    width: fit-content;
    max-width: 100%;
    padding: 4px;
    gap: 4px;
    margin-bottom: 18px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .area-tabs button {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 7px 14px;
    border-radius: 9px;
    font-size: 0.9rem;
    border: 0;
  }

  .area-tabs button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 18%, transparent);
  }

  @media (max-width: 760px) {
    .area-tabs {
      flex-wrap: nowrap;
      overflow-x: auto;
      width: auto;
      scrollbar-width: none;
    }

    .area-tabs button {
      flex: none;
      padding: 7px 12px;
    }
  }
</style>
