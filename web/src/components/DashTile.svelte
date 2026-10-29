<script>
  // A tile on the team panel's start page, in the look of the player area's tiles: symbol and title on top, the main
  // value big (in its tone), below it the facts as lines - with a bar when there is a fill -, the link at the bottom.
  // Each tile has its colour (hue) and its symbol as a large watermark.
  import { ICONS } from '../lib/icons.js';

  /**
   * icon: key of ICONS; hue: 0..359; title; value, unit, tone (good/warn/bad/info or ''); small: main value as a
   * shorter text ("Keine Wartung geplant"); rows: [{label, value, tone?, fill? 0..1}]; note: a line below;
   * link: text at the bottom; onclick: null = only shows.
   */
  let { icon, hue, title, value, unit = '', tone = '', small = false, rows = [], note = '', link = '', onclick = null } = $props();
</script>

<svelte:element this={onclick ? 'button' : 'div'} class="tile" class:static={!onclick} style="--hue: {hue}" {onclick}
  role={onclick ? undefined : 'group'}>
  <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[icon] ?? ICONS.box} /></svg>
  <span class="head">
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={ICONS[icon] ?? ICONS.box} /></svg>
    </span>
    <span class="title">{title}</span>
    {#if onclick}<span class="arrow" aria-hidden="true">→</span>{/if}
  </span>

  <span class="main" class:small>
    <strong class="{tone}-text">{value}</strong>
    {#if unit}<span class="muted">{unit}</span>{/if}
  </span>

  {#if rows.length}
    <span class="rows">
      {#each rows as r}
        <span class="row">
          <span class="line"><span class="muted">{r.label}</span><b class="{r.tone ?? ''}-text">{r.value}</b></span>
          {#if r.fill !== undefined && r.fill !== null}
            <span class="bar {r.tone ?? ''}"><span style="width: {Math.max(0, Math.min(1, r.fill)) * 100}%"></span></span>
          {/if}
        </span>
      {/each}
    </span>
  {/if}

  {#if note}<span class="note">{note}</span>{/if}

  {#if link}<span class="open">{link}</span>{/if}
</svelte:element>

<style>
  .tile {
    --tint: hsl(var(--hue) 70% 55%);
    position: relative;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 18px 20px 16px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tint) 28%, var(--border));
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--tint) 20%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
    transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
    justify-content: flex-start;
    align-items: stretch;
  }

  .tile:not(.static):hover {
    filter: none;
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--tint) 60%, var(--border));
    box-shadow: 0 10px 28px -12px color-mix(in srgb, var(--tint) 55%, transparent), var(--shadow);
  }

  .tile:focus-visible {
    outline: 2px solid var(--tint);
    outline-offset: 2px;
  }

  .watermark {
    position: absolute;
    right: -14px;
    bottom: -20px;
    width: 120px;
    height: 120px;
    color: var(--tint);
    opacity: 0.09;
    transform: rotate(-12deg);
    pointer-events: none;
    transition: transform 0.3s;
  }

  .tile:not(.static):hover .watermark {
    transform: rotate(-4deg) scale(1.06);
  }

  .head {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    flex-shrink: 0;
    border-radius: 12px;
    color: var(--tint);
    background: color-mix(in srgb, var(--tint) 18%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--tint) 35%, transparent);
  }

  .title {
    flex: 1;
    min-width: 0;
    font-weight: 800;
    font-size: 1.1rem;
  }

  .arrow {
    color: var(--tint);
    font-weight: 800;
    transition: transform 0.15s;
  }

  .tile:hover .arrow {
    transform: translateX(4px);
  }

  .main {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 4px 10px;
    min-height: 2.4rem;
  }

  .main strong {
    font-size: 2.4rem;
    line-height: 1;
    font-weight: 800;
    letter-spacing: -0.02em;
  }

  .main.small {
    align-items: center;
  }

  .main.small strong {
    font-size: 1.35rem;
    line-height: 1.2;
    letter-spacing: 0;
  }

  .rows {
    display: grid;
    gap: 10px;
    position: relative;
  }

  .row {
    display: grid;
    gap: 5px;
  }

  .line {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.9rem;
  }

  .line b {
    font-weight: 700;
    text-align: right;
    overflow-wrap: anywhere;
  }

  .bar {
    height: 6px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--tint);
    transition: width 0.4s;
  }

  .bar.warn span {
    background: var(--warn);
  }

  .bar.bad span {
    background: var(--bad);
  }

  .note {
    position: relative;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--muted);
  }

  .open {
    margin-top: auto;
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--tint);
  }

  .good-text {
    color: var(--good);
  }

  .warn-text {
    color: var(--warn);
  }

  .bad-text {
    color: var(--bad);
  }

  .info-text {
    color: var(--accent);
  }

  @media (prefers-reduced-motion: reduce) {
    .tile,
    .watermark,
    .arrow {
      transition: none;
    }
  }
</style>
