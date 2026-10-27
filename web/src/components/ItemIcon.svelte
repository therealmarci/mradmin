<script>
  // One item like in an inventory slot: the game's picture (or a short label if MRadmin has no pictures), the
  // amount, the durability bar and the enchantment glint. Hovering (or tapping) shows the tooltip as in the game.
  // With "onclick" the slot is a button (kit editor) - then a tap selects instead of showing the tooltip.
  import { items, loadItems, iconStyle, tooltipText } from '../lib/items.svelte.js';
  import { itemShort, itemTone } from '../lib/pvp.js';
  import ItemTooltip from './ItemTooltip.svelte';

  let { item = null, size = 40, placeholder = '', selected = false, onclick = null, extra = '' } = $props();

  loadItems();

  const picture = $derived(item ? iconStyle(item.type, Math.round(size * 0.8)) : null);
  const glint = $derived(!!item && (item.glint === true || (item.glint !== false && ((item.enchants?.length ?? 0) > 0 || (item.stored?.length ?? 0) > 0))));
  const durability = $derived(item && item.max > 0 && item.damage > 0 && !item.unbreakable ? Math.max(0, 1 - item.damage / item.max) : null);

  let anchor = $state();
  let open = $state(false);
  let position = $state({ x: 0, y: 0, below: false });

  function show() {
    if (!item || !anchor) return;
    const r = anchor.getBoundingClientRect();
    const below = r.top < 160;
    position = { x: Math.max(8, Math.min(r.left, window.innerWidth - 290)), y: below ? r.bottom + 6 : r.top - 6, below };
    open = true;
  }
  function hide() {
    open = false;
  }
  function tap(e) {
    if (onclick) {
      hide();
      onclick(e);
    } else if (open) {
      hide();
    } else {
      show();
    }
  }
</script>

<svelte:window onscroll={hide} />

<button
  type="button"
  class="slot {extra}"
  class:empty={!item}
  class:selected
  class:clickable={!!onclick}
  class:glint
  style="--size: {size}px"
  bind:this={anchor}
  onpointerenter={(e) => e.pointerType === 'mouse' && show()}
  onpointerleave={hide}
  onblur={hide}
  onclick={tap}
  title={item ? (items.ready ? undefined : tooltipText(item)) : placeholder}
  aria-label={item ? tooltipText(item) : placeholder}
>
  {#if item}
    {#if picture}
      <span class="picture" style={picture}></span>
    {:else}
      <span class="label {itemTone(item)}">{itemShort(item)}</span>
    {/if}
    {#if item.amount > 1}<span class="amount">{item.amount}</span>{/if}
    {#if durability != null}
      <span class="dur"><span style="width: {Math.max(8, durability * 100)}%; background: hsl({durability * 120} 85% 45%)"></span></span>
    {/if}
  {/if}
</button>

{#if open && item && items.ready}
  <div class="float" class:below={position.below} style="left: {position.x}px; top: {position.y}px" role="tooltip">
    <ItemTooltip {item} />
  </div>
{/if}

<style>
  .slot {
    position: relative;
    width: var(--size);
    height: var(--size);
    padding: 0;
    border-radius: 6px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    color: var(--text);
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    cursor: default;
    transition: none;
  }

  .slot:hover {
    filter: none;
    background: color-mix(in srgb, var(--accent) 8%, var(--surface-2));
  }

  .slot:active {
    transform: none;
  }

  .slot.clickable {
    cursor: pointer;
  }

  .slot.empty {
    background: transparent;
    border-style: dashed;
  }

  .slot.selected {
    outline: 2px solid var(--accent);
    outline-offset: 1px;
  }

  .picture {
    width: 80%;
    height: 80%;
    background-repeat: no-repeat;
  }

  .glint .picture {
    filter: drop-shadow(0 0 2px #c27bff) drop-shadow(0 0 1px #c27bff);
  }

  .glint .label {
    box-shadow: inset 0 0 0 2px color-mix(in srgb, #a855f7 70%, transparent);
  }

  .label {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.68rem;
    font-weight: 700;
  }

  .weapon { background: color-mix(in srgb, var(--bad) 18%, var(--surface)); }
  .armor { background: color-mix(in srgb, var(--accent) 18%, var(--surface)); }
  .ranged { background: color-mix(in srgb, var(--good) 18%, var(--surface)); }
  .food { background: color-mix(in srgb, var(--warn) 22%, var(--surface)); }
  .special { background: color-mix(in srgb, #a855f7 22%, var(--surface)); }

  .amount {
    position: absolute;
    right: 2px;
    bottom: 0;
    font-size: 0.78rem;
    font-weight: 700;
    color: #fff;
    text-shadow: 1px 1px 0 #3f3f3f;
  }

  .dur {
    position: absolute;
    left: 10%;
    right: 10%;
    bottom: 3px;
    height: 3px;
    background: #000;
  }

  .dur span {
    display: block;
    height: 100%;
  }

  .float {
    position: fixed;
    z-index: 1000;
    transform: translateY(-100%);
    pointer-events: none;
  }

  .float.below {
    transform: none;
  }
</style>
