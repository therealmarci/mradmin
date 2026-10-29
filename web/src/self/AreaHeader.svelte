<script>
  // Head of a plugin's area page in the look of its tile on the start page: icon and title, the first key figure big,
  // the others beside it (values like "120 / 245" as bars), warnings of the summary as lines below.
  let { area } = $props();

  const stats = $derived((area.summary ?? []).filter((b) => b.type === 'stats').flatMap((b) => b.stats));
  const notes = $derived((area.summary ?? []).filter((b) => b.type === 'text'));
  const main = $derived(stats[0]);
  const rest = $derived(stats.slice(1));

  function meter(value) {
    const m = /^\s*(\d[\d.,]*)\s*\/\s*(\d[\d.,]*)/.exec(value ?? '');
    if (!m) return null;
    const num = (s) => Number(s.replace(/[.,]/g, ''));
    const max = num(m[2]);
    return max > 0 ? Math.min(1, num(m[1]) / max) : null;
  }
</script>

<header class="head">
  <span class="watermark" aria-hidden="true">{area.icon}</span>
  <div class="intro">
    <span class="icon" aria-hidden="true">{area.icon}</span>
    <div>
      <h1>{area.title}</h1>
      {#if main}
        <div class="main"><strong class="{main.tone}-text">{main.value}</strong> <span class="muted">{main.label}</span></div>
      {/if}
    </div>
  </div>
  {#if rest.length}
    <div class="rest">
      {#each rest as s}
        {@const fill = meter(s.value)}
        <div class="stat">
          <div class="line"><span class="muted">{s.label}</span><b class="{s.tone}-text">{s.value}</b></div>
          {#if fill !== null}
            <div class="bar {s.tone}" class:full={fill >= 1}><span style="width: {fill * 100}%"></span></div>
          {/if}
        </div>
      {/each}
    </div>
  {/if}
  {#if notes.length}
    <div class="notes">
      {#each notes as n}<span class="note {n.tone}">{n.text}</span>{/each}
    </div>
  {/if}
</header>

<style>
  .head {
    position: relative;
    overflow: hidden;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
    gap: 18px 32px;
    align-items: center;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--tint) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tint) 22%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -14px;
    bottom: -34px;
    font-size: 9rem;
    line-height: 1;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    font-size: 1.8rem;
    background: color-mix(in srgb, var(--tint) 18%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--tint) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .main {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 2px;
  }

  .main strong {
    font-size: 2rem;
    line-height: 1.1;
    font-weight: 800;
  }

  .rest {
    display: grid;
    gap: 12px 20px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 170px), 1fr));
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
  }

  .bar.full span,
  .bar.warn span {
    background: var(--warn);
  }

  .bar.bad span {
    background: var(--bad);
  }

  .notes {
    grid-column: 1 / -1;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    position: relative;
  }

  .note {
    font-size: 0.85rem;
    font-weight: 600;
    padding: 6px 12px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .note.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  .note.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 13%, transparent);
  }

  .muted {
    color: var(--muted);
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

  @media (max-width: 720px) {
    .head {
      grid-template-columns: 1fr;
      padding: 18px 16px;
    }
  }
</style>
