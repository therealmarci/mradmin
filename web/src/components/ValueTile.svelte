<script>
  // A measured value as a tile: name and value, a bar towards its limit, the limits in small, and its course as a curve
  // that closes the tile. The colour follows the value's own state. Used for the chunk loaders and on the start page.
  import StateLine from './StateLine.svelte';

  /**
   * value/suffix: the big text and a small one after it; share: the bar in percent; tick: a mark on it in percent;
   * note: the small line; tone: good | warn | bad | off | locked; points/phases/live/from/to/gap: see StateLine, line: whether
   * there is something to draw (the space stays); lineHeight: height of the curve; onclick: the tile is clickable.
   */
  let {
    label, value, suffix = '', share = 0, tick = null, note = '', tone = 'good', title = '', barTitle = '',
    points = [], phases = [], live = null, from = 0, to = 0, gap = null, line = true, lineHeight = 40, onclick = null, tabindex = 0,
  } = $props();
</script>

<button class="tile {tone}" class:plain={!onclick} {title} {tabindex} onclick={() => onclick?.()}>
  <div class="row"><span>{label}</span><strong>{value}{#if suffix}&nbsp;<small class="muted">{suffix}</small>{/if}</strong></div>
  <div class="bar" title={barTitle}>
    <div class="fill" style="width: {Math.min(100, Math.max(0, share))}%"></div>
    {#if tick !== null}<div class="tick" style="left: {tick}%"></div>{/if}
  </div>
  <div class="muted tiny">{note}</div>
  <!-- Always there, so every tile has the same layout: the curve fills the bottom edge of the tile. -->
  <div class="well" style="height: {lineHeight}px">
    {#if line}<StateLine {points} {phases} {live} {from} {to} {gap} height={lineHeight} {label} />{/if}
  </div>
</button>

<style>
  .tile {
    --tn: var(--good);
    display: grid;
    /* It is a button: not centred, and the text of the tile instead of the button's. */
    justify-content: stretch;
    width: 100%;
    gap: 5px;
    /* No padding below: the curve closes the tile. */
    padding: 10px 12px 0;
    overflow: hidden;
    border-radius: 10px;
    color: var(--text);
    font: inherit;
    text-align: left;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-left: 3px solid var(--tn);
  }

  .tile:hover:not(.plain) {
    background: color-mix(in srgb, var(--tn) 8%, var(--surface-2));
  }

  .tile.plain {
    cursor: default;
  }

  .tile.off {
    --tn: var(--muted);
  }

  .tile.locked {
    --tn: var(--value-locked);
  }

  .tile.warn {
    --tn: var(--value-warn);
  }

  .tile.bad {
    --tn: var(--bad);
  }

  .row {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.88rem;
  }

  .row strong {
    color: var(--tn);
    white-space: nowrap;
  }

  .row small {
    font-weight: 600;
  }

  .bar {
    position: relative;
    height: 7px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .fill {
    height: 100%;
    border-radius: 4px;
    background: var(--tn);
  }

  .tick {
    position: absolute;
    top: -2px;
    bottom: -2px;
    width: 2px;
    border-radius: 1px;
    background: var(--value-warn);
  }

  .tiny {
    font-size: 0.72rem;
  }

  /* From edge to edge of the tile, the same height in all of them. */
  .well {
    margin: 3px -12px 0;
    border-top: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    background: color-mix(in srgb, var(--tn) 7%, transparent);
  }
</style>
