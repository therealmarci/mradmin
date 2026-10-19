<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, tpsTone, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';

  let { me } = $props();

  let data = $state(null);
  let error = $state('');

  async function load() {
    try {
      data = await api('GET', '/overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Refresh every 5 seconds while the overview is open (the server keeps a snapshot, this costs it nothing).
  $effect(() => {
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  const phaseTone = { none: 'good', planned: 'warn', upcoming: 'warn', active: 'bad' };
</script>

<div class="stack">
  <div>
    <h1>{t('home.hello', { name: me.name })}</h1>
    <p class="muted">{t('home.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    <div class="grid">
      {#if data.server}
        {@const s = data.server}
        <section class="card">
          <div class="label">{t('overview.server')}</div>
          <div class="big {tpsTone(s.tps[0])}">{s.tps[0].toFixed(1)} <span class="unit">TPS</span></div>
          <div class="facts">
            <div><span class="muted">{t('overview.tps')}</span> {s.tps.map((v) => v.toFixed(1)).join(' / ')}</div>
            <div><span class="muted">{t('overview.mspt')}</span> {s.mspt.toFixed(1)} ms</div>
            <div><span class="muted">{t('overview.ram')}</span> {s.ramUsed} / {s.ramMax} MB</div>
          </div>
          <div class="bar"><div style="width: {Math.min(100, (s.ramUsed / s.ramMax) * 100)}%"></div></div>
        </section>
      {/if}

      {#if data.tickets}
        <button class="card tile" onclick={() => go('/tickets')}>
          <div class="label">{t('overview.tickets')}</div>
          <div class="big {data.tickets.unread > 0 ? 'bad' : data.tickets.open > 0 ? 'warn' : 'good'}">
            {data.tickets.open} <span class="unit">{t('overview.openTickets')}</span>
          </div>
          {#if data.tickets.unread > 0}
            <div class="small bad">{data.tickets.unread} {t('overview.unread')}</div>
          {/if}
          <div class="small link">{t('overview.toTickets')} →</div>
        </button>
      {/if}

      {#if data.maintenance}
        {@const m = data.maintenance}
        <section class="card">
          <div class="label">{t('overview.maintenance')}</div>
          <div class="big small-big {phaseTone[m.phase]}">{t('overview.phase.' + m.phase)}</div>
          {#if m.phase !== 'none'}
            {#if m.title}<div class="small">{m.title}</div>{/if}
            <div class="facts">
              <div><span class="muted">{t('overview.from')}</span> {dateTime(m.start)}</div>
              <div><span class="muted">{t('overview.until')}</span> {m.end ? dateTime(m.end) : t('overview.openEnd')}</div>
            </div>
          {/if}
        </section>
      {/if}
    </div>

    {#if data.server}
      <section class="card">
        <div class="label">{t('overview.players')} · {data.server.online} / {data.server.max}</div>
        {#if data.server.players.length === 0}
          <p class="muted small">{t('overview.nobody')}</p>
        {:else}
          <div class="players">
            {#each data.server.players as p (p.uuid)}
              <div class="player">
                <img src={head(p.uuid, 48)} alt="" width="32" height="32" />
                <div>
                  <div class="pname">{p.name}</div>
                  <div class="muted small">
                    {p.world} · {p.ping} ms
                    {#if p.admin}· <span class="warn">{t('overview.admin')}</span>{/if}
                    {#if p.frozen}· <span class="bad">{t('overview.frozen')}</span>{/if}
                  </div>
                </div>
              </div>
            {/each}
          </div>
        {/if}
      </section>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}

  <section class="card account">
    <div class="label">{t('overview.account')}</div>
    <div class="small">
      {t('home.lastLogin')}: {dateTime(me.lastLogin)} · {t('home.recovery')}:
      <span class:warn={me.recoveryLeft <= 2}>{me.recoveryLeft} / 8</span>
    </div>
  </section>
  <p class="muted small">MRadmin {me.version}</p>
</div>

<style>
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
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
    font-weight: 600;
  }

  .big {
    font-size: 2rem;
    font-weight: 800;
    margin: 4px 0 8px;
    line-height: 1.1;
  }

  .small-big {
    font-size: 1.3rem;
  }

  .unit {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--muted);
  }

  .facts {
    font-size: 0.9rem;
    display: grid;
    gap: 2px;
  }

  .facts .muted {
    display: inline-block;
    min-width: 9em;
  }

  .bar {
    height: 6px;
    border-radius: 3px;
    background: var(--surface-2);
    margin-top: 10px;
    overflow: hidden;
  }

  .bar div {
    height: 100%;
    background: var(--accent);
  }

  .good {
    color: var(--good);
  }

  .warn {
    color: var(--warn);
  }

  .bad {
    color: var(--bad);
  }

  .link {
    color: var(--accent);
    margin-top: 6px;
  }

  .players {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    margin-top: 10px;
  }

  .player {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .player img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .pname {
    font-weight: 600;
  }

  .account {
    padding: 14px 20px;
  }
</style>
