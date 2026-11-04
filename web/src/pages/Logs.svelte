<script>
  // Page Log: events of MRadmin and of every plugin (the same as in their Discord logs) plus the actions in the web
  // interface, newest first, kept up to 14 days. Filters: source, text, time range. See LogRoutes.java.
  // Live: MRadmin sends "log" on the event stream (lib/live.svelte.js), the page then loads only what is newer.
  // Second tab "Server log" (with the web console's permission): components/ServerLog.svelte.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { ICONS } from '../lib/icons.js';
  import { live } from '../lib/live.svelte.js';
  import ServerLog from '../components/ServerLog.svelte';

  let { me } = $props();

  // events | server - remembered for this browser tab
  let tab = $state(sessionStorage.getItem('mradmin.logs.tab') === 'server' ? 'server' : 'events');
  const server = $derived(tab === 'server' && !!me?.may?.console);
  $effect(() => sessionStorage.setItem('mradmin.logs.tab', tab));

  let entries = $state([]);
  let sources = $state({});
  let more = $state(false);
  let error = $state('');
  let loading = $state(false);
  let source = $state('');
  let query = $state('');
  let range = $state('24h');

  const RANGES = { '1h': 3600000, '24h': 86400000, '7d': 604800000, '14d': 1209600000 };
  const since = $derived(Date.now() - RANGES[range]);

  async function load(older = false) {
    loading = true;
    try {
      const p = new URLSearchParams({ limit: '200', since: String(Date.now() - RANGES[range]) });
      if (source) p.set('source', source);
      if (query.trim()) p.set('q', query.trim());
      if (older && entries.length) p.set('before', String(entries[entries.length - 1].time));
      const data = await api('GET', '/logs?' + p);
      entries = older ? [...entries, ...data.entries] : data.entries;
      sources = data.sources;
      more = data.more;
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      loading = false;
    }
  }

  // Reload when a filter changes (the text after a short pause).
  let timer;
  $effect(() => {
    source;
    range;
    const q = query;
    clearTimeout(timer);
    timer = setTimeout(() => load(), q ? 300 : 0);
  });
  /** Only the entries after the newest one shown, put on top (same filters). */
  async function loadNew() {
    if (loading || !entries.length) return load();
    try {
      const newest = entries[0].time;
      const p = new URLSearchParams({ limit: '200', since: String(newest) });
      if (source) p.set('source', source);
      if (query.trim()) p.set('q', query.trim());
      const data = await api('GET', '/logs?' + p);
      const seen = new Set(entries.filter((e) => e.time === newest).map(key));
      const fresh = data.entries.filter((e) => !seen.has(key(e)));
      if (fresh.length) entries = [...fresh, ...entries];
      sources = data.sources;
    } catch (e) {
      error = errorText(e);
    }
  }
  const key = (e) => e.time + '|' + e.source + '|' + e.event + '|' + e.text;

  // A short pause collects bursts (e.g. many events in one second) into one request.
  let liveTimer;
  $effect(() => {
    if (!live.log.tick) return;
    clearTimeout(liveTimer);
    liveTimer = setTimeout(() => !document.hidden && loadNew(), 250);
  });
  // Without the event stream (connection lost): every 15 seconds.
  $effect(() => {
    if (live.connected) return;
    const id = setInterval(() => !document.hidden && loadNew(), 15000);
    return () => clearInterval(id);
  });

  const label = (s) => (s === 'web' ? t('logs.web') : s === 'mradmin' ? 'MRadmin' : sources[s] ?? s);

  /** Discord markdown to safe HTML: escaping first, then **bold**, *italic*, `code`. */
  function render(text) {
    const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return esc
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/(^|[^*])\*([^*]+)\*/g, '$1<i>$2</i>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
  }

  // Group by day for the separators.
  const days = $derived.by(() => {
    const out = [];
    for (const e of entries) {
      const day = new Date(e.time).toDateString();
      if (!out.length || out[out.length - 1].day !== day) out.push({ day, time: e.time, items: [] });
      out[out.length - 1].items.push(e);
    }
    return out;
  });
  const time = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
</script>

