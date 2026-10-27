<script>
  // MRsleep's part of a player's page: /nosleep blocks, cooldown, bypass and ban - with MRsleep's permissions.
  // view "tile": only the status as a tile on the player's overview.
  import { untrack } from 'svelte';
  import { moduleAction, playerPart } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, duration } from '../lib/format.js';
  import PartTile from './PartTile.svelte';

  let { uuid, me, name, onDone, view = 'full', onOpen } = $props();

  let p = $state(null);
  let error = $state('');
  let busy = $state(false);
  let reason = $state('');

  async function load() {
    try {
      p = await playerPart(uuid, 'mrsleep');
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

  // Each quick choice needs a level (limits in MRsleep's config): '' = support, 'Staff', 'Unlimited'. Older MRsleep
  // versions send no levels - then everything is level ''.
  const level = (kind, i) => p?.[kind + 'Levels']?.[i] ?? '';
  const may = (kind, i) => !!me.may['mrsleep' + kind[0].toUpperCase() + kind.slice(1) + level(kind, i)];

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    try {
      await moduleAction('mrsleep', { uuid, ...body });
      // bypassStaff / banUnlimited ... are the same action at a higher level (MRsleep's limits).
      const base = body.action.replace(/(Staff|Unlimited)$/, '');
      onDone(t('sleep.done.' + base));
      if (base === 'ban') reason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const time = (ms) => (ms === -1 ? t('sleep.permanent') : duration(ms));
  const nothing = $derived(p && !p.blocking.length && !p.cooldown && !p.bypass && !p.ban);
  const chips = $derived(p ? [
    ...(p.sleeping ? [{ text: '☾ ' + t('sleep.isSleeping'), tone: 'good' }] : []),
    ...(p.excluded ? [{ text: t('sleep.excluded.' + p.excluded), tone: '' }] : []),
    ...p.blocking.map((b) => ({ text: '⛔ ' + t('sleep.blocks', { world: b.world }), tone: 'warn' })),
    ...(p.cooldown ? [{ text: '⏱ ' + t('sleep.cooldownLeft', { time: duration(p.cooldown) }), tone: '' }] : []),
    ...(p.bypass ? [{ text: '✓ ' + t('sleep.bypassLeft', { time: duration(p.bypass) }), tone: 'info' }] : []),
    ...(p.ban ? [{ text: '✕ ' + (p.ban.permanent ? t('sleep.bannedPermanent') : t('sleep.bannedUntil', { time: dateTime(p.ban.until) })), tone: 'bad' }] : []),
    ...(nothing ? [{ text: '✓ ' + t('sleep.nothing'), tone: 'good' }] : []),
  ] : []);
</script>

{#if view === 'tile'}
  {@const state = !p ? null : p.ban ? ['banned', 'bad'] : p.blocking.length ? ['blocked', 'warn'] : p.sleeping ? ['sleeping', 'good'] : ['ok', 'good']}
  <PartTile label={t('nav.sleep')} loading={!p} {error} {onOpen} small
    value={state ? t('sleep.tile.' + state[0]) : ''} tone={state?.[1]}
    facts={p ? [
      { label: t('sleep.section.cooldown'), value: p.cooldown ? duration(p.cooldown) : '–' },
      { label: t('sleep.tile.bypass'), value: p.bypass ? duration(p.bypass) : '–', tone: p.bypass ? 'info' : '' },
      { label: t('player.banFact'), value: !p.ban ? '–' : p.ban.permanent ? t('sleep.permanent') : dateTime(p.ban.until), tone: p.ban ? 'bad' : '' },
    ] : []} />
{:else}
<section class="card part">
  <div class="phead">
    <h2>🛏 {t('sleep.title')}</h2>
    {#if p}
      <div class="chips">
        {#each chips as c}<span class="tag {c.tone}">{c.text}</span>{/each}
      </div>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}

  {#if p}
    {#if p.ban}
      <p class="small"><span class="muted">{t('sleep.banReason')}:</span> {p.ban.reason} · <span class="muted">{t('sleep.banBy')}:</span> {p.ban.by}</p>
    {/if}

    <div class="groups">
      {#if me.may.mrsleepClear && p.blocking.length}
        <div class="group">
          <div class="glabel">{t('sleep.section.blocks')}</div>
          <button class="secondary" disabled={busy} onclick={() => act({ action: 'release' })}>{t('sleep.release')}</button>
        </div>
      {/if}

      {#if me.may.mrsleepBypass}
        <div class="group">
          <div class="glabel">{t('sleep.section.cooldown')}</div>
          <div class="buttons">
            {#each p.bypassDurations as d, i}
              <button class="secondary" disabled={busy || !may('bypass', i)} title={may('bypass', i) ? '' : t('sleep.overLimit')}
                onclick={() => act({ action: 'bypass' + level('bypass', i), duration: String(d) })}>{t('sleep.bypassFor', { time: time(d) })}</button>
            {/each}
            {#if p.bypass}
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'removeBypass' })}>{t('sleep.removeBypass')}</button>
            {/if}
            {#if p.cooldown && (p.bypassLevels ? me.may.mrsleepReset : true)}
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'resetCooldown' })}>{t('sleep.resetCooldown')}</button>
            {/if}
          </div>
        </div>
      {/if}

      {#if me.may.mrsleepBan}
        <div class="group">
          <div class="glabel">{t('sleep.section.ban')}</div>
          {#if p.ban}
            <button class="secondary" disabled={busy} onclick={() => act({ action: 'unban' })}>{t('sleep.unban')}</button>
          {:else}
            <input type="text" bind:value={reason} maxlength="200" placeholder={t('sleep.reasonPlaceholder')} />
            <div class="buttons">
              {#each p.banDurations as d, i}
                <button class="danger" disabled={busy || !may('ban', i)} title={may('ban', i) ? '' : t('sleep.overLimit')}
                  onclick={() => act({ action: 'ban' + level('ban', i), duration: String(d), reason }, t('sleep.banConfirm', { name, time: time(d) }))}>{t('sleep.banFor', { time: time(d) })}</button>
              {/each}
            </div>
          {/if}
        </div>
      {/if}
    </div>
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

  .tag.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .tag.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  p {
    margin: 10px 0 0;
  }

  .groups {
    display: grid;
    gap: 14px;
    margin-top: 14px;
  }

  .group:empty {
    display: none;
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
