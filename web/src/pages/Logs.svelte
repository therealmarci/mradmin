<script>
  // Page Log as a log viewer: search, time range and Live/Pause on top; the sources on the left
  // (components/LogLayout.svelte), one row per entry on the right (components/LogList.svelte), all in one list by time.
  // Sources: MRadmin and every plugin (the events of their Discord logs) and the web interface - mradmin.web.audit -
  // and the console output ("console", mradmin.web.console.view; older days from Paper's log files). LogRoutes.java.
  // Live: events come with "log" on the event stream (lib/live.svelte.js), console lines once a second from
  // /console/tail (memory); paused, new entries wait above the list. Commands: page Console (admins only).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ICONS, moduleHue } from '../lib/icons.js';
  import { live } from '../lib/live.svelte.js';
  import LogLayout from '../components/LogLayout.svelte';
  import LogFilter from '../components/LogFilter.svelte';
  import LogList from '../components/LogList.svelte';

  let { me } = $props();

  let entries = $state([]);
  let queued = $state([]);
  let names = $state({});
  let counts = $state({});
  let more = $state(false);
  let error = $state('');
  let loading = $state(false);
  let selected = $state([]);
  let query = $state('');
  let range = $state('24h');
  let paused = $state(false);
  // number of the newest console line seen (for /console/tail)
  let consoleSeq = 0;

  const RANGES = { '1h': 3600000, '24h': 86400000, '7d': 604800000, '14d': 1209600000 };
  const CONSOLE = { info: '#8B95A5', warn: '#F59E0B', error: '#E5484D' };
  const wants = (source) => !selected.length || selected.includes(source);

  function params(extra = {}) {
    const p = new URLSearchParams({ limit: '200', since: String(Date.now() - RANGES[range]), ...extra });
    if (selected.length) p.set('sources', selected.join(','));
    if (query.trim()) p.set('q', query.trim());
    return p;
  }

  async function load(older = false) {
    loading = true;
    try {
      if (!older && me?.may?.console) {
        // Only the number of the newest console line - from there the live lines follow.
        consoleSeq = (await api('GET', '/console/tail?after=9000000000000000')).last;
      }
      const extra = older && entries.length ? { before: String(entries.at(-1).time) } : { counts: '1' };
      const data = await api('GET', '/logs?' + params(extra));
      entries = older ? [...entries, ...data.entries] : data.entries;
      if (!older) queued = [];
      names = data.sources;
      if (data.counts) counts = data.counts;
      more = data.more;
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      loading = false;
    }
  }

  const key = (e) => e.time + '|' + e.source + '|' + e.event + '|' + e.text;

  /** New entries on top (or waiting while paused), counted for the filter list. */
  function add(fresh) {
    if (!fresh.length) return;
    const next = { ...counts };
    for (const e of fresh) next[e.source] = (next[e.source] ?? 0) + 1;
    counts = next;
    if (paused) queued = [...fresh, ...queued];
    else entries = [...fresh, ...entries];
  }

  /** Events newer than the newest one shown or waiting (same filters, without the console). */
  async function loadNew() {
    const newest = [...queued, ...entries].find((e) => e.source !== 'console');
    if (loading || !newest) return load();
    try {
      const sources = (selected.length ? selected : Object.keys(names)).filter((s) => s !== 'console');
      if (!sources.length) return;
      const p = params({ since: String(newest.time) });
      p.set('sources', sources.join(','));
      const data = await api('GET', '/logs?' + p);
      const seen = new Set([...queued, ...entries].filter((e) => e.time === newest.time).map(key));
      add(data.entries.filter((e) => !seen.has(key(e))));
      names = { ...names, ...data.sources };
    } catch (e) {
      error = errorText(e);
    }
  }

  /** New console lines from memory (cheap, once a second). */
  async function pollConsole() {
    if (loading || document.hidden || !me?.may?.console) return;
    try {
      const result = await api('GET', '/console/tail?after=' + consoleSeq);
      if (result.last < consoleSeq) {
        // The server was restarted.
        consoleSeq = 0;
        return;
      }
      consoleSeq = result.last;
      if (!wants('console')) return;
      const q = query.trim().toLowerCase();
      const now = Date.now();
      add(
        result.lines
          .filter((l) => !q || l.text.toLowerCase().includes(q))
          .map((l, i) => ({ time: now + i, source: 'console', event: l.level, text: l.text, color: CONSOLE[l.level] }))
          .reverse(),
      );
    } catch {
      // next second again
    }
  }

  function showQueued() {
    entries = [...queued, ...entries];
    queued = [];
  }

  // Again when a filter changes (the text after a short pause).
  let timer;
  $effect(() => {
    selected;
    range;
    const q = query;
    clearTimeout(timer);
    timer = setTimeout(() => load(), q ? 300 : 0);
  });
  // A short pause collects bursts (many events in one second) into one request.
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
  $effect(() => {
    if (!me?.may?.console) return;
    const id = setInterval(pollConsole, 1000);
    return () => clearInterval(id);
  });
  $effect(() => {
    if (!paused && queued.length) showQueued();
  });

  const label = (s) =>
    s === 'web' ? t('logs.web') : s === 'mradmin' ? 'MRadmin' : s === 'console' ? t('logs.console') : names[s] ?? s;
  const color = (s) =>
    s === 'web' ? '#3b82f6' : s === 'mradmin' ? 'var(--accent)' : s === 'console' ? '#8b95a5' : `hsl(${moduleHue(s)} 62% 55%)`;

  const filterItems = $derived(
    Object.keys(names).map((s) => ({ key: s, label: label(s), color: color(s), count: counts[s] ?? 0 })),
  );

  /** Discord markdown to safe HTML: escaping first, then **bold**, *italic*, `code`. */
  function render(text) {
    const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return esc
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/(^|[^*])\*([^*]+)\*/g, '$1<i>$2</i>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
  }

  const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  // "[12:00:00] [Server thread/WARN]: text" (files) or "[12:00:00 WARN]: text" (console): time and level have their
  // own columns, the row shows only the text.
  const PREFIX = /^\[(\d{2}:\d{2}:\d{2})(?: [A-Z]+)?\](?: \[[^\]]*\])?:?\s?/;

  // Stable keys (new entries come on top): the same entry twice gets a counter.
  const rows = $derived.by(() => {
    const seen = new Map();
    return entries.map((e) => {
      const k = key(e);
      const n = seen.get(k) ?? 0;
      seen.set(k, n + 1);
      const console = e.source === 'console';
      const m = console ? PREFIX.exec(e.text) : null;
      return {
        key: n ? k + '|' + n : k,
        time: e.time,
        clock: console ? (m ? m[1] : '') : clock(e.time),
        badge: { label: label(e.source), color: color(e.source) },
        accent: e.color,
        text: console ? (m ? e.text.slice(m[0].length) : e.text) : render(e.text),
        html: !console,
        mono: console,
        tag: console ? t('logs.level.' + e.event) : e.event,
        details: console
          ? [
              { label: t('logs.detail.time'), value: new Date(e.time).toLocaleDateString() + (m ? ' ' + m[1] : '') },
              { label: t('logs.detail.line'), value: e.text },
            ]
          : [
              { label: t('logs.detail.time'), value: new Date(e.time).toLocaleString() },
              { label: t('logs.detail.source'), value: label(e.source) },
              { label: t('logs.detail.event'), value: e.event || '-' },
            ],
      };
    });
  });
