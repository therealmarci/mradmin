<script>
  // MRsleep's part of a player's page: /nosleep blocks, cooldown, bypass and ban - with MRsleep's permissions.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, duration } from '../lib/format.js';

  let { uuid, me, name, onDone } = $props();

  let p = $state(null);
  let error = $state('');
  let busy = $state(false);
  let reason = $state('');

  async function load() {
    try {
      p = await api('GET', `/players/${uuid}/mrsleep`);
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

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    try {
      await api('POST', '/mrsleep/action', { uuid, ...body });
      onDone(t('sleep.done.' + body.action));
      if (body.action === 'ban') reason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const time = (ms) => (ms === -1 ? t('sleep.permanent') : duration(ms));
  const nothing = $derived(p && !p.blocking.length && !p.cooldown && !p.bypass && !p.ban);
</script>

<section class="card part">
  <div class="phead">
    <h2>🛏 {t('sleep.title')}</h2>
    {#if p}
      <div class="chips">
        {#if p.sleeping}<span class="tag good">☾ {t('sleep.isSleeping')}</span>{/if}
        {#if p.excluded}<span class="tag">{t('sleep.excluded.' + p.excluded)}</span>{/if}
        {#each p.blocking as b}<span class="tag warn">⛔ {t('sleep.blocks', { world: b.world })}</span>{/each}
        {#if p.cooldown}<span class="tag">⏱ {t('sleep.cooldownLeft', { time: duration(p.cooldown) })}</span>{/if}
        {#if p.bypass}<span class="tag info">✓ {t('sleep.bypassLeft', { time: duration(p.bypass) })}</span>{/if}
        {#if p.ban}<span class="tag bad">✕ {p.ban.permanent ? t('sleep.bannedPermanent') : t('sleep.bannedUntil', { time: dateTime(p.ban.until) })}</span>{/if}
        {#if nothing}<span class="tag good">✓ {t('sleep.nothing')}</span>{/if}
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
            {#each p.bypassDurations as d}
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'bypass', duration: String(d) })}>{t('sleep.bypassFor', { time: time(d) })}</button>
            {/each}
            {#if p.bypass}
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'removeBypass' })}>{t('sleep.removeBypass')}</button>
            {/if}
            {#if p.cooldown}
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
              {#each p.banDurations as d}
                <button class="danger" disabled={busy}
                  onclick={() => act({ action: 'ban', duration: String(d), reason }, t('sleep.banConfirm', { name, time: time(d) }))}>{t('sleep.banFor', { time: time(d) })}</button>
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
