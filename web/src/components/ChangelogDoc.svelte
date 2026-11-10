<script>
  // One changelog written out as a plain list: headings (three levels) and entries as bullet points, the type as a
  // small coloured word in front, indented below the heading they belong to (lib/changelog.js).
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
        <span class="dot" aria-hidden="true">•</span>
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
    padding-left: calc(var(--indent) * 18px);
  }

  .d-head {
    padding-top: 10px;
    padding-bottom: 2px;
    font-weight: 700;
    overflow-wrap: anywhere;
  }

  .d-head:first-child {
    padding-top: 0;
  }

  .d-head.h1 {
    font-size: 1rem;
  }

  .d-head.h2 {
    font-size: 0.94rem;
  }

  .d-head.h3 {
    font-size: 0.88rem;
    color: var(--muted);
  }

  .d-line {
    --t: var(--muted);
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding-top: 2px;
    padding-bottom: 2px;
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

  .dot {
    flex-shrink: 0;
    color: var(--muted);
  }

  .tag {
    flex-shrink: 0;
    min-width: 72px;
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--t);
  }

  .d-text {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  @media (max-width: 520px) {
    .tag {
      min-width: 0;
    }
  }
</style>