</script>

<div class="page">
  <header class="head">
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={ICONS.news} /></svg>
    </span>
    <div class="title">
      <h1>{t('logs.title')}</h1>
      <p class="muted">{t('logs.subtitle')}</p>
    </div>
    <button type="button" class="live" class:paused aria-pressed={paused} title={t(paused ? 'logs.resume' : 'logs.pause')}
      onclick={() => (paused = !paused)}>
      <span class="pulse" aria-hidden="true"></span>
      {t(paused ? 'logs.paused' : 'logs.live')}
      <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
        {#if paused}<path d="M7 5v14l12-7z" />{:else}<path d="M7 5h3v14H7zM14 5h3v14h-3z" />{/if}
      </svg>
    </button>
  </header>

  <div class="toolbar">
    <label class="search">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
      <input type="search" placeholder={t('logs.search')} aria-label={t('logs.search')} bind:value={query} />
    </label>
    <span class="segments" role="radiogroup" aria-label={t('logs.range')}>
      {#each Object.keys(RANGES) as r}
        <button type="button" role="radio" aria-checked={range === r} class:on={range === r} onclick={() => (range = r)}>{t('logs.range.' + r)}</button>
      {/each}
    </span>
  </div>

  {#if error}<div class="error">{error}</div>{/if}
  <LogLayout active={selected.length}>
    {#snippet filters()}
      <LogFilter title={t('logs.sources')} items={filterItems} bind:selected />
    {/snippet}
    <LogList {rows} {more} {loading} empty={t('logs.empty')} pending={queued.length} onmore={() => load(true)} onshow={showQueued} />
  </LogLayout>
</div>

<style>
  .page {
    container-type: inline-size;
    display: grid;
    gap: 14px;
  }

  .head {
    display: flex;
    gap: 14px;
    align-items: center;
    padding: 16px 20px;
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
    width: 46px;
    height: 46px;
    flex-shrink: 0;
    border-radius: 12px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 16%, var(--surface-2));
  }

  .title {
    flex: 1;
    min-width: 0;
  }

  h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  .head p {
    margin: 2px 0 0;
    font-size: 0.88rem;
  }

  .live {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
    padding: 6px 12px;
    border-radius: 999px;
    border: 1px solid color-mix(in srgb, #22c55e 45%, var(--border));
    background: color-mix(in srgb, #22c55e 12%, var(--surface));
    color: #22c55e;
    font-size: 0.84rem;
    font-weight: 700;
  }

  .live.paused {
    border-color: var(--border);
    background: var(--surface-2);
    color: var(--muted);
  }

  .pulse {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: currentColor;
    animation: pulse 1.6s ease-in-out infinite;
  }

  .paused .pulse {
    animation: none;
  }

  @keyframes pulse {
    50% {
      opacity: 0.3;
    }
  }

  .toolbar {
    position: sticky;
    top: 0;
    z-index: 5;
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
    padding: 10px 12px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
  }

  .search {
    display: flex;
    flex: 1;
    min-width: 200px;
    align-items: center;
    gap: 8px;
    margin: 0;
    padding: 0 10px;
    border-radius: 9px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--muted);
  }

  .search input {
    flex: 1;
    width: auto;
    min-width: 0;
    margin: 0;
    padding: 7px 0;
    border: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    outline: none;
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

  @container (max-width: 640px) {
    .head {
      padding: 12px 14px;
    }

    .head p {
      display: none;
    }

    .toolbar {
      position: static;
    }
  }
</style>