<div class="page">
  <header class="head">
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={ICONS.news} /></svg>
    </span>
    <div>
      <h1>{t('logs.title')}</h1>
      <p class="muted">{t('logs.subtitle')}</p>
    </div>
  </header>

  {#if me?.may?.console}
    <span class="segments tabs" role="tablist">
      <button type="button" role="tab" aria-selected={tab === 'events'} class:on={tab === 'events'} onclick={() => (tab = 'events')}>{t('logs.tab.events')}</button>
      <button type="button" role="tab" aria-selected={tab === 'server'} class:on={tab === 'server'} onclick={() => (tab = 'server')}>{t('logs.tab.server')}</button>
    </span>
  {/if}

  <div class="filters">
    {#if !server}
    <div class="chips" role="group" aria-label={t('logs.source')}>
      <button type="button" class:on={source === ''} onclick={() => (source = '')}>{t('logs.all')}</button>
      {#each Object.keys(sources) as s}
        <button type="button" class:on={source === s} onclick={() => (source = s)}>{label(s)}</button>
      {/each}
    </div>
    {/if}
    <div class="row">
      <input class="search" type="search" placeholder={t(server ? 'logs.search.server' : 'logs.search')} bind:value={query} />
      <span class="segments" role="radiogroup" aria-label={t('logs.range')}>
        {#each Object.keys(RANGES) as r}
          <button type="button" role="radio" aria-checked={range === r} class:on={range === r} onclick={() => (range = r)}>{t('logs.range.' + r)}</button>
        {/each}
      </span>
    </div>
  </div>

  {#if server}
    <ServerLog {query} {since} />
  {:else}
  {#if error}<div class="error">{error}</div>{/if}

  {#if days.length}
    {#each days as d (d.day)}
      <section class="day">
        <h2>{new Date(d.time).toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
        <ul>
          {#each d.items as e, i (e.time + e.source + e.event + i)}
            <li style="--c:{e.color}">
              <span class="time mono" title={dateTime(e.time)}>{time(e.time)}</span>
              <span class="src">{label(e.source)}</span>
              <span class="text">{@html render(e.text)}<span class="event mono">{e.event}</span></span>
            </li>
          {/each}
        </ul>
      </section>
    {/each}
    {#if more}
      <button type="button" class="ghost more" disabled={loading} onclick={() => load(true)}>{t('logs.more')}</button>
    {/if}
  {:else if !loading}
    <p class="muted">{t('logs.empty')}</p>
  {/if}
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .head {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 16%, var(--surface-2));
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .head p {
    margin: 2px 0 0;
  }

  .filters {
    display: grid;
    gap: 10px;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chips button {
    padding: 4px 12px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--muted);
    font-size: 0.86rem;
    font-weight: 600;
  }

  .chips button.on {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
    background: color-mix(in srgb, var(--accent) 12%, var(--surface-2));
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }

  .search {
    flex: 1;
    min-width: 200px;
    margin: 0;
    padding: 7px 10px;
    border-radius: 9px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font: inherit;
  }

  .segments {
    display: inline-flex;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .segments button {
    padding: 5px 12px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    font-size: 0.86rem;
    font-weight: 600;
  }

  .segments button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: 0 1px 3px rgb(0 0 0 / 18%);
  }

  .tabs {
    justify-self: start;
  }

  .day h2 {
    margin: 4px 0 8px;
    font-size: 0.9rem;
    color: var(--muted);
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    overflow: hidden;
  }

  li {
    display: grid;
    grid-template-columns: 74px 110px minmax(0, 1fr);
    gap: 12px;
    align-items: baseline;
    padding: 9px 14px;
    border-left: 3px solid var(--c);
    font-size: 0.9rem;
  }

  li + li {
    border-top: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
  }

  .time {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .src {
    justify-self: start;
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 0.76rem;
    font-weight: 700;
    color: var(--c);
    background: color-mix(in srgb, var(--c) 14%, transparent);
    white-space: nowrap;
  }

  .text {
    overflow-wrap: anywhere;
  }

  .event {
    margin-left: 8px;
    color: var(--muted);
    font-size: 0.72rem;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .ghost {
    padding: 6px 14px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .more {
    justify-self: center;
  }

  @media (max-width: 640px) {
    li {
      grid-template-columns: auto 1fr;
    }

    .text {
      grid-column: 1 / -1;
    }
  }
</style>
