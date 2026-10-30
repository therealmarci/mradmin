<script>
  // An inventory laid out like the game: three rows of the inventory (slots 9-35), the hotbar (0-8), and armor +
  // offhand at the side. Every slot is an ItemIcon (picture + tooltip). With "slots" set (e.g. 27 for an ender
  // chest) it is a plain grid instead. With "onslot" the slots are buttons: onslot(slot) gets 0-35, "helmet",
  // "chestplate", "leggings", "boots" or "offhand"; "selected" marks one of them.
  // Optional for editors (kit editor): "ondrop(from, to, copy)" - an item dragged with the mouse onto another slot
  // (Ctrl held = copy); "onmenu(slot, x, y)" - right click on a slot; "onhover(slot | null)" - the slot under the mouse.
  // On touch: hold a slot = menu, hold and move = drag.
  import ItemIcon from './ItemIcon.svelte';
  import { t } from '../lib/i18n.js';

  let {
    inventory = [], armor = [], offhand = [], held = -1, slots = 0, onslot = null, selected = null, size = 40,
    ondrop = null, onmenu = null, onhover = null,
  } = $props();

  const bySlot = (list) => Object.fromEntries((list ?? []).map((i) => [i.slot, i]));
  const inv = $derived(bySlot(inventory));
  const arm = $derived(bySlot(armor));
  const off = $derived(bySlot(offhand));
  const range = (from, to) => Array.from({ length: to - from }, (_, i) => from + i);
  // Armor is sent boots ... helmet (slot 0 = boots) - shown helmet first.
  const armorSlots = [3, 2, 1, 0];
  const armorKeys = ['helmet', 'chestplate', 'leggings', 'boots'];
  const click = (slot) => (onslot ? () => onslot(slot) : null);

  // The item of a slot key (0-35, "helmet" ... "offhand").
  function itemAt(key) {
    if (key === 'offhand') return off[0];
    const a = armorKeys.indexOf(key);
    return a >= 0 ? arm[armorSlots[a]] : inv[key];
  }

  const keyOf = (el) => {
    const raw = el?.closest?.('[data-slot]')?.dataset.slot;
    return raw == null ? null : /^\d+$/.test(raw) ? Number(raw) : raw;
  };

  // Drag and drop with the mouse (touch: see below). It starts after a few pixels so a click stays a click.
  let press = null;
  let drag = $state(null); // { from, item, x, y, over, copy }
  let swallowClick = false;

  function down(e) {
    if (e.pointerType !== 'mouse') return touchDown(e);
    if (!ondrop || e.button !== 0) return;
    const from = keyOf(e.target);
    if (from == null || !itemAt(from)) return;
    press = { from, x: e.clientX, y: e.clientY };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up, { once: true });
  }

  function move(e) {
    if (!press) return;
    if (!drag && Math.hypot(e.clientX - press.x, e.clientY - press.y) < 6) return;
    const target = document.elementFromPoint(e.clientX, e.clientY);
    const over = root?.contains(target) ? keyOf(target) : null;
    drag = { from: press.from, item: itemAt(press.from), x: e.clientX, y: e.clientY, over, copy: e.ctrlKey || e.metaKey };
  }

  function up(e) {
    window.removeEventListener('pointermove', move);
    if (drag) {
      swallowClick = true;
      setTimeout(() => (swallowClick = false));
      if (drag.over != null && drag.over !== drag.from) ondrop(drag.from, drag.over, e.ctrlKey || e.metaKey);
    }
    press = null;
    drag = null;
  }

  // Touch (phones, tablets): no right click and no mouse drag there. Hold a slot briefly (450 ms): let go = menu,
  // move the finger = drag the item onto another slot. Moving before that is scrolling and changes nothing.
  let touch = null; // { from, x, y, armed, timer }
  let touchAt = 0;

  function touchDown(e) {
    if (!ondrop && !onmenu) return;
    const from = keyOf(e.target);
    if (from == null) return;
    touchEnd();
    touch = { from, x: e.clientX, y: e.clientY, armed: false, timer: setTimeout(armTouch, 450) };
    window.addEventListener('pointermove', touchMove);
    window.addEventListener('pointerup', touchUp, { once: true });
    window.addEventListener('pointercancel', touchEnd, { once: true });
    window.addEventListener('touchmove', holdStill, { passive: false });
  }

  function armTouch() {
    if (!touch) return;
    touch.armed = true;
    navigator.vibrate?.(15);
  }

  // Once armed the page must not scroll while the finger drags the item.
  function holdStill(e) {
    if (touch?.armed) e.preventDefault();
  }

  function touchMove(e) {
    if (!touch) return;
    const dist = Math.hypot(e.clientX - touch.x, e.clientY - touch.y);
    if (!touch.armed) {
      if (dist > 10) touchEnd();
      return;
    }
    if (!ondrop || !itemAt(touch.from) || (!drag && dist < 8)) return;
    // Look under the finger, the ghost floats above it so the finger does not hide the item.
    const target = document.elementFromPoint(e.clientX, e.clientY);
    const over = root?.contains(target) ? keyOf(target) : null;
    drag = { from: touch.from, item: itemAt(touch.from), x: e.clientX, y: e.clientY - 44, over, copy: false };
  }

  function touchUp() {
    const done = touch;
    touchEnd();
    if (!done?.armed) return;
    touchAt = Date.now();
    swallowClick = true;
    setTimeout(() => (swallowClick = false), 400);
    if (drag) {
      if (drag.over != null && drag.over !== drag.from) ondrop(drag.from, drag.over, false);
      drag = null;
    } else if (onmenu) {
      onmenu(done.from, done.x, done.y);
    }
  }

  function touchEnd() {
    if (touch) clearTimeout(touch.timer);
    touch = null;
    window.removeEventListener('pointermove', touchMove);
    window.removeEventListener('pointerup', touchUp);
    window.removeEventListener('pointercancel', touchEnd);
    window.removeEventListener('touchmove', holdStill);
  }

  // After a drag the browser still clicks the slot it ended on - that must not open the editor.
  function clickCapture(e) {
    if (swallowClick) {
      e.stopPropagation();
      e.preventDefault();
    }
  }

  function menu(e) {
    if (!onmenu) return;
    if (touch || Date.now() - touchAt < 800) {
      e.preventDefault();
      return;
    }
    const key = keyOf(e.target);
    if (key == null) return;
    e.preventDefault();
    onmenu(key, e.clientX, e.clientY);
  }

  function over(e) {
    if (onhover) onhover(keyOf(e.target));
  }

  let root = $state();
  const extraOf = (key, base = '') => [base, drag && drag.over === key && key !== drag.from ? 'drop-over' : '', drag && drag.from === key ? 'drag-from' : ''].filter(Boolean).join(' ');
  const notip = $derived(!!drag);
