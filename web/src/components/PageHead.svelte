<script>
  // The head of every page of the administration (since the sidebar, 1.6): title and breadcrumbs on the left, the main
  // actions on the right, below one slim line of key figures. No big head tile any more - the sidebar says where you are.
  //
  // title: text; crumbs: [{label, path}] in front of the title (detail pages: "Duelle › Runde #482");
  // stats: [{label, value, tone?}] - tone good | warn | bad | info colours the value; actions: snippet (buttons);
  // onnav(path): instead of going there at once (e.g. a page with unsaved changes asks first).
  // Phone (since 1.7): the title moves into the bar at the top (big, shrinks while scrolling), the last crumb becomes
  // its back button - here only the actions and key figures stay.
  import { go } from '../lib/router.svelte.js';
  import { device, claimHead } from '../lib/mobile.svelte.js';

  let { title, crumbs = [], stats = [], actions = null, onnav = null } = $props();

  const back = $derived(crumbs.length ? { label: crumbs[crumbs.length - 1].label, path: crumbs[crumbs.length - 1].path, onnav } : null);
  let claim = null;
  $effect(() => {
    claim = claimHead({ title, back });
    return () => claim.release();
  });
  $effect(() => {
    claim?.update({ title, back });
  });
</script>

<header class="page-head" class:phone={device.mobile} class:empty={device.mobile && !actions && !stats.length}>
  <div class="line">
    <h1>
      {#each crumbs as c (c.path)}
        <a class="crumb" href={c.path} onclick={(e) => { e.preventDefault(); (onnav ?? go)(c.path); }}>{c.label}</a>
        <span class="sep" aria-hidden="true">›</span>
      {/each}
      <span class="title">{title}</span>
    </h1>
    {#if actions}<div class="actions">{@render actions()}</div>{/if}
  </div>
  {#if stats.length}
    <ul class="stats">
      {#each stats as s (s.label)}
        <li><strong class={s.tone ?? ''}>{s.value}</strong> {s.label}</li>
      {/each}
    </ul>
  {/if}
</header>

<style>
  .page-head {
    display: grid;
    gap: 4px;
    margin-bottom: 14px;
  }

  /* Phone: the bar at the top shows the title; actions as a row of buttons, key figures as chips */
  .phone h1 {
    display: none;
  }

  .phone.empty {
    display: none;
  }

  .phone .line {
    min-height: 0;
  }

  .phone .actions {
    width: 100%;
  }

  .phone .actions :global(button) {
    flex: 1 1 auto;
  }

  .phone .stats {
    order: -1;
    gap: 6px;
    font-size: 0.82rem;
    overflow-x: auto;
    flex-wrap: nowrap;
    scrollbar-width: none;
    margin: 0 -16px;
    padding: 0 16px 2px;
  }

  .phone .stats li {
    flex: none;
    padding: 4px 10px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .phone .stats li + li::before {
    content: none;
  }

  .line {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px 16px;
    min-height: 38px;
  }

  h1 {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0 8px;
    margin: 0;
    font-size: 1.4rem;
  }

  .crumb {
    color: var(--muted);
    font-weight: 600;
    text-decoration: none;
  }

  .crumb:hover {
    color: var(--text);
  }

  .sep {
    color: var(--muted);
    font-weight: 400;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .stats {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 0;
    margin: 0;
    padding: 0;
    list-style: none;
    color: var(--muted);
    font-size: 0.9rem;
  }

  .stats li + li::before {
    content: '·';
    margin: 0 8px;
  }

  .stats strong {
    color: var(--text);
    font-variant-numeric: tabular-nums;
  }

  .stats .good {
    color: var(--good);
  }

  .stats .warn {
    color: var(--warn);
  }

  .stats .bad {
    color: var(--bad);
  }

  .stats .info {
    color: var(--accent);
  }
</style>
