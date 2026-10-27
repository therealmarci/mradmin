<script>
  // One MRpvp round by its Game-ID. Running: both players live (health, inventory, position on the mini map) and
  // the combat log so far. Finished: result and numbers, the saved inventories (on entering and at the end, with
  // ender chest and MRtoolbar belts), the combat log and a replay of the positions on the mini map.
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

  let { id, me } = $props();

  let running = $state(null);
  let game = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let snapshotIndex = $state(0);
  let view = $state('inventory');
  let replayT = $state(0);
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

  // Replay: the positions at (or just before) the chosen time.
  const times = $derived([...new Set((game?.positions ?? []).map((p) => p.t))]);
  const replayMarkers = $derived.by(() => {
    if (!game?.positions?.length) return [];
    const latest = {};
    for (const p of game.positions) {
      if (p.t <= replayT) latest[p.side] = p;
    }
    return Object.values(latest);
  });
  const liveMarkers = $derived(
    (running?.players ?? []).filter((p) => p.x != null).map((p) => ({
      x: p.x, z: p.z, yaw: p.yaw, side: p.side, hit: flashes[p.side]?.hit, hurt: flashes[p.side]?.hurt,
    })),
  );
  const snapshots = $derived(game?.snapshots ?? []);
  const snapshot = $derived(snapshots[snapshotIndex] ?? null);
  const log = $derived((running ?? game)?.events ?? []);
  const players = $derived((running ?? game)?.players ?? []);

  function line(e) {
    const actor = nameOf(e.actor, players);
    const target = nameOf(e.target, players);
    const dmg = e.damage != null ? hearts(e.damage) + '❤' : '';
    return t('pvp.log.' + e.type, { actor, target, damage: dmg, weapon: e.weapon ?? '', cause: e.cause ?? '' });
  }
</script>

