<script>
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, duration, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import SleepPart from '../components/SleepPart.svelte';
  import ChunksPart from '../components/ChunksPart.svelte';
  import PvpPart from '../components/PvpPart.svelte';
  import BansPart from '../components/BansPart.svelte';
  import PartTile from '../components/PartTile.svelte';
  import Toast from '../components/Toast.svelte';

  // Without a section: preview of the player and one tile per section; a tile opens /players/<uuid>/<section>.
  let { uuid, me, section = null } = $props();

  let player = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let kickReason = $state('');

  async function load() {
    try {
      player = await api('GET', '/players/' + uuid);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    uuid;
    untrack(load);
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  async function act(action, body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', `/players/${uuid}/${action}`, body);
      message = t('player.done.' + action);
      if (action === 'kick') kickReason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Shown by the parts (they handle their own errors) - one place for the confirmation.
  function done(text) {
    message = text;
    error = '';
  }

  const self = $derived(player && player.uuid === me.uuid);

  const PARTS = { mrsleep: SleepPart, mrchunks: ChunksPart, mrpvp: PvpPart, libertybans: BansPart };
  // Sections this player's page has, in the order of the tiles.
  const sections = $derived(player ? [
    ...(player.tickets ? ['tickets'] : []),
    ...Object.keys(PARTS).filter((id) => player.parts[id]),
  ] : []);
  const open = (id) => go(`/players/${uuid}/${id}`);
  const openTickets = $derived(player?.tickets?.filter((tk) => tk.open).length ?? 0);

  // An unknown or unavailable section (old link, plugin removed) shows the overview.
  $effect(() => {
    if (player && section && !sections.includes(section)) go(`/players/${uuid}`, true);
  });

  // Messages of the overview do not belong to a section and the other way round.
  $effect(() => {
    section;
    untrack(() => { message = ''; });
  });
</script>

<div class="page">
  {#if section && player}
    <button class="back" onclick={() => go(`/players/${uuid}`)}>← {t('player.backTo', { name: player.name })}</button>
  {:else}
    <button class="back" onclick={() => go('/players')}>← {t('player.back')}</button>
  {/if}
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if player && section}
    <!-- One section of the player: a small head, then the section itself. -->
    <header class="mini">
      <img src={head(player.uuid)} alt="" width="36" height="36" />
      <strong>{player.name}</strong>
      <span class="state" class:on={player.online}>
        {player.online ? '● ' + t('players.online') : t('player.lastSeen', { time: ago(player.lastSeen) })}
      </span>
    </header>
    {#if section === 'tickets'}
      <section class="card">
        <h2>{t('player.tickets')}</h2>
        {#if player.tickets.length === 0}
          <p class="muted small">{t('player.noTickets')}</p>
        {:else}
          <ul class="tickets">
            {#each player.tickets as tk (tk.id)}
              <li>
                <button class="ticket" class:closed={!tk.open} onclick={() => go('/tickets/' + tk.id)}>
                  <span class="id">#{tk.id}</span>
                  <span class="tsum">{tk.summary || tk.category}</span>
                  <span class="muted small">{t('player.role.' + tk.role)} · {tk.open ? t('ticket.statusOpen') : t('ticket.statusClosed')} · {ago(tk.lastActivity)}</span>
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      </section>
    {:else if PARTS[section]}
      {@const Part = PARTS[section]}
      <Part {uuid} {me} name={player.name} onDone={done} />
    {/if}
  {:else if player}
    <section class="head" class:online={player.online}>
      <div class="who">
        <img src={head(player.uuid)} alt="" width="72" height="72" />
        <div>
          <h1>{player.name}</h1>
          <div class="badges">
            <span class="badge" class:on={player.online}>
              {player.online ? '● ' + t('players.online') : t('player.lastSeen', { time: ago(player.lastSeen) })}
            </span>
            {#if player.frozen}<span class="badge info">❄ {t('flag.frozen')}</span>{/if}
            {#if player.adminMode}<span class="badge warn">{t('overview.admin')}</span>{/if}
          </div>
        </div>
      </div>

      <!-- The facts as small tiles: label on top, value below. -->
      <div class="facts">
        {#if player.online}
          <div class="fact"><div class="flabel">{t('ticket.location')}</div>
            <div class="fvalue mono coords"><span>{player.world}</span><span>X {player.x}</span><span>Y {player.y}</span><span>Z {player.z}</span></div></div>
          <div class="fact"><div class="flabel">{t('player.gamemode')}</div><div class="fvalue">{t('player.mode.' + player.gamemode)}</div></div>
          <div class="fact"><div class="flabel">{t('health.ping')}</div><div class="fvalue">{player.ping} ms</div></div>
          <div class="fact"><div class="flabel">{t('player.vitals')}</div>
            <div class="fvalue">♥ {player.health} · 🍗 {player.food} · Lv {player.level}</div></div>
        {/if}
        <div class="fact"><div class="flabel">{t('player.firstPlayed')}</div><div class="fvalue">{dateTime(player.firstPlayed)}</div></div>
        {#if player.playtime >= 0}
          <div class="fact"><div class="flabel">{t('player.playtime')}</div><div class="fvalue">{duration(player.playtime)}</div></div>
        {/if}
      </div>

      {#if !self && player.online && (player.may.freeze || player.may.kick)}
        <div class="actions">
          {#if player.may.freeze}
            <button class="secondary" disabled={busy} onclick={() => act(player.frozen ? 'unfreeze' : 'freeze')}>
              {player.frozen ? '💧 ' + t('player.unfreeze') : '❄ ' + t('player.freeze')}
            </button>
          {/if}
          {#if player.may.kick}
            <div class="kick">
              <input type="text" bind:value={kickReason} maxlength="200" placeholder={t('player.kickReason')} />
              <button class="danger" disabled={busy}
                onclick={() => act('kick', { reason: kickReason }, t('player.kickConfirm', { name: player.name }))}>👢 {t('player.kick')}</button>
            </div>
          {/if}
        </div>
      {/if}
    </section>

    {#if sections.length}
      <div class="tiles">
        {#each sections as id (id)}
          {#if id === 'tickets'}
            <PartTile label={t('player.tickets')} icon="ticket" onOpen={() => open('tickets')}
              value={String(openTickets)} unit={t('player.ticketsUnit')} tone={openTickets ? 'warn' : 'good'}
              facts={[
                { label: t('player.total'), value: String(player.tickets.length) },
                { label: t('player.role.creator'), value: String(player.tickets.filter((tk) => tk.role === 'creator').length) },
                { label: t('player.role.target'), value: String(player.tickets.filter((tk) => tk.role === 'target').length) },
              ]} />
          {:else}
            {@const Part = PARTS[id]}
            <Part {uuid} {me} name={player.name} onDone={done} view="tile" onOpen={() => open(id)} />
          {/if}
        {/each}
      </div>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --pl: var(--accent);
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--pl);
    padding: 4px 0;
  }

  /* Head of the player: big head, name and state, the facts and the actions. */
  .head {
    display: grid;
    gap: 16px;
    padding: 20px 22px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pl) 28%, var(--border));
    border-left: 4px solid var(--muted);
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pl) 18%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .head.online {
    border-left-color: var(--good);
  }

  .who {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .who img {
    border-radius: 12px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  h1 {
    margin: 0;
    font-size: 1.5rem;
    overflow-wrap: anywhere;
  }

  h2 {
    margin: 0 0 10px;
    font-size: 1.1rem;
  }

  .badges {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }

  .badge {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    color: var(--muted);
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .badge.on {
    color: var(--good);
    border-color: color-mix(in srgb, var(--good) 40%, var(--border));
  }

  .badge.info {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 40%, var(--border));
  }

  .badge.warn {
    color: var(--warn);
    border-color: color-mix(in srgb, var(--warn) 40%, var(--border));
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .fact {
    display: grid;
    gap: 3px;
    align-content: start;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    min-width: 0;
  }

  .flabel {
    font-size: 0.74rem;
    font-weight: 600;
    color: var(--muted);
  }

  .fvalue {
    font-weight: 700;
    font-size: 0.92rem;
    overflow-wrap: anywhere;
  }

  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 8px;
  }

  .coords span {
    white-space: nowrap;
  }

  .mono {
    font-family: ui-monospace, monospace;
    font-size: 0.85rem;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .kick {
    display: flex;
    gap: 8px;
    flex: 1;
    min-width: min(100%, 320px);
  }

  .kick input {
    flex: 1;
    min-width: 0;
  }

  .tiles {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  /* A section: small head */
  .mini {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pl) 25%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pl) 14%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .mini img {
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .state {
    margin-left: auto;
    font-size: 0.85rem;
    color: var(--muted);
  }

  .state.on {
    color: var(--good);
  }

  .tickets {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .ticket {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: flex-start;
    gap: 4px 10px;
    padding: 10px 14px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--warn);
    border-radius: 10px;
  }

  .ticket:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--pl) 45%, var(--border));
  }

  .ticket.closed {
    border-left-color: var(--muted);
  }

  .id {
    font-weight: 800;
    color: var(--muted);
  }

  .tsum {
    font-weight: 700;
    flex: 1;
    min-width: 0;
  }

  @media (max-width: 640px) {
    .head {
      padding: 16px;
    }

    .who img {
      width: 56px;
      height: 56px;
    }
  }
</style>
