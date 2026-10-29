<script>
  // One MRpvp round by its Game-ID. Running: both players live (health, inventory, position on the mini map) and
  // the combat log so far. Finished: result and numbers, the saved inventories (on entering and at the end, with
  // ender chest and MRtoolbar belts), the combat log and a replay of the positions on the mini map.
  // The two sides keep the colours of their dots on the map (1 red, 2 blue) everywhere on the page.
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { head, dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { live as events } from '../lib/live.svelte.js';
  import { hearts, clock } from '../lib/pvp.js';
  import ItemGrid from '../components/ItemGrid.svelte';
  import ArenaMap from '../components/ArenaMap.svelte';
  import Hearts from '../components/Hearts.svelte';
  import Toast from '../components/Toast.svelte';

  let { id, me } = $props();

  let running = $state(null);
  let game = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let snapshotIndex = $state(0);
  let view = $state('inventory');
  let replayT = $state(0);
  let playing = $state(false);
  let speed = $state(1);
  // When the live data came - the time of the round runs on from there between two loads.
  let receivedAt = 0;
  // Optional reason when taking a round out of the statistics.
  let note = $state('');
  // side -> { hit, hurt }: lights up for a moment after a hit (dot on the map, hearts shake)
  let flashes = $state({ 1: { hit: false, hurt: false }, 2: { hit: false, hurt: false } });
  const FLASH = 900;
  const timers = {};

  /** MRpvp sends when each player last hit / was hurt - recent ones light up (also on several pages at once). */
  function flash(data) {
    for (const p of data.players ?? []) {
      for (const [kind, at] of [['hit', p.lastHitAt], ['hurt', p.lastHurtAt]]) {
        const age = at ? data.now - at : Infinity;
        if (age < FLASH) {
          flashes[p.side][kind] = true;
          clearTimeout(timers[p.side + kind]);
          timers[p.side + kind] = setTimeout(() => (flashes[p.side][kind] = false), FLASH - age);
        }
      }
    }
  }

  async function load() {
    try {
      running = await moduleData('mrpvp', 'live', { id });
      receivedAt = Date.now();
      flash(running);
      error = '';
      return;
    } catch (e) {
      running = null;
      if (e.status !== 404) {
        error = errorText(e);
        return;
      }
    }
    if (!me.may.mrpvpGames) {
      error = t('pvp.noGamePermission');
      return;
    }
    try {
      game = await moduleData('mrpvp', 'game', { id });
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    id;
    untrack(load);
    // While it runs, the positions move - every 2 s; finished rounds do not change.
    const timer = setInterval(() => running && load(), 2000);
    return () => clearInterval(timer);
  });
  $effect(() => {
    if (events.plugin.plugin === 'mrpvp' && events.plugin.id === String(id) && events.plugin.tick) untrack(load);
  });

  // Replay: runs smoothly with the chosen speed, stops at the end.
  $effect(() => {
    if (!playing) return;
    let last = performance.now();
    let frame;
    const step = (now) => {
      const end = times[times.length - 1] ?? 0;
      replayT = Math.min(end, replayT + (now - last) * speed);
      last = now;
      if (replayT >= end) {
        playing = false;
        return;
      }
      frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  });

  function togglePlay() {
    if (!playing && replayT >= (times[times.length - 1] ?? 0)) replayT = 0;
    playing = !playing;
  }

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrpvp', body);
      message = t('pvp.done.' + body.action, { id });
      if (body.action === 'uncount') note = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const nameOf = (uuid, players) => players?.find((p) => p.uuid === uuid)?.name ?? '';
  const sideOf = (uuid) => players.find((p) => p.uuid === uuid)?.side ?? 0;

  // Replay: the positions at (or just before) the chosen time.
  const times = $derived([...new Set((game?.positions ?? []).map((p) => p.t))]);
  // The positions per side, in time order.
  const tracks = $derived.by(() => {
    const bySide = { 1: [], 2: [] };
    for (const p of game?.positions ?? []) (bySide[p.side] ??= []).push(p);
    for (const list of Object.values(bySide)) list.sort((a, b) => a.t - b.t);
    return bySide;
  });

  /** Where a side stood at a time: between two samples (every half second) the dot glides. */
  function posAt(side, time) {
    const list = tracks[side] ?? [];
    if (!list.length || time < list[0].t) return null;
    let i = list.length - 1;
    while (i > 0 && list[i].t > time) i--;
    const a = list[i];
    const b = list[i + 1];
    if (!b) return a;
    const k = (time - a.t) / (b.t - a.t || 1);
    return { ...a, x: a.x + (b.x - a.x) * k, z: a.z + (b.z - a.z) * k };
  }

  // What happens in the fight as effects on the map (see ArenaMap): where it happened, and for a hit where the
  // attacker stood. pos(side, t) gives a side's position at a time.
  function effectsOf(list, pos) {
    return list.map((e) => {
      const actor = sideOf(e.actor);
      const target = sideOf(e.target);
      const from = e.type === 'hit' && actor ? pos(actor, e.t) : null;
      // Where MRpvp logged it - unless that lies outside the map (then the player's position).
      const m = (running ?? game)?.map;
      const inside = e.x != null && m && e.x >= 0 && e.z >= 0 && e.x <= m.sizeX && e.z <= m.sizeZ;
      const at = inside ? { x: e.x, z: e.z } : pos(target || actor, e.t);
      const dmg = e.damage != null ? `-${hearts(e.damage)}❤` : '';
      return {
        t: e.t, kind: e.type, x: at?.x, z: at?.z, fx: from?.x, fz: from?.z, side: actor || target,
        label: e.type === 'hit' || e.type === 'damage' ? dmg : t('pvp.fx.' + e.type),
      };
    });
  }

  // A side lit up shortly after it hit (yellow) or was hit (red) - in the replay from the log.
  function recent(side, kind, time) {
    return log.some((e) => e.type === 'hit' && time - e.t >= 0 && time - e.t < FLASH && sideOf(kind === 'hit' ? e.actor : e.target) === side);
  }

  const replayMarkers = $derived(
    [1, 2].map((side) => {
      const p = posAt(side, replayT);
      return p && { x: p.x, z: p.z, yaw: p.yaw, side, hit: recent(side, 'hit', replayT), hurt: recent(side, 'hurt', replayT) };
    }).filter(Boolean),
  );
  const replayEffects = $derived(game ? effectsOf(log, posAt) : []);
  const liveMarkers = $derived(
    (running?.players ?? []).filter((p) => p.x != null).map((p) => ({
      x: p.x, z: p.z, yaw: p.yaw, side: p.side, hit: flashes[p.side]?.hit, hurt: flashes[p.side]?.hurt,
    })),
  );
  // Live: the last entries; the attacker stands where the live position shows him.
  const liveEffects = $derived(
    running ? effectsOf(log.slice(-20), (side) => running.players.find((p) => p.side === side && p.x != null) ?? null) : [],
  );
  const liveClock = () => (running ? running.elapsed + (Date.now() - receivedAt) : 0);
  const replayClock = () => replayT;
  const snapshots = $derived(game?.snapshots ?? []);
  const snapshot = $derived(snapshots[snapshotIndex] ?? null);
  const log = $derived((running ?? game)?.events ?? []);
  const players = $derived([...((running ?? game)?.players ?? [])].sort((a, b) => a.side - b.side));
  // The last log entry at the replay time - marked in the log.
  const currentEvent = $derived(game ? log.findLastIndex((e) => e.t <= replayT) : -1);

  function line(e) {
    const actor = nameOf(e.actor, players);
    const target = nameOf(e.target, players);
    const dmg = e.damage != null ? hearts(e.damage) + '❤' : '';
    return t('pvp.log.' + e.type, { actor, target, damage: dmg, weapon: e.weapon ?? '', cause: e.cause ?? '' });
  }

  // The colour of the whole round: fighting green, paused/countdown yellow; finished by result.
  const tone = $derived(
    running
      ? running.adminPause || running.state === 'paused' || running.state === 'countdown' ? 'warn' : running.state === 'running' ? 'good' : ''
      : game ? { win: 'good', draw: 'warn' }[game.result] ?? '' : '',
  );

  // Both sides next to each other with bars from the middle (finished rounds).
  const compare = $derived.by(() => {
    if (!game || players.length < 2) return [];
    const [a, b] = players;
    const row = (label, va, vb, ta, tb) => ({ label, a: va, b: vb, ta: ta ?? String(va), tb: tb ?? String(vb), max: Math.max(va, vb, 0.0001) });
    return [
      row(t('pvp.dealt'), a.damageDealt, b.damageDealt, hearts(a.damageDealt) + '❤', hearts(b.damageDealt) + '❤'),
      row(t('pvp.taken'), a.damageTaken, b.damageTaken, hearts(a.damageTaken) + '❤', hearts(b.damageTaken) + '❤'),
      row(t('pvp.hits'), a.hits, b.hits, `${a.hits} (${a.crits} ${t('pvp.crits')})`, `${b.hits} (${b.crits} ${t('pvp.crits')})`),
      row(t('pvp.arrows'), a.arrowsHit, b.arrowsHit, `${a.arrowsHit}/${a.arrowsShot}`, `${b.arrowsHit}/${b.arrowsShot}`),
      row(t('pvp.totems'), a.totems, b.totems),
      row(t('pvp.healthEnd'), a.healthEnd ?? 0, b.healthEnd ?? 0, hearts(a.healthEnd) + '❤', hearts(b.healthEnd) + '❤'),
    ];
  });

  const rating = $derived(
    !game ? '' : game.review === 'pending' ? t('pvp.review.pending') : game.review === 'removed' ? t('pvp.review.removed')
      : game.counted ? t('pvp.counted') : t('pvp.notCounted'),
  );
</script>

<!-- One fighter in the head: side colour, head, name, hearts (running) or result (finished). -->
{#snippet fighter(p, right)}
  {@const won = game && p.uuid === game.winner}
  {@const lost = game && game.winner && p.uuid !== game.winner}
  <div class="f side{p.side}" class:right class:won class:lost>
    <img src={head(p.uuid)} alt="" width="56" height="56" class:off={running && !p.online} />
    <div class="f-who">
      <button class="name" disabled={!me.may.players} onclick={() => go('/players/' + p.uuid)}>{p.name}</button>
      {#if running}
        <Hearts health={p.health} max={p.maxHealth} absorption={p.absorption} hurt={flashes[p.side]?.hurt} size={13} />
        <span class="muted small">
          {hearts(p.health)}❤ · {t('pvp.hits')} {p.hits} · {t('pvp.dealt')} {hearts(p.damageDealt)}❤{#if !p.online} · {t('pvp.offline')}{/if}
        </span>
      {:else}
        <span class="outcome {p.outcome}">{won ? t('pvp.game.winner') : p.outcome ? t('pvp.result.' + p.outcome) : '-'}</span>
      {/if}
    </div>
  </div>
{/snippet}

<!-- The combat log as a time line, the dot in the colour of the side that acted. -->
{#snippet logList(list, clickable)}
  {#if list.length === 0}
    <p class="muted small">{t('pvp.logEmpty')}</p>
  {:else}
    <ol class="log">
      {#each list as e, i}
        {@const side = sideOf(e.actor) || sideOf(e.target)}
        <li class:current={clickable && i === currentEvent} class:end={e.type === 'end'}>
          <button class="logline" disabled={!clickable} onclick={() => { replayT = e.t; playing = false; }}>
            <span class="dot side{side}"></span>
            <span class="time mono">{clock(e.t)}</span>
            <span class="text">{line(e)}</span>
          </button>
        </li>
      {/each}
    </ol>
  {/if}
{/snippet}

<div class="page">
  <button class="back" onclick={() => go('/pvp')}>← {t('pvp.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if running || game}
    {@const r = running ?? game}
    <!-- Head: the two fighters face each other, state and time in the middle -->
    <header class="duel {tone}">
      {#if players[0]}{@render fighter(players[0], false)}{/if}
      <div class="mid">
        <span class="status">
          {#if running}
            {running.adminPause ? t('pvp.state.adminPaused', { name: running.adminPause }) : t('pvp.state.' + running.state)}
          {:else}
            {t('pvp.result.' + game.result)}
          {/if}
        </span>
        <span class="clock mono">{clock(running ? running.elapsed : game.duration)}</span>
        <span class="vs">VS</span>
        <span class="muted small">{t('pvp.gameTitle', { id })}</span>
      </div>
      {#if players[1]}{@render fighter(players[1], true)}{/if}
    </header>

    <!-- Facts -->
    <div class="facts">
      <div class="fact"><span class="label">{t('pvp.game.kit')}</span><strong>{r.kitName}</strong></div>
      <div class="fact"><span class="label">{t('pvp.game.arena')}</span><strong>{r.arenaName}</strong></div>
      {#if game}
        <div class="fact"><span class="label">{t('pvp.game.started')}</span><strong>{dateTime(game.startedAt)}</strong></div>
        <div class="fact"><span class="label">{t('pvp.game.end')}</span><strong>{t('pvp.reason.' + game.reason)}</strong>
          {#if game.stoppedBy}<span class="muted small">{t('pvp.stoppedBy', { name: game.stoppedBy })}</span>{/if}</div>
        <div class="fact" class:warn={game.review === 'pending'} class:off={!game.counted && game.review !== 'pending'}>
          <span class="label">{t('pvp.game.rating')}</span><strong>{rating}</strong>
          {#if game.reviewedBy}<span class="muted small">{t('pvp.review.by', { name: game.reviewedBy })}</span>{/if}
          {#if game.reviewNote}<span class="muted small">{t('pvp.reviewNote', { note: game.reviewNote })}</span>{/if}
        </div>
      {/if}
    </div>

    {#if running}
      <!-- Controls for the team -->
      {#if running.state !== 'ending' && (me.may.mrpvpStop || me.may.mrpvpPause)}
        <section class="card tile">
          <h2>{t('pvp.game.controls')}</h2>
          <div class="controls">
            {#if me.may.mrpvpPause}
              {#if running.adminPause}
                <button disabled={busy} onclick={() => act({ action: 'resume', id: String(id) })}>▶ {t('pvp.resume')}</button>
              {:else if running.state === 'running'}
                <button class="secondary" disabled={busy} onclick={() => act({ action: 'pause', id: String(id) })}>⏸ {t('pvp.pause')}</button>
              {/if}
            {/if}
            {#if me.may.mrpvpStop}
              {#each players as p (p.uuid)}
                <button class="secondary win-btn side{p.side}" disabled={busy}
                  onclick={() => act({ action: 'win', id: String(id), side: String(p.side) }, t('pvp.winConfirm', { name: p.name, id }))}>
                  <span class="dot side{p.side}"></span>{t('pvp.winFor', { name: p.name })}</button>
              {/each}
              <button class="danger stop" disabled={busy} onclick={() => act({ action: 'stop', id: String(id) }, t('pvp.stopConfirm', { id }))}>
                {t('pvp.stop')}
              </button>
            {/if}
          </div>
        </section>
      {/if}

      <div class="two">
        <section class="card tile">
          <h2>{t('pvp.map')}</h2>
          <ArenaMap map={running.map} markers={liveMarkers} effects={liveEffects} clock={liveClock} />
        </section>
        <section class="card tile">
          <h2>{t('pvp.log')}<span class="n">{log.length}</span></h2>
          <div class="log-box">{@render logList([...log].reverse().slice(0, 60), false)}</div>
        </section>
      </div>

      <div class="two">
        {#each players as p (p.uuid)}
          <section class="card tile inv side{p.side}">
            <div class="inv-head">
              <img src={head(p.uuid)} alt="" width="28" height="28" />
              <h2>{p.name}</h2>
              <span class="chip">{t('pvp.food')} {p.food ?? '-'}</span>
            </div>
            {#if p.inventory}
              <div class="inv-grid"><ItemGrid inventory={p.inventory} armor={p.armor} offhand={p.offhand} held={p.held} /></div>
            {/if}
          </section>
        {/each}
      </div>
    {:else}
      <!-- Review: flags waiting for a decision, or take it out of / back into the statistics -->
      {#if game.flags?.length}
        <section class="card tile review" class:pending={game.review === 'pending'}>
          <div class="flags">
            <strong>{t('pvp.review.flags')}</strong>
            {#each game.flags as fl}<span class="flag">{t('pvp.flag.' + fl)}</span>{/each}
          </div>
          {#if game.review === 'pending' && me.may.mrpvpReview}
            <p class="muted small">{t('pvp.review.explain')}</p>
            <div class="row-actions">
              <button disabled={busy} onclick={() => act({ action: 'count', id: String(id) })}>{t('pvp.review.count')}</button>
              <button class="danger" disabled={busy}
                onclick={() => act({ action: 'discard', id: String(id) }, t('pvp.review.discardConfirm', { id }))}>{t('pvp.review.discard')}</button>
            </div>
          {/if}
        </section>
      {/if}
      {#if me.may.mrpvpReview && game.review !== 'pending' && (game.counted || game.mayCountAgain)}
        <section class="card tile review">
          {#if game.counted}
            <p class="muted small">{t('pvp.uncountHint')}</p>
            <div class="row-actions">
              <input type="text" bind:value={note} maxlength="200" placeholder={t('pvp.notePlaceholder')} aria-label={t('pvp.notePlaceholder')} />
              <button class="secondary" disabled={busy}
                onclick={() => act({ action: 'uncount', id: String(id), note }, t('pvp.uncountConfirm', { id }))}>{t('pvp.uncount')}</button>
            </div>
          {:else}
            <div class="row-actions">
              <button disabled={busy} onclick={() => act({ action: 'recount', id: String(id) }, t('pvp.recountConfirm', { id }))}>{t('pvp.recount')}</button>
            </div>
          {/if}
        </section>
      {/if}

      <!-- Both sides compared -->
      {#if compare.length}
        <section class="card tile">
          <h2>{t('pvp.game.compare')}</h2>
          <div class="cmp">
            <div class="cmp-head side1"><img src={head(players[0].uuid)} alt="" width="22" height="22" />{players[0].name}</div>
            <span></span>
            <div class="cmp-head side2 right">{players[1].name}<img src={head(players[1].uuid)} alt="" width="22" height="22" /></div>
            {#each compare as c}
              <div class="cmp-side side1" class:lead={c.a > c.b}>
                <span class="v">{c.ta}</span>
                <span class="bar"><span style="width: {(c.a / c.max) * 100}%"></span></span>
              </div>
              <span class="cmp-label">{c.label}</span>
              <div class="cmp-side side2 right" class:lead={c.b > c.a}>
                <span class="bar"><span style="width: {(c.b / c.max) * 100}%"></span></span>
                <span class="v">{c.tb}</span>
              </div>
            {/each}
          </div>
        </section>
      {/if}

      <!-- Replay and log -->
      <div class="two">
        <section class="card tile">
          <h2>{t('pvp.replay')}</h2>
          <ArenaMap map={game.map} markers={replayMarkers} effects={replayEffects} clock={replayClock} />
          {#if times.length}
            <div class="player-bar">
              <button class="play" onclick={togglePlay} aria-label={playing ? t('pvp.game.stopReplay') : t('pvp.game.play')}>{playing ? '⏸' : '▶'}</button>
              <input type="range" min="0" max={times[times.length - 1]} step="100" bind:value={replayT} oninput={() => (playing = false)} />
              <span class="muted small mono">{clock(replayT)} / {clock(times[times.length - 1])}</span>
            </div>
            <div class="seg speed" role="radiogroup" aria-label={t('pvp.game.speed')}>
              {#each [0.25, 0.5, 1, 2] as v}
                <button role="radio" aria-checked={speed === v} class:active={speed === v} onclick={() => (speed = v)}>{v}×</button>
              {/each}
            </div>
          {:else}
            <p class="muted small">{t('pvp.noPositions')}</p>
          {/if}
        </section>
        <section class="card tile">
          <h2>{t('pvp.log')}<span class="n">{log.length}</span></h2>
          <div class="log-box">{@render logList(log, true)}</div>
        </section>
      </div>

      <!-- Saved inventories -->
      <section class="card tile">
        <h2>{t('pvp.inventories')}</h2>
        {#if snapshots.length === 0}
          <p class="muted small">{t('pvp.noSnapshots')}</p>
        {:else}
          <div class="snap-bar">
            <div class="seg" role="tablist">
              {#each snapshots as s, i (s.id)}
                <button role="tab" aria-selected={snapshotIndex === i} class:active={snapshotIndex === i} onclick={() => (snapshotIndex = i)}>
                  <img src={head(s.uuid)} alt="" width="18" height="18" />{s.name} · {t('pvp.phase.' + s.phase)}
                </button>
              {/each}
            </div>
            {#if snapshot}
              <div class="seg" role="tablist">
                {#each ['inventory', 'enderchest', 'belts'] as v}
                  <button role="tab" aria-selected={view === v} class:active={view === v} onclick={() => (view = v)}>{t('pvp.view.' + v)}</button>
                {/each}
              </div>
            {/if}
          </div>
          {#if snapshot}
            <div class="snap-facts">
              <span class="chip">{dateTime(snapshot.takenAt)}</span>
              <span class="chip">Level {snapshot.level}</span>
              <span class="chip">{hearts(snapshot.health)}❤</span>
              <span class="chip">{snapshot.gamemode}</span>
              <span class="chip mono">{snapshot.world} {snapshot.x} {snapshot.y} {snapshot.z}</span>
              {#if snapshot.phase === 'join'}
                <span class="chip" class:good={snapshot.restoredAt} class:warn={!snapshot.restoredAt}>
                  {snapshot.restoredAt ? t('pvp.restored', { date: dateTime(snapshot.restoredAt) }) : t('pvp.owed')}
                </span>
              {/if}
            </div>
            <div class="inv-grid">
              {#if snapshot.unreadable}
                <div class="error">{t('pvp.unreadable')}</div>
              {:else if view === 'inventory'}
                <ItemGrid inventory={snapshot.inventory} armor={snapshot.armor} offhand={snapshot.offhand} />
              {:else if view === 'enderchest'}
                <ItemGrid inventory={snapshot.enderchest} slots={27} />
              {:else if snapshot.belts?.length}
                <div class="belts">
                  {#each snapshot.belts as b}
                    <div class="belt">
                      <span class="muted small">{t('pvp.beltSlot', { slot: b.slot + 1 })}</span>
                      <ItemGrid inventory={b.items} slots={8} />
                    </div>
                  {/each}
                </div>
              {:else}
                <p class="muted small">{t('pvp.noBelts')}</p>
              {/if}
            </div>
            {#if snapshot.phase === 'join' && !snapshot.restoredAt && me.may.mrpvpRestore}
              <div class="row-actions">
                <button disabled={busy}
                  onclick={() => act({ action: 'giveBack', uuid: snapshot.uuid }, t('pvp.giveBackConfirm', { name: snapshot.name }))}>
                  {t('pvp.giveBack')}
                </button>
              </div>
            {/if}
          {/if}
        {/if}
      </section>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --pv: var(--accent);
    --s1: #e5484d;
    --s2: #3b82f6;
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--pv);
    padding: 4px 0;
  }

  .side1 {
    --sd: var(--s1);
  }

  .side2 {
    --sd: var(--s2);
  }

  /* Head: fighter | state + time | fighter */
  .duel {
    --st: var(--muted);
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 16px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pv) 30%, var(--border));
    border-top: 4px solid var(--st);
    background:
      radial-gradient(80% 140% at 0% 50%, color-mix(in srgb, var(--s1) 12%, transparent), transparent 60%),
      radial-gradient(80% 140% at 100% 50%, color-mix(in srgb, var(--s2) 12%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .duel.good {
    --st: var(--good);
  }

  .duel.warn {
    --st: var(--warn);
  }

  .f {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
  }

  .f.right {
    flex-direction: row-reverse;
    text-align: right;
  }

  .f img {
    flex-shrink: 0;
    border-radius: 12px;
    image-rendering: pixelated;
    border: 3px solid var(--sd);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--sd) 20%, transparent);
  }

  .f img.off {
    filter: grayscale(1);
    opacity: 0.6;
  }

  .f.lost img {
    filter: grayscale(0.7);
    opacity: 0.75;
  }

  .f-who {
    display: grid;
    gap: 4px;
    min-width: 0;
    justify-items: start;
  }

  .f.right .f-who {
    justify-items: end;
  }

  .name {
    padding: 0;
    background: transparent;
    border: 0;
    font: inherit;
    font-size: 1.2rem;
    font-weight: 800;
    color: var(--text);
    overflow-wrap: anywhere;
    text-align: inherit;
  }

  .name:not(:disabled):hover {
    filter: none;
    color: var(--sd);
  }

  .name:disabled {
    opacity: 1;
    cursor: default;
  }

  .outcome {
    font-size: 0.75rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .f.won .outcome {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .outcome.draw {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 15%, transparent);
  }

  .mid {
    display: grid;
    justify-items: center;
    gap: 2px;
    text-align: center;
  }

  .status {
    font-size: 0.75rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--st);
  }

  .clock {
    font-size: 2rem;
    font-weight: 800;
    line-height: 1.1;
  }

  .vs {
    font-size: 0.72rem;
    font-weight: 800;
    letter-spacing: 0.12em;
    color: var(--muted);
  }

  /* Facts as tiles */
  .facts {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .fact {
    --tn: var(--pv);
    display: grid;
    gap: 2px;
    align-content: start;
    padding: 12px 14px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .fact.warn {
    --tn: var(--warn);
  }

  .fact.off {
    --tn: var(--muted);
  }

  .label {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 700;
  }

  .fact strong {
    overflow-wrap: anywhere;
  }

  /* Tiles */
  .tile {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.02rem;
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .two {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
    align-items: start;
  }

  .controls,
  .row-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    align-items: center;
  }

  .row-actions input {
    flex: 1 1 220px;
  }

  .stop {
    margin-left: auto;
  }

  .dot {
    width: 9px;
    height: 9px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--sd, var(--muted));
  }

  .win-btn:hover {
    border-color: var(--sd);
  }

  /* Combat log as a time line */
  .log-box {
    max-height: 440px;
    overflow-y: auto;
    margin-right: -6px;
    padding-right: 6px;
  }

  .log {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 2px;
  }

  .logline {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    width: 100%;
    padding: 5px 8px;
    border: 0;
    border-radius: 8px;
    text-align: left;
    font: inherit;
    font-size: 0.86rem;
    font-weight: 400;
    color: var(--text);
    background: transparent;
  }

  .logline:not(:disabled):hover {
    filter: none;
    background: var(--surface-2);
  }

  .logline:disabled {
    opacity: 1;
    cursor: default;
  }

  .log li.current .logline {
    background: color-mix(in srgb, var(--pv) 14%, var(--surface-2));
  }

  .log li.end .text {
    font-weight: 700;
  }

  .time {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .text {
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  /* Replay controls */
  .player-bar {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .play {
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 50%;
  }

  .speed {
    justify-self: start;
  }

  .speed button {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .player-bar input[type='range'] {
    flex: 1;
    min-width: 0;
  }

  /* Live inventories, one per side */
  .inv {
    border-top: 3px solid var(--sd);
  }

  .inv-head {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .inv-head img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .inv-head h2 {
    flex: 1;
    min-width: 0;
  }

  .inv-grid {
    display: flex;
    justify-content: center;
    overflow-x: auto;
    padding: 12px;
    border-radius: 12px;
    background: color-mix(in srgb, #000 18%, var(--surface-2));
    border: 1px solid var(--border);
  }

  .chip {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .chip.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .chip.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  /* Review */
  .review {
    border-left: 4px solid var(--border);
  }

  .review.pending {
    border-left-color: var(--warn);
    background: color-mix(in srgb, var(--warn) 5%, var(--surface));
  }

  .flags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .flag {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  /* Comparison: values with bars growing from the middle */
  .cmp {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 8px 14px;
  }

  .cmp-head {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 800;
    color: var(--sd);
  }

  .cmp-head img {
    border-radius: 5px;
    image-rendering: pixelated;
  }

  .cmp-head.right {
    justify-content: flex-end;
  }

  .cmp-label {
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--muted);
    text-align: center;
  }

  .cmp-side {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .cmp-side .v {
    min-width: 64px;
    font-weight: 700;
    font-size: 0.9rem;
  }

  .cmp-side.right .v {
    text-align: right;
  }

  .cmp-side.lead .v {
    color: var(--sd);
  }

  .bar {
    flex: 1;
    display: flex;
    height: 8px;
    border-radius: 4px;
    overflow: hidden;
    background: color-mix(in srgb, var(--border) 80%, transparent);
  }

  .side1 .bar {
    justify-content: flex-end;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: var(--sd);
  }

  /* Saved inventories */
  .snap-bar {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    gap: 10px;
  }

  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 9px;
    font-size: 0.86rem;
  }

  .seg button img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .seg button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .snap-facts {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .belts {
    display: grid;
    gap: 10px;
  }

  .belt {
    display: grid;
    gap: 4px;
  }

  @media (max-width: 700px) {
    .duel {
      grid-template-columns: minmax(0, 1fr);
      padding: 18px 16px;
    }

    .f.right {
      flex-direction: row;
      text-align: left;
    }

    .f.right .f-who {
      justify-items: start;
    }

    .mid {
      order: -1;
    }

    .cmp-side .v {
      min-width: 0;
    }

    .stop {
      margin-left: 0;
    }
  }
</style>
