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

  let { uuid, me } = $props();

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
</script>

<div class="stack">
  <button class="back" onclick={() => go('/players')}>← {t('player.back')}</button>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if player}
    <section class="card">
      <div class="title">
        <img src={head(player.uuid)} alt="" width="52" height="52" />
        <div>
          <h1>{player.name}</h1>
          <div class="state" class:on={player.online}>
            {player.online ? '● ' + t('players.online') : t('player.lastSeen', { time: ago(player.lastSeen) })}
            {#if player.frozen} · <span class="info-text">❄ {t('flag.frozen')}</span>{/if}
            {#if player.adminMode} · <span class="warn-text">{t('overview.admin')}</span>{/if}
          </div>
        </div>
      </div>

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

    {#if player.tickets}
      <section class="card">
        <h2>{t('player.tickets')}</h2>
        {#if player.tickets.length === 0}
          <p class="muted small">{t('player.noTickets')}</p>
        {:else}
          <ul class="tickets">
            {#each player.tickets as tk (tk.id)}
              <li>
                <button class="link-row" onclick={() => go('/tickets/' + tk.id)}>
                  <span class="id">#{tk.id}</span>
                  <span class="tsum">{tk.summary || tk.category}</span>
                  <span class="muted small">{t('player.role.' + tk.role)} · {tk.open ? t('ticket.statusOpen') : t('ticket.statusClosed')} · {ago(tk.lastActivity)}</span>
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      </section>
    {/if}

    {#if player.parts.mrsleep}
      <SleepPart {uuid} {me} name={player.name} onDone={done} />
    {/if}
    {#if player.parts.mrchunks}
      <ChunksPart {uuid} {me} name={player.name} onDone={done} />
    {/if}
    {#if player.parts.mrpvp}
      <PvpPart {uuid} {me} name={player.name} onDone={done} />
    {/if}
    {#if player.parts.libertybans}
      <BansPart {uuid} {me} name={player.name} onDone={done} />
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .back {
    justify-self: start;
    background: transparent;
    color: var(--accent);
    padding: 4px 0;
  }

  .title {
    display: flex;
    gap: 14px;
    align-items: center;
  }

  .title img {
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .title h1 {
    margin: 0;
  }

  .state {
    color: var(--muted);
    font-size: 0.9rem;
  }

  .state.on {
    color: var(--good);
    font-weight: 600;
  }

  .info-text {
    color: var(--accent);
  }

  .warn-text {
    color: var(--warn);
  }

  .facts {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 170px), 1fr));
    margin: 14px 0 0;
  }

  .fact {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    min-width: 0;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .fvalue {
    font-size: 0.92rem;
    font-weight: 600;
  }

  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 10px;
  }

  .coords span {
    white-space: nowrap;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 16px;
    align-items: center;
  }

  .kick {
    display: flex;
    gap: 8px;
    flex: 1;
    min-width: min(100%, 320px);
  }

  .kick input {
    flex: 1;
  }

  h2 {
    font-size: 1.05rem;
    margin: 0 0 10px;
  }

  .tickets {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .link-row {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    align-items: baseline;
    justify-content: flex-start;
    width: 100%;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    font-weight: 400;
    padding: 8px 12px;
  }

  .id {
    font-weight: 800;
  }

  .tsum {
    font-weight: 600;
  }
</style>
