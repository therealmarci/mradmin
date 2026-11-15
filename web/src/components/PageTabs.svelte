<script>
  // The one style of tabs in the administration (since the sidebar, 1.6): a row with an underline, every tab has its own
  // address (back button, bookmarks and links land on it). Filters look different (FilterChips).
  //
  // tabs: [{id, label, path, n?, tone?}] - n = small counter, tone warn | bad colours it; current: id of the open tab.
  //
  // Phone (since 1.7): segments in a row that scrolls sideways, the open one in view; swiping the page to the side
  // opens the next / previous tab.
  import { go } from '../lib/router.svelte.js';
  import { device } from '../lib/mobile.svelte.js';
  import { onSideSwipe } from '../lib/swipe.js';

  let { tabs, current } = $props();

  let row = $state();
  $effect(() => {
    current;
    if (device.mobile) row?.querySelector('.on')?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
  });
  $effect(() => {
    if (!device.mobile || tabs.length < 2) return;
    return onSideSwipe((dir) => {
      const i = tabs.findIndex((x) => x.id === current);
      const next = tabs[i + dir];
      if (next) go(next.path, true);
    });
  });
</script>

{#if tabs.length > 1}
  <nav class="page-tabs" class:phone={device.mobile} role="tablist" bind:this={row}>
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

  /* Phone: segments (pills) in one row that scrolls sideways, full width of the screen */
  .phone {
    gap: 6px;
    margin: 0 -16px 14px;
    padding: 2px 16px 4px;
    border-bottom: 0;
    scroll-snap-type: x proximity;
  }

  .phone a {
    scroll-snap-align: center;
    margin: 0;
    padding: 7px 14px;
    border: 0;
    border-radius: 999px;
    background: var(--surface-2);
    font-size: 0.9rem;
  }

  .phone a.on {
    color: var(--accent-text);
    background: var(--accent);
  }

  .phone a.on .n {
    color: var(--accent);
    background: var(--accent-text);
  }

  .n.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 16%, transparent);
  }
</style>
