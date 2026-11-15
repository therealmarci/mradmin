<script>
  // The surroundings of a ticket log as a map that plays smoothly: the last 30 minutes of play of the creator in
  // 5-second frames (logging service, log.timeline) and at the end the moment the ticket was created (log.area). The
  // map follows the player over recorded terrain; entities, despawned items and deaths appear when they happened.
  // Right of it a table of the minute the frame is in - the player's actions, the surroundings and the server.
  // Part of the shared ticket log view (MRadmin and the MRtechnology website show the same).
  //
  // log: the whole snapshot   lang: "de"/"en"   mark: optional rectangle {x0, z0, x1, z1, label}
  import { onDestroy } from 'svelte';
  import { tl } from './texts.js';
  import { safeImage, safeColor, num } from './safe.js';

  let { log, lang, mark = null } = $props();
  const T = (k, v) => tl(lang, k, v);

  const VIEW = 256;
  const area = $derived(log.area ?? null);
  const timeline = $derived(log.timeline ?? null);
  const minutes = $derived(timeline?.minutes ?? []);
  const types = $derived(timeline?.types ?? []);
  const cats = $derived(timeline?.categories ?? []);
  const chunkData = $derived(timeline?.chunks ?? []);
  const tiles = $derived(Object.values(timeline?.tiles ?? {}).map((t) => ({ ...t, image: safeImage(t.image) })).filter((t) => t.image));
  const areaImage = $derived(safeImage(area?.image));

  // every 5-second frame of every minute, then the moment of the ticket
  const frames = $derived.by(() => {
    const out = [];
    minutes.forEach((m, mi) => {
      for (const f of m.frames ?? []) out.push({ kind: 'frame', mi, time: num(f[0]), world: String(f[1]), x: num(f[2]), z: num(f[3]), refs: f[4] ?? [] });
    });
    if (area) out.push({ kind: 'created', mi: -1, time: num(log.time), world: String(area.world), x: num(area.center?.[0]), z: num(area.center?.[1]) });
    return out;
  });

  let index = $state(-1);
  const at = $derived(index < 0 || index >= frames.length ? frames.length - 1 : index);
  const frame = $derived(frames[at]);
  const minute = $derived(frame && frame.mi >= 0 ? minutes[frame.mi] : null);

  // ---------------------------------------------------------------- playing
  const SPEEDS = [10, 30, 60];
  let speed = $state(30);
  let playing = $state(false);
  let timer = null;
  function tickOn() {
    if (at >= frames.length - 1) stop();
    else index = at + 1;
  }
  function play() {
    if (playing) return stop();
    if (at >= frames.length - 1) index = 0;
    playing = true;
    timer = setInterval(tickOn, 5000 / speed);
  }
  function stop() {
    playing = false;
    clearInterval(timer);
  }
  function setSpeed(s) {
    speed = s;
    if (playing) {
      clearInterval(timer);
      timer = setInterval(tickOn, 5000 / speed);
    }
  }
  onDestroy(stop);
  const go = (i) => { stop(); index = Math.max(0, Math.min(frames.length - 1, i)); };
  const firstOf = (mi) => frames.findIndex((f) => f.mi === mi);

  // ------------------------------------------------------------------ layers
  let layers = $state({ entities: true, despawned: true, deaths: true, path: true, players: true });
  const COLORS = { hostile: 'var(--bad)', passive: 'var(--good)', item: 'var(--warn)', projectile: 'var(--muted)', vehicle: 'var(--muted)', other: 'var(--muted)' };

  const view = $derived(frame ? { x: frame.x - VIEW / 2, z: frame.z - VIEW / 2 } : { x: 0, z: 0 });
  const shownTiles = $derived(frame ? tiles.filter((t) => t.world === frame.world && t.x0 < view.x + VIEW && t.x0 + 256 > view.x && t.z0 < view.z + VIEW && t.z0 + 256 > view.z) : []);

  function groups(refs) {
    const out = [];
    for (const id of refs) {
      const g = chunkData[id] ?? [];
      for (let i = 0; i + 4 < g.length; i += 5) out.push([types[g[i]] ?? '?', cats[g[i + 1]] ?? 'other', g[i + 2], g[i + 3], g[i + 4]]);
    }
    return out;
  }
  const entities = $derived(frame?.kind === 'frame' ? groups(frame.refs) : area?.entities ?? []);

  // despawned items and deaths of the last 20 seconds before the frame (older ones fade out)
  const WINDOW = 20000;
  const allDespawns = $derived(minutes.flatMap((m) => m.despawnRows ?? []));
  const allDeaths = $derived(minutes.flatMap((m) => m.deaths ?? []));
  const despawnRows = $derived(frame?.kind === 'frame'
    ? allDespawns.filter((r) => r[0] <= frame.time && r[0] > frame.time - WINDOW) : area?.despawned?.rows ?? []);
  const deathRows = $derived(frame?.kind === 'frame'
    ? allDeaths.filter((d) => d.time <= frame.time && d.time > frame.time - WINDOW) : area?.deaths?.rows ?? []);
  const fade = (t) => (frame?.kind === 'frame' ? Math.max(0.25, 1 - (frame.time - t) / WINDOW) : 1);

  // the way: up to this frame in this world, the current minute bold
  const trail = $derived.by(() => {
    if (!frame) return { old: [], now: [] };
    const old = [];
    let line = [];
    let lineMinute = null;
    const now = [];
    for (const f of frames) {
      if (f.kind !== 'frame' || f.time > frame.time) break;
      if (f.world !== frame.world) {
        if (line.length > 1) old.push(line);
        line = [];
        continue;
      }
      const p = [f.x + 0.5, f.z + 0.5];
      if (f.mi === frame.mi) now.push(p);
      else {
        if (lineMinute !== null && f.mi !== lineMinute && line.length) line = [line.at(-1)];
        line.push(p);
        lineMinute = f.mi;
      }
    }
    if (line.length > 1) old.push(line);
    if (old.length && now.length) now.unshift(old.at(-1).at(-1));
    return { old, now };
  });

  // --------------------------------------------------------------- the table
  const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const hm = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const name = (type) => String(type).replace(/^item:/, '').replace(/^player:/, '');
  const list = (map, n = 6) => Object.entries(map ?? {}).sort((a, b) => b[1] - a[1]).slice(0, n).map(([k, v]) => `${v}× ${name(k)}`).join(', ');
  const sum = (map) => Object.values(map ?? {}).reduce((a, b) => a + num(b), 0);
  const counts = (refs) => groups(refs ?? []).reduce((a, [type, , , , n]) => ((a[type] = (a[type] ?? 0) + n), a), {});

  const health = $derived.by(() => {
    const f = log.health?.fields ?? [];
    return (log.health?.rows ?? []).map((r) => Object.fromEntries(f.map((k, i) => [k, r[i]])));
  });

  const details = $derived.by(() => {
    if (!minute) return null;
    const m = minute;
    const from = m.start, to = m.start + 60000;
    const now = counts(m.frames?.at(-1)?.[4]);
    const prevMinute = frame.mi > 0 ? minutes[frame.mi - 1] : null;
    const prev = prevMinute ? counts(prevMinute.frames?.at(-1)?.[4]) : null;
    let changes = [];
    if (prev) {
      const keys = new Set([...Object.keys(prev), ...Object.keys(now)]);
      changes = [...keys].map((k) => [k, (now[k] ?? 0) - (prev[k] ?? 0)]).filter(([, d]) => d !== 0)
        .sort((a, b) => Math.abs(b[1]) - Math.abs(a[1])).slice(0, 4);
    }
    const pts = health.filter((p) => p.time >= from && p.time < to);
    const lines = (log.console?.lines ?? []).filter(([t, l]) => t >= from && t < to && (l === 'warn' || l === 'error'));
    const evts = (log.events ?? []).filter((e) => e.time >= from && e.time < to);
    const dmg = (map) => `${Math.round(sum(map) * 10) / 10} (${Object.entries(map).map(([k, v]) => `${name(k)} ${v}`).join(', ')})`;
    return {
      actions: [
        sum(m.broken) && [T('mBroken'), list(m.broken)],
        sum(m.placed) && [T('mPlaced'), list(m.placed)],
        sum(m.picked) && [T('mPicked'), list(m.picked)],
        sum(m.dropped) && [T('mDropped'), list(m.dropped)],
        sum(m.damageTaken) && [T('mTaken'), dmg(m.damageTaken)],
        sum(m.damageDealt) && [T('mDealt'), dmg(m.damageDealt)],
        m.commands?.length && [T('mCommands'), m.commands.join('  ')],
      ].filter(Boolean),
      events: m.events ?? [],
      around: [
        [T('entities'), sum(now) + (changes.length ? ' (' + changes.map(([k, d]) => `${d > 0 ? '+' : ''}${d} ${name(k)}`).join(', ') + ')' : '')],
        sum(m.despawned) && [T('layerDespawned'), list(m.despawned)],
        m.deaths?.length && [T('layerDeaths'), m.deaths.map((d) => name(d.type)).join(', ')],
        m.came?.length && [T('mCame'), m.came.join(', ')],
        m.went?.length && [T('mWent'), m.went.join(', ')],
      ].filter(Boolean),
      server: pts.length ? [
        ['TPS', `${Math.min(...pts.map((p) => p.tps)).toFixed(1)} – ${(pts.reduce((a, p) => a + p.tps, 0) / pts.length).toFixed(1)}`],
        [T('mspt'), `Ø ${(pts.reduce((a, p) => a + p.mspt, 0) / pts.length).toFixed(1)} ms · max ${Math.max(...pts.map((p) => p.msptMax)).toFixed(0)} ms`],
      ] : [],
      lines: lines.slice(0, 4),
      lineCount: lines.length,
      evts: evts.slice(0, 4),
    };
  });

  // marks in the minute strip: what stands out in each minute
  function tone(m) {
    if (m.events?.some((e) => e.kind === 'death')) return 'bad';
    if ((log.console?.lines ?? []).some(([t, l]) => l === 'error' && t >= m.start && t < m.start + 60000)) return 'bad';
    if (sum(m.damageTaken) > 6 || m.deaths?.length) return 'warn';
    if (sum(m.broken) + sum(m.placed) + (m.events?.length ?? 0) + (m.commands?.length ?? 0) > 0) return 'act';
    return 'calm';
  }
  const gapBefore = (mi) => mi > 0 && minutes[mi].start - minutes[mi - 1].start > 60000;
  const radius = (n) => Math.min(4, 0.9 + Math.log2(num(n) + 1) * 0.6);
