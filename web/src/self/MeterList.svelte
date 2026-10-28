<script>
  // Bars of a plugin (SelfBlock.Meter): label and value, the bar (fill 0..1) with an optional mark (e.g. where the
  // warning starts) and a small hint below. Colour from the tone, otherwise the plugin's colour (--tint).
  let { meters, compact = false } = $props();
</script>

<div class="meters" class:compact>
  {#each meters as m, i (i)}
    <div class="meter">
      <div class="line"><span class="label">{m.label}</span><b class="{m.tone}-text">{m.value}</b></div>
      <div class="bar">
        <span class="fill {m.tone}" style="width: {m.fill * 100}%"></span>
        {#if m.mark >= 0}<span class="mark" style="left: {m.mark * 100}%"></span>{/if}
      </div>
      {#if m.hint && !compact}<div class="hint">{m.hint}</div>{/if}
    </div>
  {/each}
</div>

<style>
  .meters {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .meters.compact {
    gap: 8px;
    grid-template-columns: 1fr;
  }

  .meter {
    display: grid;
    gap: 5px;
    min-width: 0;
  }

  .line {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.88rem;
  }

  .label {
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .bar {
    position: relative;
    height: 7px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .compact .bar {
    height: 5px;
  }

  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    border-radius: 4px;
    background: var(--tint, var(--accent));
    transition: width 0.4s;
  }

  .fill.good {
    background: var(--good);
  }

  .fill.warn {
    background: var(--warn);
  }

  .fill.bad {
    background: var(--bad);
  }

  .fill.info {
    background: var(--accent);
  }

  .mark {
    position: absolute;
    top: -3px;
    bottom: -3px;
    width: 2px;
    margin-left: -1px;
    border-radius: 1px;
    background: var(--text);
    opacity: 0.55;
  }

  .hint {
    font-size: 0.75rem;
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

  .info-text {
    color: var(--accent);
  }
</style>
