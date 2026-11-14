<script>
  // The filter row of the administration (since the sidebar, 1.6): round chips (one chosen), optionally a search field
  // on the right. Looks deliberately different from the tabs (PageTabs) - a filter narrows a list, a tab is a page.
  //
  // options: [{id, label, n?, tone?}] - tone good | warn | bad | info adds a coloured dot; value: chosen id;
  // onchange(id); search: null (no field) or the text, onsearch(text); placeholder for the field.
  let { options = [], value, onchange, search = null, onsearch = null, placeholder = '' } = $props();
</script>

<div class="filter-row">
  {#if options.length}
    <div class="chips" role="radiogroup">
      {#each options as o (o.id)}
        <button type="button" role="radio" aria-checked={value === o.id} class:on={value === o.id} onclick={() => onchange(o.id)}>
          {#if o.tone}<span class="dot {o.tone}"></span>{/if}{o.label}{#if o.n != null}<span class="n">{o.n}</span>{/if}
        </button>
      {/each}
    </div>
  {/if}
  {#if search !== null}
    <input class="search" type="search" value={search} {placeholder} oninput={(e) => onsearch(e.currentTarget.value)} />
  {/if}
</div>

<style>
  .filter-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 14px;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  button {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 4px 12px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
    font-size: 0.85rem;
    font-weight: 600;
  }

  button:hover {
    color: var(--text);
  }

  button.on {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
    background: color-mix(in srgb, var(--accent) 10%, transparent);
  }

  .n {
    font-variant-numeric: tabular-nums;
    opacity: 0.75;
  }

  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--muted);
  }

  .dot.good {
    background: var(--good);
  }

  .dot.warn {
    background: var(--warn);
  }

  .dot.bad {
    background: var(--bad);
  }

  .dot.info {
    background: var(--accent);
  }

  .search {
    width: min(260px, 100%);
    margin: 0;
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.88rem;
  }
</style>
