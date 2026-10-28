<script>
  // The tooltip of an item as the game draws it (dark box, coloured lines). Used floating by ItemIcon and as the
  // live preview in the item editor.
  import { tooltip } from '../lib/items.svelte.js';

  let { item } = $props();

  const style = (s) =>
    [s.color ? `color:${s.color}` : '', s.b ? 'font-weight:700' : '', s.i ? 'font-style:italic' : '',
      s.u || s.s ? `text-decoration:${s.u ? 'underline' : ''} ${s.s ? 'line-through' : ''}` : ''].filter(Boolean).join(';');
</script>

<div class="tooltip">
  {#each tooltip(item) as line, i}
    <div class="line" class:first={i === 0}>
      {#each line as span}<span style={style(span)}>{span.text}</span>{/each}
    </div>
  {/each}
  {#if item.amount > 1}<div class="line count">× {item.amount}</div>{/if}
</div>

<style>
  .tooltip {
    max-width: 280px;
    padding: 6px 8px;
    background: rgb(16 0 16 / 94%);
    border: 2px solid #2a0a5e;
    outline: 1px solid rgb(16 0 16 / 94%);
    border-radius: 4px;
    color: #aaaaaa;
    font-size: 0.85rem;
    line-height: 1.35;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .line.first {
    color: #fff;
    margin-bottom: 2px;
  }

  .count {
    color: #888;
  }
</style>