<div class="page stack">
  <button class="back" onclick={() => go('/pvp')}>← {t('pvp.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if running}
    <div class="head">
      <h1>{t('pvp.gameTitle', { id })}</h1>
      <span class="badge-status running">{running.adminPause ? t('pvp.state.adminPaused', { name: running.adminPause }) : t('pvp.state.' + running.state)}</span>
      <span class="muted">{clock(running.elapsed)} · {running.kitName} · {running.arenaName}</span>
    </div>
    {#if running.state !== 'ending' && (me.may.mrpvpStop || me.may.mrpvpPause)}
      <div class="controls">
        {#if me.may.mrpvpPause}
          {#if running.adminPause}
            <button disabled={busy} onclick={() => act({ action: 'resume', id: String(id) })}>▶ {t('pvp.resume')}</button>
          {:else if running.state === 'running'}
            <button class="secondary" disabled={busy} onclick={() => act({ action: 'pause', id: String(id) })}>⏸ {t('pvp.pause')}</button>
          {/if}
        {/if}
        {#if me.may.mrpvpStop}
          {#each running.players as p (p.uuid)}
            <button class="secondary" disabled={busy}
              onclick={() => act({ action: 'win', id: String(id), side: String(p.side) }, t('pvp.winConfirm', { name: p.name, id }))}>🏆 {t('pvp.winFor', { name: p.name })}</button>
          {/each}
          <button class="danger" disabled={busy} onclick={() => act({ action: 'stop', id: String(id) }, t('pvp.stopConfirm', { id }))}>
            {t('pvp.stop')}
          </button>
        {/if}
      </div>
    {/if}
    <div class="two">
      <section class="card pad">
        <h2>{t('pvp.map')}</h2>
        <ArenaMap map={running.map} markers={liveMarkers} />
      </section>
      <section class="card pad log">
        <h2>{t('pvp.log')}</h2>
        {#each [...log].reverse().slice(0, 60) as e}
          <div class="small"><span class="mono muted">{clock(e.t)}</span> {line(e)}</div>
        {:else}
          <p class="muted small">{t('pvp.logEmpty')}</p>
        {/each}
      </section>
    </div>
    {#each running.players as p (p.uuid)}
      <section class="card pad">
        <div class="fighter">
          <img src={head(p.uuid)} alt="" width="36" height="36" />
          <div class="who">
            <strong>{p.name}</strong>
            <Hearts health={p.health} max={p.maxHealth} absorption={p.absorption} hurt={flashes[p.side]?.hurt} />
            <div class="muted small">
              {hearts(p.health)}❤ · {t('pvp.food')} {p.food ?? '-'} · {t('pvp.dealt')} {hearts(p.damageDealt)}❤ · {t('pvp.hits')} {p.hits}
            </div>
          </div>
        </div>
        {#if p.inventory}
          <ItemGrid inventory={p.inventory} armor={p.armor} offhand={p.offhand} held={p.held} />
        {/if}
      </section>
    {/each}
  {:else if game}
    <div class="head">
      <h1>{t('pvp.gameTitle', { id })}</h1>
      <span class="badge-status {game.result}">{t('pvp.result.' + game.result)}</span>
      <span class="muted">{t('pvp.reason.' + game.reason)} · {clock(game.duration)} · {game.kitName} · {game.arenaName}</span>
    </div>
    <p class="muted small">
      {dateTime(game.startedAt)} ·
      {game.review === 'pending' ? t('pvp.review.pending') : game.review === 'removed' ? t('pvp.review.removed')
        : game.counted ? t('pvp.counted') : t('pvp.notCounted')}
      {#if game.reviewedBy} · {t('pvp.review.by', { name: game.reviewedBy })}{/if}
      {#if game.reviewNote} · {t('pvp.reviewNote', { note: game.reviewNote })}{/if}
      {#if game.stoppedBy} · {t('pvp.stoppedBy', { name: game.stoppedBy })}{/if}
    </p>
    {#if me.may.mrpvpReview && game.review !== 'pending' && (game.counted || game.mayCountAgain)}
      <div class="card pad review">
        {#if game.counted}
          <p class="muted small">{t('pvp.uncountHint')}</p>
          <div class="review-actions">
            <input type="text" bind:value={note} maxlength="200" placeholder={t('pvp.notePlaceholder')} aria-label={t('pvp.notePlaceholder')} />
            <button class="secondary" disabled={busy}
              onclick={() => act({ action: 'uncount', id: String(id), note }, t('pvp.uncountConfirm', { id }))}>{t('pvp.uncount')}</button>
          </div>
        {:else}
          <div class="review-actions">
            <button disabled={busy} onclick={() => act({ action: 'recount', id: String(id) }, t('pvp.recountConfirm', { id }))}>{t('pvp.recount')}</button>
          </div>
        {/if}
      </div>
    {/if}
    {#if game.flags?.length}
      <div class="card pad review" class:pending={game.review === 'pending'}>
        <div class="small"><strong>{t('pvp.review.flags')}</strong>
          {#each game.flags as fl}<span class="flag">{t('pvp.flag.' + fl)}</span>{/each}</div>
        {#if game.review === 'pending' && me.may.mrpvpReview}
          <p class="muted small">{t('pvp.review.explain')}</p>
          <div class="review-actions">
            <button disabled={busy} onclick={() => act({ action: 'count', id: String(id) })}>{t('pvp.review.count')}</button>
            <button class="secondary" disabled={busy}
              onclick={() => act({ action: 'discard', id: String(id) }, t('pvp.review.discardConfirm', { id }))}>{t('pvp.review.discard')}</button>
          </div>
        {/if}
      </div>
    {/if}

    <div class="fighters">
      {#each game.players as p (p.uuid)}
        <section class="card pad" class:won={p.uuid === game.winner}>
          <div class="fighter">
            <img src={head(p.uuid)} alt="" width="36" height="36" />
            <div class="who">
              <button class="link" onclick={() => me.may.players && go('/players/' + p.uuid)}><strong>{p.name}</strong></button>
              <div class="muted small">{p.outcome ? t('pvp.result.' + p.outcome) : '-'}</div>
            </div>
          </div>
          <dl class="numbers small">
            <dt>{t('pvp.dealt')}</dt><dd>{hearts(p.damageDealt)}❤</dd>
            <dt>{t('pvp.taken')}</dt><dd>{hearts(p.damageTaken)}❤</dd>
            <dt>{t('pvp.hits')}</dt><dd>{p.hits} ({p.crits} {t('pvp.crits')})</dd>
            <dt>{t('pvp.arrows')}</dt><dd>{p.arrowsHit}/{p.arrowsShot}</dd>
            <dt>{t('pvp.totems')}</dt><dd>{p.totems}</dd>
            <dt>{t('pvp.healthEnd')}</dt><dd>{hearts(p.healthEnd)}❤</dd>
          </dl>
        </section>
      {/each}
    </div>

    <div class="two">
      <section class="card pad">
        <h2>{t('pvp.replay')}</h2>
        <ArenaMap map={game.map} markers={replayMarkers} />
        {#if times.length}
          <input type="range" min="0" max={times[times.length - 1]} step="500" bind:value={replayT} />
          <div class="muted small mono">{clock(replayT)} / {clock(times[times.length - 1])}</div>
        {:else}
          <p class="muted small">{t('pvp.noPositions')}</p>
        {/if}
      </section>
      <section class="card pad log">
        <h2>{t('pvp.log')}</h2>
        {#each log as e}
          <button class="logline small" onclick={() => (replayT = e.t)}>
            <span class="mono muted">{clock(e.t)}</span> {line(e)}
          </button>
        {:else}
          <p class="muted small">{t('pvp.logEmpty')}</p>
        {/each}
      </section>
    </div>

    <section class="card pad">
      <h2>{t('pvp.inventories')}</h2>
      {#if snapshots.length === 0}
        <p class="muted small">{t('pvp.noSnapshots')}</p>
      {:else}
        <div class="chips">
          {#each snapshots as s, i (s.id)}
            <button class:active={snapshotIndex === i} onclick={() => (snapshotIndex = i)}>
              {s.name} · {t('pvp.phase.' + s.phase)}
            </button>
          {/each}
        </div>
        {#if snapshot}
          <div class="chips">
            <button class:active={view === 'inventory'} onclick={() => (view = 'inventory')}>{t('pvp.view.inventory')}</button>
            <button class:active={view === 'enderchest'} onclick={() => (view = 'enderchest')}>{t('pvp.view.enderchest')}</button>
            <button class:active={view === 'belts'} onclick={() => (view = 'belts')}>{t('pvp.view.belts')}</button>
          </div>
          <p class="muted small">
            {dateTime(snapshot.takenAt)} · Level {snapshot.level} · {hearts(snapshot.health)}❤ · {snapshot.gamemode} ·
            <span class="mono">{snapshot.world} {snapshot.x} {snapshot.y} {snapshot.z}</span>
            {#if snapshot.phase === 'join'}
              · {snapshot.restoredAt ? t('pvp.restored', { date: dateTime(snapshot.restoredAt) }) : t('pvp.owed')}
            {/if}
          </p>
          {#if snapshot.unreadable}
            <div class="error">{t('pvp.unreadable')}</div>
          {:else if view === 'inventory'}
            <ItemGrid inventory={snapshot.inventory} armor={snapshot.armor} offhand={snapshot.offhand} />
          {:else if view === 'enderchest'}
            <ItemGrid inventory={snapshot.enderchest} slots={27} />
          {:else if snapshot.belts?.length}
            {#each snapshot.belts as b}
              <div class="belt">
                <div class="muted small">{t('pvp.beltSlot', { slot: b.slot + 1 })}</div>
                <ItemGrid inventory={b.items} slots={8} />
              </div>
            {/each}
          {:else}
            <p class="muted small">{t('pvp.noBelts')}</p>
          {/if}
          {#if snapshot.phase === 'join' && !snapshot.restoredAt && me.may.mrpvpRestore}
            <button class="secondary" disabled={busy}
              onclick={() => act({ action: 'giveBack', uuid: snapshot.uuid }, t('pvp.giveBackConfirm', { name: snapshot.name }))}>
              {t('pvp.giveBack')}
            </button>
          {/if}
        {/if}
      {/if}
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .head {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }

  .head h1 {
    margin: 0;
  }

  .pad {
    padding: 14px 16px;
    display: grid;
    gap: 10px;
  }

  .two {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
  }

  .fighters {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  }

  .won {
    border: 2px solid var(--good);
  }

  .fighter {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .fighter img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .who {
    flex: 1;
    min-width: 0;
  }

  .link {
    background: none;
    border: 0;
    padding: 0;
    color: var(--text);
  }


  .numbers {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 2px 12px;
    margin: 0;
  }

  .numbers dt {
    color: var(--muted);
  }

  .numbers dd {
    margin: 0;
  }

  .log {
    max-height: 420px;
    overflow-y: auto;
    align-content: start;
    gap: 4px;
  }

  .logline {
    background: none;
    border: 0;
    padding: 2px 0;
    text-align: left;
    color: var(--text);
    font-weight: 400;
  }

  .logline:hover {
    background: var(--surface-2);
  }

  input[type='range'] {
    width: 100%;
    max-width: 480px;
  }

  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.85rem;
  }

  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .badge-status {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .badge-status.running, .badge-status.win { color: var(--good); }
  .badge-status.draw { color: var(--warn); }

  .belt {
    display: grid;
    gap: 4px;
  }
  .review {
    display: grid;
    gap: 8px;
    border-left: 4px solid var(--border);
  }

  .review.pending {
    border-left-color: var(--warn);
  }

  .review .flag {
    display: inline-block;
    margin-left: 6px;
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--warn);
  }

  .review-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .review-actions input {
    flex: 1 1 220px;
  }

  .controls {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
</style>
