<script>
  // An inventory laid out like the game: three rows of the inventory (slots 9-35), the hotbar (0-8), and armor +
  // offhand at the side. Every slot is an ItemIcon (picture + tooltip). With "slots" set (e.g. 27 for an ender
  // chest) it is a plain grid instead. With "onslot" the slots are buttons: onslot(slot) gets 0-35, "helmet",
  // "chestplate", "leggings", "boots" or "offhand"; "selected" marks one of them.
  import ItemIcon from './ItemIcon.svelte';
  import { t } from '../lib/i18n.js';

  let { inventory = [], armor = [], offhand = [], held = -1, slots = 0, onslot = null, selected = null, size = 40 } = $props();

  const bySlot = (list) => Object.fromEntries((list ?? []).map((i) => [i.slot, i]));
  const inv = $derived(bySlot(inventory));
  const arm = $derived(bySlot(armor));
  const off = $derived(bySlot(offhand));
  const range = (from, to) => Array.from({ length: to - from }, (_, i) => from + i);
  // Armor is sent boots ... helmet (slot 0 = boots) - shown helmet first.
  const armorSlots = [3, 2, 1, 0];
  const armorKeys = ['helmet', 'chestplate', 'leggings', 'boots'];
  const click = (slot) => (onslot ? () => onslot(slot) : null);
</script>

{#if slots > 0}
  <div class="grid" style="--size: {size}px">
    {#each range(0, slots) as s}
      <ItemIcon item={inv[s]} {size} onclick={click(s)} selected={selected === s} />
    {/each}
  </div>
{:else}
  <div class="player" style="--size: {size}px">
    <div class="side">
      {#each armorSlots as s, i}
        <ItemIcon item={arm[s]} {size} extra="armor-slot" placeholder={t('pvp.slot.' + armorKeys[i])}
          onclick={click(armorKeys[i])} selected={selected === armorKeys[i]} />
      {/each}
      <ItemIcon item={off[0]} {size} extra="armor-slot" placeholder={t('pvp.slot.offhand')}
        onclick={click('offhand')} selected={selected === 'offhand'} />
    </div>
    <div class="main">
      <div class="grid">
        {#each range(9, 36) as s}
          <ItemIcon item={inv[s]} {size} onclick={click(s)} selected={selected === s} />
        {/each}
      </div>
      <div class="grid hotbar">
        {#each range(0, 9) as s}
          <ItemIcon item={inv[s]} {size} extra={s === held ? 'held' : ''} onclick={click(s)} selected={selected === s} />
        {/each}
      </div>
    </div>
  </div>
{/if}

<style>
  .player {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    overflow-x: auto;
  }

  .side {
    display: grid;
    gap: 4px;
  }

  .main {
    display: grid;
    gap: 8px;
  }

  .grid {
    display: grid;
    gap: 4px;
    grid-template-columns: repeat(9, var(--size, 40px));
  }

  .player :global(.held) {
    outline: 2px solid var(--accent);
    outline-offset: 1px;
  }

  .player :global(.armor-slot.empty) {
    opacity: 0.6;
  }
</style>
