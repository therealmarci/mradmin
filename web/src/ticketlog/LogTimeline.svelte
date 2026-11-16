<script>
  // The shared timeline of a ticket log: one bar for the whole span with a cursor - the moment chosen here is the one
  // every tab shows (the map plays there, the chart marks it, the log scrolls to it). Three thin rows of marks:
  // performance (lag), server log (errors, warnings, events) and the player (deaths, teleports, joins). Below play,
  // speed, the time and chips that jump to the next error, lag or death after the cursor.
  //
  // marks: { perf: [{time, tone}], log: [{time, tone}], player: [{time, tone, kind}] }   from/to: span (ms)
  // time: bindable cursor (ms)
  import { onDestroy } from 'svelte';
  import { tl } from './texts.js';

  let { lang, from, to, marks, time = $bindable() } = $props();
  const T = (k, v) => tl(lang, k, v);
  const span = $derived(Math.max(1, to - from));
  const pos = (t) => ((t - from) / span) * 100;
  const clock = (t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  // ---------------------------------------------------------------- playing
  const SPEEDS = [1, 5, 15, 60];
  let speed = $state(5);
  let playing = $state(false);
  let frame = 0;
  let last = 0;
  function step(now) {
    if (!playing) return;
    if (last) time = Math.min(to, time + (now - last) * speed);
    last = now;
    if (time >= to) {
      playing = false;
      return;
    }
    frame = requestAnimationFrame(step);
  }
  function play() {
    if (playing) return pause();
    if (time >= to - 500) time = from;
    playing = true;
    last = 0;
    frame = requestAnimationFrame(step);
  }
  function pause() {
    playing = false;
    cancelAnimationFrame(frame);
  }
  onDestroy(pause);
  const seek = (t) => (time = Math.max(from, Math.min(to, t)));

  // ------------------------------------------------------------- the bar
  let bar = $state();
  let dragging = false;
  const at = (event) => {
    const r = bar.getBoundingClientRect();
    return from + Math.max(0, Math.min(1, (event.clientX - r.left) / r.width)) * span;
  };
  function down(event) {
    dragging = true;
    bar.setPointerCapture(event.pointerId);
    seek(at(event));
  }
  function moveTo(event) {
    if (dragging) seek(at(event));
  }
  function keys(event) {
    const big = event.shiftKey ? 60000 : 5000;
    if (event.key === 'ArrowLeft') seek(time - big);
    else if (event.key === 'ArrowRight') seek(time + big);
    else if (event.key === ' ') play();
    else if (event.key === 'Home') seek(from);
    else if (event.key === 'End') seek(to);
    else return;
    event.preventDefault();
  }

  // performance marks become stretches: neighbouring seconds of the same tone join
  const perfBands = $derived.by(() => {
    const out = [];
    for (const m of [...marks.perf].sort((a, b) => a.time - b.time)) {
      const lastBand = out.at(-1);
      if (lastBand && lastBand.tone === m.tone && m.time - lastBand.to <= 2000) lastBand.to = m.time + 1000;
      else out.push({ from: m.time, to: m.time + 1000, tone: m.tone });
    }
    return out;
  });

  // chips: jump to the next of a kind after the cursor (from the start again at the end)
  const JUMPS = $derived([
    { id: 'error', label: T('jumpError'), tone: 'bad', list: marks.log.filter((m) => m.tone === 'bad') },
    { id: 'warn', label: T('jumpWarn'), tone: 'warn', list: marks.log.filter((m) => m.tone === 'warn') },
    { id: 'lag', label: T('jumpLag'), tone: 'bad', list: perfBands.filter((b) => b.tone === 'bad').map((b) => ({ time: b.from })) },
    { id: 'death', label: T('jumpDeath'), tone: 'bad', list: marks.player.filter((m) => m.kind === 'death') },
    { id: 'event', label: T('jumpEvent'), tone: 'act', list: marks.log.filter((m) => m.tone === 'act') },
  ].filter((j) => j.list.length));
  function jump(j) {
    const sorted = j.list.map((m) => m.time).sort((a, b) => a - b);
    pause();
    seek(sorted.find((t) => t > time + 500) ?? sorted[0]);
  }
</script>

<div class="tline">
  <div class="controls">
    <button class="play" onclick={play} aria-label={T('play')}>{playing ? '❚❚' : '▶'}</button>
    <span class="now">{time >= to ? T('created') : clock(time)}</span>
    <div class="speeds" role="group" aria-label={T('speed')}>
      {#each SPEEDS as s}<button class:on={speed === s} onclick={() => (speed = s)}>{s}×</button>{/each}
    </div>
    <div class="jumps">
      {#each JUMPS as j (j.id)}
        <button class="jump {j.tone}" onclick={() => jump(j)} title={T('jumpHint')}>{j.label} <b>{j.list.length}</b> ›</button>
      {/each}
    </div>
  </div>

  <div class="barwrap">
  <div class="names muted"><span>{T('lanePerf')}</span><span>{T('laneLog')}</span><span>{T('lanePlayer')}</span></div>
  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
  <div class="bar" bind:this={bar} role="slider" tabindex="0" aria-label={T('timeline')} aria-valuemin={from} aria-valuemax={to}
    aria-valuenow={time} aria-valuetext={clock(time)} onpointerdown={down} onpointermove={moveTo} onpointerup={() => (dragging = false)} onkeydown={keys}>
    <div class="row perf">
      {#each perfBands as b}<i class="band {b.tone}" style="left: {pos(b.from)}%; width: {Math.max(0.3, pos(b.to) - pos(b.from))}%"></i>{/each}
    </div>
    <div class="row log">
      {#each marks.log as m}<i class="tick {m.tone}" style="left: {pos(m.time)}%"></i>{/each}
    </div>
    <div class="row player">
      {#each marks.player as m}<i class="tick {m.tone}" class:big={m.kind === 'death'} style="left: {pos(m.time)}%"></i>{/each}
    </div>
    <i class="played" style="width: {pos(time)}%"></i>
    <i class="cursor" style="left: {pos(time)}%"></i>
  </div>
  <span></span>
  <div class="axis muted">
    <span>{clock(from)}</span>
    <span>{T('created')} {clock(to)}</span>
  </div>
  </div>

</div>

<style>
  .tline {
    display: grid;
    gap: 8px;
  }

  .controls {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .play {
    width: 38px;
    height: 38px;
    padding: 0;
    border-radius: 50%;
    font-size: 0.9rem;
  }

  .now {
    min-width: 7.5rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .speeds {
    display: flex;
    gap: 2px;
    padding: 2px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .speeds button {
    padding: 4px 9px;
    font-size: 0.76rem;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--muted);
  }

  .speeds button.on {
    color: var(--text);
    background: var(--surface);
  }

  .jumps {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-left: auto;
  }

  .jump {
    padding: 4px 10px;
    font-size: 0.76rem;
    font-weight: 600;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text);
  }

  .jump b {
    color: var(--muted);
  }

  .jump.bad {
    border-color: color-mix(in srgb, var(--bad) 50%, var(--border));
  }

  .jump.warn {
    border-color: color-mix(in srgb, var(--warn) 50%, var(--border));
  }

  .jump.act {
    border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
  }

  .bar {
    position: relative;
    display: grid;
    grid-template-rows: 1fr 1fr 1fr;
    gap: 3px;
    height: 46px;
    padding: 5px 0;
    border-radius: 8px;
    background: var(--surface-2);
    cursor: pointer;
    touch-action: none;
    user-select: none;
  }

  .bar:focus-visible {
    outline: 2px solid var(--accent);
  }

  .row {
    position: relative;
    margin: 0 6px;
  }

  .row::before {
    content: '';
    position: absolute;
    inset: 45% 0 auto;
    height: 1px;
    background: color-mix(in srgb, var(--border) 70%, transparent);
  }

  .band,
  .tick {
    position: absolute;
    top: 0;
    bottom: 0;
    border-radius: 2px;
  }

  .band {
    opacity: 0.85;
  }

  .tick {
    width: 2px;
    margin-left: -1px;
    background: color-mix(in srgb, var(--muted) 60%, transparent);
  }

  .tick.big {
    width: 4px;
    margin-left: -2px;
  }

  .band.bad,
  .tick.bad {
    background: var(--bad);
  }

  .band.warn,
  .tick.warn {
    background: var(--warn);
  }

  .tick.act {
    background: var(--accent);
  }

  .played {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    border-radius: 8px 0 0 8px;
    background: color-mix(in srgb, var(--accent) 10%, transparent);
    pointer-events: none;
  }

  .cursor {
    position: absolute;
    top: -3px;
    bottom: -3px;
    width: 3px;
    margin-left: -1.5px;
    border-radius: 2px;
    background: var(--text);
    box-shadow: 0 0 0 2px var(--surface);
    pointer-events: none;
  }

  .barwrap {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    gap: 4px 8px;
  }

  .names {
    display: grid;
    grid-template-rows: 1fr 1fr 1fr;
    gap: 3px;
    padding: 5px 0;
    font-size: 0.68rem;
    line-height: 1;
    align-items: center;
  }

  .axis {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.72rem;
  }

  @media (max-width: 560px) {
    .jumps {
      margin-left: 0;
    }
  }
</style>
