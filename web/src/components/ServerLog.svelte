<script>
  // Page Log, tab "Server log": the lines of the server console - older ones from Paper's log files (up to 14 days,
  // GET /console/history), new ones live (GET /console/tail, once a second). Only mradmin.web.console.view, no
  // unlocking (that is only for running commands in the web console). Links and IPs are hidden by the server.
  // Filter by level (info, warn, error); newest first; paused, new lines wait above the list.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import LogLayout from './LogLayout.svelte';
  import LogFilter from './LogFilter.svelte';
  import LogList from './LogList.svelte';

  let { query = '', since = 0, paused = false } = $props();

  const KEEP = 3000;
  const COLORS = { info: '#8b95a5', warn: '#f59e0b', error: '#e5484d' };

  let lines = $state([]);
  let queued = $state([]);
  let counts = $state({});
  let levels = $state([]);
  let more = $state(false);
  let error = $state('');
  let loading = $state(false);
  let last = 0;

  async function load(older = false) {
    loading = true;
    try {
      const p = new URLSearchParams({ since: String(since) });
      if (query.trim()) p.set('q', query.trim());
      if (levels.length) p.set('levels', levels.join(','));
      if (older && lines.length) p.set('before', String(lines.at(-1).key));
      const data = await api('GET', '/console/history?' + p);
      if (older) {
        lines = [...lines, ...data.lines].slice(0, KEEP);
      } else {
        lines = data.lines;
        queued = [];
        last = data.last;
      }
      if (data.counts) counts = data.counts;
      more = data.more;
      error = '';
    } catch (e) {
      error = errorText(e);
    } finally {
      loading = false;
    }
  }

  async function poll() {
    if (loading || document.hidden) return;
    try {
      const result = await api('GET', '/console/tail?after=' + last);
      if (result.last < last) {
        // The server was restarted - read the files again.
        load();
        return;
      }
      last = result.last;
      const q = query.trim().toLowerCase();
      const now = Date.now();
      const fresh = result.lines
        .filter((l) => (!q || l.text.toLowerCase().includes(q)))
        .map((l) => ({ key: 'live-' + l.seq, time: now, level: l.level, text: l.text }));
      if (!fresh.length) return;
      for (const l of fresh) counts = { ...counts, [l.level]: (counts[l.level] ?? 0) + 1 };
      const shown = fresh.filter((l) => !levels.length || levels.includes(l.level)).reverse();
      if (paused) queued = [...shown, ...queued].slice(0, KEEP);
      else lines = [...shown, ...lines].slice(0, KEEP);
    } catch (e) {
      error = errorText(e);
    }
  }

  function showQueued() {
    lines = [...queued, ...lines].slice(0, KEEP);
    queued = [];
  }

  // Again when the filters change (the text after a short pause).
  let timer;
  $effect(() => {
    since;
    levels;
    const q = query;
    clearTimeout(timer);
    timer = setTimeout(() => load(), q ? 300 : 0);
  });
  $effect(() => {
    const id = setInterval(poll, 1000);
    return () => clearInterval(id);
  });
  $effect(() => {
    if (!paused && queued.length) showQueued();
  });

  // "[12:00:00] [Server thread/WARN]: text" (files) or "[12:00:00 WARN]: text" (console) - time and level have
  // their own columns, the row shows only the text.
  const PREFIX = /^\[(\d{2}:\d{2}:\d{2})(?: [A-Z]+)?\](?: \[[^\]]*\])?:?\s?/;

  const filterItems = $derived(
    ['info', 'warn', 'error'].map((l) => ({ key: l, label: t('logs.level.' + l), color: COLORS[l], count: counts[l] ?? 0 })),
  );

  const rows = $derived(
    lines.map((l) => {
      const m = PREFIX.exec(l.text);
      return {
        key: l.key,
        time: l.time,
        clock: m ? m[1] : '',
        badge: m ? { label: t('logs.level.' + l.level), color: COLORS[l.level] } : { label: '', color: COLORS[l.level] },
        text: m ? l.text.slice(m[0].length) : l.text,
        details: [
          { label: t('logs.detail.time'), value: new Date(l.time).toLocaleDateString() + (m ? ' ' + m[1] : '') },
          { label: t('logs.detail.line'), value: l.text },
        ],
      };
    }),
  );
</script>

{#if error}<div class="error">{error}</div>{/if}
<LogLayout active={levels.length}>
  {#snippet filters()}
    <LogFilter title={t('logs.levels')} items={filterItems} bind:selected={levels} />
    <p class="hint">{t('logs.server.hint')}</p>
  {/snippet}
  <LogList {rows} mono {more} {loading} empty={t('logs.server.empty')} pending={queued.length} onmore={() => load(true)} onshow={showQueued} />
</LogLayout>

<style>
  .hint {
    margin: 0;
    padding: 0 6px;
    color: var(--muted);
    font-size: 0.78rem;
    line-height: 1.45;
  }
</style>
