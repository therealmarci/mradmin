<script>
  // A full-screen map of our own over the pictures squaremap renders (512 px per tile, at the highest zoom 1 block per
  // pixel; "extra" levels enlarge further), with floating panels like a map app: search and layers top left, the
  // world top right, zoom bottom right, the details of a marker bottom left (a sheet from below on phones).
  // Drag to move, wheel / pinch / +/- to zoom, click a marker for its details, right-click (long press on phones) for
  // actions - on a marker its own, on a free spot those "here" of the layers. The page gives worlds and layers and
  // runs the actions (onaction); this component only draws and asks (own dialogs instead of prompt/confirm).
  import { untrack } from 'svelte';
  import { fly, fade } from 'svelte/transition';
  import { t } from '../lib/i18n.js';

  let { worlds = [], layers = [], tileUrl, onaction, storageKey = 'mradmin.map', initial = null } = $props();

  const TILE = 512;
  const LONG_PRESS = 500;

  /** Remembered per browser: world, view, the layers switched off and whether the layer panel is open. */
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
  let layersOpen = $state(saved.layersOpen ?? true);
  let width = $state(600);
  let height = $state(420);
  let box = $state(null);
  let cursor = $state(null);
  let menu = $state(null);
  let selectedKey = $state(null);
  let busy = $state(false);
  let broken = $state(new Set());
  let query = $state('');
  let searchFocus = $state(false);
  let worldOpen = $state(false);
  let phoneLayers = $state(false);
  let dialog = $state(null);
  let phone = $state(false);

  $effect(() => {
    const media = window.matchMedia('(max-width: 759px)');
    const update = () => (phone = media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  });

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
      localStorage.setItem(storageKey, JSON.stringify({ world: worldKey, zoom, x: cx, z: cz, hidden: [...hidden], layersOpen }));
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
  /** Names next to the pins only once the map is close enough. */
  const showLabels = $derived(world ? zoom >= world.zoomMax - 1 : false);

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
  const markerKey = (m) => layerKey(m.layer) + ':' + m.id;

  /** Every marker of every layer and world, with its layer and colour. */
  const allMarkers = $derived(
    layers.flatMap((l) => l.markers.map((m) => ({ ...m, layer: l, color: m.color || l.color }))),
  );

  // Areas first, so the pins stay on top of them.
  const markers = $derived(
    allMarkers
      .filter((m) => m.world === worldKey && !hidden.has(layerKey(m.layer)))
      .sort((a, b) => (b.width > 0) - (a.width > 0)),
  );

  // The marker stays selected over a reload (the page asks again every few seconds) - gone markers close the panel.
  const selected = $derived(selectedKey ? allMarkers.find((m) => markerKey(m) === selectedKey) ?? null : null);

  const results = $derived.by(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return allMarkers.filter((m) => m.label.toLowerCase().includes(q)).slice(0, 8);
  });

  const worldName = (key) => worlds.find((w) => w.key === key)?.name ?? key;
  const countIn = (l) => l.markers.filter((m) => m.world === worldKey).length;

  // ---------------------------------------------------------------- moving

  let drag = null;
  let press = null;
  let lastTouch = 0;
  const pointers = new Map();
  let pinch = null;

  function cancelPress() {
    clearTimeout(press?.timer);
    press = null;
  }

  /** Touch: holding still opens the menu like a right-click. */
  function startPress(e, marker) {
    if (e.pointerType !== 'touch') return;
    lastTouch = Date.now();
    cancelPress();
    const { clientX, clientY } = e;
    press = {
      x: clientX,
      y: clientY,
      timer: setTimeout(() => {
        press = null;
        drag = null;
        openMenu(clientX, clientY, marker);
      }, LONG_PRESS),
    };
  }

  function down(e) {
    if (e.button !== 0) return;
    menu = null;
    worldOpen = false;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    e.currentTarget.setPointerCapture(e.pointerId);
    if (pointers.size === 2) {
      cancelPress();
      drag = null;
      const [a, b] = [...pointers.values()];
      pinch = { dist: Math.hypot(a.x - b.x, a.y - b.y) };
      return;
    }
    drag = { x: e.clientX, y: e.clientY, cx, cz, moved: false };
    startPress(e, null);
  }

  function move(e) {
    const rect = box.getBoundingClientRect();
    if (e.pointerType !== 'touch') {
      cursor = { x: Math.floor(cx + (e.clientX - rect.left - width / 2) / ppb), z: Math.floor(cz + (e.clientY - rect.top - height / 2) / ppb) };
    }
    if (pointers.has(e.pointerId)) pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (press && Math.abs(e.clientX - press.x) + Math.abs(e.clientY - press.y) > 8) cancelPress();
    if (pinch && pointers.size === 2) {
      // Zoom has whole steps (one picture size each): a step once the fingers moved far enough apart or together.
      const [a, b] = [...pointers.values()];
      const dist = Math.hypot(a.x - b.x, a.y - b.y);
      const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      if (dist > pinch.dist * 1.5) {
        zoomBy(1, mid.x, mid.y);
        pinch.dist = dist;
      } else if (dist < pinch.dist / 1.5) {
        zoomBy(-1, mid.x, mid.y);
        pinch.dist = dist;
      }
      return;
    }
    if (!drag) return;
    const dx = e.clientX - drag.x;
    const dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
    cx = drag.cx - dx / ppb;
    cz = drag.cz - dy / ppb;
  }

  function up(e) {
    pointers.delete(e.pointerId);
    cancelPress();
    if (pinch) {
      if (pointers.size < 2) pinch = null;
      drag = null;
      return;
    }
    const wasClick = drag && !drag.moved;
    drag = null;
    if (wasClick && e.target === e.currentTarget) {
      selectedKey = null;
      phoneLayers = false;
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

  // Touchpads send many small wheel events: collect them into whole steps.
  let wheelSum = 0;
  function wheel(e) {
    e.preventDefault();
    wheelSum += e.deltaY;
    if (Math.abs(wheelSum) < 40) return;
    zoomBy(wheelSum < 0 ? 1 : -1, e.clientX, e.clientY);
    wheelSum = 0;
  }

  function markerDown(e, m) {
    e.stopPropagation();
    menu = null;
    startPress(e, m);
  }

  function markerUp() {
    cancelPress();
  }

  function select(m) {
    if (Date.now() - lastTouch < LONG_PRESS + 100 && menu) return;
    selectedKey = markerKey(m);
    phoneLayers = false;
  }

  /** Jump to a marker (from the search), also into another world. */
  function goTo(m) {
    const target = worlds.find((w) => w.key === m.world);
    if (!target) return;
    worldKey = target.key;
    zoom = Math.max(zoom ?? 0, target.zoomMax - 1);
    cx = m.x + (m.width > 0 ? m.width / 2 : 0.5);
    cz = m.z + (m.width > 0 ? m.length / 2 : 0.5);
    if (hidden.has(layerKey(m.layer))) toggleLayer(m.layer);
    selectedKey = markerKey(m);
    query = '';
    searchFocus = false;
  }

  function pickWorld(key) {
    worldOpen = false;
    if (key === worldKey) return;
    worldKey = key;
    zoom = null;
    selectedKey = null;
  }

  // ---------------------------------------------------------------- menus and dialogs

  function context(e, marker) {
    e.preventDefault();
    e.stopPropagation();
    // On phones the long press opens the menu itself (some browsers send contextmenu as well).
    if (Date.now() - lastTouch < 1000) return;
    openMenu(e.clientX, e.clientY, marker);
  }

  function openMenu(clientX, clientY, marker) {
    const rect = box.getBoundingClientRect();
    const px = clientX - rect.left;
    const pz = clientY - rect.top;
    if (marker) {
      if (!marker.actions.length) {
        selectedKey = markerKey(marker);
        return;
      }
      menu = { px, pz, title: marker.label, color: marker.color, groups: [{ layer: marker.layer, actions: marker.actions, args: { marker: marker.id } }] };
      return;
    }
    const x = Math.floor(cx + (px - width / 2) / ppb);
    const z = Math.floor(cz + (pz - height / 2) / ppb);
    const groups = layers
      .filter((l) => l.here.length)
      .map((l) => ({ layer: l, actions: l.here, args: { world: worldKey, x: String(x), z: String(z) } }));
    if (!groups.length) return;
    menu = { px, pz, title: `X ${x} · Z ${z}`, groups };
  }

  /** Our own dialog: resolves with the text (input), true (confirm) or null (cancelled). */
  function ask(spec) {
    return new Promise((resolve) => {
      dialog = { ...spec, text: spec.value ?? '', resolve };
    });
  }

  function closeDialog(result) {
    const d = dialog;
    dialog = null;
    d?.resolve(result);
  }

  async function run(layer, action, args) {
    menu = null;
    if (action.confirm && !(await ask({ kind: 'confirm', title: action.label, message: action.confirm, tone: action.tone }))) return;
    const all = { ...args };
    if (action.input) {
      const value = await ask({
        kind: 'input',
        title: action.label,
        label: action.input.label,
        value: action.input.value ?? '',
        max: action.input.maxLength || 200,
      });
      if (value === null) return;
      all.value = value.slice(0, action.input.maxLength || 200);
    }
    busy = true;
    try {
      await onaction(layer.module, layer.id, action.id, all);
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

  function keys(e) {
    if (e.key !== 'Escape') return;
    if (dialog) closeDialog(null);
    else if (menu) menu = null;
    else if (worldOpen) worldOpen = false;
    else if (selectedKey) selectedKey = null;
  }

  function focusOnMount(node) {
    node.focus();
    if (node.select) node.select();
  }
</script>

<svelte:window onkeydown={keys} />

{#snippet layerList()}
  {#each layers as l (layerKey(l))}
    {@const on = !hidden.has(layerKey(l))}
    <div class="lrow" style="--c:{l.color}">
      <button class="lname" class:off={!on} aria-pressed={on} onclick={() => toggleLayer(l)}>
        <span class="check" aria-hidden="true">{#if on}✓{/if}</span>
        <span class="dot" aria-hidden="true"></span>
        <span class="lt">{l.name}</span>
        <span class="count">{countIn(l)}</span>
      </button>
      {#if l.toggle}
        <button class="ltoggle" class:on={l.toggle.on} disabled={busy} aria-pressed={l.toggle.on}
          onclick={() => run(l, { id: l.toggle.action }, { on: String(!l.toggle.on) })}>
          <span>{l.toggle.label}</span><span class="knob" aria-hidden="true"></span>
        </button>
      {/if}
    </div>
  {:else}
    <p class="muted small empty">{t('map.noLayers')}</p>
  {/each}
  <p class="muted hint">{t('map.hint')}</p>
{/snippet}

<div class="mapview" class:phone>
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="map" bind:this={box} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}
    onpointerleave={() => (cursor = null)} onwheel={wheel} oncontextmenu={(e) => context(e, null)}>
    {#each tiles as tile (tile.src)}
      {#if !broken.has(tile.src)}
        <img class="tile" class:pixel={zoom > world.zoomMax} src={tile.src} alt="" draggable="false"
          style="left:{tile.left}px;top:{tile.top}px;width:{tile.size}px;height:{tile.size}px"
          onerror={() => (broken = new Set([...broken, tile.src]))} />
      {/if}
    {/each}
    {#each markers as m (markerKey(m))}
      {@const active = selectedKey === markerKey(m)}
      {#if m.width > 0}
        <button class="area" class:active style="--c:{m.color};left:{sx(m.x)}px;top:{sz(m.z)}px;width:{Math.max(4, m.width * ppb)}px;height:{Math.max(4, m.length * ppb)}px"
          aria-label={m.label} onpointerdown={(e) => markerDown(e, m)} onpointerup={markerUp} onclick={() => select(m)}
          oncontextmenu={(e) => context(e, m)}>
          {#if showLabels && m.width * ppb > 60}<span class="alabel">{m.label}</span>{/if}
        </button>
      {:else}
        <button class="point" class:active style="--c:{m.color};left:{sx(m.x + 0.5)}px;top:{sz(m.z + 0.5)}px" aria-label={m.label}
          onpointerdown={(e) => markerDown(e, m)} onpointerup={markerUp} onclick={() => select(m)} oncontextmenu={(e) => context(e, m)}>
          <svg class="pin" viewBox="0 0 24 32" width="24" height="32" aria-hidden="true">
            <path d="M12 1C6 1 1.5 5.4 1.5 11.2 1.5 18.6 12 31 12 31s10.5-12.4 10.5-19.8C22.5 5.4 18 1 12 1z" />
            <circle cx="12" cy="11" r="4" />
          </svg>
          {#if showLabels || active}<span class="label">{m.label}</span>{/if}
        </button>
      {/if}
    {/each}
  </div>

  <!-- top: search + layers on the left, world on the right -->
  <div class="top">
    <div class="col">
      <div class="panel search" class:open={searchFocus && query.trim()}>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
          <circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" />
        </svg>
        <input type="search" placeholder={t('map.search')} bind:value={query} onfocus={() => (searchFocus = true)}
          onblur={() => setTimeout(() => (searchFocus = false), 150)}
          onkeydown={(e) => { if (e.key === 'Enter' && results.length) goTo(results[0]); }} />
        {#if query}<button class="clear" aria-label={t('common.close')} onclick={() => (query = '')}>✕</button>{/if}
      </div>
      {#if searchFocus && query.trim()}
        <div class="panel results" transition:fade={{ duration: 100 }}>
          {#each results as m (markerKey(m))}
            <button class="result" style="--c:{m.color}" onmousedown={(e) => e.preventDefault()} onclick={() => goTo(m)}>
              <span class="dot" aria-hidden="true"></span>
              <span class="rt">{m.label}</span>
              <span class="rs">{m.layer.name}{m.world !== worldKey ? ' · ' + worldName(m.world) : ''}</span>
            </button>
          {:else}
            <p class="muted small empty">{t('map.noResults')}</p>
          {/each}
        </div>
      {/if}

      {#if !phone}
        <div class="panel layers">
          <button class="phead" aria-expanded={layersOpen} onclick={() => (layersOpen = !layersOpen)}>
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="m12 3 9 5-9 5-9-5 9-5z" /><path d="m3 13 9 5 9-5" />
            </svg>
            <strong>{t('map.layers')}</strong>
            <span class="caret" class:up={layersOpen} aria-hidden="true">▾</span>
          </button>
          {#if layersOpen}
            <div class="pbody" transition:fly={{ y: -6, duration: 140 }}>{@render layerList()}</div>
          {/if}
        </div>
      {/if}
    </div>

    {#if worlds.length > 1}
      <div class="worldbox">
        <button class="panel pill" aria-haspopup="listbox" aria-expanded={worldOpen} onclick={() => (worldOpen = !worldOpen)}>
          <span class="wdot {world?.type ?? ''}" aria-hidden="true"></span>
          <span class="wname">{world?.name}</span>
          <span class="caret" class:up={worldOpen} aria-hidden="true">▾</span>
        </button>
        {#if worldOpen}
          <div class="panel wlist" role="listbox" transition:fly={{ y: -6, duration: 140 }}>
            {#each worlds as w (w.key)}
              <button role="option" aria-selected={w.key === worldKey} class:active={w.key === worldKey} onclick={() => pickWorld(w.key)}>
                <span class="wdot {w.type}" aria-hidden="true"></span>{w.name}
              </button>
            {/each}
          </div>
        {/if}
      </div>
    {/if}
  </div>

  <!-- bottom right: zoom -->
  <div class="controls">
    {#if phone}
      <button class="panel round" class:on={phoneLayers} aria-label={t('map.layers')} onclick={() => { phoneLayers = !phoneLayers; selectedKey = null; }}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="m12 3 9 5-9 5-9-5 9-5z" /><path d="m3 13 9 5 9-5" />
        </svg>
      </button>
    {/if}
    <div class="panel zoom">
      <button aria-label={t('map.zoomIn')} title={t('map.zoomIn')} disabled={zoom >= maxZoom} onclick={() => zoomBy(1)}>+</button>
      <span class="sep"></span>
      <button aria-label={t('map.zoomOut')} title={t('map.zoomOut')} disabled={zoom <= 0} onclick={() => zoomBy(-1)}>−</button>
    </div>
    <button class="panel round" aria-label={t('map.spawn')} title={t('map.spawn')} onclick={toSpawn}>
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <circle cx="12" cy="12" r="6" /><circle cx="12" cy="12" r="1.5" fill="currentColor" />
        <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
      </svg>
    </button>
  </div>

  {#if cursor && !phone}
    <div class="panel coords">X {cursor.x} <span class="muted">·</span> Z {cursor.z}</div>
  {/if}

  <!-- the selected marker; a sheet from below on phones -->
  {#if selected}
    <div class="panel detail" style="--c:{selected.color}" transition:fly={{ y: phone ? 220 : 12, duration: 200 }}>
      {#if phone}<span class="grip" aria-hidden="true"></span>{/if}
      <div class="dhead">
        <span class="dot big" aria-hidden="true"></span>
        <div class="dtitle">
          <strong>{selected.label}</strong>
          <span class="muted small">{selected.layer.name}</span>
        </div>
        <button class="x" aria-label={t('common.close')} onclick={() => (selectedKey = null)}>✕</button>
      </div>
      {#if selected.details.length}
        <ul class="dlines">
          {#each selected.details as line}<li>{line}</li>{/each}
        </ul>
      {/if}
      {#if selected.actions.length}
        <div class="dactions">
          {#each selected.actions as a (a.id)}
            <button class="act {a.tone}" disabled={busy} onclick={() => run(selected.layer, a, { marker: selected.id })}>{a.label}</button>
          {/each}
        </div>
      {/if}
    </div>
  {/if}

  {#if phone && phoneLayers}
    <div class="panel detail sheet-layers" transition:fly={{ y: 260, duration: 200 }}>
      <span class="grip" aria-hidden="true"></span>
      <div class="dhead">
        <strong class="dtitle">{t('map.layers')}</strong>
        <button class="x" aria-label={t('common.close')} onclick={() => (phoneLayers = false)}>✕</button>
      </div>
      <div class="pbody">{@render layerList()}</div>
    </div>
  {/if}

  {#if menu}
    <div class="panel menu" style="left:{Math.max(8, Math.min(menu.px, width - 228))}px;top:{Math.max(8, Math.min(menu.pz, height - 60 - menu.groups.length * 40))}px"
      transition:fade={{ duration: 90 }}>
      <div class="mtitle">{menu.title}</div>
      {#each menu.groups as g (layerKey(g.layer))}
        {#if menu.groups.length > 1}<div class="mgroup" style="--c:{g.layer.color}"><span class="dot"></span>{g.layer.name}</div>{/if}
        {#each g.actions as a (a.id)}
          <button class="mitem {a.tone}" disabled={busy} onclick={() => run(g.layer, a, g.args)}>{a.label}</button>
        {/each}
      {/each}
    </div>
  {/if}

  {#if dialog}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="scrim" transition:fade={{ duration: 120 }} onclick={(e) => { if (e.target === e.currentTarget) closeDialog(null); }}>
      <div class="panel dialog" role="dialog" aria-modal="true" aria-label={dialog.title} transition:fly={{ y: 12, duration: 160 }}>
        <form class="dform"
          onsubmit={(e) => { e.preventDefault(); closeDialog(dialog.kind === 'input' ? dialog.text : true); }}>
          <h3>{dialog.title}</h3>
          {#if dialog.kind === 'input'}
            <label for="map-dialog-input">{dialog.label}</label>
            <input id="map-dialog-input" bind:value={dialog.text} maxlength={dialog.max} use:focusOnMount />
            <span class="counter muted">{dialog.text.length} / {dialog.max}</span>
          {:else}
            <p>{dialog.message}</p>
          {/if}
          <div class="dbuttons">
            <button type="button" class="secondary" onclick={() => closeDialog(null)}>{t('common.cancel')}</button>
            {#if dialog.kind === 'input'}
              <button type="submit" disabled={!dialog.text.trim()}>{t('map.ok')}</button>
            {:else}
              <button type="submit" class:bad={dialog.tone === 'bad'} use:focusOnMount>{dialog.title}</button>
            {/if}
          </div>
        </form>
      </div>
    </div>
  {/if}
</div>

<style>
  .mapview {
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: #0d0f12;
    --gap: 12px;
  }

  .map {
    position: absolute;
    inset: 0;
    overflow: hidden;
    cursor: grab;
    touch-action: none;
    user-select: none;
    -webkit-user-select: none;
    -webkit-touch-callout: none;
  }

  .map:active {
    cursor: grabbing;
  }

  .tile {
    position: absolute;
    max-width: none;
    pointer-events: none;
  }

  .tile.pixel {
    image-rendering: pixelated;
  }

  /* ------------------------------------------------ markers */

  .point {
    position: absolute;
    display: flex;
    align-items: flex-end;
    justify-content: flex-start;
    gap: 4px;
    padding: 0;
    /* the tip of the pin on the spot */
    transform: translate(-12px, -31px);
    background: transparent;
    border: none;
    color: #fff;
    cursor: pointer;
    filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.5));
  }

  .point:hover {
    filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.5)) brightness(1.08);
    z-index: 2;
  }

  .point.active {
    z-index: 3;
  }

  .pin {
    flex-shrink: 0;
    transition: transform 0.12s;
    transform-origin: 50% 100%;
  }

  .point.active .pin {
    transform: scale(1.25);
  }

  .pin path {
    fill: var(--c);
    stroke: #fff;
    stroke-width: 2;
  }

  .pin circle {
    fill: #fff;
  }

  .label,
  .alabel {
    margin-bottom: 14px;
    font-size: 0.75rem;
    font-weight: 700;
    line-height: 1.3;
    white-space: nowrap;
    padding: 2px 7px;
    border-radius: 6px;
    color: #fff;
    background: rgb(15 17 21 / 0.72);
    backdrop-filter: blur(4px);
  }

  .area {
    position: absolute;
    display: flex;
    align-items: flex-start;
    justify-content: flex-start;
    padding: 4px;
    border: 1.5px solid color-mix(in srgb, var(--c) 85%, #fff);
    background: color-mix(in srgb, var(--c) 16%, transparent);
    border-radius: 3px;
    cursor: pointer;
    transition: background 0.12s;
  }

  .area:hover,
  .area.active {
    background: color-mix(in srgb, var(--c) 30%, transparent);
    filter: none;
  }

  .area.active {
    border-width: 2px;
    box-shadow: 0 0 0 2px rgb(255 255 255 / 0.5);
  }

  .alabel {
    margin: 0;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* ------------------------------------------------ floating panels */

  .panel {
    background: color-mix(in srgb, var(--surface) 94%, transparent);
    backdrop-filter: blur(12px);
    border: 1px solid var(--border);
    border-radius: 12px;
    box-shadow: 0 2px 6px rgb(0 0 0 / 0.12), 0 8px 24px -8px rgb(0 0 0 / 0.3);
    color: var(--text);
  }

  .top {
    position: absolute;
    top: var(--gap);
    left: var(--gap);
    right: var(--gap);
    z-index: 4;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    pointer-events: none;
  }

  .top > * {
    pointer-events: auto;
  }

  .col {
    display: grid;
    gap: 8px;
    width: 320px;
    max-width: 100%;
  }

  .search {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 6px 0 12px;
    color: var(--muted);
  }

  .search input {
    flex: 1;
    min-width: 0;
    padding: 11px 0;
    background: transparent;
    border: none;
    box-shadow: none;
    color: var(--text);
  }

  .search input::-webkit-search-cancel-button {
    display: none;
  }

  .clear,
  .x {
    width: 30px;
    height: 30px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 50%;
    background: transparent;
    color: var(--muted);
    font-size: 0.85rem;
  }

  .clear:hover,
  .x:hover {
    background: var(--surface-2);
    filter: none;
  }

  .results {
    display: grid;
    padding: 4px;
    max-height: 50vh;
    overflow-y: auto;
  }

  .result {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    column-gap: 10px;
    justify-content: flex-start;
    align-items: center;
    padding: 7px 10px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    text-align: left;
  }

  .result:hover {
    background: var(--surface-2);
    filter: none;
  }

  .result .dot {
    grid-row: span 2;
  }

  .rt {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rs {
    font-size: 0.78rem;
    font-weight: 500;
    color: var(--muted);
  }

  .empty {
    margin: 6px 10px;
  }

  .layers {
    overflow: hidden;
  }

  .phead {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    width: 100%;
    padding: 10px 12px;
    border-radius: 0;
    background: transparent;
    color: var(--text);
  }

  .phead:hover {
    filter: none;
    background: color-mix(in srgb, var(--surface-2) 60%, transparent);
  }

  .phead svg {
    color: var(--muted);
  }

  .caret {
    margin-left: auto;
    color: var(--muted);
    transition: transform 0.15s;
  }

  .caret.up {
    transform: rotate(180deg);
  }

  .pbody {
    display: grid;
    gap: 2px;
    padding: 0 6px 8px;
    max-height: min(55vh, 460px);
    overflow-y: auto;
  }

  .lrow {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 6px;
  }

  .lname {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    min-width: 0;
    padding: 7px 8px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    font-weight: 600;
  }

  .lname:hover {
    background: var(--surface-2);
    filter: none;
  }

  .lname.off {
    color: var(--muted);
  }

  .check {
    display: grid;
    place-items: center;
    width: 18px;
    height: 18px;
    flex-shrink: 0;
    border-radius: 5px;
    font-size: 0.7rem;
    color: #fff;
    background: var(--accent);
    border: 1.5px solid var(--accent);
  }

  .lname.off .check {
    background: transparent;
    border-color: var(--border);
  }

  .lname.off .dot {
    opacity: 0.45;
  }

  .lt {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    margin-left: auto;
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .ltoggle {
    gap: 6px;
    padding: 4px 6px 4px 9px;
    border-radius: 999px;
    font-size: 0.74rem;
    background: var(--surface-2);
    color: var(--muted);
    border: 1px solid var(--border);
  }

  .knob {
    position: relative;
    width: 24px;
    height: 14px;
    border-radius: 999px;
    background: var(--border);
    transition: background 0.15s;
  }

  .knob::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #fff;
    transition: translate 0.15s;
  }

  .ltoggle.on {
    color: var(--text);
  }

  .ltoggle.on .knob {
    background: var(--good);
  }

  .ltoggle.on .knob::after {
    translate: 10px 0;
  }

  .hint {
    margin: 6px 8px 0;
    font-size: 0.76rem;
    line-height: 1.4;
  }

  .dot {
    width: 10px;
    height: 10px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--c);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--c) 25%, transparent);
  }

  .dot.big {
    width: 14px;
    height: 14px;
  }

  /* world */

  .worldbox {
    position: relative;
    display: grid;
    justify-items: end;
    gap: 6px;
  }

  .pill {
    gap: 8px;
    padding: 9px 14px;
    border-radius: 999px;
    font-weight: 600;
  }

  .pill:hover {
    filter: none;
  }

  .wname {
    white-space: nowrap;
    max-width: 40vw;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .wdot {
    width: 10px;
    height: 10px;
    flex-shrink: 0;
    border-radius: 3px;
    background: #4caf50;
  }

  .wdot.nether {
    background: #c0392b;
  }

  .wdot.the_end,
  .wdot.end {
    background: #b39ddb;
  }

  .wlist {
    display: grid;
    min-width: 200px;
    padding: 4px;
  }

  .wlist button {
    justify-content: flex-start;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    font-weight: 500;
  }

  .wlist button:hover {
    background: var(--surface-2);
    filter: none;
  }

  .wlist button.active {
    font-weight: 700;
    color: var(--accent);
  }

  /* bottom right */

  .controls {
    position: absolute;
    right: var(--gap);
    bottom: var(--gap);
    z-index: 4;
    display: grid;
    justify-items: center;
    gap: 8px;
  }

  .zoom {
    display: grid;
    overflow: hidden;
  }

  .zoom button,
  .round {
    width: 42px;
    height: 42px;
    padding: 0;
    font-size: 1.35rem;
    font-weight: 500;
    background: transparent;
    color: var(--text);
    border-radius: 0;
  }

  .round {
    border-radius: 12px;
    background: color-mix(in srgb, var(--surface) 94%, transparent);
  }

  .round.on {
    color: var(--accent);
  }

  .zoom button:hover:not(:disabled),
  .round:hover {
    background: var(--surface-2);
    filter: none;
  }

  .sep {
    height: 1px;
    margin: 0 8px;
    background: var(--border);
  }

  .coords {
    position: absolute;
    right: calc(var(--gap) + 54px);
    bottom: var(--gap);
    z-index: 4;
    padding: 5px 12px;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    pointer-events: none;
  }

  /* details */

  .detail {
    position: absolute;
    left: var(--gap);
    bottom: var(--gap);
    z-index: 5;
    display: grid;
    gap: 10px;
    width: 340px;
    max-width: calc(100% - 2 * var(--gap) - 60px);
    max-height: calc(100% - 2 * var(--gap));
    overflow-y: auto;
    padding: 14px 14px 14px 16px;
    border-left: 4px solid var(--c, var(--border));
  }

  .dhead {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .dtitle {
    display: grid;
    min-width: 0;
    flex: 1;
  }

  .dtitle strong {
    font-size: 1.02rem;
    overflow-wrap: anywhere;
  }

  .dlines {
    display: grid;
    gap: 3px;
    margin: 0;
    padding: 0;
    list-style: none;
    font-size: 0.87rem;
  }

  .dlines li {
    padding: 5px 10px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .dactions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .act {
    padding: 7px 14px;
    font-size: 0.85rem;
    background: var(--surface-2);
    color: var(--text);
    border-color: var(--border);
  }

  .act.good {
    color: var(--good);
  }

  .act.bad {
    color: var(--bad);
    border-color: color-mix(in srgb, var(--bad) 40%, var(--border));
  }

  .grip {
    justify-self: center;
    width: 38px;
    height: 4px;
    margin: -4px 0 -2px;
    border-radius: 999px;
    background: var(--border);
  }

  /* context menu */

  .menu {
    position: absolute;
    z-index: 6;
    display: grid;
    width: 220px;
    padding: 5px;
  }

  .mtitle {
    padding: 5px 9px 4px;
    font-size: 0.76rem;
    font-weight: 700;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mgroup {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 6px 9px 2px;
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--muted);
  }

  .mgroup .dot {
    width: 8px;
    height: 8px;
  }

  .mitem {
    justify-content: flex-start;
    padding: 8px 10px;
    border-radius: 7px;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    text-align: left;
  }

  .mitem:hover {
    background: var(--surface-2);
    filter: none;
  }

  .mitem.good {
    color: var(--good);
  }

  .mitem.bad {
    color: var(--bad);
  }

  /* dialog */

  .scrim {
    position: absolute;
    inset: 0;
    z-index: 20;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 0.35);
  }

  .dialog {
    width: min(380px, 100%);
    padding: 18px;
    background: var(--surface);
  }

  .dform {
    display: grid;
    gap: 8px;
  }

  .dialog h3 {
    margin: 0 0 4px;
    font-size: 1.05rem;
  }

  .dialog label {
    margin: 0;
  }

  .dialog p {
    margin: 0;
    color: var(--muted);
  }

  .counter {
    justify-self: end;
    font-size: 0.75rem;
    font-variant-numeric: tabular-nums;
  }

  .dbuttons {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 6px;
  }

  .dbuttons .bad {
    background: var(--bad);
  }

  /* ------------------------------------------------ phone */

  .phone {
    --gap: 10px;
  }

  .phone .col {
    flex: 1;
    width: auto;
    min-width: 0;
  }

  .phone .detail {
    left: 0;
    right: 0;
    bottom: 0;
    width: auto;
    max-width: none;
    max-height: 70%;
    padding: 14px 16px calc(16px + env(safe-area-inset-bottom, 0px));
    border-radius: 18px 18px 0 0;
    border-left: none;
    border-bottom: none;
    border-top: 4px solid var(--c, var(--border));
  }

  .phone .sheet-layers {
    border-top-color: var(--border);
    border-top-width: 1px;
  }

  .phone .sheet-layers .pbody {
    padding: 0;
    max-height: none;
  }
</style>
