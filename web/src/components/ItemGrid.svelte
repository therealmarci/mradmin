<script>
  // An inventory as tiles, laid out like the game: three rows of the inventory (slots 9-35), the hotbar (0-8),
  // and armor + offhand at the side. Without the game's textures every tile shows a short label, the amount and a
  // colour per kind of item; the tooltip has the name, enchantments and durability. With "slots" set (e.g. 27 for
  // an ender chest) it is a plain grid instead.
  import { itemShort, itemTone, itemTitle } from '../lib/pvp.js';
  import { t } from '../lib/i18n.js';

  let { inventory = [], armor = [], offhand = [], held = -1, slots = 0 } = $props();

  const bySlot = (list) => Object.fromEntries((list ?? []).map((i) => [i.slot, i]));
  const inv = $derived(bySlot(inventory));
  const arm = $derived(bySlot(armor));
  const off = $derived(bySlot(offhand));
  const range = (from, to) => Array.from({ length: to - from }, (_, i) => from + i);
  // Armor is sent boots ... helmet (slot 0 = boots) - shown helmet first.
  const armorSlots = [3, 2, 1, 0];
  const armorKeys = ['helmet', 'chestplate', 'leggings', 'boots'];
</script>

{#snippet tile(item, extra = '', placeholder = '')}
  {#if item}
    <div class="tile {itemTone(item)} {extra}" class:glint={item.enchants?.length} title={itemTitle(item)}>
      <span class="short">{itemShort(item)}</span>
      {#if item.amount > 1}<span class="amount">{item.amount}</span>{/if}
      {#if item.max > 0 && item.damage > 0}
        <span class="dur" style="width: {Math.max(4, 100 - (item.damage / item.max) * 100)}%"></span>
      {/if}
    </div>
  {:else}
    <div class="tile empty {extra}" title={placeholder}></div>
  {/if}
{/snippet}

{#if slots > 0}
  <div class="grid" style="--cols: 9">
    {#each range(0, slots) as s}{@render tile(inv[s])}{/each}
  </div>
{:else}
  <div class="player">
    <div class="side">
      {#each armorSlots as s, i}{@render tile(arm[s], 'armor-slot', t('pvp.slot.' + armorKeys[i]))}{/each}
      {@render tile(off[0], 'armor-slot', t('pvp.slot.offhand'))}
    </div>
    <div class="main">
      <div class="grid" style="--cols: 9">
        {#each range(9, 36) as s}{@render tile(inv[s])}{/each}
      </div>
      <div class="grid hotbar" style="--cols: 9">
        {#each range(0, 9) as s}{@render tile(inv[s], s === held ? 'held' : '')}{/each}
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
    grid-template-columns: repeat(var(--cols), 40px);
  }

  .tile {
    position: relative;
    width: 40px;
    height: 40px;
    border-radius: 6px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.68rem;
    font-weight: 700;
    color: var(--text);
    overflow: hidden;
    cursor: default;
  }

  .tile.empty {
    background: transparent;
    border-style: dashed;
  }

  .tile.armor-slot.empty {
    opacity: 0.6;
  }

  .tile.held {
    outline: 2px solid var(--accent);
    outline-offset: 1px;
  }

  .tile.glint {
    box-shadow: inset 0 0 0 2px color-mix(in srgb, #a855f7 70%, transparent);
  }

  .weapon { background: color-mix(in srgb, var(--bad) 18%, var(--surface)); }
  .armor { background: color-mix(in srgb, var(--accent) 18%, var(--surface)); }
  .ranged { background: color-mix(in srgb, var(--good) 18%, var(--surface)); }
  .food { background: color-mix(in srgb, var(--warn) 22%, var(--surface)); }
  .special { background: color-mix(in srgb, #a855f7 22%, var(--surface)); }

  .amount {
    position: absolute;
    right: 3px;
    bottom: 1px;
    font-size: 0.7rem;
  }

  .dur {
    position: absolute;
    left: 0;
    bottom: 0;
    height: 3px;
    background: var(--good);
  }
</style>
