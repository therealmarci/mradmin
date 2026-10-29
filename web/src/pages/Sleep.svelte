<script>
  // MRsleep's worlds: sleep status and who blocks with /nosleep. Everything about one player (cooldown, bypass,
  // ban) is on their page in the player management.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { head, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Toast from '../components/Toast.svelte';
  import { ICONS } from '../lib/icons.js';

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

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.moon} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.moon} /></svg>
      </span>
      <div>
        <h1>{t('sleep.title')}</h1>
        {#if overview}
          <p class="muted">{t('sleep.subtitle', { percentage: overview.percentage, minimum: overview.minimum, cooldown: duration(overview.cooldown) })}</p>
        {/if}
      </div>
    </div>
    {#if overview}
      <span class="state" class:warn={overview.blocked > 0}>
        {overview.blocked > 0 ? '⛔ ' + t('home.sleepBlocked', { n: overview.blocked }) : '✓ ' + t('home.sleepFree')}
      </span>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if overview}
    <div class="kpis">
      <div class="kpi" class:warn={overview.blocked > 0}><div class="label">{t('sleep.blocked')}</div><div class="value">{overview.blocked}</div></div>
      <div class="kpi"><div class="label">{t('sleep.cooldowns')}</div><div class="value">{overview.cooldowns}</div></div>
      <div class="kpi"><div class="label">{t('sleep.bypasses')}</div><div class="value">{overview.bypasses}</div></div>
      <div class="kpi" class:bad={overview.bans > 0}><div class="label">{t('sleep.bans')}</div><div class="value">{overview.bans}</div></div>
    </div>
    {#if me.may.players}
      <p class="muted small hint">{t('sleep.playersHint')}</p>
    {/if}

    <div class="worlds">
      {#each worlds as w (w.name)}
        {@const ph = phase(w)}
        {@const share = w.needed ? Math.min(1, w.sleeping / w.needed) : 0}
        <section class="world {ph.key}" class:blocked={w.blockers.length > 0}>
          <div class="whead">
            <span class="w-icon" aria-hidden="true">{ph.icon}</span>
            <h2>{w.name}</h2>
            <span class="chip">{t('sleep.phase.' + ph.key)}</span>
          </div>
          <div class="progress-row">
            <strong>{w.sleeping} <span class="muted">/ {w.needed}</span></strong>
            <span class="muted small">{t('sleep.sleeping', { counted: w.counted, exempt: w.exempt })}</span>
          </div>
          <div class="bar" class:full={share >= 1}><span style="width: {share * 100}%"></span></div>

          {#if w.blockers.length}
            <div class="blockers">
              <div class="b-head">⛔ {t('sleep.worldBlocked')}</div>
              <ul>
                {#each w.blockers as b (b.uuid)}
                  <li>
                    <button class="who" disabled={!me.may.players} onclick={() => go('/players/' + b.uuid)}>
                      <img src={head(b.uuid)} alt="" width="24" height="24" />
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
                <button class="danger mini clear" disabled={busy}
                  onclick={() => act({ action: 'clear', world: w.name }, t('sleep.clearConfirm', { world: w.name }))}>{t('sleep.clear')}</button>
              {/if}
            </div>
          {:else}
            <p class="free small">✓ {t('sleep.free')}</p>
          {/if}
        </section>
      {/each}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --sl: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--sl) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--sl) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--sl);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--sl);
    background: color-mix(in srgb, var(--sl) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--sl) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .state {
    position: relative;
    padding: 6px 14px;
    border-radius: 999px;
    font-weight: 700;
    color: var(--good);
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--good) 40%, var(--border));
  }

  .state.warn {
    color: var(--warn);
    border-color: color-mix(in srgb, var(--warn) 40%, var(--border));
  }

  /* Key figures, like on the health page */
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .kpi {
    --tn: var(--sl);
    display: grid;
    gap: 4px;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--sl) 10%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .kpi.warn {
    --tn: var(--warn);
  }

  .kpi.bad {
    --tn: var(--bad);
  }

  .kpi.warn .value,
  .kpi.bad .value {
    color: var(--tn);
  }

  .label {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.15;
  }

  .hint {
    margin: -4px 0 0;
  }

  /* One card per world: the phase on the left edge (day grey, night blue, thunder yellow), blocked yellow. */
  .worlds {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
    align-items: start;
  }

  .world {
    --st: var(--muted);
    display: grid;
    gap: 10px;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 4px solid var(--st);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .world.night {
    --st: var(--accent);
  }

  .world.thunder {
    --st: var(--warn);
  }

  .world.blocked {
    --st: var(--warn);
    background: color-mix(in srgb, var(--warn) 5%, var(--surface));
  }

  .whead {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .w-icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 10px;
    font-size: 1.1rem;
    background: color-mix(in srgb, var(--st) 14%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--st) 30%, transparent);
  }

  h2 {
    font-size: 1.05rem;
    margin: 0;
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .chip {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--st);
    background: color-mix(in srgb, var(--st) 14%, transparent);
  }

  .progress-row {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 6px 12px;
  }

  .progress-row strong {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1;
  }

  .progress-row strong .muted {
    font-size: 1rem;
  }

  .bar {
    height: 8px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: var(--sl);
    transition: width 0.4s;
  }

  .bar.full span {
    background: var(--good);
  }

  .blockers {
    display: grid;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }

  .b-head {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--warn);
  }

  .blockers ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .blockers li {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 6px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .who {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-weight: 700;
    margin-right: auto;
  }

  .who:disabled {
    opacity: 1;
    cursor: default;
  }

  .who:not(:disabled):hover {
    filter: none;
    color: var(--sl);
  }

  .who img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .clear {
    justify-self: start;
  }

  .free {
    margin: 0;
    padding-top: 10px;
    border-top: 1px solid var(--border);
    color: var(--good);
    font-weight: 600;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }
</style>
