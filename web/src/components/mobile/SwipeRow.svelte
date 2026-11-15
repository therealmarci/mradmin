<script>
  // A row of a list that can be swiped to the left (phone layout, since 1.7): behind it quick actions appear; swiping
  // far through runs the first one. Without actions (or on a computer) it is just the row.
  // actions: [{label, tone: 'accent' | 'good' | 'bad' | 'warn', onclick}]; children: the row.
  import { device } from '../../lib/mobile.svelte.js';

  let { actions = [], children } = $props();

  const WIDTH = 84;
  let x = $state(0);
  let dragging = $state(false);
  let row = $state();
  const open = $derived(x < 0);
  const max = $derived(actions.length * WIDTH);

  $effect(() => {
    if (!device.mobile || !actions.length || !row) return;
    let x0 = 0, y0 = 0, base = 0, dir = null;
    const start = (e) => {
      x0 = e.touches[0].clientX;
      y0 = e.touches[0].clientY;
      base = x;
      dir = null;
    };
    const move = (e) => {
      const dx = e.touches[0].clientX - x0, dy = e.touches[0].clientY - y0;
      if (dir === null && Math.abs(dx) + Math.abs(dy) > 8) dir = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y';
      if (dir !== 'x') return;
      if (e.cancelable) e.preventDefault();
      dragging = true;
      const next = base + dx;
      // a little resistance beyond the buttons
      x = next > 0 ? 0 : next < -max ? -max + (next + max) * 0.35 : next;
    };
    const end = () => {
      if (dir !== 'x') return;
      dragging = false;
      if (x < -max - 60) {
        x = 0;
        actions[0].onclick();
      } else x = x < -max / 2 ? -max : 0;
    };
    row.addEventListener('touchstart', start, { passive: true });
    row.addEventListener('touchmove', move, { passive: false });
    row.addEventListener('touchend', end, { passive: true });
    return () => {
      row.removeEventListener('touchstart', start);
      row.removeEventListener('touchmove', move);
      row.removeEventListener('touchend', end);
    };
  });

  // A tap somewhere else closes it.
  function outside(e) {
    if (open && !row?.contains(e.target)) x = 0;
  }
</script>

<svelte:window ontouchstart={outside} />

<div class="swipe" bind:this={row} data-noswipe={actions.length ? '' : undefined}>
  {#if actions.length && (open || dragging)}
    <div class="actions" style:width="{Math.max(max, -x)}px">
      {#each actions as a, i (a.label)}
        <button class="act {a.tone ?? 'accent'}" style:flex={i === 0 && -x > max ? `1 0 ${WIDTH}px` : `0 0 ${WIDTH}px`}
          onclick={() => { x = 0; a.onclick(); }}>{a.label}</button>
      {/each}
    </div>
  {/if}
  <div class="front" class:dragging style:transform={x ? `translateX(${x}px)` : null}
    onclickcapture={(e) => { if (open) { e.stopPropagation(); e.preventDefault(); x = 0; } }}>
    {@render children()}
  </div>
</div>

<style>
  .swipe {
    position: relative;
    overflow: hidden;
  }

  .front {
    position: relative;
    background: inherit;
    transition: transform 0.25s cubic-bezier(0.2, 0.9, 0.3, 1);
  }

  .front.dragging {
    transition: none;
  }

  .actions {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: row-reverse;
  }

  .act {
    height: 100%;
    padding: 0 8px;
    border: 0;
    border-radius: 0;
    font-size: 0.85rem;
    font-weight: 700;
    color: #fff;
    white-space: normal;
    line-height: 1.2;
    transition: flex-basis 0.15s;
  }

  .act:active {
    transform: none;
  }

  .accent {
    background: var(--accent);
  }

  .good {
    background: var(--good);
  }

  .warn {
    background: var(--warn);
  }

  .bad {
    background: var(--bad);
  }
</style>
