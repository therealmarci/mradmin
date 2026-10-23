<script>
  // Health like in Minecraft: one heart = 2 points, half hearts, golden hearts for absorption. "hurt" makes the row
  // shake for a moment (the player was just hit).
  let { health = 0, max = 20, absorption = 0, hurt = false, size = 16 } = $props();

  const hearts = $derived.by(() => {
    const list = [];
    const count = Math.ceil((max || 20) / 2);
    for (let i = 0; i < count; i++) {
      const rest = (health ?? 0) - i * 2;
      list.push(rest >= 2 ? 'full' : rest >= 1 ? 'half' : 'empty');
    }
    const golden = Math.ceil((absorption ?? 0) / 2);
    for (let i = 0; i < golden; i++) {
      const rest = absorption - i * 2;
      list.push(rest >= 2 ? 'gold' : 'gold-half');
    }
    return list;
  });

  const PATH = 'M8 14.5 1.6 8.2A3.9 3.9 0 0 1 8 3.4a3.9 3.9 0 0 1 6.4 4.8Z';
</script>

<div class="hearts" class:hurt title="{Math.round(((health ?? 0) / 2) * 10) / 10} ❤">
  {#each hearts as kind, i (i)}
    <svg viewBox="0 0 16 16" width={size} height={size} class={kind} aria-hidden="true">
      <defs>
        <clipPath id="half-{i}"><rect x="0" y="0" width="8" height="16" /></clipPath>
      </defs>
      <path d={PATH} class="back" />
      {#if kind === 'full' || kind === 'gold'}
        <path d={PATH} class="fill" />
      {:else if kind === 'half' || kind === 'gold-half'}
        <path d={PATH} class="fill" clip-path="url(#half-{i})" />
      {/if}
    </svg>
  {/each}
</div>

<style>
  .hearts {
    display: flex;
    flex-wrap: wrap;
    gap: 1px;
  }

  .back {
    fill: var(--surface-2);
    stroke: rgba(0, 0, 0, 0.45);
    stroke-width: 1.2;
  }

  .fill {
    fill: #e5484d;
  }

  .gold .fill,
  .gold-half .fill {
    fill: #f5c542;
  }

  .hurt {
    animation: shake 0.35s;
  }

  .hurt .fill {
    fill: #ff8080;
  }

  @keyframes shake {
    0%, 100% { transform: translateX(0); }
    25% { transform: translateX(-2px); }
    75% { transform: translateX(2px); }
  }
</style>
