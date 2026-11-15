<script>
  // The surroundings of a ticket log: the 16 x 16 chunks around the player (or the place of the ticket) from above,
  // with every entity (grouped), items that despawned, deaths, the player's way and other players - each a layer to
  // switch on and off. Below short lists instead of masses of points: entities by kind, despawned items, deaths and
  // the inventory. Part of the shared ticket log view (MRadmin and the MRtechnology website show the same).
  //
  // area: log.area   lang: "de"/"en"   minutes: span of the log   mark: optional rectangle {x0, z0, x1, z1, label}
  import { tl } from './texts.js';

  let { area, lang, minutes, mark = null } = $props();
  const T = (k, v) => tl(lang, k, v);

  const x0 = $derived(area.x0);
  const z0 = $derived(area.z0);
  const size = $derived(area.size);

  let layers = $state({ entities: true, despawned: true, deaths: true, path: true, players: true });

  const COLORS = { hostile: 'var(--bad)', passive: 'var(--good)', item: 'var(--warn)', projectile: 'var(--muted)', vehicle: 'var(--muted)', other: 'var(--muted)' };
  const entities = $derived(area.entities ?? []);
  const despawnedRows = $derived(area.despawned?.rows ?? []);
  const deathRows = $derived(area.deaths?.rows ?? []);
  const players = $derived(area.players ?? []);

  // the way as lines - a point without coordinates (another world) breaks it
  const pathLines = $derived.by(() => {
    const lines = [];
    let current = [];
    for (const p of area.path ?? []) {
      if (p.length < 3) {
        if (current.length > 1) lines.push(current);
        current = [];
      } else current.push(p);
    }
    if (current.length > 1) lines.push(current);
    return lines;
  });
  const pathCount = $derived((area.path ?? []).filter((p) => p.length === 3).length);

  const byKind = $derived.by(() => {
    const sum = {};
    for (const [type, cat, , , n] of entities) {
      sum[type] ??= { n: 0, cat };
      sum[type].n += n;
    }
    return Object.entries(sum).sort((a, b) => b[1].n - a[1].n).slice(0, 12);
  });

  const radius = (n) => Math.min(4, 0.9 + Math.log2(n + 1) * 0.6);
  const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const name = (type) => type.replace(/^item:/, '').replace(/^player:/, '');
</script>

