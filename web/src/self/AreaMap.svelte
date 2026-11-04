<script>
  // A small map of areas (e.g. a chunk loader and what is around it): one drawing per world, each area as a coloured
  // rectangle with its name. Where squaremap renders the world, its pictures lie underneath (the same ones as on the
  // tab "Map"); otherwise the chunk grid on a plain ground. A click on an area with a link opens it (e.g. on the big
  // map), the button below as well.
  import { t } from '../lib/i18n.js';
  import { api } from '../lib/api.js';

  // flat: inside an opened card or a group (no frame of its own).
  let { block, flat = false } = $props();

  const worlds = $derived([...new Set(block.rects.map((r) => r.world))]);
  let world = $state(null);
  const current = $derived(world && worlds.includes(world) ? world : worlds[0]);
  const rects = $derived(block.rects.filter((r) => r.world === current));

  // The worlds squaremap renders - asked once per page, shared by all small maps.
  let mapWorlds = $state([]);
  $effect(() => {
    loadWorlds().then((list) => (mapWorlds = list));
  });
  const tileWorld = $derived(mapWorlds.find((w) => w.key === current) ?? null);

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

  // squaremap's pictures at full detail (one pixel per block, 512 blocks per picture) covering the section.
  const TILE = 512;
  const tiles = $derived.by(() => {
    if (!tileWorld) return [];
    const list = [];
    for (let tx = Math.floor(view.x / TILE); tx <= Math.floor((view.x + view.w) / TILE); tx++) {
      for (let tz = Math.floor(view.z / TILE); tz <= Math.floor((view.z + view.h) / TILE); tz++) {
        list.push({ x: tx * TILE, z: tz * TILE, src: `/api/self/map/tiles/${tileWorld.folder}/${tileWorld.zoomMax}/${tx}_${tz}.png` });
      }
    }
    return list;
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

  // Links to MRadmin itself (the big map) open in the same tab, others in a new one.
  const own = (url) => {
    try {
      return new URL(url, location.href).origin === location.origin;
    } catch {
      return false;
    }
  };

  function open(url) {
    if (own(url)) location.href = url;
    else window.open(url, '_blank', 'noopener');
  }
</script>

<script module>
  let pending = null;

  /** The worlds with pictures, once per page load; none without a map (or an older MRadmin page). */
  function loadWorlds() {
    pending ??= api('GET', '/self/map/worlds').then((r) => r.worlds ?? []).catch(() => []);
    return pending;
  }
</script>

<section class="card" class:flat>
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
    <svg viewBox="{view.x} {view.z} {view.w} {view.h}" role="img" aria-label={block.title} class:terrain={tileWorld}>
      <rect x={view.x} y={view.z} width={view.w} height={view.h} class="ground" />
      {#each tiles as tile (tile.src)}
        <image href={tile.src} x={tile.x} y={tile.z} width={TILE} height={TILE} preserveAspectRatio="none" class="tile" />
      {/each}
      {#each grid as l}<line x1={l.x1} y1={l.z1} x2={l.x2} y2={l.z2} class="grid" />{/each}
      {#each rects as r}
        <a href={r.link ?? undefined} onclick={(e) => { if (r.link) { e.preventDefault(); open(r.link); } }}>
          <rect x={r.minX} y={r.minZ} width={r.maxX - r.minX} height={r.maxZ - r.minZ} fill={r.color} stroke={r.color} class="area">
            <title>{r.label}</title>
          </rect>
          <text x={(r.minX + r.maxX) / 2} y={(r.minZ + r.maxZ) / 2} font-size={label(r)} class="name">{r.label}</text>
        </a>
      {/each}
    </svg>
    {#if !tileWorld}<p class="muted small">{t('self.mapHint')}</p>{/if}
  {/if}
  {#if block.link}
    <button class="secondary small open" onclick={() => open(block.link)}>{block.linkLabel}</button>
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

  .tile {
    image-rendering: pixelated;
  }

  .grid {
    stroke: var(--border);
    stroke-width: 0.5;
    vector-effect: non-scaling-stroke;
  }

  /* Over the terrain the grid is a faint light line. */
  .terrain .grid {
    stroke: rgb(255 255 255 / 0.18);
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

  /* Over the terrain the name stays readable: white with a dark edge. */
  .terrain .name {
    fill: #fff;
    stroke: rgb(0 0 0 / 0.65);
    stroke-width: 3px;
    paint-order: stroke;
    vector-effect: non-scaling-stroke;
  }

  .open {
    margin-top: 10px;
  }

  p {
    margin: 8px 0 0;
  }

  /* Inside an opened card or a group: no frame of its own, a line above instead, a smaller heading. */
  .flat {
    padding: 16px 0 0;
    border: 0;
    border-top: 1px solid var(--border);
    border-radius: 0;
    box-shadow: none;
    background: transparent;
  }

  .flat h2 {
    font-size: 0.95rem;
  }

  .flat svg {
    max-height: 320px;
  }
</style>
