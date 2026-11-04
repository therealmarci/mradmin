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
  import { head } from '../lib/format.js';

  /**
   * page: what the server offers besides the layers ({openAtPlayer, follow, chunkGrid, self, others});
   * positions: {self: {world, x, z, yaw} | null, players: [{uuid, name, world, x, z, yaw}]} - asked by the page.
   */
  let { worlds = [], layers = [], tileUrl, onaction, storageKey = 'mradmin.map', initial = null, page = {},
    positions = null } = $props();

  const TILE = 512;
  const LONG_PRESS = 500;
  /** Symbols of the actions (Action.icon). */
  const ICONS = {
    plus: 'M12 5v14M5 12h14',
    edit: 'M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4',
    trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13',
    power: 'M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0',
    compass: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5-5 2 2-5 5-2z',
    eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  };

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
  // The chunk grid starts switched off (the layers of the plugins on).
  let grid = $state(saved.grid ?? false);
  let follow = $state(false);
  let collapsed = $state(new Set(saved.collapsed ?? []));
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
      localStorage.setItem(storageKey, JSON.stringify({ world: worldKey, zoom, x: cx, z: cz, hidden: [...hidden], layersOpen,
        grid, collapsed: [...collapsed] }));
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

  // ---------------------------------------------------------------- layer list in groups

  const SELF = 'mradmin:self';
  const PLAYERS = 'mradmin:players';

  /** The layers under their headings: the plugins' groups in their order, then players and the chunk grid. */
  const groups = $derived.by(() => {
    const list = [];
    for (const l of layers) {
      const title = l.group || l.module;
      let g = list.find((x) => x.title === title);
      if (!g) list.push((g = { key: 'g:' + title, title, rows: [] }));
      g.rows.push({ key: layerKey(l), name: l.name, color: l.color, count: countIn(l), layer: l });
    }
    const people = [];
    if (page.self && positions?.self) people.push({ key: SELF, name: t('map.me'), color: 'var(--accent)', count: null });
    if (page.others) {
      people.push({ key: PLAYERS, name: t('map.others'), color: '#f59e0b', count: (positions?.players ?? []).filter((p) => p.world === worldKey).length });
    }
    if (people.length) list.push({ key: 'g:players', title: t('map.players'), rows: people });
    if (page.chunkGrid) list.push({ key: 'g:map', title: t('map.extras'), rows: [{ key: 'grid', name: t('map.chunkGrid'), grid: true }] });
    return list;
  });

  function toggleGroup(key) {
    const next = new Set(collapsed);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    collapsed = next;
  }

  function toggleRow(row) {
    if (row.grid) {
      grid = !grid;
      return;
    }
    const next = new Set(hidden);
    if (next.has(row.key)) next.delete(row.key);
    else next.add(row.key);
    hidden = next;
  }

  const rowOn = (row) => (row.grid ? grid : !hidden.has(row.key));

  // ---------------------------------------------------------------- chunk grid

  const gridSize = $derived(16 * ppb);
  /** Lines along the chunk borders - only once they are far enough apart to be useful. */
  const gridStyle = $derived.by(() => {
    if (!grid || !page.chunkGrid || gridSize < 6 || cx === null) return null;
    const ox = ((sx(0) % gridSize) + gridSize) % gridSize;
    const oz = ((sz(0) % gridSize) + gridSize) % gridSize;
    return `background-size:${gridSize}px ${gridSize}px;background-position:${ox}px ${oz}px`;
  });

  // ---------------------------------------------------------------- players

  /**
   * The players as drawn. The server sends snapshots with their time ({@code at}); the map plays them back a little
   * later than they happened and slides evenly from one snapshot to the next - so the players move without pauses,
   * even if an answer arrives late. Without {@code at} (older server) the answer's arrival time counts.
   */
  let drawn = $state([]);
  let opened = false;
  /** uuid -> {name, self, samples: [{t, world, x, z, yaw}]} */
  const tracks = new Map();
  /** server clock - performance.now(), the best seen (= the least delayed answer) of the last answers */
  let offsets = [];
  let frame = 0;

  /** How far behind the playback runs: one round of asking plus room for a late answer. */
  const lag = $derived(Math.max(1, page.updateSeconds ?? 1) * 1000 + 450);

  $effect(() => {
    const p = positions;
    if (!p) return;
    const targets = [
      ...(p.self && page.self ? [{ ...p.self, self: true }] : []),
      ...(page.others ? p.players : []).map((o) => ({ ...o, self: false })),
    ];
    untrack(() => {
      openAtPlayer(p.self);
      take(targets, p.at ?? Date.now());
    });
  });

  $effect(() => () => cancelAnimationFrame(frame));

  function take(targets, at) {
    offsets.push(at - performance.now());
    if (offsets.length > 12) offsets.shift();
    const seen = new Set();
    for (const tg of targets) {
      seen.add(tg.uuid);
      let tr = tracks.get(tg.uuid);
      if (!tr) tracks.set(tg.uuid, (tr = { samples: [] }));
      tr.name = tg.name;
      tr.self = tg.self;
      const last = tr.samples.at(-1);
      if (last && at <= last.t) continue; // the same snapshot again (the server keeps it a moment)
      // A new world or a long gap (tab was in the background) is a jump, not a slide.
      if (last && (last.world !== tg.world || at - last.t > lag * 3)) tr.samples = [];
      tr.samples.push({ t: at, world: tg.world, x: tg.x, z: tg.z, yaw: tg.yaw });
      if (tr.samples.length > 8) tr.samples.shift();
    }
    for (const id of [...tracks.keys()]) if (!seen.has(id)) tracks.delete(id);
    if (!frame) frame = requestAnimationFrame(play);
  }

  function play() {
    frame = 0;
    if (!tracks.size) {
      drawn = [];
      return;
    }
    const now = performance.now() + Math.max(...offsets) - lag;
    const list = [];
    for (const [uuid, tr] of tracks) {
      const at = sample(tr.samples, now);
      list.push({ uuid, name: tr.name, self: tr.self, ...at });
    }
    drawn = list;
    if (follow) keepCentered();
    frame = requestAnimationFrame(play);
  }

  /** Where a player was at the time {@code now}: between the two snapshots around it, else the nearest one. */
  function sample(samples, now) {
    if (now <= samples[0].t || samples.length === 1) return samples[0];
    for (let i = 1; i < samples.length; i++) {
      const b = samples[i];
      if (now <= b.t) {
        const a = samples[i - 1];
        const k = (now - a.t) / (b.t - a.t);
        let turn = (((b.yaw - a.yaw) % 360) + 540) % 360 - 180; // the short way round
        return { world: b.world, x: a.x + (b.x - a.x) * k, z: a.z + (b.z - a.z) * k, yaw: a.yaw + turn * k };
      }
    }
    return samples.at(-1);
  }

  /** Once, at the first answer: the map starts where the player stands (unless a link names a spot). */
  function openAtPlayer(self) {
    if (opened) return;
    opened = true;
    if (!self || start || !page.openAtPlayer) return;
    const w = worlds.find((x) => x.key === self.world);
    if (!w) return;
    worldKey = w.key;
    if (zoom === null) zoom = w.zoomDefault;
    cx = self.x;
    cz = self.z;
  }

  const me = $derived(positions?.self ?? null);
  const visiblePlayers = $derived(
    drawn.filter((d) => d.world === worldKey && !hidden.has(d.self ? SELF : PLAYERS)),
  );

  function keepCentered() {
    const self = drawn.find((d) => d.self) ?? me;
    if (!self) return;
    if (self.world !== worldKey) {
      if (!worlds.some((w) => w.key === self.world)) return;
      worldKey = self.world;
    }
    cx = self.x;
    cz = self.z;
  }

  function toggleFollow() {
    follow = !follow;
    if (follow) keepCentered();
  }

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
    if (Math.abs(dx) + Math.abs(dy) > 4) {
      drag.moved = true;
      follow = false;
    }
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
    follow = false;
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
    follow = false;
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
      const items = marker.actions.map((a) => ({ layer: marker.layer, action: a, args: { marker: marker.id } }));
      menu = { px, pz, title: marker.label, sub: marker.layer.name, color: marker.color, ...split(items) };
      return;
    }
    const x = Math.floor(cx + (px - width / 2) / ppb);
    const z = Math.floor(cz + (pz - height / 2) / ppb);
    const items = layers.flatMap((l) =>
      l.here.map((a) => ({ layer: l, action: a, args: { world: worldKey, x: String(x), z: String(z) } })));
    if (!items.length) return;
    menu = { px, pz, title: `X ${x} · Z ${z}`, sub: world?.name ?? '', color: null, ...split(items) };
  }

  /** Red ones (delete ...) at the end, below a line. */
  function split(items) {
    return { items: items.filter((i) => i.action.tone !== 'bad'), danger: items.filter((i) => i.action.tone === 'bad') };
  }

  /**
   * Our own dialog: resolves with {value, choice} (input and/or selection), true (confirm) or null (cancelled).
   */
  function ask(spec) {
    return new Promise((resolve) => {
      const pick = spec.choice ? (spec.choice.options.some((o) => o.id === spec.choice.value) ? spec.choice.value : spec.choice.options[0]?.id) : null;
      dialog = { ...spec, text: spec.value ?? '', pick, resolve };
    });
  }

  function submitDialog() {
    if (dialog.kind === 'confirm') closeDialog(true);
    else if (!dialog.input || dialog.text.trim()) closeDialog({ value: dialog.text, choice: dialog.pick });
  }

  function closeDialog(result) {
    const d = dialog;
    dialog = null;
    d?.resolve(result);
  }

  async function run(layer, action, args) {
    menu = null;
    if (action.confirm && !(await ask({ kind: 'confirm', title: action.label, message: action.confirm, tone: action.tone, icon: action.icon }))) return;
    const all = { ...args };
    if (action.input || action.choice) {
      const answer = await ask({
        kind: 'form',
        title: action.label,
        icon: action.icon,
        input: !!action.input,
        label: action.input?.label ?? '',
        value: action.input?.value ?? '',
        max: action.input?.maxLength || 200,
        choice: action.choice ?? null,
      });
      if (answer === null) return;
      if (action.input) all.value = answer.value.slice(0, action.input.maxLength || 200);
      if (action.choice && answer.choice) all.choice = answer.choice;
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
    follow = false;
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
  {#each groups as g (g.key)}
    {@const open = !collapsed.has(g.key)}
    {@const onCount = g.rows.filter(rowOn).length}
    <div class="lgroup">
      <button class="ghead" aria-expanded={open} onclick={() => toggleGroup(g.key)}>
        <span class="caret small-caret" class:up={open} aria-hidden="true">▾</span>
        <span class="gt">{g.title}</span>
        <span class="count">{onCount}/{g.rows.length}</span>
      </button>
      {#if open}
        <div class="gbody">
          {#each g.rows as row (row.key)}
            {@const on = rowOn(row)}
            <div class="lrow" style="--c:{row.color ?? 'var(--muted)'}">
              <button class="lname" class:off={!on} aria-pressed={on} onclick={() => toggleRow(row)}>
                <span class="check" aria-hidden="true">{#if on}✓{/if}</span>
                {#if row.grid}
                  <svg class="gicon" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
                    <path d="M1 5.5h14M1 10.5h14M5.5 1v14M10.5 1v14" />
                  </svg>
                {:else}
                  <span class="dot" aria-hidden="true"></span>
                {/if}
                <span class="lt">{row.name}</span>
                {#if row.grid && on && gridSize < 6}
                  <span class="count">{t('map.gridZoom')}</span>
                {:else if row.count !== null && row.count !== undefined}
                  <span class="count">{row.count}</span>
                {/if}
              </button>
              {#if row.layer?.toggle}
                {@const l = row.layer}
                <button class="ltoggle" class:on={l.toggle.on} disabled={busy} aria-pressed={l.toggle.on}
                  onclick={() => run(l, { id: l.toggle.action }, { on: String(!l.toggle.on) })}>
                  <span>{l.toggle.label}</span><span class="knob" aria-hidden="true"></span>
                </button>
              {/if}
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {:else}
    <p class="muted small empty">{t('map.noLayers')}</p>
  {/each}
  <p class="muted hint">{t('map.hint')}</p>
{/snippet}

{#snippet glyph(name, size = 16)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name] ?? ''} /></svg>
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
    {#if gridStyle}<div class="grid" style={gridStyle}></div>{/if}
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
    {#each visiblePlayers as p (p.uuid)}
      <div class="player" class:self={p.self} style="transform:translate3d({sx(p.x)}px,{sz(p.z)}px,0)">
        <span class="look" style="rotate:{p.yaw}deg" aria-hidden="true"></span>
        <img class="face" src={head(p.uuid)} alt="" width="22" height="22" draggable="false" />
        <span class="pname">{p.name}</span>
      </div>
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
    {#if page.follow && me}
      <button class="panel round" class:on={follow} aria-pressed={follow} aria-label={t(follow ? 'map.followOff' : 'map.follow')}
        title={t(follow ? 'map.followOff' : 'map.follow')} onclick={toggleFollow}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill={follow ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2"
          stroke-linejoin="round" aria-hidden="true"><path d="M3 11 21 3l-8 18-2-8-8-2z" /></svg>
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
          {#each [...selected.actions].sort((a, b) => (a.tone === 'bad') - (b.tone === 'bad')) as a (a.id)}
            <button class="act {a.tone}" disabled={busy} onclick={() => run(selected.layer, a, { marker: selected.id })}>
              {#if ICONS[a.icon]}{@render glyph(a.icon, 15)}{/if}{a.label}
            </button>
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
    {@const rows = menu.items.length + menu.danger.length}
    <div class="panel menu" role="menu" style="left:{Math.max(8, Math.min(menu.px, width - 248))}px;top:{Math.max(8, Math.min(menu.pz, height - 70 - rows * 40))}px"
      transition:fly={{ y: -4, duration: 110 }}>
      <div class="mhead" style="--c:{menu.color ?? 'var(--muted)'}">
        {#if menu.color}<span class="dot" aria-hidden="true"></span>{:else}
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M12 21s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" /><circle cx="12" cy="9" r="2.5" />
          </svg>
        {/if}
        <div class="mtitles">
          <span class="mtitle">{menu.title}</span>
          {#if menu.sub}<span class="msub">{menu.sub}</span>{/if}
        </div>
      </div>
      {#each menu.items as i (layerKey(i.layer) + i.action.id)}
        <button class="mitem {i.action.tone}" role="menuitem" disabled={busy} onclick={() => run(i.layer, i.action, i.args)}>
          <span class="micon">{@render glyph(ICONS[i.action.icon] ? i.action.icon : 'eye', 16)}</span>{i.action.label}
        </button>
      {/each}
      {#if menu.danger.length}
        {#if menu.items.length}<span class="msep" aria-hidden="true"></span>{/if}
        {#each menu.danger as i (layerKey(i.layer) + i.action.id)}
          <button class="mitem bad" role="menuitem" disabled={busy} onclick={() => run(i.layer, i.action, i.args)}>
            <span class="micon">{@render glyph(ICONS[i.action.icon] ? i.action.icon : 'trash', 16)}</span>{i.action.label}
          </button>
        {/each}
      {/if}
    </div>
  {/if}

  {#if dialog}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="scrim" transition:fade={{ duration: 120 }} onclick={(e) => { if (e.target === e.currentTarget) closeDialog(null); }}>
      <div class="panel dialog" role="dialog" aria-modal="true" aria-label={dialog.title} transition:fly={{ y: 12, duration: 160 }}>
        <form class="dform" onsubmit={(e) => { e.preventDefault(); submitDialog(); }}>
          <h3>
            {#if ICONS[dialog.icon]}<span class="dicon" class:bad={dialog.tone === 'bad'}>{@render glyph(dialog.icon, 18)}</span>{/if}
            {dialog.title}
          </h3>
          {#if dialog.kind === 'form'}
            {#if dialog.input}
              <label for="map-dialog-input">{dialog.label}</label>
              <input id="map-dialog-input" bind:value={dialog.text} maxlength={dialog.max} use:focusOnMount />
              <span class="counter muted">{dialog.text.length} / {dialog.max}</span>
            {/if}
            {#if dialog.choice}
              <span class="clabel">{dialog.choice.label}</span>
              <div class="choices" role="radiogroup" aria-label={dialog.choice.label}>
                {#each dialog.choice.options as o (o.id)}
                  <button type="button" role="radio" aria-checked={dialog.pick === o.id} class="choice" class:on={dialog.pick === o.id}
                    style="--c:{o.color || 'var(--accent)'}" onclick={() => (dialog.pick = o.id)}>
                    <span class="dot" aria-hidden="true"></span>{o.label}
                  </button>
                {/each}
              </div>
            {/if}
          {:else}
            <p>{dialog.message}</p>
          {/if}
          <div class="dbuttons">
            <button type="button" class="secondary" onclick={() => closeDialog(null)}>{t('common.cancel')}</button>
            {#if dialog.kind === 'form'}
              <button type="submit" disabled={dialog.input && !dialog.text.trim()}>{t('map.ok')}</button>
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
    width: 240px;
    padding: 6px;
    border-radius: 14px;
  }

  .mhead {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 6px 10px 8px;
    margin-bottom: 4px;
    border-bottom: 1px solid var(--border);
    color: var(--muted);
  }

  .mtitles {
    display: grid;
    min-width: 0;
  }

  .mtitle {
    font-size: 0.86rem;
    font-weight: 700;
    color: var(--text);
    font-variant-numeric: tabular-nums;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .msub {
    font-size: 0.74rem;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mitem {
    justify-content: flex-start;
    gap: 10px;
    padding: 9px 10px;
    border-radius: 9px;
    background: transparent;
    color: var(--text);
    font-weight: 600;
    text-align: left;
  }

  .mitem:hover:not(:disabled) {
    background: var(--surface-2);
    filter: none;
  }

  .micon {
    display: grid;
    place-items: center;
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border-radius: 7px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .mitem.good .micon {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .mitem.bad {
    color: var(--bad);
  }

  .mitem.bad .micon {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .mitem.bad:hover:not(:disabled) {
    background: color-mix(in srgb, var(--bad) 10%, transparent);
  }

  .msep {
    height: 1px;
    margin: 4px 6px;
    background: var(--border);
  }

  /* layer groups */

  .lgroup + .lgroup {
    margin-top: 4px;
    padding-top: 4px;
    border-top: 1px solid var(--border);
  }

  .ghead {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    width: 100%;
    padding: 6px 8px;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .ghead:hover {
    background: var(--surface-2);
    filter: none;
  }

  .small-caret {
    margin-left: 0;
    font-size: 0.8rem;
    transform: rotate(-90deg);
  }

  .small-caret.up {
    transform: none;
  }

  .gt {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .gbody {
    display: grid;
    gap: 2px;
    padding-left: 12px;
  }

  .gicon {
    flex-shrink: 0;
    fill: none;
    stroke: var(--muted);
    stroke-width: 1.6;
  }

  /* chunk grid */

  .grid {
    position: absolute;
    inset: 0;
    pointer-events: none;
    background-image:
      linear-gradient(to right, rgb(255 255 255 / 0.28) 1px, transparent 1px),
      linear-gradient(to bottom, rgb(255 255 255 / 0.28) 1px, transparent 1px);
    mix-blend-mode: overlay;
  }

  /* players */

  .player {
    position: absolute;
    top: 0;
    left: 0;
    will-change: transform;
    z-index: 4;
    display: grid;
    justify-items: center;
    width: 0;
    height: 0;
    pointer-events: none;
  }

  .face {
    position: absolute;
    top: -11px;
    left: -11px;
    width: 22px;
    height: 22px;
    border-radius: 5px;
    border: 2px solid #fff;
    box-shadow: 0 1px 4px rgb(0 0 0 / 0.5);
    image-rendering: pixelated;
    background: #333;
  }

  .player.self .face {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 35%, transparent), 0 1px 4px rgb(0 0 0 / 0.5);
  }

  /* the direction the player looks: a cone pointing south at yaw 0, turned by the yaw */
  .look {
    position: absolute;
    top: -26px;
    left: -26px;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: conic-gradient(from 150deg, transparent 0deg, rgb(255 255 255 / 0.55) 30deg, transparent 60deg);
    -webkit-mask: radial-gradient(circle, transparent 11px, #000 12px);
    mask: radial-gradient(circle, transparent 11px, #000 12px);
  }

  .player.self .look {
    background: conic-gradient(from 150deg, transparent 0deg, color-mix(in srgb, var(--accent) 80%, #fff) 30deg, transparent 60deg);
  }

  .pname {
    position: absolute;
    top: 14px;
    font-size: 0.72rem;
    font-weight: 700;
    white-space: nowrap;
    padding: 1px 6px;
    border-radius: 6px;
    color: #fff;
    background: rgb(15 17 21 / 0.72);
    translate: -50% 0;
    left: 0;
  }

  /* dialog: selection */

  .clabel {
    margin-top: 6px;
    font-weight: 600;
    font-size: 0.9rem;
  }

  .choices {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .choice {
    gap: 8px;
    padding: 7px 12px;
    border-radius: 999px;
    font-weight: 600;
    font-size: 0.86rem;
    background: var(--surface-2);
    color: var(--text);
    border: 1.5px solid var(--border);
  }

  .choice.on {
    border-color: var(--c);
    background: color-mix(in srgb, var(--c) 14%, var(--surface-2));
  }

  .choice:hover {
    filter: none;
  }

  .dicon {
    display: inline-grid;
    place-items: center;
    width: 28px;
    height: 28px;
    margin-right: 6px;
    vertical-align: middle;
    border-radius: 8px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .dicon.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .act :global(svg) {
    flex-shrink: 0;
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
    display: flex;
    align-items: center;
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
