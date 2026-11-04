<script>
  // One group of the filter column on the page Log: "All" plus one row per entry with colour dot and count. Several
  // can be chosen; none chosen = all. selected: array of keys (bindable).
  import { t } from '../lib/i18n.js';

  let { title, items = [], selected = $bindable([]) } = $props();

  const total = $derived(items.reduce((sum, i) => sum + (i.count ?? 0), 0));

  function toggle(key) {
    selected = selected.includes(key) ? selected.filter((k) => k !== key) : [...selected, key];
  }
</script>

<section>
  <h3>{title}</h3>
  <ul>
    <li>
      <button type="button" class="row" class:on={!selected.length} aria-pressed={!selected.length} onclick={() => (selected = [])}>
        <span class="box" aria-hidden="true"></span>
        <span class="label">{t('logs.all')}</span>
        <span class="num">{total}</span>
      </button>
    </li>
    {#each items as item (item.key)}
      <li>
        <button type="button" class="row" class:on={selected.includes(item.key)} aria-pressed={selected.includes(item.key)}
          style="--c:{item.color}" onclick={() => toggle(item.key)}>
          <span class="box" aria-hidden="true"></span>
          <span class="dot" aria-hidden="true"></span>
          <span class="label">{item.label}</span>
          <span class="num">{item.count ?? 0}</span>
        </button>
      </li>
    {/each}
  </ul>
</section>

<style>
  h3 {
    margin: 0 0 6px;
    padding: 0 6px;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--muted);
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 2px;
  }

  .row {
    display: flex;
    width: 100%;
    justify-content: flex-start;
    align-items: center;
    gap: 8px;
    padding: 6px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--text);
    font-size: 0.88rem;
    font-weight: 500;
    text-align: left;
  }

  .row:hover {
    background: var(--surface-2);
  }

  .box {
    flex-shrink: 0;
    width: 14px;
    height: 14px;
    border-radius: 4px;
    border: 1.5px solid var(--border);
    background: var(--surface-2);
  }

  .row.on .box {
    border-color: var(--accent);
    background: var(--accent)
      url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath d='M3.5 8.5l3 3 6-7' fill='none' stroke='white' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E")
      center / 12px no-repeat;
  }

  .dot {
    flex-shrink: 0;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--c);
  }

  .label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .num {
    color: var(--muted);
    font-size: 0.78rem;
    font-variant-numeric: tabular-nums;
  }
</style>
