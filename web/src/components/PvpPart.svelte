<script>
  // MRpvp's part of a player's page: duel statistics, current round, last rounds, duel ban, statistics reset and
  // states that are still owed - with MRpvp's permissions. Built like the sleep and chunk loader parts: status chips
  // in the head, tiles for statistics and rounds (a click opens the duel control), actions in groups below.
  // view "tile": only the status as a tile on the player's overview.
  import { untrack } from 'svelte';
  import { moduleAction, playerPart } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { hearts, clock } from '../lib/pvp.js';
  import PartTile from './PartTile.svelte';

  let { uuid, me, name, onDone, view = 'full', onOpen } = $props();

  let p = $state(null);
  let error = $state('');
  let busy = $state(false);
  let reason = $state('');

  async function load() {
    try {
      p = await playerPart(uuid, 'mrpvp');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    uuid;
    untrack(load);
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
  });

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    try {
      await moduleAction('mrpvp', { uuid, ...body });
      onDone(t('pvp.done.' + body.action, { name }));
      if (body.action === 'ban') reason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Older MRpvp versions send no quick choices - the same defaults as its config.
  const DEFAULT_DURATIONS = [86_400_000, 604_800_000, -1];
  const durations = $derived(p?.banDurations ?? DEFAULT_DURATIONS);
  const time = (ms) => (ms === -1 ? t('pvp.permanent') : duration(ms));

  function ban(ms) {
    // MRpvp: 0 = permanent.
    act({ action: 'ban', duration: String(ms === -1 ? 0 : ms), reason }, t('pvp.banConfirm', { name, time: time(ms) }));
  }

  const chips = $derived(p ? [
    ...(p.game ? [{ text: '⚔ ' + t('pvp.chipInGame', { id: p.game }), tone: 'info' }] : []),
    ...(p.ban ? [{ text: '✕ ' + (p.ban.permanent ? t('sleep.bannedPermanent') : t('sleep.bannedUntil', { time: dateTime(p.ban.until) })), tone: 'bad' }] : []),
    ...(p.owed > 0 ? [{ text: '📦 ' + t('pvp.chipOwed', { n: p.owed }), tone: 'warn' }] : []),
    ...(!p.game && !p.ban && !p.owed ? [{ text: '✓ ' + t('sleep.nothing'), tone: 'good' }] : []),
  ] : []);

  const stats = $derived(p ? [
    { label: t('pvp.games'), value: p.stats.games },
    { label: t('pvp.wins'), value: p.stats.wins },
    { label: 'K/D', value: p.stats.kd.toFixed(2) },
    { label: t('pvp.winRate'), value: p.stats.winRate.toFixed(1) + ' %' },
    { label: t('pvp.bestStreak'), value: p.stats.bestStreak },
  ] : []);
</script>

{#if view === 'tile'}
  <PartTile label={t('nav.pvp')} loading={!p} {error} {onOpen} tone={p?.ban ? 'bad' : p?.game ? 'info' : ''}
    value={p ? String(p.stats.games) : ''} unit={t('pvp.tileUnit')}
    facts={p ? [
      ...(p.game ? [{ label: t('pvp.tileLive'), value: '#' + p.game, tone: 'info' }] : []),
      { label: t('pvp.wins'), value: String(p.stats.wins) },
      { label: t('pvp.winRate'), value: p.stats.games ? p.stats.winRate.toFixed(0) + ' %' : '–' },
      { label: t('player.banFact'), value: !p.ban ? '–' : p.ban.permanent ? t('sleep.permanent') : dateTime(p.ban.until), tone: p.ban ? 'bad' : '' },
    ] : []} />
{:else}
<section class="card part">
  <div class="phead">
    <h2>⚔ {t('pvp.partTitle')}</h2>
    {#if p}
      <div class="chips">
        {#each chips as c}<span class="tag {c.tone}">{c.text}</span>{/each}
      </div>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}

  {#if p}
    <div class="stats">
      {#each stats as s (s.label)}
        <div class="fact"><div class="flabel">{s.label}</div><div class="fvalue">{s.value}</div></div>
      {/each}
    </div>

    {#if p.game}
      <button class="live" onclick={() => go('/pvp/' + p.game)}>
        <span class="dot"></span>
        <span><strong>{t('pvp.liveTitle', { id: p.game })}</strong><br /><span class="muted small">{t('pvp.liveHint')}</span></span>
        <span class="go">→</span>
      </button>
    {/if}

    {#if me.may.mrpvpGames}
      <h3>{t('pvp.lastRounds')}</h3>
      {#if p.games.length === 0}
        <p class="muted small">{t('pvp.noFinished')}</p>
      {:else}
        <div class="rounds">
          {#each p.games as g (g.id)}
            {@const self = g.players.find((x) => x.uuid === uuid)}
            {@const other = g.players.find((x) => x.uuid !== uuid)}
            {@const outcome = self?.outcome ?? g.result}
            <button class="round {outcome}" onclick={() => go('/pvp/' + g.id)}>
              <span class="rtop"><span class="outcome">{t('pvp.result.' + outcome)}</span><span class="muted mono">#{g.id}</span></span>
              <strong>vs {other?.name ?? '?'}</strong>
              <span class="muted small">⏱ {clock(g.duration)} · {hearts(self?.damageDealt)}❤</span>
              <span class="muted small">{dateTime(g.startedAt)}</span>
            </button>
          {/each}
        </div>
      {/if}
    {/if}

    {#if me.may.mrpvpBan || (me.may.mrpvpRestore && p.owed > 0) || (me.may.mrpvpReset && p.stats.games > 0)}
      <div class="groups">
        {#if me.may.mrpvpBan}
          <div class="group">
            <div class="glabel">{t('pvp.banSection')}</div>
            {#if p.ban}
              <p class="small"><span class="muted">{t('sleep.banReason')}:</span> {p.ban.reason || '-'} · <span class="muted">{t('sleep.banBy')}:</span> {p.ban.by}</p>
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'unban' })}>{t('pvp.unban')}</button>
            {:else}
              <input type="text" bind:value={reason} maxlength="200" placeholder={t('pvp.reasonPlaceholder')} />
              <div class="buttons">
                {#each durations as d}
                  <button class="danger" disabled={busy} onclick={() => ban(d)}>{t('sleep.banFor', { time: time(d) })}</button>
                {/each}
              </div>
            {/if}
          </div>
        {/if}
        {#if me.may.mrpvpRestore && p.owed > 0}
          <div class="group">
            <div class="glabel">{t('pvp.owedSection')}</div>
            <p class="muted small">{t('pvp.owedCount', { n: p.owed })}</p>
            <button class="secondary" disabled={busy} onclick={() => act({ action: 'giveBack' }, t('pvp.giveBackConfirm', { name }))}>
              {t('pvp.giveBack')}
            </button>
          </div>
        {/if}
        {#if me.may.mrpvpReset && p.stats.games > 0}
          <div class="group">
            <div class="glabel">{t('pvp.resetSection')}</div>
            <button class="danger" disabled={busy} onclick={() => act({ action: 'reset' }, t('pvp.resetConfirm', { name }))}>
              {t('pvp.reset')}
            </button>
          </div>
        {/if}
      </div>
    {/if}
  {:else if !error}
    <p class="muted small">{t('common.loading')}</p>
  {/if}
</section>
{/if}

<style>
  .phead {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    align-items: center;
  }

  h2 {
    font-size: 1.05rem;
    margin: 0;
  }

  h3 {
    font-size: 0.9rem;
    margin: 16px 0 8px;
    color: var(--muted);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.good { color: var(--good); background: color-mix(in srgb, var(--good) 14%, transparent); }
  .tag.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 16%, transparent); }
  .tag.bad { color: var(--bad); background: color-mix(in srgb, var(--bad) 14%, transparent); }
  .tag.info { color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); }

  .stats {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr));
    margin-top: 14px;
  }

  .fact {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .fvalue {
    font-size: 1.3rem;
    font-weight: 700;
  }

  /* The running round: a tile that leads to the duel control. */
  .live {
    display: flex;
    gap: 12px;
    align-items: center;
    width: 100%;
    margin-top: 14px;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    background: color-mix(in srgb, var(--accent) 10%, var(--surface-2));
    border: 1px solid var(--accent);
    border-radius: 10px;
    padding: 12px 14px;
  }

  .live .go {
    margin-left: auto;
    color: var(--accent);
    font-size: 1.2rem;
    font-weight: 700;
  }

  .dot {
    width: 10px;
    height: 10px;
    flex: none;
    border-radius: 50%;
    background: var(--bad);
    animation: pulse 1.4s infinite;
  }

  @keyframes pulse {
    50% { opacity: 0.3; }
  }

  .rounds {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  /* Every round a tile, coloured by its outcome for this player. */
  .round {
    display: grid;
    gap: 2px;
    justify-items: start;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--border);
    border-radius: 10px;
    padding: 8px 10px;
  }

  .round:hover {
    border-color: var(--accent);
  }

  .rtop {
    display: flex;
    justify-content: space-between;
    width: 100%;
    gap: 8px;
  }

  .outcome {
    font-weight: 700;
    font-size: 0.85rem;
  }

  .round.win { border-left-color: var(--good); }
  .round.win .outcome { color: var(--good); }
  .round.loss { border-left-color: var(--bad); }
  .round.loss .outcome { color: var(--bad); }
  .round.draw { border-left-color: var(--warn); }
  .round.draw .outcome { color: var(--warn); }

  .groups {
    display: grid;
    gap: 14px;
    margin-top: 16px;
    border-top: 1px solid var(--border);
    padding-top: 14px;
  }

  .group p {
    margin: 0 0 6px;
  }

  .glabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
    margin-bottom: 6px;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }
</style>
