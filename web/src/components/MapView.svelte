<script>
  // A small map of our own over the pictures squaremap renders (512 px per tile, at the highest zoom 1 block per
  // pixel; "extra" levels enlarge further). Drag to move, wheel or +/- to zoom, click on a marker for its details,
  // right-click (long press on phones) for actions - on a marker its own, on a free spot those "here" of the layers.
  // The page gives worlds and layers and runs the actions (onaction); this component only draws and asks.
  import { untrack } from 'svelte';
  import { t } from '../lib/i18n.js';

  let { worlds = [], layers = [], tileUrl, onaction, storageKey = 'mradmin.map', initial = null } = $props();

  const TILE = 512;

  /** Remembered per browser: world, view and the layers switched off. */
  function remembered() {
    try {
      return JSON.parse(localStorage.getItem(storageKey) ?? '{}');
    } catch {
      return {};
    }
  }
  const saved = remembered();

  // A link to a place (e.g. a chunk loader in game: ?world=...&x=...&z=...) wins over what the browser remembers.
  // Only the place at the start counts, later changes of the prop do not move the map.
  const start = untrack(() => initial);
  let worldKey = $state(start?.world ?? saved.world ?? null);
  let zoom = $state(start ? 99 : saved.zoom ?? null);
  let cx = $state(start?.x ?? saved.x ?? null);
  let cz = $state(start?.z ?? saved.z ?? null);
  let hidden = $state(new Set(saved.hidden ?? []));
  let width = $state(600);
  let height = $state(420);
  let box = $state(null);
  let cursor = $state(null);
  let menu = $state(null);
  let selected = $state(null);
  let busy = $state(false);
  let broken = $state(new Set());

  const world = $derived(worlds.find((w) => w.key === worldKey) ?? worlds[0] ?? null);

  // Zoom of a link (99 = "as close as useful") or of the browser kept within what the world offers.
  $effect(() => {
    if (world && zoom !== null && zoom > world.zoomMax + world.zoomExtra) {
      zoom = world.zoomMax;
    }
  });

  // A world that is gone, or the first visit: start at its spawn with its default zoom.
  $effect(() => {
    if (!world) return;
    if (worldKey !== world.key || zoom === null || cx === null) {
      worldKey = world.key;
      zoom = world.zoomDefault;
      cx = world.spawnX;
      cz = world.spawnZ;
    }
  });

  $effect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ world: worldKey, zoom, x: cx, z: cz, hidden: [...hidden] }));
    } catch {
      // private mode
    }
  });

  $effect(() => {
    if (!box) return;
    const observer = new ResizeObserver(() => {
      width = box.clientWidth;
      height = box.clientHeight;
    });
    observer.observe(box);
    return () => observer.disconnect();
  });

  const maxZoom = $derived(world ? world.zoomMax + world.zoomExtra : 0);
  /** Screen pixels per block. */
  const ppb = $derived(world && zoom !== null ? Math.pow(2, zoom - world.zoomMax) : 1);
  const tileZoom = $derived(world ? Math.min(zoom ?? 0, world.zoomMax) : 0);
  const tileBlocks = $derived(world ? TILE * Math.pow(2, world.zoomMax - tileZoom) : TILE);

  const sx = (x) => width / 2 + (x - cx) * ppb;
  const sz = (z) => height / 2 + (z - cz) * ppb;

  const tiles = $derived.by(() => {
    if (!world || cx === null) return [];
    const half = { x: width / 2 / ppb, z: height / 2 / ppb };
    const list = [];
    const x0 = Math.floor((cx - half.x) / tileBlocks);
    const x1 = Math.floor((cx + half.x) / tileBlocks);
    const z0 = Math.floor((cz - half.z) / tileBlocks);
    const z1 = Math.floor((cz + half.z) / tileBlocks);
    for (let tx = x0; tx <= x1; tx++) {
      for (let tz = z0; tz <= z1; tz++) {
        const src = tileUrl(world.folder, tileZoom, tx, tz);
        list.push({ src, left: sx(tx * tileBlocks), top: sz(tz * tileBlocks), size: tileBlocks * ppb });
      }
    }
    return list;
  });

  const layerKey = (l) => l.module + ':' + l.id;

  const markers = $derived(
    layers
      .filter((l) => !hidden.has(layerKey(l)))
      .flatMap((l) => l.markers.filter((m) => m.world === worldKey).map((m) => ({ ...m, layer: l, color: m.color || l.color }))),
  );

  // ---------------------------------------------------------------- moving

  let drag = null;

  function down(e) {
    if (e.button !== 0) return;
    menu = null;
    drag = { x: e.clientX, y: e.clientY, cx, cz, moved: false };
    e.currentTarget.setPointerCapture(e.pointerId);
  }

  function move(e) {
    const rect = box.getBoundingClientRect();
    cursor = { x: Math.floor(cx + (e.clientX - rect.left - width / 2) / ppb), z: Math.floor(cz + (e.clientY - rect.top - height / 2) / ppb) };
    if (!drag) return;
    const dx = e.clientX - drag.x;
    const dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
    cx = drag.cx - dx / ppb;
    cz = drag.cz - dy / ppb;
  }

  function up(e) {
    const wasClick = drag && !drag.moved;
    drag = null;
    if (wasClick && e.target === e.currentTarget) {
      selected = null;
    }
  }

  /** Zoom one step, keeping the block under the mouse where it is. */
  function zoomBy(step, clientX, clientY) {
    if (!world) return;
    const next = Math.max(0, Math.min(maxZoom, zoom + step));
    if (next === zoom) return;
    const rect = box.getBoundingClientRect();
    const px = clientX === undefined ? width / 2 : clientX - rect.left;
    const pz = clientY === undefined ? height / 2 : clientY - rect.top;
    const bx = cx + (px - width / 2) / ppb;
    const bz = cz + (pz - height / 2) / ppb;
    const nextPpb = Math.pow(2, next - world.zoomMax);
    cx = bx - (px - width / 2) / nextPpb;
    cz = bz - (pz - height / 2) / nextPpb;
    zoom = next;
  }

  function wheel(e) {
    e.preventDefault();
    zoomBy(e.deltaY < 0 ? 1 : -1, e.clientX, e.clientY);
  }

  // ---------------------------------------------------------------- menus

  function context(e, marker) {
    e.preventDefault();
    e.stopPropagation();
    const rect = box.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const pz = e.clientY - rect.top;
    if (marker) {
      if (!marker.actions.length) return;
      menu = { px, pz, title: marker.label, groups: [{ layer: marker.layer, actions: marker.actions, args: { marker: marker.id } }] };
      return;
    }
    const x = Math.floor(cx + (px - width / 2) / ppb);
    const z = Math.floor(cz + (pz - height / 2) / ppb);
    const groups = layers
      .filter((l) => l.here.length)
      .map((l) => ({ layer: l, actions: l.here, args: { world: worldKey, x: String(x), z: String(z) } }));
    if (!groups.length) return;
    menu = { px, pz, title: `${x} · ${z}`, groups };
  }

  async function run(layer, action, args) {
    menu = null;
    if (action.confirm && !confirm(action.confirm)) return;
    const all = { ...args };
    if (action.input) {
      const value = prompt(action.input.label, action.input.value ?? '');
      if (value === null) return;
      all.value = value.slice(0, action.input.maxLength || 200);
    }
    busy = true;
    try {
      await onaction(layer.module, layer.id, action.id, all);
      selected = null;
    } finally {
      busy = false;
    }
  }

  function toggleLayer(l) {
    const next = new Set(hidden);
    const key = layerKey(l);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    hidden = next;
  }

  function toSpawn() {
    if (!world) return;
    cx = world.spawnX;
    cz = world.spawnZ;
    zoom = world.zoomDefault;
  }