</script>

{#if frame}
  <div class="lmap">
    <div class="lhead">
      <div>
        <h3>{T('area')}</h3>
        <p class="muted small">{minutes.length ? T('playHint', { n: minutes.length, who: area?.player ?? log.ticket?.creator ?? '' }) : T('areaHint', { size: VIEW, who: area?.player ?? T('thePlace') })}</p>
      </div>
      <div class="layers">
        <button class:on={layers.entities} onclick={() => (layers.entities = !layers.entities)}>{T('layerEntities')}</button>
        <button class:on={layers.despawned} onclick={() => (layers.despawned = !layers.despawned)}>{T('layerDespawned')}</button>
        <button class:on={layers.deaths} onclick={() => (layers.deaths = !layers.deaths)}>{T('layerDeaths')}</button>
        {#if minutes.length}<button class:on={layers.path} onclick={() => (layers.path = !layers.path)}>{T('layerPath')}</button>{/if}
        <button class:on={layers.players} onclick={() => (layers.players = !layers.players)}>{T('layerPlayers')}</button>
      </div>
    </div>

    {#if frames.length > 1}
      <div class="player">
        <button class="secondary small" onclick={() => go(at - 1)} disabled={at === 0} aria-label={T('prev')}>◀</button>
        <button class="small" onclick={play} aria-label={T('play')}>{playing ? '❚❚' : '▶'}</button>
        <button class="secondary small" onclick={() => go(at + 1)} disabled={at === frames.length - 1} aria-label={T('next')}>▶</button>
        <input class="scrub" type="range" min="0" max={frames.length - 1} value={at} oninput={(e) => go(Number(e.currentTarget.value))} aria-label={T('minutes')} />
        <span class="when">{frame.kind === 'created' ? T('created') : clock(frame.time)}</span>
        <div class="speeds" role="group" aria-label={T('speed')}>
          {#each SPEEDS as s}<button class:on={speed === s} onclick={() => setSpeed(s)}>{s}×</button>{/each}
        </div>
      </div>
      {#if minutes.length}
        <div class="strip" role="group" aria-label={T('minutes')}>
          {#each minutes as m, mi}
            {#if gapBefore(mi)}<span class="gap" title={T('offline')}></span>{/if}
            <button class="seg {tone(m)}" class:sel={frame.mi === mi} title={hm(m.start)} aria-label={hm(m.start)} onclick={() => go(firstOf(mi))}></button>
          {/each}
          {#if area}<button class="seg created" class:sel={frame.kind === 'created'} title={T('created')} aria-label={T('created')} onclick={() => go(frames.length - 1)}></button>{/if}
        </div>
      {/if}
    {/if}

    <div class="split">
      <div class="mapbox">
        <svg viewBox="{view.x} {view.z} {VIEW} {VIEW}" preserveAspectRatio="none" aria-label={T('area')}>
          <rect x={view.x} y={view.z} width={VIEW} height={VIEW} class="ground" />
          {#each shownTiles as t}
            <image href={t.image} x={t.x0} y={t.z0} width="256" height="256" preserveAspectRatio="none" class="tile" />
          {/each}
          {#if frame.kind === 'created' && areaImage}
            <image href={areaImage} x={area.x0} y={area.z0} width={area.size} height={area.size} preserveAspectRatio="none" class="tile" />
          {/if}
          {#if mark}
            <rect x={mark.x0} y={mark.z0} width={mark.x1 - mark.x0 + 1} height={mark.z1 - mark.z0 + 1} class="mark"><title>{mark.label}</title></rect>
          {/if}
          {#if layers.path}
            {#each trail.old as line}<polyline points={line.map((p) => p.join(',')).join(' ')} class="path old" />{/each}
            {#if trail.now.length > 1}<polyline points={trail.now.map((p) => p.join(',')).join(' ')} class="path" />{/if}
          {/if}
          {#if layers.entities}
            {#each entities as [type, cat, x, z, n]}
              <circle cx={num(x) + 1} cy={num(z) + 1} r={radius(n)} fill={COLORS[cat] ?? COLORS.other} class="ent"><title>{name(type)} × {n} · {x} {z}</title></circle>
            {/each}
          {/if}
          {#if layers.despawned}
            {#each despawnRows as [time, x, z, item, amount]}
              <path d="M{num(x) - 0.8},{num(z) - 0.8}l2.6,2.6M{num(x) + 1.8},{num(z) - 0.8}l-2.6,2.6" class="desp" opacity={fade(time)}><title>{clock(time)} · {item} × {amount}</title></path>
            {/each}
          {/if}
          {#if layers.deaths}
            {#each deathRows as d}
              <rect x={num(d.x) - 1} y={num(d.z) - 1} width="3" height="3" class="death" class:pl={String(d.type).startsWith('player:')} opacity={fade(d.time)} transform="rotate(45 {num(d.x) + 0.5} {num(d.z) + 0.5})">
                <title>{clock(d.time)} · {name(d.type)}{d.cause ? ' · ' + name(d.cause) : ''}{d.message ? ' · ' + d.message : ''}</title>
              </rect>
            {/each}
          {/if}
          {#if layers.players && frame.kind === 'created'}
            {#each area?.players ?? [] as [pname, x, z]}<circle cx={num(x) + 0.5} cy={num(z) + 0.5} r="2.6" class="other"><title>{pname}</title></circle>{/each}
          {/if}
          <circle cx={frame.x + 0.5} cy={frame.z + 0.5} r="3.4" class="me"><title>{frame.x} {frame.z}</title></circle>
        </svg>
        <span class="coords">{frame.world} · {frame.x} {frame.z}</span>
      </div>

      <div class="table">
        {#if details}
          <h4 class="t-title">{hm(minute.start)}–{hm(minute.start + 60000)} <span class="muted">· {T('minuteOf', { n: frame.mi + 1, of: minutes.length })}</span></h4>
          <section>
            <h5>{T('mActions')}</h5>
            {#each details.events as e}<div class="row ev {e.kind === 'death' ? 'death' : ''}" class:now={Math.abs(e.time - frame.time) < 5000}><span>{clock(e.time)}</span><b>{T('ev_' + (['join', 'quit', 'death', 'teleport'].includes(e.kind) ? e.kind : 'join'))}</b> {e.text}</div>{/each}
            {#each details.actions as [label, value]}<div class="row"><b>{label}</b> {value}</div>{/each}
            {#if !details.events.length && !details.actions.length}<div class="row muted">{T('none')}</div>{/if}
          </section>
          <section>
            <h5>{T('mAround')}</h5>
            {#each details.around as [label, value]}<div class="row"><b>{label}</b> {value}</div>{/each}
          </section>
          <section>
            <h5>{T('server')}</h5>
            {#each details.server as [label, value]}<div class="row"><b>{label}</b> {value}</div>{:else}<div class="row muted">{T('mNoServer')}</div>{/each}
            {#if details.lineCount}<div class="row"><b>{T('mProblems', { n: details.lineCount })}</b></div>{/if}
            {#each details.lines as [, level, text]}<div class="row line {level === 'error' ? 'error' : 'warn'}">{text}</div>{/each}
            {#each details.evts as e}<div class="row"><span style="color: {safeColor(e.color)}">{e.source}</span> {e.text}</div>{/each}
          </section>
        {:else}
          <h4 class="t-title">{T('created')} {hm(frame.time)}</h4>
          <section>
            <h5>{T('topEntities')}</h5>
            {#each Object.entries((area?.entities ?? []).reduce((a, [type, , , , n]) => ((a[type] = (a[type] ?? 0) + num(n)), a), {})).sort((a, b) => b[1] - a[1]).slice(0, 10) as [type, n]}
              <div class="row"><b>{n}×</b> {name(type)}</div>
            {:else}<div class="row muted">{T('none')}</div>{/each}
          </section>
          <section>
            <h5>{T('despawnedTotal', { count: area?.despawned?.total ?? 0 })}</h5>
            {#each (area?.despawned?.summary ?? []).slice(0, 8) as [item, n]}<div class="row"><b>{n}×</b> {item}</div>{:else}<div class="row muted">{T('none')}</div>{/each}
          </section>
          <section>
            <h5>{T('deathsTotal', { count: area?.deaths?.total ?? 0 })}</h5>
            {#each (area?.deaths?.rows ?? []).slice(-8).reverse() as d}
              <div class="row"><span>{clock(d.time)}</span> <b>{name(d.type)}</b> {d.message ?? [d.cause, d.killer].filter(Boolean).join(' · ')}</div>
            {:else}<div class="row muted">{T('none')}</div>{/each}
          </section>
        {/if}
      </div>
    </div>

    <div class="legend muted small">
      <span><i style="background: var(--bad)"></i>{T('hostile')}</span>
      <span><i style="background: var(--good)"></i>{T('passive')}</span>
      <span><i style="background: var(--warn)"></i>{T('item')}</span>
      <span><i style="background: var(--muted)"></i>{T('other')}</span>
      <span>✕ {T('layerDespawned')}</span>
      <span>◆ {T('layerDeaths')}</span>
      {#if minutes.length}<span><i class="line"></i>{T('layerPath')}</span>{/if}
    </div>

    {#if area?.inventory}
      <h4>{T('inventory', { name: area.player })}</h4>
      <div class="inv">
        {#each area.inventory as [slot, item, amount, extra]}
          <span title={`${T('slot')} ${slot}`}>{item}{amount > 1 ? ' × ' + amount : ''}{#if extra}<small> {extra}</small>{/if}</span>
        {:else}<span class="muted small">{T('none')}</span>{/each}
      </div>
    {/if}
  </div>
{/if}

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
    font-size: 0.8rem;
    color: var(--muted);
    font-weight: 600;
  }

  h5 {
    margin: 0 0 4px;
    font-size: 0.72rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
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

  .player {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .player > button {
    padding: 5px 10px;
    min-width: 36px;
  }

  .scrub {
    flex: 1 1 200px;
    width: auto;
    padding: 0;
    accent-color: var(--accent);
  }

  .speeds {
    display: flex;
    gap: 2px;
  }

  .speeds button {
    padding: 3px 8px;
    font-size: 0.74rem;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--muted);
  }

  .speeds button.on {
    color: var(--text);
    border-color: var(--accent);
  }

  .row.now {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    border-radius: 4px;
  }

  .strip {
    flex: 1 1 240px;
    display: flex;
    align-items: stretch;
    gap: 2px;
    height: 18px;
    padding: 3px;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .seg {
    flex: 1;
    min-width: 4px;
    padding: 0;
    border: 0;
    border-radius: 3px;
    background: color-mix(in srgb, var(--muted) 30%, transparent);
  }

  .seg.act {
    background: color-mix(in srgb, var(--accent) 55%, transparent);
  }

  .seg.warn {
    background: var(--warn);
  }

  .seg.bad {
    background: var(--bad);
  }

  .seg.created {
    background: var(--text);
    flex: 0 0 10px;
  }

  .seg.sel {
    outline: 2px solid var(--text);
    outline-offset: 1px;
  }

  .gap {
    flex: 0 0 6px;
    background: repeating-linear-gradient(90deg, var(--border) 0 2px, transparent 2px 4px);
  }

  .when {
    font-weight: 700;
    font-size: 0.85rem;
    min-width: 5.5rem;
    text-align: right;
  }

  .split {
    display: grid;
    grid-template-columns: minmax(0, 1.25fr) minmax(240px, 1fr);
    gap: 14px;
    align-items: start;
  }

  @container (max-width: 720px) {
    .split {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  @media (max-width: 760px) {
    .split {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  .mapbox {
    position: relative;
    width: 100%;
    aspect-ratio: 1;
    border-radius: 10px;
    overflow: hidden;
    background: var(--surface-2);
  }

  .mapbox svg {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  .ground {
    fill: var(--surface-2);
  }

  .tile {
    image-rendering: pixelated;
  }

  .coords {
    position: absolute;
    left: 8px;
    bottom: 6px;
    font-size: 0.72rem;
    padding: 2px 7px;
    border-radius: 6px;
    background: rgb(0 0 0 / 55%);
    color: #fff;
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
    stroke-width: 1.1;
    stroke-linejoin: round;
  }

  .path.old {
    stroke-width: 0.7;
    opacity: 0.4;
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

  .table {
    display: grid;
    gap: 12px;
    min-width: 0;
    max-height: 560px;
    overflow: auto;
    font-size: 0.82rem;
  }

  .t-title {
    margin: 0;
    font-size: 0.95rem;
    color: var(--text);
  }

  .table section {
    display: grid;
    gap: 3px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .row {
    overflow-wrap: anywhere;
    line-height: 1.4;
  }

  .row b {
    font-weight: 650;
  }

  .row span {
    color: var(--muted);
    margin-right: 4px;
  }

  .row.ev.death b {
    color: var(--bad);
  }

  .row.line {
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.74rem;
  }

  .row.line.warn {
    color: var(--warn);
  }

  .row.line.error {
    color: var(--bad);
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 6px 14px;
  }

  .legend i {
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
