<script>
  // One changelog written out like a document: headings (three levels) and entries with their type tag, indented below
  // the heading they belong to (lib/changelog.js).
  import { t } from '../lib/i18n.js';
  import { lines } from '../lib/changelog.js';

  let { entries } = $props();
</script>

<div class="doc">
  {#each lines(entries) as l}
    {#if l.heading}
      <div class="d-head h{l.level}" style="--indent: {l.indent}">{l.text}</div>
    {:else}
      <div class="d-line t-{l.type}" style="--indent: {l.indent}">
        <span class="tag">{t('news.type.' + l.type)}</span>
        <span class="d-text">{l.text}</span>
      </div>
    {/if}
  {/each}
</div>

<style>
  .doc {
    display: grid;
  }

  .d-head,
  .d-line {
    --step: 20px;
    padding-left: calc(var(--indent) * var(--step));
    background: repeating-linear-gradient(90deg, var(--border) 0 2px, transparent 2px var(--step)) left top /
      calc(var(--indent) * var(--step)) 100% no-repeat;
  }

  .d-head {
    padding-top: 12px;
    padding-bottom: 4px;
    font-weight: 800;
    overflow-wrap: anywhere;
  }

  .d-head:first-child {
    padding-top: 0;
  }

  .d-head.h1 {
    font-size: 0.82rem;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--accent);
  }

  .d-head.h2 {
    font-size: 1rem;
  }

  .d-head.h3 {
    font-size: 0.9rem;
    color: var(--muted);
  }

  .d-line {
    --t: var(--muted);
    display: flex;
    align-items: baseline;
    gap: 10px;
    padding-top: 3px;
    padding-bottom: 3px;
  }

  .t-new {
    --t: var(--good);
  }

  .t-changed {
    --t: var(--accent);
  }

  .t-fixed {
    --t: var(--warn);
  }

  .t-removed {
    --t: var(--bad);
  }

  .tag {
    flex-shrink: 0;
    min-width: 76px;
    padding: 1px 8px;
    border-radius: 6px;
    text-align: center;
    font-size: 0.7rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--t);
    background: color-mix(in srgb, var(--t) 14%, transparent);
  }

  .d-text {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  @media (max-width: 520px) {
    .d-head,
    .d-line {
      --step: 12px;
    }

    .tag {
      min-width: 0;
    }
  }
</style>
