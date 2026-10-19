<script>
  import { t } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';

  let { me } = $props();
</script>

<div class="stack">
  <div>
    <h1>{t('home.hello', { name: me.name })}</h1>
    <p class="muted">{t('home.subtitle')}</p>
  </div>

  <div class="grid">
    <div class="card">
      <div class="label">{t('home.lastLogin')}</div>
      <div class="value">{dateTime(me.lastLogin)}</div>
    </div>
    <button class="card tile" onclick={() => go('/account')}>
      <div class="label">{t('home.recovery')}</div>
      <div class="value" class:low={me.recoveryLeft <= 2}>{me.recoveryLeft} / 8</div>
      {#if me.recoveryLeft <= 2}
        <div class="small low">{t('home.recoveryLow')}</div>
      {/if}
    </button>
  </div>

  <div class="notice">{t('home.soon')}</div>
  <p class="muted small">MRadmin {me.version}</p>
</div>

<style>
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  }

  .tile {
    text-align: left;
    color: var(--text);
    display: block;
    font-weight: 400;
  }

  .label {
    color: var(--muted);
    font-size: 0.85rem;
  }

  .value {
    font-size: 1.4rem;
    font-weight: 700;
    margin-top: 4px;
  }

  .low {
    color: var(--warn);
  }
</style>
