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
  import { poll } from '../lib/poll.js';

  /**
   * page: what the server offers besides the layers ({openAtPlayer, follow, chunkGrid, self, others});
   * positions: {self: {world, x, z, yaw} | null, players: [{uuid, name, world, x, z, yaw}]} - asked by the page.
   * stamps(folder, zoom, ["x_z", …]): when those pictures were last drawn ({"x_z": seconds}) - asked every 15 seconds,
   * changed pictures are loaded again (tileUrl gets their time as 5th argument).
   */
  let { worlds = [], layers = [], tileUrl, onaction, storageKey = 'mradmin.map', initial = null, page = {},
    positions = null, stamps = null } = $props();

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
  /** Panels the player moved: id -> {c: corner tl|tr|bl|br, x, y: distance from that corner's edges}. */
  let layout = $state(saved.layout ?? {});
  /** Measured size of every panel (to keep moved ones inside the map). */
  let sizes = $state({});
  /** The panel being dragged right now: {id, left, top, ox, oy}. */
  let moving = $state(null);
  let copied = $state(false);

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
        grid, collapsed: [...collapsed], layout }));
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
        const name = `${tx}_${tz}`;
        const src = tileUrl(world.folder, tileZoom, tx, tz, versions[`${world.folder}/${tileZoom}/${name}`]);
        list.push({ src, name, key: `${world.folder}/${tileZoom}/${name}`, left: sx(tx * tileBlocks), top: sz(tz * tileBlocks), size: tileBlocks * ppb });
      }
    }
    return list;
  });


  // Fresh pictures: every 15 seconds (tab visible) the page asks when the shown pictures were last drawn; a picture
  // drawn after the one on screen gets its time in the address and loads again - the others stay as they are.
  let versions = $state({});
  const seen = new Map();
  async function refreshTiles() {
    if (!stamps || !world) return;
    const folder = world.folder;
    const zoom = tileZoom;
    const names = untrack(() => tiles.map((tl) => tl.name)).slice(0, 200);
    if (!names.length) return;
    let result;
    try {
      result = await stamps(folder, zoom, names);
    } catch {
      return;
    }
    const next = { ...versions };
    let changed = false;
    for (const [name, at] of Object.entries(result)) {
      const key = `${folder}/${zoom}/${name}`;
      const known = seen.get(key);
      seen.set(key, at);
      // The first answer is what is on screen already; later ones only if newer.
      if (known !== undefined && at > known) {
        next[key] = at;
        changed = true;
      }
    }
    if (changed) versions = next;
  }
  $effect(() => poll(refreshTiles, 15_000));
  const layerKey = (l) => l.module + ':' + l.id;
  const markerKey = (m) => layerKey(m.layer) + ':' + m.id;

  /** Every marker of every layer and world, with its layer and colour. */
  const allMarkers = $derived(
    layers.flatMap((l) => l.markers.map((m) => ({ ...m, layer: l, color: m.color || l.color }))),
  );

  // Areas first, so the pins stay on top of them.
  const markers = $derived(
    allMarkers
      .filter((m) => m.world === mapKey && !hidden.has(layerKey(m.layer)))
      .sort((a, b) => (b.width > 0) - (a.width > 0)),
  );

  // The marker stays selected over a reload (the page asks again every few seconds) - gone markers close the panel.
  const selected = $derived(selectedKey ? allMarkers.find((m) => markerKey(m) === selectedKey) ?? null : null);

  const results = $derived.by(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return allMarkers.filter((m) => m.label.toLowerCase().includes(q)).slice(0, 8);
  });

  // The Nether ceiling is a map of its own over the same dimension: markers belong to the dimension (base).
  const mapKey = $derived(world ? (world.base ?? world.key) : null);
  const baseOf = (key) => worlds.find((w) => w.key === key)?.base ?? key;
  const label = (w) => (w.roof ? `${w.name} · ${t('map.roof')}` : w.name);
  const worldName = (key) => { const w = worlds.find((x) => x.key === key); return w ? label(w) : key; };
  const countIn = (l) => l.markers.filter((m) => m.world === mapKey).length;

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

  /** The switch of a whole group: all on -> all off, otherwise all on. */
  function toggleAll(g) {
    const on = !g.rows.every(rowOn);
    const next = new Set(hidden);
    for (const row of g.rows) {
      if (row.grid) grid = on;
      else if (on) next.delete(row.key);
      else next.add(row.key);
    }
    hidden = next;
  }

  /** What a plugin's switch is about, without its state ("in Xaero: on" -> "in Xaero"). */
  const toggleName = (label) => label.split(':')[0].trim();

  // ---------------------------------------------------------------- arrangement of the panels

  const SNAP = 8;
  /** Closer than this to an edge = right at the edge (with the usual gap). */
  const EDGE = 28;
  const GAP = 12;

  /** Where a panel stands: the default from the CSS, a moved one from the layout, kept inside the map. */
  function dockStyle(id) {
    if (phone) return id === 'search' ? `right:${GAP + (sizes.world?.w ?? 0) + 8}px` : '';
    if (moving?.id === id) return `left:${moving.left}px;top:${moving.top}px;right:auto;bottom:auto`;
    const p = layout[id];
    if (!p) return '';
    const s = sizes[id] ?? { w: 0, h: 0 };
    const x = Math.max(0, Math.min(p.x, width - s.w));
    const y = Math.max(0, Math.min(p.y, height - s.h));
    const h = p.c[1] === 'l' ? `left:${x}px;right:auto` : `right:${x}px;left:auto`;
    const v = p.c[0] === 't' ? `top:${y}px;bottom:auto` : `bottom:${y}px;top:auto`;
    return `${h};${v}`;
  }

  /** A moved panel at the bottom opens its lists upwards. */
  const atBottom = (id) => !phone && layout[id]?.c?.[0] === 'b';

  function measure(node, id) {
    const observer = new ResizeObserver(() => {
      sizes = { ...sizes, [id]: { w: node.offsetWidth, h: node.offsetHeight } };
    });
    observer.observe(node);
    return { destroy: () => observer.disconnect() };
  }

  function grab(e, id) {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    const dock = e.currentTarget.closest('.dock');
    const outer = dock.parentElement.getBoundingClientRect();
    const r = dock.getBoundingClientRect();
    moving = { id, left: r.left - outer.left, top: r.top - outer.top, ox: e.clientX - r.left, oy: e.clientY - r.top, outer };
    e.currentTarget.setPointerCapture(e.pointerId);
  }

  function dragDock(e) {
    if (!moving) return;
    const s = sizes[moving.id] ?? { w: 0, h: 0 };
    moving.left = Math.max(0, Math.min(e.clientX - moving.outer.left - moving.ox, width - s.w));
    moving.top = Math.max(0, Math.min(e.clientY - moving.outer.top - moving.oy, height - s.h));
  }

  /** Dropped: anchored to the nearest corner, on a grid of 8 px, close to an edge right at it. */
  function dropDock() {
    if (!moving) return;
    const { id, left, top } = moving;
    const s = sizes[id] ?? { w: 0, h: 0 };
    const c = (top + s.h / 2 < height / 2 ? 't' : 'b') + (left + s.w / 2 < width / 2 ? 'l' : 'r');
    const snap = (v) => (v < EDGE ? GAP : Math.round(v / SNAP) * SNAP);
    const x = snap(c[1] === 'l' ? left : width - left - s.w);
    const y = snap(c[0] === 't' ? top : height - top - s.h);
    layout = { ...layout, [id]: { c, x, y } };
    moving = null;
  }

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


  /** The closest zoom of a world; an area (chunk loader) still has to fit into the view. */
  function closest(w, m = null) {
    let z = w.zoomMax + w.zoomExtra;
    const size = m && m.width > 0 ? Math.max(m.width, m.length) : 0;
    while (z > 0 && size * Math.pow(2, z - w.zoomMax) > Math.min(width, height) * 0.6) z--;
    return z;
  }
  /** Jump to a marker (from the search) at the closest zoom, also into another world. */
  function goTo(m) {
    // Already on a map of that dimension (e.g. the Nether ceiling): stay on it.
    const target = world?.base === m.world ? world : worlds.find((w) => w.key === m.world);
    if (!target) return;
    follow = false;
    worldKey = target.key;
    zoom = closest(target, m);
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
      l.here.map((a) => ({ layer: l, action: a, args: { world: mapKey, x: String(x), z: String(z) } })));
    if (!items.length) return;
    menu = { px, pz, title: `X ${x} · Z ${z}`, sub: world ? label(world) : '', color: null, ...split(items) };
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

  /** The cross hair: to the player at the closest zoom (also into their world), without a position to the spawn. */
  function aim() {
    const self = drawn.find((d) => d.self) ?? me;
    if (!self || !worlds.some((w) => w.key === self.world)) {
      toSpawn();
      return;
    }
    const target = worlds.find((w) => w.key === self.world);
    worldKey = target.key;
    zoom = closest(target);
    cx = self.x;
    cz = self.z;
  }

  /** The selected marker: its spot, the distance to the player (same world) and centring on it. */
  const spot = $derived(selected ? {
    x: Math.floor(selected.x + (selected.width > 0 ? selected.width / 2 : 0)),
    z: Math.floor(selected.z + (selected.width > 0 ? selected.length / 2 : 0)),
  } : null);
  const distance = $derived.by(() => {
    const self = drawn.find((d) => d.self) ?? me;
    if (!spot || !self || baseOf(self.world) !== selected.world) return null;
    return Math.round(Math.hypot(self.x - spot.x, self.z - spot.z));
  });

  function centerOn() {
    if (!spot) return;
    follow = false;
    zoom = closest(world, selected);
    cx = spot.x;
    cz = spot.z;
  }

  async function copySpot() {
    try {
      await navigator.clipboard.writeText(`${spot.x} ${spot.z}`);
      copied = true;
      setTimeout(() => (copied = false), 1500);
    } catch {
      // no clipboard (http, old browser) - nothing to do
    }
  }

  /** A plugin's lines "Label: value" as two columns, other lines as they are. */
  function line(text) {
    const i = text.indexOf(': ');
    return i > 0 && i < 28 ? { key: text.slice(0, i), value: text.slice(i + 2) } : { value: text };
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
    {@const toggles = [...new Set(g.rows.filter((r) => r.layer?.toggle).map((r) => toggleName(r.layer.toggle.label)))]}
    <div class="lgroup">
      <div class="ghead">
        <button class="gopen" aria-expanded={open} onclick={() => toggleGroup(g.key)}>
          <span class="small-caret" class:up={open} aria-hidden="true">▸</span>
          <span class="gt">{g.title}</span>
          <span class="count">{onCount}/{g.rows.length}</span>
        </button>
        <button class="check" class:on={onCount === g.rows.length} class:some={onCount > 0 && onCount < g.rows.length}
          aria-label={t('map.allOn')} title={t('map.allOn')} onclick={() => toggleAll(g)}>
          {onCount === g.rows.length ? '✓' : onCount > 0 ? '–' : ''}
        </button>
      </div>
      {#if open}
        <div class="gbody">
          {#each g.rows as row (row.key)}
            {@const on = rowOn(row)}
            <div class="lrow" class:off={!on} style="--c:{row.color ?? 'var(--muted)'}">
              <button class="lname" aria-pressed={on} onclick={() => toggleRow(row)}>
                {#if row.grid}
                  <svg class="gicon" viewBox="0 0 16 16" width="11" height="11" aria-hidden="true">
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
                <span class="check" class:on aria-hidden="true">{#if on}✓{/if}</span>
              </button>
              {#if row.layer?.toggle}
                {@const l = row.layer}
                <button class="ltoggle" class:on={l.toggle.on} disabled={busy} aria-pressed={l.toggle.on}
                  aria-label={l.toggle.label} title={l.toggle.label}
                  onclick={() => run(l, { id: l.toggle.action }, { on: String(!l.toggle.on) })}></button>
              {:else if toggles.length}
                <span class="ltoggle none" aria-hidden="true"></span>
              {/if}
            </div>
          {/each}
          {#if toggles.length}
            <p class="legend"><span class="ltoggle on" aria-hidden="true"></span>= {toggles.join(', ')}</p>
          {/if}
        </div>
      {/if}
    </div>
  {:else}
    <p class="muted small empty">{t('map.noLayers')}</p>
  {/each}
{/snippet}

{#snippet glyph(name, size = 16)}
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d={ICONS[name] ?? ''} /></svg>
{/snippet}

<div class="mapview" class:phone>
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="map" bind:this={box} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}
    onpointerleave={() => (cursor = null)} onwheel={wheel} oncontextmenu={(e) => context(e, null)}>
    <!-- keyed by the place, not the address: a newer picture swaps in without a blank moment -->
    {#each tiles as tile (tile.key)}
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

  {#snippet handle(id)}
    {#if !phone}
      <button class="handle" aria-label={t('map.drag')} title={t('map.drag')} onpointerdown={(e) => grab(e, id)}
        onpointermove={dragDock} onpointerup={dropDock} onpointercancel={dropDock}>
        <svg viewBox="0 0 16 6" width="16" height="6" aria-hidden="true">
          <circle cx="2" cy="1.5" r="1.2" /><circle cx="8" cy="1.5" r="1.2" /><circle cx="14" cy="1.5" r="1.2" />
          <circle cx="2" cy="4.5" r="1.2" /><circle cx="8" cy="4.5" r="1.2" /><circle cx="14" cy="4.5" r="1.2" />
        </svg>
      </button>
    {/if}
  {/snippet}

  <!-- floating panels: each can be dragged by its handle (not on phones) and snaps to the corners -->
  <div class="dock d-search" class:moving={moving?.id === 'search'} class:up={atBottom('search')} style={dockStyle('search')}
    use:measure={'search'}>
    {@render handle('search')}
    <div class="panel search">
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
            <span class="rs">{m.layer.name}{m.world !== mapKey ? ' · ' + worldName(m.world) : ''}</span>
          </button>
        {:else}
          <p class="muted small empty">{t('map.noResults')}</p>
        {/each}
      </div>
    {/if}
  </div>

  {#if !phone}
    <div class="dock d-layers" class:moving={moving?.id === 'layers'} style={dockStyle('layers')} use:measure={'layers'}>
      {@render handle('layers')}
      <div class="panel layers">
        <button class="phead" aria-expanded={layersOpen} title={t('map.hint')} onclick={() => (layersOpen = !layersOpen)}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="m12 3 9 5-9 5-9-5 9-5z" /><path d="m3 13 9 5 9-5" />
          </svg>
          <strong>{t('map.layers')}</strong>
          <span class="caret" class:up={layersOpen} aria-hidden="true">▾</span>
        </button>
        {#if layersOpen}
          <div class="pbody" transition:fly={{ y: -6, duration: 140 }}>
            {@render layerList()}
            {#if Object.keys(layout).length}
              <button class="reset" onclick={() => (layout = {})}>{t('map.resetLayout')}</button>
            {/if}
          </div>
        {/if}
      </div>
    </div>
  {/if}

  {#if worlds.length > 1}
    <div class="dock d-world" class:moving={moving?.id === 'world'} class:up={atBottom('world')} style={dockStyle('world')}
      use:measure={'world'}>
      {@render handle('world')}
      <div class="worldbox">
        <button class="panel pill" aria-haspopup="listbox" aria-expanded={worldOpen} onclick={() => (worldOpen = !worldOpen)}>
          <span class="wdot {world?.type ?? ''}" aria-hidden="true"></span>
          <span class="wname">{world ? label(world) : ''}</span>
          <span class="caret" class:up={worldOpen} aria-hidden="true">▾</span>
        </button>
        {#if worldOpen}
          <div class="panel wlist" role="listbox" transition:fly={{ y: -6, duration: 140 }}>
            {#each worlds as w (w.key)}
              <button role="option" aria-selected={w.key === worldKey} class:active={w.key === worldKey} onclick={() => pickWorld(w.key)}>
                <span class="wdot {w.type}" aria-hidden="true"></span>{label(w)}
              </button>
            {/each}
          </div>
        {/if}
      </div>
    </div>
  {/if}

  <!-- from top to bottom: (phone: layers) to me, follow, zoom in, zoom out -->
  <div class="dock d-controls" class:moving={moving?.id === 'controls'} style={dockStyle('controls')} use:measure={'controls'}>
    {@render handle('controls')}
    <div class="controls">
      {#if phone}
        <button class="panel round" class:on={phoneLayers} aria-label={t('map.layers')} onclick={() => { phoneLayers = !phoneLayers; selectedKey = null; }}>
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="m12 3 9 5-9 5-9-5 9-5z" /><path d="m3 13 9 5 9-5" />
          </svg>
        </button>
      {/if}
      <button class="panel round" aria-label={t(me ? 'map.toMe' : 'map.spawn')} title={t(me ? 'map.toMe' : 'map.spawn')} onclick={aim}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="12" cy="12" r="6" /><circle cx="12" cy="12" r="1.5" fill="currentColor" />
          <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
        </svg>
      </button>
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
    </div>
  </div>

  {#if !phone}
    <div class="dock d-coords" class:moving={moving?.id === 'coords'} style={dockStyle('coords')} use:measure={'coords'}>
      {@render handle('coords')}
      <div class="panel coords">
        {#if cursor}X {cursor.x} <span class="muted">·</span> Z {cursor.z}{:else}<span class="muted">X – · Z –</span>{/if}
      </div>
    </div>
  {/if}

  <!-- the selected marker; a sheet from below on phones -->
  {#if selected}
    <div class="dock d-detail" class:moving={moving?.id === 'detail'} style={dockStyle('detail')} use:measure={'detail'}
      transition:fly={{ y: phone ? 220 : 12, duration: 200 }}>
      {@render handle('detail')}
      <div class="panel detail" style="--c:{selected.color || selected.layer.color || 'var(--accent)'}">
        {#if phone}<span class="grip" aria-hidden="true"></span>{/if}
        <div class="dhead">
          <span class="dbadge" aria-hidden="true">
            {#if selected.width > 0}
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="4" y="4" width="16" height="16" rx="2" /></svg>
            {:else}
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2">
                <path d="M12 21s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" /><circle cx="12" cy="9" r="2.5" />
              </svg>
            {/if}
          </span>
          <div class="dtitle">
            <strong>{selected.label}</strong>
            <span class="crumb">{selected.layer.group || selected.layer.module} <span aria-hidden="true">›</span> {selected.layer.name}</span>
          </div>
          <button class="x" aria-label={t('map.center')} title={t('map.center')} onclick={centerOn}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <circle cx="12" cy="12" r="6" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
            </svg>
          </button>
          <button class="x" aria-label={t('common.close')} onclick={() => (selectedKey = null)}>✕</button>
        </div>

        <div class="facts">
          <button class="fact spotline" title={t('map.copy')} onclick={copySpot}>
            <span class="fk">{worldName(selected.world)}</span>
            <span class="fv mono">X {spot.x} · Z {spot.z}</span>
            <span class="copy" aria-hidden="true">{copied ? '✓' : '⧉'}</span>
          </button>
          {#if distance !== null}
            <div class="fact"><span class="fk">{t('map.distance')}</span><span class="fv">{t('map.away', { n: distance.toLocaleString() })}</span></div>
          {/if}
          {#if selected.width > 0}
            <div class="fact"><span class="fk">{t('map.area')}</span><span class="fv">{t('map.size', { w: selected.width, l: selected.length })}</span></div>
          {/if}
          {#each selected.details as text}
            {@const l = line(text)}
            <div class="fact" class:wide={!l.key}>
              {#if l.key}<span class="fk">{l.key}</span>{/if}<span class="fv">{l.value}</span>
            </div>
          {/each}
        </div>

        {#if selected.actions.length}
          {@const parts = split(selected.actions.map((a) => ({ layer: selected.layer, action: a })))}
          <div class="dactions">
            {#each parts.items as i (i.action.id)}
              <button class="mitem {i.action.tone}" disabled={busy} onclick={() => run(selected.layer, i.action, { marker: selected.id })}>
                <span class="micon">{#if ICONS[i.action.icon]}{@render glyph(i.action.icon, 15)}{/if}</span>{i.action.label}
              </button>
            {/each}
            {#if parts.danger.length}
              {#if parts.items.length}<span class="msep" aria-hidden="true"></span>{/if}
              {#each parts.danger as i (i.action.id)}
                <button class="mitem bad" disabled={busy} onclick={() => run(selected.layer, i.action, { marker: selected.id })}>
                  <span class="micon">{#if ICONS[i.action.icon]}{@render glyph(i.action.icon, 15)}{/if}</span>{i.action.label}
                </button>
              {/each}
            {/if}
          </div>
        {/if}
      </div>
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

  /* every floating panel sits in a dock: default places here, moved ones get them inline */
  .dock {
    position: absolute;
    z-index: 4;
  }

  .dock.moving {
    z-index: 8;
    opacity: 0.92;
    filter: drop-shadow(0 10px 24px rgb(0 0 0 / 0.35));
  }

  .d-search {
    top: var(--gap);
    left: var(--gap);
    width: 300px;
    z-index: 6;
  }

  .d-layers {
    top: calc(var(--gap) + 56px);
    left: var(--gap);
  }

  .d-world {
    top: var(--gap);
    right: var(--gap);
    z-index: 6;
  }

  .d-controls {
    right: var(--gap);
    bottom: var(--gap);
  }

  .d-coords {
    right: calc(var(--gap) + 54px);
    bottom: var(--gap);
  }

  .d-detail {
    left: var(--gap);
    bottom: var(--gap);
    z-index: 5;
  }

  /* lists of a panel moved to the bottom open upwards */
  .dock.up {
    display: flex;
    flex-direction: column-reverse;
  }

  .d-search {
    display: grid;
    gap: 8px;
  }

  .d-search.up {
    display: flex;
  }

  .d-search.up .results {
    margin-bottom: 8px;
  }

  .d-world.up .worldbox {
    display: flex;
    flex-direction: column-reverse;
    align-items: flex-end;
  }

  /* the handle to move a panel: shows above it on hover */
  .handle {
    position: absolute;
    top: -9px;
    left: 50%;
    z-index: 1;
    display: grid;
    place-items: center;
    width: 34px;
    height: 14px;
    padding: 0;
    translate: -50% 0;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--muted);
    cursor: grab;
    touch-action: none;
    opacity: 0;
    transition: opacity 0.12s;
  }

  .handle svg {
    fill: currentColor;
  }

  .dock:hover > .handle,
  .dock.moving > .handle,
  .handle:focus-visible {
    opacity: 1;
  }

  .handle:hover {
    color: var(--text);
    filter: none;
  }

  .dock.moving > .handle {
    cursor: grabbing;
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
    width: 236px;
    overflow: hidden;
  }

  .phead {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    width: 100%;
    padding: 8px 10px;
    border-radius: 0;
    background: transparent;
    color: var(--text);
    font-size: 0.88rem;
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
    padding: 0 5px 6px;
    max-height: min(60vh, 520px);
    overflow-y: auto;
  }

  /* layer tree: group head with its own switch, compact rows below */

  .lgroup + .lgroup {
    margin-top: 2px;
    padding-top: 2px;
    border-top: 1px solid var(--border);
  }

  .ghead {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 4px;
    padding-right: 5px;
  }

  .gopen {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    min-width: 0;
    padding: 5px 6px;
    border-radius: 7px;
    background: transparent;
    color: var(--muted);
    font-size: 0.7rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

  .gopen:hover {
    background: var(--surface-2);
    color: var(--text);
    filter: none;
  }

  .small-caret {
    width: 8px;
    font-size: 0.7rem;
    transition: transform 0.15s;
  }

  .small-caret.up {
    transform: rotate(90deg);
  }

  .gt {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .gbody {
    display: grid;
    padding: 0 0 3px 14px;
  }

  .lrow {
    display: flex;
    align-items: center;
    gap: 4px;
    padding-right: 5px;
  }

  .lname {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: flex-start;
    gap: 7px;
    min-width: 0;
    height: 28px;
    padding: 0 0 0 6px;
    border-radius: 7px;
    background: transparent;
    color: var(--text);
    font-size: 0.84rem;
    font-weight: 600;
  }

  .lname:hover {
    background: var(--surface-2);
    filter: none;
  }

  .lrow.off .lname {
    color: var(--muted);
  }

  .lrow.off .dot {
    opacity: 0.4;
  }

  .lt {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    margin-left: auto;
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .check {
    display: grid;
    place-items: center;
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 4px;
    font-size: 0.62rem;
    line-height: 1;
    color: #fff;
    background: transparent;
    border: 1.5px solid var(--border);
  }

  .check.on,
  .check.some {
    background: var(--accent);
    border-color: var(--accent);
  }

  .check.some {
    background: color-mix(in srgb, var(--accent) 55%, transparent);
  }

  button.check:hover {
    filter: brightness(1.1);
  }

  /* the plugin's switch of a layer (MRwaypoints: shown in Xaero) as a small dot */
  .ltoggle {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 50%;
    background: transparent;
    border: 1.5px solid var(--muted);
  }

  .ltoggle.on {
    border-color: var(--good);
    background: radial-gradient(circle, var(--good) 0 3.5px, transparent 4px);
  }

  .ltoggle.none {
    visibility: hidden;
  }

  button.ltoggle:hover:not(:disabled) {
    filter: none;
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 20%, transparent);
  }

  .legend {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 2px 6px 0;
    font-size: 0.7rem;
    color: var(--muted);
  }

  .legend .ltoggle {
    width: 12px;
    height: 12px;
    background: radial-gradient(circle, var(--good) 0 2.5px, transparent 3px);
  }

  .reset {
    justify-self: start;
    margin: 4px 6px 0;
    padding: 4px 8px;
    border-radius: 7px;
    font-size: 0.74rem;
    background: transparent;
    color: var(--accent);
  }

  .reset:hover {
    background: var(--surface-2);
    filter: none;
  }

  .gicon {
    flex-shrink: 0;
    fill: none;
    stroke: var(--muted);
    stroke-width: 1.6;
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
    padding: 5px 12px;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  /* details of the selected marker */

  .detail {
    display: grid;
    gap: 12px;
    width: 320px;
    max-height: calc(100vh - 160px);
    overflow-y: auto;
    padding: 12px;
  }

  .dhead {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .dbadge {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    flex-shrink: 0;
    border-radius: 10px;
    color: #fff;
    background: var(--c);
    box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.25);
  }

  .dtitle {
    display: grid;
    min-width: 0;
    flex: 1;
  }

  .dtitle strong {
    font-size: 1rem;
    line-height: 1.25;
    overflow-wrap: anywhere;
  }

  .crumb {
    font-size: 0.74rem;
    font-weight: 600;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .facts {
    display: grid;
    border: 1px solid var(--border);
    border-radius: 10px;
    overflow: hidden;
  }

  .fact {
    display: grid;
    grid-template-columns: 6.5rem minmax(0, 1fr) auto;
    align-items: baseline;
    gap: 8px;
    padding: 7px 10px;
    font-size: 0.84rem;
    text-align: left;
    border-radius: 0;
    background: transparent;
    color: var(--text);
  }

  .fact + .fact {
    border-top: 1px solid var(--border);
  }

  .fact.wide {
    grid-template-columns: minmax(0, 1fr);
  }

  .fk {
    font-size: 0.74rem;
    font-weight: 600;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .fv {
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  .mono {
    font-variant-numeric: tabular-nums;
  }

  .spotline {
    justify-content: stretch;
    cursor: copy;
  }

  .spotline:hover {
    background: var(--surface-2);
    filter: none;
  }

  .copy {
    color: var(--muted);
    font-size: 0.85rem;
  }

  .dactions {
    display: grid;
    gap: 1px;
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

  .phone .d-search {
    width: auto;
  }

  .phone .d-detail {
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 7;
  }

  .phone .detail,
  .phone .sheet-layers {
    width: auto;
    max-height: 70vh;
    padding: 14px 16px calc(16px + env(safe-area-inset-bottom, 0px));
    border-radius: 18px 18px 0 0;
    border-bottom: none;
  }

  .phone .detail {
    border-top: 4px solid var(--c, var(--border));
  }

  .sheet-layers {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 7;
    display: grid;
    gap: 10px;
    overflow-y: auto;
  }

  .phone .sheet-layers .pbody {
    padding: 0;
    max-height: none;
  }
</style>