<div class="lmap">
  <div class="lhead">
    <div>
      <h3>{T('area')}</h3>
      <p class="muted small">{T('areaHint', { size, who: area.player ?? T('thePlace') })} · {area.world}</p>
    </div>
    <div class="layers">
      <button class:on={layers.entities} onclick={() => (layers.entities = !layers.entities)}>{T('layerEntities')} <b>{entities.reduce((a, e) => a + e[4], 0)}</b></button>
      <button class:on={layers.despawned} onclick={() => (layers.despawned = !layers.despawned)}>{T('layerDespawned')} <b>{area.despawned?.total ?? 0}</b></button>
      <button class:on={layers.deaths} onclick={() => (layers.deaths = !layers.deaths)}>{T('layerDeaths')} <b>{area.deaths?.total ?? 0}</b></button>
      {#if area.path}<button class:on={layers.path} onclick={() => (layers.path = !layers.path)}>{T('layerPath')} <b>{pathCount}</b></button>{/if}
      <button class:on={layers.players} onclick={() => (layers.players = !layers.players)}>{T('layerPlayers')} <b>{players.length}</b></button>
    </div>
  </div>

  <div class="mapbox">
    {#if area.image}
      <img src={area.image} alt="" draggable="false" />
    {:else}
      <p class="muted small nomap">{T('noMap')}</p>
    {/if}
    <svg viewBox="{x0} {z0} {size} {size}" preserveAspectRatio="none" aria-label={T('area')}>
      {#if mark}
        <rect x={mark.x0} y={mark.z0} width={mark.x1 - mark.x0 + 1} height={mark.z1 - mark.z0 + 1} class="mark"><title>{mark.label}</title></rect>
      {/if}
      {#if layers.path}
        {#each pathLines as line}
          <polyline points={line.map((p) => `${p[1] + 0.5},${p[2] + 0.5}`).join(' ')} class="path" />
          {#each line as p}<circle cx={p[1] + 0.5} cy={p[2] + 0.5} r="0.8" class="pathdot"><title>{clock(p[0])} · {p[1]} {p[2]}</title></circle>{/each}
        {/each}
      {/if}
      {#if layers.entities}
        {#each entities as [type, cat, x, z, n]}
          <circle cx={x + 1} cy={z + 1} r={radius(n)} fill={COLORS[cat] ?? COLORS.other} class="ent"><title>{name(type)} × {n} · {x} {z}</title></circle>
        {/each}
      {/if}
      {#if layers.despawned}
        {#each despawnedRows as [time, x, z, item, amount]}
          <path d="M{x - 0.8},{z - 0.8}l{2.6},{2.6}M{x + 1.8},{z - 0.8}l{-2.6},{2.6}" class="desp"><title>{clock(time)} · {item} × {amount} · {x} {z}</title></path>
        {/each}
      {/if}
      {#if layers.deaths}
        {#each deathRows as d}
          <rect x={d.x - 1} y={d.z - 1} width="3" height="3" class="death" class:pl={d.type.startsWith('player:')} transform="rotate(45 {d.x + 0.5} {d.z + 0.5})">
            <title>{clock(d.time)} · {name(d.type)}{d.cause ? ' · ' + d.cause : ''}{d.killer ? ' · ' + d.killer : ''}{d.message ? ' · ' + d.message : ''}</title>
          </rect>
        {/each}
      {/if}
      {#if layers.players}
        {#each players as [pname, x, z]}
          <circle cx={x + 0.5} cy={z + 0.5} r="2.6" class="other"><title>{pname} · {x} {z}</title></circle>
        {/each}
      {/if}
      <circle cx={area.center[0] + 0.5} cy={area.center[1] + 0.5} r="3.4" class="me"><title>{area.player ?? T('thePlace')} · {area.center[0]} {area.center[1]}</title></circle>
    </svg>
  </div>
  <div class="legend muted small">
    <span><i style="background: var(--bad)"></i>{T('hostile')}</span>
    <span><i style="background: var(--good)"></i>{T('passive')}</span>
    <span><i style="background: var(--warn)"></i>{T('item')}</span>
    <span><i style="background: var(--muted)"></i>{T('other')}</span>
    <span>✕ {T('layerDespawned')}</span>
    <span>◆ {T('layerDeaths')}</span>
    {#if area.path}<span><i class="line"></i>{T('pathHint', { minutes })}</span>{/if}
  </div>
  {#if area.entitiesCut}<p class="muted small">{T('entitiesCut')}</p>{/if}

  <div class="lists">
    <div>
      <h4>{T('topEntities')}</h4>
      {#each byKind as [type, { n, cat }]}
        <div class="crow"><span><i class="dot" style="background: {COLORS[cat] ?? COLORS.other}"></i>{name(type)}</span><b>{n}</b></div>
      {:else}<span class="muted small">{T('none')}</span>{/each}
    </div>
    <div>
      <h4>{T('despawnedTotal', { count: area.despawned?.total ?? 0 })}</h4>
      {#each (area.despawned?.summary ?? []).slice(0, 12) as [item, n]}
        <div class="crow"><span>{item}</span><b>{n}</b></div>
      {:else}<span class="muted small">{T('none')}</span>{/each}
    </div>
    <div>
      <h4>{T('deathsTotal', { count: area.deaths?.total ?? 0 })}</h4>
      {#each deathRows.slice(-12).reverse() as d}
        <div class="crow"><span class:pl={d.type.startsWith('player:')}>{clock(d.time)} {name(d.type)} <small class="muted">{d.message ?? [d.cause, d.killer].filter(Boolean).join(' · ')}</small></span></div>
      {:else}<span class="muted small">{T('none')}</span>{/each}
    </div>
  </div>

  {#if area.inventory}
    <h4>{T('inventory', { name: area.player })}</h4>
    <div class="inv">
      {#each area.inventory as [slot, item, amount, extra]}
        <span title={`${T('slot')} ${slot}`}>{item}{amount > 1 ? ' × ' + amount : ''}{#if extra}<small> {extra}</small>{/if}</span>
      {:else}<span class="muted small">{T('none')}</span>{/each}
    </div>
  {/if}
</div>

<style>
  .lmap {
    display: grid;
    gap: 10px;
    min-width: 0;
  }

  .lhead {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    gap: 10px;
  }

  h3 {
    margin: 0;
    font-size: 0.95rem;
  }

  h4 {
    margin: 4px 0;
    font-size: 0.78rem;
    color: var(--muted);
    font-weight: 600;
  }

  p {
    margin: 0;
  }

  .layers {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .layers button {
    padding: 4px 10px;
    font-size: 0.78rem;
    font-weight: 600;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

  .layers button.on {
    color: var(--text);
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .layers b {
    font-weight: 700;
    opacity: 0.8;
  }

  .mapbox {
    position: relative;
    width: min(100%, 640px);
    aspect-ratio: 1;
    margin-inline: auto;
    border-radius: 10px;
    overflow: hidden;
    background: var(--surface-2);
  }

  .mapbox img,
  .mapbox svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .mapbox img {
    image-rendering: pixelated;
    user-select: none;
  }

  .nomap {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
  }

  .ent {
    stroke: rgb(0 0 0 / 55%);
    stroke-width: 0.35;
  }

  .desp {
    stroke: #ffb020;
    stroke-width: 0.7;
    fill: none;
  }

  .death {
    fill: #ff4d4d;
    stroke: #000;
    stroke-width: 0.35;
  }

  .death.pl {
    fill: #ff2bd6;
  }

  .path {
    fill: none;
    stroke: var(--accent);
    stroke-width: 0.9;
    stroke-linejoin: round;
    opacity: 0.85;
  }

  .pathdot {
    fill: var(--accent);
  }

  .other {
    fill: #fff;
    stroke: var(--accent);
    stroke-width: 0.8;
  }

  .me {
    fill: none;
    stroke: #fff;
    stroke-width: 1.2;
  }

  .mark {
    fill: color-mix(in srgb, var(--accent) 12%, transparent);
    stroke: var(--accent);
    stroke-width: 0.8;
    stroke-dasharray: 2 1.5;
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 6px 14px;
  }

  .legend i,
  .dot {
    display: inline-block;
    width: 9px;
    height: 9px;
    margin-right: 5px;
    border-radius: 50%;
    vertical-align: middle;
  }

  .legend i.line {
    width: 14px;
    height: 3px;
    border-radius: 2px;
    background: var(--accent);
  }

  .lists {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 6px 16px;
  }

  .crow {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.8rem;
    padding: 2px 0;
    border-bottom: 1px solid color-mix(in srgb, var(--border) 50%, transparent);
  }

  .crow span {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .pl {
    font-weight: 700;
  }

  .inv {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
  }

  .inv span {
    font-size: 0.78rem;
    padding: 3px 8px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .inv small {
    color: var(--muted);
  }
</style>
