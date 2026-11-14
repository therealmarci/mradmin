<script>
  // The head of every page of the administration (since the sidebar, 1.6): title and breadcrumbs on the left, the main
  // actions on the right, below one slim line of key figures. No big head tile any more - the sidebar says where you are.
  //
  // title: text; crumbs: [{label, path}] in front of the title (detail pages: "Duelle › Runde #482");
  // stats: [{label, value, tone?}] - tone good | warn | bad | info colours the value; actions: snippet (buttons).
  import { go } from '../lib/router.svelte.js';

  let { title, crumbs = [], stats = [], actions = null } = $props();
</script>

<header class="page-head">
  <div class="line">
    <h1>
      {#each crumbs as c (c.path)}
        <a class="crumb" href={c.path} onclick={(e) => { e.preventDefault(); go(c.path); }}>{c.label}</a>
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
