<script>
  // Tile of one plugin area on the start page of the player area. Built from the area's summary (SelfBlocks): the
  // first key figure big, the others below - values like "12 / 49" as a bar -, texts (e.g. a ban) as a line. Each
  // plugin gets its own colour from its id, its icon as a large watermark.
  import { t } from '../lib/i18n.js';

  let { area, onclick } = $props();

  const stats = $derived(area.summary.filter((b) => b.type === 'stats').flatMap((b) => b.stats));
  const texts = $derived(area.summary.filter((b) => b.type === 'text'));
  const main = $derived(stats[0]);
  const rest = $derived(stats.slice(1));

  // A stable hue per plugin, so a plugin always has the same colour.
  const hue = $derived([...area.module].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7));

  // "12 / 49" or "120 / 245 Chunks" -> fill of the bar (0..1); everything else -> null
  function meter(value) {
    const m = /^\s*(\d[\d.,]*)\s*\/\s*(\d[\d.,]*)/.exec(value ?? '');
    if (!m) return null;
    const num = (s) => Number(s.replace(/[.,]/g, ''));
    const max = num(m[2]);
    return max > 0 ? Math.min(1, num(m[1]) / max) : null;
  }
</script>

<button class="tile" style="--hue: {hue}" {onclick}>
  <span class="watermark" aria-hidden="true">{area.icon}</span>
  <span class="head">
    <span class="icon" aria-hidden="true">{area.icon}</span>
    <span class="title">{area.title}</span>
    <span class="arrow" aria-hidden="true">→</span>
  </span>

  {#if main}
    <span class="main">
      <strong class="{main.tone}-text">{main.value}</strong>
      <span class="muted">{main.label}</span>
    </span>
  {/if}

  {#if rest.length}
    <span class="rest">
      {#each rest as s}
        {@const fill = meter(s.value)}
        <span class="stat">
          <span class="line"><span class="muted">{s.label}</span><b class="{s.tone}-text">{s.value}</b></span>
          {#if fill !== null}
            <span class="bar {s.tone}" class:full={fill >= 1}><span style="width: {fill * 100}%"></span></span>
          {/if}
        </span>
      {/each}
    </span>
  {/if}

  {#each texts as x}
    <span class="note {x.tone}-text">{x.text}</span>
  {/each}

  <span class="open">{t('self.open')}</span>
</button>

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

  .tile:hover {
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
    right: -10px;
    bottom: -22px;
    font-size: 7.5rem;
    line-height: 1;
    opacity: 0.07;
    transform: rotate(-12deg);
    pointer-events: none;
    transition: transform 0.3s;
  }

  .tile:hover .watermark {
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
    font-size: 1.4rem;
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
  }

  .main strong {
    font-size: 2.4rem;
    line-height: 1;
    font-weight: 800;
    letter-spacing: -0.02em;
  }

  .rest {
    display: grid;
    gap: 10px;
    position: relative;
  }

  .stat {
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

  .bar.full span,
  .bar.warn span {
    background: var(--warn);
  }

  .bar.bad span {
    background: var(--bad);
  }

  .note {
    font-size: 0.85rem;
    font-weight: 600;
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