</script>

{#if slots > 0}
  <div class="grid" style="--size: {size}px">
    {#each range(0, slots) as s}
      <ItemIcon item={inv[s]} {size} onclick={click(s)} selected={selected === s} />
    {/each}
  </div>
{:else}
  <div class="player" class:dragging={!!drag} style="--size: {size}px" bind:this={root} role="presentation"
    onpointerdown={down} onclickcapture={clickCapture} oncontextmenu={menu} onpointerover={over}
    onpointerleave={() => onhover?.(null)}>
    <div class="side">
      {#each armorSlots as s, i}
        <ItemIcon item={arm[s]} {size} extra={extraOf(armorKeys[i], 'armor-slot')} placeholder={t('pvp.slot.' + armorKeys[i])}
          onclick={click(armorKeys[i])} selected={selected === armorKeys[i]} slotKey={armorKeys[i]} {notip} />
      {/each}
      <ItemIcon item={off[0]} {size} extra={extraOf('offhand', 'armor-slot')} placeholder={t('pvp.slot.offhand')}
        onclick={click('offhand')} selected={selected === 'offhand'} slotKey="offhand" {notip} />
    </div>
    <div class="main">
      <div class="grid">
        {#each range(9, 36) as s}
          <ItemIcon item={inv[s]} {size} extra={extraOf(s)} onclick={click(s)} selected={selected === s} slotKey={s} {notip} />
        {/each}
      </div>
      <div class="grid hotbar">
        {#each range(0, 9) as s}
          <ItemIcon item={inv[s]} {size} extra={extraOf(s, s === held ? 'held' : '')} onclick={click(s)} selected={selected === s} slotKey={s} {notip} />
        {/each}
      </div>
    </div>
  </div>
{/if}

{#if drag}
  <div class="ghost" style="left: {drag.x}px; top: {drag.y}px" aria-hidden="true">
    <ItemIcon item={drag.item} {size} notip />
    {#if drag.copy}<span class="plus">+</span>{/if}
  </div>
{/if}

<style>
  .player {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    overflow-x: auto;
    -webkit-touch-callout: none;
    -webkit-user-select: none;
    user-select: none;
  }

  .player.dragging {
    cursor: grabbing;
    user-select: none;
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

  .player :global(.drop-over) {
    outline: 2px dashed var(--accent);
    outline-offset: 1px;
    background: color-mix(in srgb, var(--accent) 18%, var(--surface-2));
  }

  .player :global(.drag-from) {
    opacity: 0.35;
  }

  /* The item under the mouse while dragging */
  .ghost {
    position: fixed;
    z-index: 1100;
    translate: -50% -50%;
    pointer-events: none;
    filter: drop-shadow(0 6px 10px rgb(0 0 0 / 0.35));
  }

  .plus {
    position: absolute;
    right: -6px;
    top: -6px;
    display: grid;
    place-items: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    font-size: 0.8rem;
    font-weight: 800;
    color: var(--accent-text);
    background: var(--accent);
  }
</style>
