<script>
  // One tile on a player's overview, built like the tiles on the home page: label, big value, values as sub-tiles,
  // link. The four rows are a subgrid of the surrounding grid, so tiles in one row sit on the same levels.
  // A click opens the full section.
  import { t } from '../lib/i18n.js';

  /**
   * value / unit: the big value and its small unit; small: a word instead of a number (smaller type);
   * tone: 'good' | 'warn' | 'bad' | 'info' | '' colours the value; facts: [{ label, value, tone }].
   */
  let { label, value = '', unit = '', small = false, tone = '', facts = [], loading = false, error = '', onOpen } = $props();
</script>

<button class="card ptile" onclick={onOpen}>
  <div class="label">{label}</div>
  {#if error}
    <div class="error small">{error}</div>
  {:else if loading}
    <div class="big small-big muted">…</div>
  {:else}
    <div class="big {tone}" class:small-big={small}>{value}{#if unit}<span class="unit">{unit}</span>{/if}</div>
  {/if}
  <div class="facts">
    {#if !loading && !error}
      {#each facts as f (f.label)}
        <div><span class="muted">{f.label}</span><span class={f.tone ?? ''}>{f.value}</span></div>
      {/each}
    {/if}
  </div>
  <div class="small link">{t('player.open')} →</div>
</button>

<style>
  /* Same as the home page tiles: four rows, part of the grid's rows (subgrid), content from the top. */
  .ptile {
    display: grid;
    grid-row: span 4;
    grid-template-rows: subgrid;
    row-gap: 6px;
    align-content: start;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    justify-content: stretch;
    justify-items: stretch;
  }

  .label {
    color: var(--muted);
    font-size: 0.85rem;
    font-weight: 600;
  }

  .big {
    font-size: 2rem;
    font-weight: 800;
    line-height: 1.1;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0 6px;
    min-height: 2.4rem;
  }

  .small-big {
    font-size: 1.3rem;
  }

  .unit {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--muted);
  }

  .facts {
    display: grid;
    gap: 6px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 96px), 1fr));
    align-content: start;
  }

  .facts > div {
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 8px;
    padding: 6px 10px;
    font-size: 0.92rem;
    font-weight: 700;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .facts .muted {
    display: block;
    font-size: 0.74rem;
    font-weight: 600;
    margin-bottom: 1px;
  }

  .good { color: var(--good); }
  .warn { color: var(--warn); }
  .bad { color: var(--bad); }
  .info { color: var(--accent); }

  .link {
    color: var(--accent);
    align-self: end;
  }
</style>
