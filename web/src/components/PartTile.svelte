<script>
  // One tile on a player's overview: icon and title, a main value, status chips - a click opens the full section.
  // The same for every section (tickets, MRadmin's and the plugins' parts), so they all look alike.
  import { t } from '../lib/i18n.js';

  /** chips: [{ text, tone: 'good' | 'warn' | 'bad' | 'info' | '' }] */
  let { icon, title, value = '', sub = '', chips = [], loading = false, error = '', onOpen } = $props();
</script>

<button class="card ptile" onclick={onOpen}>
  <div class="phead"><span class="icon">{icon}</span><span class="ptitle">{title}</span></div>
  {#if error}
    <div class="error small">{error}</div>
  {:else if loading}
    <div class="muted small">{t('common.loading')}</div>
  {:else}
    {#if value}<div class="pvalue">{value}</div>{/if}
    {#if sub}<div class="muted small">{sub}</div>{/if}
    {#if chips.length}
      <div class="chips">
        {#each chips as c}<span class="tag {c.tone}">{c.text}</span>{/each}
      </div>
    {/if}
  {/if}
  <div class="open">{t('player.open')} →</div>
</button>

<style>
  /* Buttons centre their content - a tile is a column from the top with the link at the bottom. */
  .ptile {
    display: grid;
    grid-template-rows: auto auto auto 1fr auto;
    gap: 6px;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    justify-items: start;
    align-content: start;
    min-height: 150px;
    transition: border-color 0.15s;
  }

  .ptile:hover {
    border-color: var(--accent);
  }

  .phead {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .icon {
    font-size: 1.2rem;
  }

  .ptitle {
    font-weight: 700;
  }

  .pvalue {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.15;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.good { color: var(--good); background: color-mix(in srgb, var(--good) 14%, transparent); }
  .tag.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 16%, transparent); }
  .tag.bad { color: var(--bad); background: color-mix(in srgb, var(--bad) 14%, transparent); }
  .tag.info { color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); }

  .open {
    align-self: end;
    color: var(--accent);
    font-size: 0.85rem;
    font-weight: 600;
  }
</style>
