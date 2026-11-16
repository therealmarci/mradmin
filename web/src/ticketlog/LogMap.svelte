<script>
  // The map of a ticket log, played by the shared timeline (time = its cursor): the creator's last minutes of play -
  // position every second (track), entities every 5 seconds (frames) - and at the end the moment the ticket was created
  // (log.area). Between two recordings the player glides and the entities fade over, so it plays like a video.
  // Zoom (wheel, buttons) and drag inside the 16 x 16 chunks around the player; "follow" keeps him in the middle.
  // Right of it: tiles for the moment (player, around, server) and the chronicle - everything that happened with its
  // time; the current row is marked and scrolls along, a click jumps there.
  // Part of the shared ticket log view (MRadmin and the MRtechnology website show the same).
  //
  // log: the whole snapshot   lang   time: the moment (ms)   onseek(ms): jump   mark: optional rectangle {x0, z0, x1, z1, label}
  import { tl } from './texts.js';
  import { safeImage, num } from './safe.js';

  let { log, lang, time, onseek = () => {}, mark = null } = $props();
  const T = (k, v) => tl(lang, k, v);

  const AREA = 256; // the recorded area around the player: 16 x 16 chunks
  const area = $derived(log.area ?? null);
  const timeline = $derived(log.timeline ?? null);
  const minutes = $derived(timeline?.minutes ?? []);
  const types = $derived(timeline?.types ?? []);
  const cats = $derived(timeline?.categories ?? []);
  const chunkData = $derived(timeline?.chunks ?? []);
  const tiles = $derived(Object.values(timeline?.tiles ?? {}).map((t) => ({ ...t, image: safeImage(t.image) })).filter((t) => t.image));
  const areaImage = $derived(safeImage(area?.image));
  const created = $derived(!!area && time >= log.time - 500);

  // ------------------------------------------------------------- recordings
  const frames = $derived(minutes.flatMap((m) => (m.frames ?? []).map((f) => ({
    time: num(f[0]), world: String(f[1]), x: num(f[2]), z: num(f[3]), refs: Array.isArray(f[4]) ? f[4] : [], hand: f[5] ? String(f[5]) : null,
  }))));
  // every second: time, world, x, y, z, health, food (older logs: the 5-second frames instead)
  const track = $derived.by(() => {
    const rows = minutes.flatMap((m) => m.track ?? []).map((r) => ({ time: num(r[0]), world: String(r[1]), x: num(r[2]), y: num(r[3], null), z: num(r[4]), health: num(r[5], null), food: num(r[6], null) }));
    return rows.length ? rows : frames.map((f) => ({ time: f.time, world: f.world, x: f.x, y: null, z: f.z, health: null, food: null }));
  });
  /** Index of the last row with row.time <= t (-1 if none). */
  function before(list, t) {
    let lo = 0, hi = list.length - 1, found = -1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (list[mid].time <= t) { found = mid; lo = mid + 1; } else hi = mid - 1;
    }
    return found;
  }

  // where the player is right now: between two track rows in a straight line (not over a teleport or world change)
  const ti = $derived(before(track, time));
  const here = $derived.by(() => {
    if (created) return { world: String(area.world), x: num(area.center?.[0]), z: num(area.center?.[1]), row: track[ti] ?? null };
    const a = track[Math.max(0, ti)];
    if (!a) return area ? { world: String(area.world), x: num(area.center?.[0]), z: num(area.center?.[1]), row: null } : null;
    const b = track[ti + 1];
    if (ti < 0 || !b || b.world !== a.world || b.time - a.time > 6000 || Math.hypot(b.x - a.x, b.z - a.z) > 48) return { world: a.world, x: a.x, z: a.z, row: a };
    const f = (time - a.time) / (b.time - a.time);
    return { world: a.world, x: a.x + (b.x - a.x) * f, z: a.z + (b.z - a.z) * f, row: a };
  });

  // entities: the frame before and the one after, faded over
  const fi = $derived(before(frames, time));
  function groups(refs) {
    const out = [];
    for (const id of refs) {
      const g = chunkData[id] ?? [];
      for (let i = 0; i + 4 < g.length; i += 5) out.push([types[g[i]] ?? '?', cats[g[i + 1]] ?? 'other', num(g[i + 2]), num(g[i + 3]), num(g[i + 4])]);
    }
    return out;
  }
  const entNow = $derived(created ? area?.entities ?? [] : fi >= 0 ? groups(frames[fi].refs) : []);
  const entNext = $derived(!created && fi >= 0 && frames[fi + 1] && frames[fi + 1].world === frames[fi].world ? groups(frames[fi + 1].refs) : null);
  const fade = $derived(entNext ? Math.max(0, Math.min(1, (time - frames[fi].time) / Math.max(1, frames[fi + 1].time - frames[fi].time))) : 0);

  // despawned items and deaths of the last 20 seconds (older ones fade out)
  const WINDOW = 20000;
  const allDespawns = $derived(minutes.flatMap((m) => m.despawnRows ?? []));
  const allDeaths = $derived(minutes.flatMap((m) => m.deaths ?? []));
  const despawnRows = $derived(created ? area?.despawned?.rows ?? [] : allDespawns.filter((r) => r[0] <= time && r[0] > time - WINDOW));
  const deathRows = $derived(created ? area?.deaths?.rows ?? [] : allDeaths.filter((d) => d.time <= time && d.time > time - WINDOW));
  const age = (t) => (created ? 1 : Math.max(0.25, 1 - (time - t) / WINDOW));

  // the way up to now in this world, the last minute stronger
  const trail = $derived.by(() => {
    if (ti < 0 || created) return { old: '', recent: '' };
    const world = track[ti].world;
    const old = [], recent = [];
    for (let i = 0; i <= ti; i++) {
      const r = track[i];
      if (r.world !== world) { old.length = 0; recent.length = 0; continue; }
      (track[ti].time - r.time > 60000 ? old : recent).push(`${r.x + 0.5},${r.z + 0.5}`);
    }
    if (old.length && recent.length) recent.unshift(old.at(-1));
    return { old: old.join(' '), recent: recent.join(' ') };
  });

  // ------------------------------------------------------------------- view
  let zoom = $state(1); // 1 = the whole 16 x 16 chunks, up to 8
  let off = $state({ x: 0, z: 0 }); // shift from the player while not following
  let follow = $state(true);
  const size = $derived(AREA / zoom);
  const limit = $derived((AREA - size) / 2);
  const clampOff = (o) => ({ x: Math.max(-limit, Math.min(limit, o.x)), z: Math.max(-limit, Math.min(limit, o.z)) });
  const center = $derived(here ? { x: here.x + 0.5 + (follow ? 0 : clampOff(off).x), z: here.z + 0.5 + (follow ? 0 : clampOff(off).z) } : { x: 0, z: 0 });
  const view = $derived({ x: center.x - size / 2, z: center.z - size / 2 });
  const k = $derived(1 / Math.pow(zoom, 0.65)); // marks grow slower than the map
  const shownTiles = $derived(here ? tiles.filter((t) => t.world === here.world && t.x0 < view.x + size && t.x0 + 256 > view.x && t.z0 < view.z + size && t.z0 + 256 > view.z) : []);

  function setZoom(z) {
    zoom = Math.max(1, Math.min(8, z));
    off = clampOff(off);
  }
  let svg = $state();
  let drag = null;
  function down(e) {
    if (zoom === 1) return;
    drag = { x: e.clientX, z: e.clientY, off: follow ? { x: 0, z: 0 } : clampOff(off) };
    svg.setPointerCapture(e.pointerId);
  }
  function move(e) {
    if (!drag) return;
    const scale = size / svg.getBoundingClientRect().width;
    follow = false;
    off = clampOff({ x: drag.off.x - (e.clientX - drag.x) * scale, z: drag.off.z - (e.clientY - drag.z) * scale });
  }
  function wheelZoom(node) {
    const wheel = (e) => {
      e.preventDefault();
      setZoom(zoom * (e.deltaY < 0 ? 1.25 : 0.8));
    };
    node.addEventListener('wheel', wheel, { passive: false });
    return { destroy: () => node.removeEventListener('wheel', wheel) };
  }

  let layers = $state({ entities: true, despawned: true, deaths: true, path: true });
  const COLORS = { hostile: 'var(--bad)', passive: 'var(--good)', item: 'var(--warn)', projectile: 'var(--muted)', vehicle: 'var(--muted)', other: 'var(--muted)' };
  const radius = (n) => Math.min(4, 0.9 + Math.log2(num(n) + 1) * 0.6) * k;
  const name = (type) => String(type).replace(/^item:/, '').replace(/^player:/, '');
  const clock = (t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  // ----------------------------------------------------------- the moment
  const health = $derived.by(() => {
    const f = log.health?.fields ?? [];
    return (log.health?.rows ?? []).map((r) => Object.fromEntries(f.map((key, i) => [key, r[i]])));
  });
  const serverNow = $derived(health[Math.max(0, before(health, time))] ?? null);
  const around = $derived.by(() => {
    const sum = {};
    for (const [type, , , , n] of entNow) sum[type] = (sum[type] ?? 0) + n;
    const list = Object.entries(sum).sort((a, b) => b[1] - a[1]);
    return { total: list.reduce((a, [, n]) => a + n, 0), top: list.slice(0, 4) };
  });
  const hand = $derived(created ? log.player?.hand ?? null : fi >= 0 ? frames[fi].hand : null);
  const player = $derived(here?.row ?? null);
  const hp = $derived(created ? num(log.player?.health, null) : player?.health ?? null);
  const food = $derived(created ? num(log.player?.food, null) : player?.food ?? null);

  // -------------------------------------------------------- the chronicle
  const KINDS = {
    broken: ['act', '⛏'], placed: ['act', '▣'], picked: ['act', '↑'], dropped: ['act', '↓'], command: ['act', '/'],
    taken: ['fight', '♥'], dealt: ['fight', '⚔'], death: ['fight', '☠'],
    came: ['around', '→'], went: ['around', '←'], despawn: ['around', '✕'], mobdeath: ['around', '◆'],
    join: ['move', '●'], quit: ['move', '○'], teleport: ['move', '↷'],
  };
  const chronicle = $derived.by(() => {
    const rows = [];
    const add = (t, kind, text) => KINDS[kind] && rows.push({ time: num(t), kind, group: KINDS[kind][0], icon: KINDS[kind][1], text });
    for (const m of minutes) {
      for (const [t, kind, what, n] of m.actions ?? []) {
        if (kind === 'came' || kind === 'went' || kind === 'command') add(t, kind, T('c_' + kind, { what: String(what) }));
        else add(t, kind, T('c_' + kind, { n: num(n), what: name(what) }));
      }
      for (const e of m.events ?? []) {
        const kind = ['join', 'quit', 'death', 'teleport'].includes(e.kind) ? e.kind : 'join';
        add(e.time, kind, T('ev_' + kind) + (e.text ? ' · ' + e.text : ''));
      }
      // around: same item / same kind within 5 seconds as one row
      const merged = new Map();
      for (const [t, , , item, n] of m.despawnRows ?? []) {
        const key = 'd' + item + Math.floor(num(t) / 5000);
        const row = merged.get(key) ?? { time: num(t), kind: 'despawn', what: name(item), n: 0 };
        row.n += num(n);
        merged.set(key, row);
      }
      for (const d of m.deaths ?? []) {
        if (String(d.type).startsWith('player:')) continue;
        const key = 'm' + d.type + Math.floor(num(d.time) / 5000);
        const row = merged.get(key) ?? { time: num(d.time), kind: 'mobdeath', what: name(d.type), n: 0 };
        row.n += 1;
        merged.set(key, row);
      }
      for (const r of merged.values()) add(r.time, r.kind, T('c_' + r.kind, { n: r.n, what: r.what }));
      // older logs without timed actions: the sums of the minute at its start
      if (!m.actions) {
        for (const kind of ['broken', 'placed', 'picked', 'dropped']) for (const [what, n] of Object.entries(m[kind] ?? {})) add(m.start, kind, T('c_' + kind, { n, what: name(what) }));
        for (const c of m.commands ?? []) add(m.start, 'command', T('c_command', { what: String(c) }));
      }
    }
    return rows.sort((a, b) => a.time - b.time);
  });
  const GROUPS = ['act', 'fight', 'around', 'move'];
  let shown = $state({ act: true, fight: true, around: true, move: true });
  const rows = $derived(chronicle.filter((r) => shown[r.group]));
  const current = $derived(before(rows, time));

  let list = $state();
  $effect(() => {
    // keep the current row in view - only inside the list, the page does not move
    const el = list?.querySelector(`[data-i="${current}"]`);
    if (!el) return;
    const top = el.offsetTop;
    if (top < list.scrollTop || top > list.scrollTop + list.clientHeight - el.offsetHeight) list.scrollTop = top - list.clientHeight / 2;
  });
</script>

{#if here}
  <div class="lmap">
    <div class="mapcol">
      <div class="mapbox">
        <svg bind:this={svg} viewBox="{view.x} {view.z} {size} {size}" preserveAspectRatio="none" role="application" aria-label={T('area')} use:wheelZoom
          class:grab={zoom > 1} onpointerdown={down} onpointermove={move} onpointerup={() => (drag = null)}>
          <defs>
            <pattern id="tlog-nodata" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <rect width="8" height="8" class="nodata-bg" /><line x1="0" y1="0" x2="0" y2="8" class="nodata-line" />
            </pattern>
          </defs>
          <rect x={view.x} y={view.z} width={size} height={size} fill="url(#tlog-nodata)"><title>{T('noTerrain')}</title></rect>
          {#if areaImage && area.world === here.world && !created}
            <image href={areaImage} x={area.x0} y={area.z0} width={area.size} height={area.size} preserveAspectRatio="none" class="tile" />
          {/if}
          {#each shownTiles as t}
            <image href={t.image} x={t.x0} y={t.z0} width="256" height="256" preserveAspectRatio="none" class="tile" />
          {/each}
          {#if created && areaImage}
            <image href={areaImage} x={area.x0} y={area.z0} width={area.size} height={area.size} preserveAspectRatio="none" class="tile" />
          {/if}
          {#if mark}
            <rect x={mark.x0} y={mark.z0} width={mark.x1 - mark.x0 + 1} height={mark.z1 - mark.z0 + 1} class="mark" style="stroke-width: {1.5 * k}"><title>{mark.label}</title></rect>
          {/if}
          {#if layers.path}
            {#if trail.old}<polyline points={trail.old} class="path old" style="stroke-width: {1.2 * k}" />{/if}
            {#if trail.recent}<polyline points={trail.recent + ` ${here.x + 0.5},${here.z + 0.5}`} class="path" style="stroke-width: {1.6 * k}" />{/if}
          {/if}
          {#if layers.entities}
            {#each entNow as [type, cat, x, z, n]}
              <circle cx={x + 1} cy={z + 1} r={radius(n)} fill={COLORS[cat] ?? COLORS.other} class="ent" opacity={1 - fade}><title>{name(type)} × {n}</title></circle>
            {/each}
            {#each entNext ?? [] as [type, cat, x, z, n]}
              <circle cx={x + 1} cy={z + 1} r={radius(n)} fill={COLORS[cat] ?? COLORS.other} class="ent" opacity={fade}><title>{name(type)} × {n}</title></circle>
            {/each}
          {/if}
          {#if layers.despawned}
            {#each despawnRows as [t, x, z, item, amount]}
              {@const s = 1.3 * k}
              <path d="M{num(x) + 0.5 - s},{num(z) + 0.5 - s}l{2 * s},{2 * s}M{num(x) + 0.5 + s},{num(z) + 0.5 - s}l{-2 * s},{2 * s}" class="desp" style="stroke-width: {0.8 * k}" opacity={age(t)}><title>{clock(t)} · {item} × {amount}</title></path>
            {/each}
          {/if}
          {#if layers.deaths}
            {#each deathRows as d}
              <rect x={num(d.x) + 0.5 - 1.5 * k} y={num(d.z) + 0.5 - 1.5 * k} width={3 * k} height={3 * k} class="death" class:pl={String(d.type).startsWith('player:')}
                opacity={age(d.time)} transform="rotate(45 {num(d.x) + 0.5} {num(d.z) + 0.5})" style="stroke-width: {0.5 * k}"><title>{clock(d.time)} · {name(d.type)}</title></rect>
            {/each}
          {/if}
          {#if created}
            {#each area?.players ?? [] as [pname, x, z]}<circle cx={num(x) + 0.5} cy={num(z) + 0.5} r={2.6 * k} class="other" style="stroke-width: {0.8 * k}"><title>{pname}</title></circle>{/each}
          {/if}
          <circle cx={here.x + 0.5} cy={here.z + 0.5} r={3.4 * k} class="me" style="stroke-width: {1.2 * k}"><title>{Math.round(here.x)} {Math.round(here.z)}</title></circle>
        </svg>
        <div class="tools">
          <button onclick={() => setZoom(zoom * 1.5)} aria-label={T('zoomIn')} title={T('zoomIn')} disabled={zoom >= 8}>+</button>
          <button onclick={() => setZoom(zoom / 1.5)} aria-label={T('zoomOut')} title={T('zoomOut')} disabled={zoom <= 1}>−</button>
          <button class:on={follow} onclick={() => { follow = true; off = { x: 0, z: 0 }; }} title={T('follow')} aria-label={T('follow')}>⌖</button>
        </div>
        {#if !created && track.length && time < track[0].time}
          <button class="starts" onclick={() => onseek(track[0].time)}>{T('recordingFrom', { time: clock(track[0].time) })} →</button>
        {/if}
        <span class="coords">{here.world} · {Math.round(here.x)} {Math.round(here.z)}{zoom > 1 ? ` · ${Math.round(zoom * 10) / 10}×` : ''}</span>
      </div>
      <div class="legend">
        {#each [['entities', T('layerEntities')], ['despawned', T('layerDespawned')], ['deaths', T('layerDeaths')], ['path', T('layerPath')]] as [id, label]}
          <button class:on={layers[id]} onclick={() => (layers[id] = !layers[id])}>
            {#if id === 'entities'}<i class="dot" style="background: var(--good)"></i>{:else if id === 'despawned'}✕{:else if id === 'deaths'}◆{:else}<i class="line"></i>{/if}
            {label}
          </button>
        {/each}
        <span class="muted small keys"><i class="dot" style="background: var(--bad)"></i>{T('hostile')} <i class="dot" style="background: var(--good)"></i>{T('passive')} <i class="dot" style="background: var(--warn)"></i>{T('item')}</span>
      </div>
    </div>

    <div class="side">
      <div class="moment">
        <div class="mt">
          <span>{T('player')}</span>
          <b>{[hp != null && `❤ ${hp}`, food != null && `🍗 ${food}`].filter(Boolean).join("  ·  ") || "-"}</b>
          <small>{hand ? name(hand) : T('emptyHand')}{player?.y != null ? ` · Y ${player.y}` : ''}</small>
        </div>
        <div class="mt">
          <span>{T('around')}</span>
          <b>{around.total} {T('entities')}</b>
          <small>{around.top.map(([type, n]) => `${n} ${name(type)}`).join(', ') || T('none')}</small>
        </div>
        <div class="mt">
          <span>{T('server')}</span>
          {#if serverNow}
            <b class:bad={serverNow.tps < 15} class:warn={serverNow.tps >= 15 && serverNow.tps < 19}>{num(serverNow.tps).toFixed(1)} TPS</b>
            <small>{T('mspt')} {num(serverNow.mspt).toFixed(1)} ms · max {Math.round(num(serverNow.msptMax))} ms</small>
          {:else}<b>-</b><small>{T('mNoServer')}</small>{/if}
        </div>
      </div>

      <div class="chron">
        <div class="chead">
          <h4>{T('chronicle')}</h4>
          <div class="filters">
            {#each GROUPS as g}<button class:on={shown[g]} onclick={() => (shown[g] = !shown[g])}>{T('g_' + g)}</button>{/each}
          </div>
        </div>
        <div class="rows" bind:this={list}>
          {#each rows as r, i}
            <button class="crow {r.group}" class:now={i === current} class:future={r.time > time} data-i={i} onclick={() => onseek(r.time)}>
              <span class="ct">{clock(r.time)}</span><span class="ci">{r.icon}</span><span class="cx">{r.text}</span>
            </button>
          {:else}
            <p class="muted small empty">{T('noChronicle')}</p>
          {/each}
        </div>
      </div>
    </div>

    {#if created && area?.inventory}
      <div class="inv-wrap">
        <h4>{T('inventory', { name: area.player })}</h4>
        <div class="inv">
          {#each area.inventory as [slot, item, amount, extra]}
            <span title={`${T('slot')} ${slot}`}>{item}{amount > 1 ? ' × ' + amount : ''}{#if extra}<small> {extra}</small>{/if}</span>
          {:else}<span class="muted small">{T('none')}</span>{/each}
        </div>
      </div>
    {:else if area?.inventory}
      <p class="muted small inv-hint">{T('inventoryAtEnd')}</p>
    {/if}
  </div>
{:else}
  <p class="muted small">{T('noMap')}</p>
{/if}

<style>
  .lmap {
    display: grid;
    grid-template-columns: minmax(0, 1.25fr) minmax(280px, 1fr);
    gap: 14px;
    min-width: 0;
  }

  @media (max-width: 860px) {
    .lmap {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  h4 {
    margin: 0;
    font-size: 0.82rem;
  }

  p {
    margin: 0;
  }

  .mapcol {
    display: grid;
    gap: 8px;
    align-content: start;
    min-width: 0;
  }

  .mapbox {
    position: relative;
    aspect-ratio: 1;
    border-radius: 12px;
    overflow: hidden;
    background: var(--surface-2);
  }

  svg {
    display: block;
    width: 100%;
    height: 100%;
    touch-action: none;
  }

  svg.grab {
    cursor: grab;
  }

  svg.grab:active {
    cursor: grabbing;
  }

  .tile {
    image-rendering: pixelated;
  }

  .nodata-bg {
    fill: var(--surface-2);
  }

  .nodata-line {
    stroke: color-mix(in srgb, var(--muted) 25%, transparent);
    stroke-width: 2;
  }

  .mark {
    fill: color-mix(in srgb, var(--accent) 10%, transparent);
    stroke: var(--accent);
    stroke-dasharray: 3 2;
  }

  .path {
    fill: none;
    stroke: var(--accent);
    stroke-linejoin: round;
    stroke-linecap: round;
  }

  .path.old {
    opacity: 0.4;
  }

  .ent {
    stroke: rgba(0, 0, 0, 0.45);
    stroke-width: 0.3;
  }

  .desp {
    stroke: var(--warn);
  }

  .death {
    fill: var(--muted);
    stroke: #000;
  }

  .death.pl {
    fill: var(--bad);
  }

  .other {
    fill: #fff;
    stroke: var(--accent);
  }

  .me {
    fill: var(--accent);
    stroke: #fff;
  }

  .tools {
    position: absolute;
    top: 10px;
    right: 10px;
    display: grid;
    gap: 4px;
  }

  .tools button {
    width: 32px;
    height: 32px;
    padding: 0;
    font-size: 1rem;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: color-mix(in srgb, var(--surface) 88%, transparent);
    color: var(--text);
  }

  .tools button.on {
    color: var(--accent);
    border-color: var(--accent);
  }

  .starts {
    position: absolute;
    left: 50%;
    top: 12px;
    transform: translateX(-50%);
    padding: 6px 12px;
    font-size: 0.78rem;
    border-radius: 999px;
    box-shadow: var(--shadow);
  }

  .coords {
    position: absolute;
    left: 10px;
    bottom: 10px;
    padding: 3px 8px;
    border-radius: 6px;
    font-size: 0.74rem;
    background: color-mix(in srgb, var(--surface) 85%, transparent);
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .legend button {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 3px 9px;
    font-size: 0.74rem;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

  .legend button.on {
    color: var(--text);
    border-color: color-mix(in srgb, var(--accent) 60%, var(--border));
  }

  .keys {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
  }

  .dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .legend .line {
    display: inline-block;
    width: 12px;
    height: 2px;
    background: var(--accent);
  }

  .side {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
  }

  .moment {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .mt {
    display: grid;
    gap: 2px;
    align-content: start;
    min-width: 0;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .mt span {
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--muted);
  }

  .mt b {
    font-size: 0.92rem;
    white-space: nowrap;
  }

  .mt small {
    font-size: 0.72rem;
    color: var(--muted);
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .bad {
    color: var(--bad);
  }

  .warn {
    color: var(--warn);
  }

  .chron {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-height: 0;
    border-radius: 12px;
    border: 1px solid var(--border);
    padding: 10px 0 0;
  }

  .chead {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 0 10px;
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    gap: 3px;
  }

  .filters button {
    padding: 2px 8px;
    font-size: 0.7rem;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

  .filters button.on {
    color: var(--text);
    border-color: color-mix(in srgb, var(--accent) 60%, var(--border));
  }

  .rows {
    position: relative;
    flex: 1;
    min-height: 220px;
    max-height: 460px;
    overflow: auto;
    padding: 0 4px 6px;
  }

  .crow {
    display: grid;
    grid-template-columns: auto 1.2rem minmax(0, 1fr);
    justify-content: flex-start;
    align-items: baseline;
    gap: 8px;
    width: 100%;
    padding: 4px 6px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--text);
    font-size: 0.78rem;
    font-weight: 400;
    text-align: left;
  }

  .crow:hover {
    background: var(--surface-2);
  }

  .crow.future {
    opacity: 0.45;
  }

  .crow.now {
    background: color-mix(in srgb, var(--accent) 16%, transparent);
    opacity: 1;
  }

  .ct {
    color: var(--muted);
    font-variant-numeric: tabular-nums;
    font-size: 0.72rem;
  }

  .ci {
    text-align: center;
    color: var(--muted);
  }

  .crow.fight .ci {
    color: var(--bad);
  }

  .crow.act .ci {
    color: var(--accent);
  }

  .cx {
    overflow-wrap: anywhere;
  }

  .empty {
    padding: 10px 6px;
  }

  .inv-wrap {
    grid-column: 1 / -1;
    display: grid;
    gap: 6px;
  }

  .inv-hint {
    grid-column: 1 / -1;
  }

  .inv {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .inv span {
    font-size: 0.76rem;
    padding: 2px 8px;
    border-radius: 6px;
    background: var(--surface-2);
  }

  .inv small {
    color: var(--muted);
  }

  @media (max-width: 560px) {
    .moment {
      grid-template-columns: minmax(0, 1fr);
    }

    .keys {
      margin-left: 0;
    }
  }
</style>
