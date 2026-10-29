<script>
  // A small map of areas (e.g. chunk loaders) without terrain: one drawing per world, the chunk grid in the
  // background, each area as a coloured rectangle with its name. A click on an area with a link opens it (e.g. on
  // squaremap), the button below opens the whole map.
  import { t } from '../lib/i18n.js';

  let { block } = $props();

  const worlds = $derived([...new Set(block.rects.map((r) => r.world))]);
  let world = $state(null);
  const current = $derived(world && worlds.includes(world) ? world : worlds[0]);
  const rects = $derived(block.rects.filter((r) => r.world === current));

  // Visible section: all areas plus a margin of two chunks, at least 8 chunks wide.
  const view = $derived.by(() => {
    if (!rects.length) return { x: 0, z: 0, w: 128, h: 128 };
    let minX = Math.min(...rects.map((r) => r.minX)) - 32;
    let minZ = Math.min(...rects.map((r) => r.minZ)) - 32;
    let maxX = Math.max(...rects.map((r) => r.maxX)) + 32;
    let maxZ = Math.max(...rects.map((r) => r.maxZ)) + 32;
    const size = Math.max(maxX - minX, maxZ - minZ, 128);
    const cx = (minX + maxX) / 2;
    const cz = (minZ + maxZ) / 2;
    return { x: cx - size / 2, z: cz - size / 2, w: size, h: size };
  });

  // Chunk lines only while they are not too dense.
  const grid = $derived.by(() => {
    const lines = [];
    if (view.w / 16 > 96) return lines;
    for (let x = Math.ceil(view.x / 16) * 16; x <= view.x + view.w; x += 16) lines.push({ x1: x, z1: view.z, x2: x, z2: view.z + view.h });
    for (let z = Math.ceil(view.z / 16) * 16; z <= view.z + view.h; z += 16) lines.push({ x1: view.x, z1: z, x2: view.x + view.w, z2: z });
    return lines;
  });

  const label = (r) => Math.max(2, Math.min(r.maxX - r.minX, r.maxZ - r.minZ) / 4);
</script>

<section class="card">
  <div class="head">
    {#if block.title}<h2>{block.title}</h2>{/if}
    {#if worlds.length > 1}
      <select bind:value={world} aria-label={t('self.world')}>
        {#each worlds as w}<option value={w} selected={w === current}>{w}</option>{/each}
      </select>
    {/if}
  </div>
  {#if rects.length === 0}
    <p class="muted">{t('self.mapEmpty')}</p>
  {:else}
    <svg viewBox="{view.x} {view.z} {view.w} {view.h}" role="img" aria-label={block.title}>
      <rect x={view.x} y={view.z} width={view.w} height={view.h} class="ground" />
      {#each grid as l}<line x1={l.x1} y1={l.z1} x2={l.x2} y2={l.z2} class="grid" />{/each}
      {#each rects as r}
        <a href={r.link ?? undefined} target="_blank" rel="noopener noreferrer">
          <rect x={r.minX} y={r.minZ} width={r.maxX - r.minX} height={r.maxZ - r.minZ} fill={r.color} stroke={r.color} class="area">
            <title>{r.label}</title>
          </rect>
          <text x={(r.minX + r.maxX) / 2} y={(r.minZ + r.maxZ) / 2} font-size={label(r)} class="name">{r.label}</text>
        </a>
      {/each}
    </svg>
    <p class="muted small">{t('self.mapHint')}</p>
  {/if}
  {#if block.link}
    <button class="secondary small open" onclick={() => window.open(block.link, '_blank', 'noopener')}>{block.linkLabel}</button>
  {/if}
</section>

<style>
  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  svg {
    width: 100%;
    max-height: 520px;
    border-radius: 10px;
    display: block;
  }

  .ground {
    fill: var(--surface-2);
  }

  .grid {
    stroke: var(--border);
    stroke-width: 0.5;
    vector-effect: non-scaling-stroke;
  }

  .area {
    fill-opacity: 0.3;
    stroke-width: 2;
    vector-effect: non-scaling-stroke;
  }

  .name {
    fill: var(--text);
    text-anchor: middle;
    dominant-baseline: middle;
    font-weight: 700;
    pointer-events: none;
  }

  .open {
    margin-top: 10px;
  }

  p {
    margin: 8px 0 0;
  }
</style>
