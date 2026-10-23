<script>
  // MRpvp's part of a player's page: duel statistics, current round, last rounds, duel ban, statistics reset and
  // states that are still owed - with MRpvp's permissions.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { hearts, clock } from '../lib/pvp.js';

  let { uuid, me, name, onDone } = $props();

  let p = $state(null);
  let error = $state('');
  let busy = $state(false);
  let reason = $state('');
  let days = $state('7');

  async function load() {
    try {
      p = await api('GET', `/players/${uuid}/mrpvp`);
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
      await api('POST', '/mrpvp/action', { uuid, ...body });
      onDone(t('pvp.done.' + body.action, { name }));
      if (body.action === 'ban') reason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function ban() {
    const d = Number(days);
    act({ action: 'ban', duration: String(d > 0 ? d * 86400000 : 0), reason });
  }
</script>

<section class="card part">
  <h2>{t('pvp.partTitle')}</h2>
  {#if error}<div class="error">{error}</div>{/if}
  {#if p}
    <div class="stats">
      <div><span class="muted small">{t('pvp.games')}</span><strong>{p.stats.games}</strong></div>
      <div><span class="muted small">{t('pvp.wins')}</span><strong>{p.stats.wins}</strong></div>
      <div><span class="muted small">K/D</span><strong>{p.stats.kd.toFixed(2)}</strong></div>
      <div><span class="muted small">{t('pvp.winRate')}</span><strong>{p.stats.winRate.toFixed(1)} %</strong></div>
      <div><span class="muted small">{t('pvp.bestStreak')}</span><strong>{p.stats.bestStreak}</strong></div>
    </div>
    {#if p.game}
      <button class="secondary" onclick={() => go('/pvp/' + p.game)}>{t('pvp.inGame', { id: p.game })}</button>
    {/if}
    {#if p.owed > 0}
      <div class="notice small">{t('pvp.owedCount', { n: p.owed })}</div>
      {#if me.may.mrpvpRestore}
        <button class="secondary" disabled={busy} onclick={() => act({ action: 'giveBack' }, t('pvp.giveBackConfirm', { name }))}>
          {t('pvp.giveBack')}
        </button>
      {/if}
    {/if}

    {#if p.games.length && me.may.mrpvpGames}
      <div class="games">
        {#each p.games as g (g.id)}
          {@const self = g.players.find((x) => x.uuid === uuid)}
          {@const other = g.players.find((x) => x.uuid !== uuid)}
          <button class="game small" onclick={() => go('/pvp/' + g.id)}>
            <span class="mono">#{g.id}</span>
            <span class="outcome {self?.outcome ?? g.result}">{t('pvp.result.' + (self?.outcome ?? g.result))}</span>
            <span>vs {other?.name ?? '?'}</span>
            <span class="muted">{clock(g.duration)} · {hearts(self?.damageDealt)}❤ · {dateTime(g.startedAt)}</span>
          </button>
        {/each}
      </div>
    {/if}

    {#if p.ban}
      <div class="error small">
        {t('pvp.bannedUntil', { until: p.ban.permanent ? t('pvp.permanent') : dateTime(p.ban.until) })}
        {#if p.ban.reason} - {p.ban.reason}{/if} ({p.ban.by})
      </div>
      {#if me.may.mrpvpBan}
        <button class="secondary" disabled={busy} onclick={() => act({ action: 'unban' })}>{t('pvp.unban')}</button>
      {/if}
    {:else if me.may.mrpvpBan}
      <div class="ban">
        <select bind:value={days}>
          <option value="1">1 {t('pvp.day')}</option>
          <option value="7">7 {t('pvp.days')}</option>
          <option value="30">30 {t('pvp.days')}</option>
          <option value="0">{t('pvp.permanent')}</option>
        </select>
        <input type="text" maxlength="200" bind:value={reason} placeholder={t('pvp.reasonPlaceholder')} />
        <button class="secondary" disabled={busy} onclick={ban}>{t('pvp.ban')}</button>
      </div>
    {/if}
    {#if me.may.mrpvpReset && p.stats.games > 0}
      <button class="secondary danger" disabled={busy} onclick={() => act({ action: 'reset' }, t('pvp.resetConfirm', { name }))}>
        {t('pvp.reset')}
      </button>
    {/if}
  {:else if !error}
    <p class="muted small">{t('common.loading')}</p>
  {/if}
</section>

<style>
  .part {
    padding: 14px 16px;
    display: grid;
    gap: 10px;
  }

  .stats {
    display: flex;
    gap: 18px;
    flex-wrap: wrap;
  }

  .stats div {
    display: grid;
  }

  .games {
    display: grid;
    gap: 4px;
  }

  .game {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    background: none;
    border: 0;
    padding: 4px 0;
    text-align: left;
    color: var(--text);
    font-weight: 400;
  }

  .game:hover {
    background: var(--surface-2);
  }

  .outcome.win { color: var(--good); font-weight: 700; }
  .outcome.loss { color: var(--bad); font-weight: 700; }
  .outcome.draw { color: var(--warn); font-weight: 700; }

  .ban {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .ban input {
    flex: 1;
    min-width: 160px;
  }

  .danger {
    color: var(--bad);
  }
</style>