</script>

<div class="mapview">
  <div class="bar">
    {#if worlds.length > 1}
      <select value={worldKey} onchange={(e) => { worldKey = e.currentTarget.value; zoom = null; }}>
        {#each worlds as w (w.key)}<option value={w.key}>{w.name}</option>{/each}
      </select>
    {/if}
    <div class="zoom">
      <button class="secondary" title={t('map.zoomOut')} disabled={zoom <= 0} onclick={() => zoomBy(-1)}>−</button>
      <button class="secondary" title={t('map.zoomIn')} disabled={zoom >= maxZoom} onclick={() => zoomBy(1)}>+</button>
      <button class="secondary" onclick={toSpawn}>{t('map.spawn')}</button>
    </div>
    {#if cursor}<span class="coords">X {cursor.x} · Z {cursor.z}</span>{/if}
  </div>

  <div class="frame">
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="map" bind:this={box} onpointerdown={down} onpointermove={move} onpointerup={up}
      onpointerleave={() => (cursor = null)} onwheel={wheel} oncontextmenu={(e) => context(e, null)}>
      <div class="layer-tiles">
        {#each tiles as tile (tile.src)}
          {#if !broken.has(tile.src)}
            <img class="tile" class:pixel={zoom > world.zoomMax} src={tile.src} alt="" draggable="false"
              style="left:{tile.left}px;top:{tile.top}px;width:{tile.size}px;height:{tile.size}px"
              onerror={() => (broken = new Set([...broken, tile.src]))} />
          {/if}
        {/each}
      </div>
      {#each markers as m (m.layer.module + m.layer.id + m.id)}
        {#if m.width > 0}
          <button class="area" style="--c:{m.color};left:{sx(m.x)}px;top:{sz(m.z)}px;width:{Math.max(4, m.width * ppb)}px;height:{Math.max(4, m.length * ppb)}px"
            title={m.label} onpointerdown={(e) => e.stopPropagation()} onclick={() => (selected = m)}
            oncontextmenu={(e) => context(e, m)}></button>
        {:else}
          <button class="point" style="--c:{m.color};left:{sx(m.x + 0.5)}px;top:{sz(m.z + 0.5)}px" title={m.label}
            onpointerdown={(e) => e.stopPropagation()} onclick={() => (selected = m)} oncontextmenu={(e) => context(e, m)}>
            <span class="pin"></span>
            {#if zoom >= (world?.zoomMax ?? 3) - 1}<span class="label">{m.label}</span>{/if}
          </button>
        {/if}
      {/each}

      {#if menu}
        <div class="menu" style="left:{Math.min(menu.px, width - 230)}px;top:{Math.min(menu.pz, height - 40)}px"
          onpointerdown={(e) => e.stopPropagation()}>
          <div class="menu-title">{menu.title}</div>
          {#each menu.groups as g (g.layer.module + g.layer.id)}
            {#if menu.groups.length > 1}<div class="menu-group" style="--c:{g.layer.color}">{g.layer.name}</div>{/if}
            {#each g.actions as a (a.id)}
              <button class="menu-item {a.tone}" disabled={busy} onclick={() => run(g.layer, a, g.args)}>{a.label}</button>
            {/each}
          {/each}
        </div>
      {/if}
    </div>

    <aside class="side">
      <h3>{t('map.layers')}</h3>
      {#each layers as l (layerKey(l))}
        <div class="lrow" style="--c:{l.color}">
          <label class="lname"><input type="checkbox" checked={!hidden.has(layerKey(l))} onchange={() => toggleLayer(l)} />
            <span class="dot"></span>{l.name} <span class="muted small">{l.markers.filter((m) => m.world === worldKey).length}</span></label>
          {#if l.toggle}
            <button class="switch" class:on={l.toggle.on} disabled={busy}
              onclick={() => run(l, { id: l.toggle.action }, { on: String(!l.toggle.on) })}>{l.toggle.label}</button>
          {/if}
        </div>
      {:else}
        <p class="muted small">{t('map.noLayers')}</p>
      {/each}

      {#if selected}
        <div class="details" style="--c:{selected.color}">
          <strong>{selected.label}</strong>
          {#each selected.details as line}<span class="small">{line}</span>{/each}
          {#if selected.actions.length}
            <div class="dactions">
              {#each selected.actions as a (a.id)}
                <button class="mini {a.tone === 'bad' ? 'danger' : 'secondary'}" disabled={busy}
                  onclick={() => run(selected.layer, a, { marker: selected.id })}>{a.label}</button>
              {/each}
            </div>
          {/if}
        </div>
      {/if}
      <p class="muted small hint">{t('map.hint')}</p>
    </aside>
  </div>
</div>

<style>
  .mapview {
    display: grid;
    gap: 10px;
  }

  .bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .zoom {
    display: flex;
    gap: 6px;
  }

  .zoom button {
    min-width: 36px;
    padding: 4px 10px;
  }

  .coords {
    margin-left: auto;
    font-variant-numeric: tabular-nums;
    color: var(--muted);
    font-size: 0.85rem;
  }

  .frame {
    display: grid;
    gap: 12px;
    grid-template-columns: minmax(0, 1fr) 260px;
    align-items: start;
  }

  .map {
    position: relative;
    overflow: hidden;
    height: min(70vh, 640px);
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: #0d0f12;
    cursor: grab;
    touch-action: none;
    user-select: none;
  }

  .map:active {
    cursor: grabbing;
  }

  .layer-tiles {
    position: absolute;
    inset: 0;
  }

  .tile {
    position: absolute;
    max-width: none;
    pointer-events: none;
  }

  .tile.pixel {
    image-rendering: pixelated;
  }

  .point {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 0;
    transform: translate(-7px, -7px);
    background: transparent;
    border: none;
    color: #fff;
    cursor: pointer;
  }

  .pin {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--c);
    border: 2px solid rgba(0, 0, 0, 0.7);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.6);
  }

  .label {
    font-size: 0.75rem;
    font-weight: 700;
    white-space: nowrap;
    padding: 1px 6px;
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.6);
  }

  .area {
    position: absolute;
    padding: 0;
    border: 2px solid var(--c);
    background: color-mix(in srgb, var(--c) 22%, transparent);
    border-radius: 2px;
    cursor: pointer;
  }

  .menu {
    position: absolute;
    z-index: 5;
    display: grid;
    min-width: 200px;
    max-width: 220px;
    padding: 6px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .menu-title {
    padding: 4px 8px;
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--muted);
  }

  .menu-group {
    padding: 4px 8px 2px;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--c);
  }

  .menu-item {
    justify-content: flex-start;
    padding: 6px 10px;
    border-radius: 6px;
    background: transparent;
    color: var(--text);
    text-align: left;
  }

  .menu-item:hover {
    background: var(--surface-2);
    filter: none;
  }

  .menu-item.bad {
    color: var(--bad);
  }

  .side {
    display: grid;
    gap: 8px;
    padding: 12px 14px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
  }

  h3 {
    margin: 0;
    font-size: 0.95rem;
  }

  .lrow {
    display: grid;
    gap: 4px;
  }

  .lname {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .dot {
    width: 10px;
    height: 10px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--c);
  }

  .switch {
    justify-self: start;
    justify-content: flex-start;
    margin-left: 26px;
    padding: 2px 10px;
    font-size: 0.75rem;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
    border: 1px solid var(--border);
  }

  .switch.on {
    color: var(--good);
    border-color: color-mix(in srgb, var(--good) 45%, var(--border));
  }

  .details {
    display: grid;
    gap: 3px;
    margin-top: 6px;
    padding: 10px 12px;
    border-radius: 10px;
    border-left: 3px solid var(--c);
    background: var(--surface-2);
  }

  .dactions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .hint {
    margin: 4px 0 0;
  }

  @media (max-width: 800px) {
    .frame {
      grid-template-columns: 1fr;
    }

    .map {
      height: 60vh;
    }
  }
</style>
