<script>
  // MRsleep's worlds: sleep status and who blocks with /nosleep. Everything about one player (cooldown, bypass,
  // ban) is on their page in the player management.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { head, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Toast from '../components/Toast.svelte';

  let { me } = $props();

  let overview = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      overview = await moduleData('mrsleep', 'overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Every 3 seconds - sleeping and waking up show quickly.
  $effect(() => {
    load();
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  });

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrsleep', body);
      message = t('sleep.done.' + body.action);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const worlds = $derived((overview?.worlds ?? []).filter((w) => w.managed));
  const phase = (w) => (w.thunder ? { icon: '⛈', key: 'thunder' } : w.night ? { icon: '☽', key: 'night' } : { icon: '☀', key: 'day' });
</script>

<div class="stack">
  <div>
    <h1>{t('sleep.title')}</h1>
    {#if overview}
      <p class="muted small">
        {t('sleep.subtitle', { percentage: overview.percentage, minimum: overview.minimum, cooldown: duration(overview.cooldown) })}
      </p>
    {/if}
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if overview}
    <div class="kpis">
      <div class="card kpi"><div class="label">{t('sleep.blocked')}</div><div class="value">{overview.blocked}</div></div>
      <div class="card kpi"><div class="label">{t('sleep.cooldowns')}</div><div class="value">{overview.cooldowns}</div></div>
      <div class="card kpi"><div class="label">{t('sleep.bypasses')}</div><div class="value">{overview.bypasses}</div></div>
      <div class="card kpi"><div class="label">{t('sleep.bans')}</div><div class="value">{overview.bans}</div></div>
    </div>
    {#if me.may.players}
      <p class="muted small">{t('sleep.playersHint')}</p>
    {/if}

    <div class="worlds">
      {#each worlds as w (w.name)}
        {@const ph = phase(w)}
        <section class="card world">
          <div class="whead">
            <h2>{w.name}</h2>
            <span class="chip">{ph.icon} {t('sleep.phase.' + ph.key)}</span>
          </div>
          <div class="progress-row">
            <strong>{w.sleeping} / {w.needed}</strong>
            <span class="muted small">{t('sleep.sleeping', { counted: w.counted, exempt: w.exempt })}</span>
          </div>
          <div class="bar"><div style="width: {w.needed ? Math.min(100, (w.sleeping / w.needed) * 100) : 0}%"></div></div>

          {#if w.blockers.length}
            <div class="blockers">
              <div class="small warn-text">⛔ {t('sleep.worldBlocked')}</div>
              <ul>
                {#each w.blockers as b (b.uuid)}
                  <li>
                    <button class="who" disabled={!me.may.players} onclick={() => go('/players/' + b.uuid)}>
                      <img src={head(b.uuid)} alt="" width="22" height="22" />
                      <span>{b.name}</span>
                    </button>
                    <span class="muted small">{b.pending ? t('sleep.pending') : t('sleep.nights', { n: b.nights })}</span>
                    {#if me.may.mrsleepClear}
                      <button class="secondary mini" disabled={busy}
                        onclick={() => act({ action: 'release', uuid: b.uuid })}>{t('sleep.release')}</button>
                    {/if}
                  </li>
                {/each}
              </ul>
              {#if me.may.mrsleepClear && w.blockers.length > 1}
                <button class="danger mini" disabled={busy}
                  onclick={() => act({ action: 'clear', world: w.name }, t('sleep.clearConfirm', { world: w.name }))}>{t('sleep.clear')}</button>
              {/if}
            </div>
          {:else}
            <p class="muted small">{t('sleep.free')}</p>
          {/if}
        </section>
      {/each}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  }

  .kpi {
    padding: 14px 16px;
  }

  .label {
    color: var(--muted);
    font-size: 0.82rem;
    font-weight: 600;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 700;
  }

  .worlds {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  }

  .whead {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  .whead h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .chip {
    font-size: 0.8rem;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--surface-2);
  }

  .progress-row {
    display: flex;
    gap: 10px;
    align-items: baseline;
    margin-top: 12px;
  }

  .progress-row strong {
    font-size: 1.4rem;
  }

  .bar {
    height: 8px;
    border-radius: 4px;
    background: var(--surface-2);
    margin: 6px 0 10px;
    overflow: hidden;
  }

  .bar div {
    height: 100%;
    background: var(--accent);
  }

  .blockers ul {
    list-style: none;
    padding: 0;
    margin: 6px 0 8px;
    display: grid;
    gap: 6px;
  }

  .blockers li {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .blockers li .mini {
    margin-left: auto;
  }

  .who {
    display: flex;
    gap: 8px;
    align-items: center;
    background: transparent;
    color: var(--text);
    padding: 2px 4px;
    font-weight: 600;
  }

  .who:disabled {
    opacity: 1;
  }

  img {
    border-radius: 5px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .warn-text {
    color: var(--warn);
    font-weight: 600;
  }
</style>
