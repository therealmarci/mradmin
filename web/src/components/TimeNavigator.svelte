<script>
  // The whole stored time at one glance, with the part the chart shows marked: a click or dragging jumps there at once,
  // however small the part is. Scrolling in the chart only moves in steps of what it shows.
  import StateLine from './StateLine.svelte';
  import { dateTime } from '../lib/format.js';

  /** from/to: all of the time; viewFrom/viewTo: the part shown; points/phases/gap: see StateLine; onjump(ms): new middle. */
  let { from, to, viewFrom, viewTo, points = [], phases = [], gap = null, label = '', onjump } = $props();

  let dragging = false;
  const percent = (ms) => Math.min(100, Math.max(0, ((ms - from) / Math.max(1, to - from)) * 100));
  const left = $derived(percent(viewFrom));
  // A minute in twelve hours would be invisible.
  const width = $derived(Math.max(1.5, percent(viewTo) - left));

  function jump(event) {
    const rect = event.currentTarget.getBoundingClientRect();
    onjump(from + Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width)) * (to - from));
  }

  function press(event) {
    dragging = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    jump(event);
  }

  function keys(event) {
    const step = (to - from) / 20;
    const middle = (viewFrom + viewTo) / 2;
    if (event.key === 'ArrowLeft') onjump(middle - step);
    else if (event.key === 'ArrowRight') onjump(middle + step);
    else return;
    event.preventDefault();
  }
</script>

<div class="navigator">
  <div class="strip" role="slider" tabindex="0" aria-label={label} aria-valuemin={from} aria-valuemax={to}
    aria-valuenow={viewTo} onpointerdown={press} onpointermove={(e) => dragging && jump(e)}
    onpointerup={() => (dragging = false)} onpointercancel={() => (dragging = false)} onkeydown={keys}>
    <StateLine {points} {phases} {from} {to} {gap} height={40} {label} />
    <span class="part" style="left: {left}%; width: {width}%"></span>
  </div>
  <div class="ends muted"><span>{dateTime(from)}</span><span>{dateTime(to)}</span></div>
</div>

<style>
  .navigator {
    display: grid;
    gap: 3px;
    padding: 10px 16px;
    border-radius: 14px;
    background: var(--surface);
  }

  .strip {
    position: relative;
    height: 40px;
    overflow: hidden;
    border-radius: 8px;
    background: var(--surface-2);
    cursor: pointer;
    touch-action: none;
    user-select: none;
  }

  .part {
    position: absolute;
    top: 0;
    bottom: 0;
    pointer-events: none;
    background: color-mix(in srgb, var(--text) 14%, transparent);
    border-left: 2px solid var(--text);
    border-right: 2px solid var(--text);
  }

  .ends {
    display: flex;
    justify-content: space-between;
    font-size: 0.72rem;
  }
</style>
