<script>
  // Page Log, tab "Server log": the lines of the server console - older ones from Paper's log files (up to 14 days,
  // GET /console/history), new ones live (GET /console/log, once a second). Locked like the web console: unlocked
  // there with a fresh code, for the same browser session. Links and IPs are hidden by the server. Newest first.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';

  let { query = '', since = 0 } = $props();

  const KEEP = 3000;

  let info = $state(null);
  let lines = $state([]);
  let more = $state(false);
  let error = $state('');
  let loading = $state(false);
  let last = 0;

  async function loadState() {
    try {
      info = await api('GET', '/console/state');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  function locked(e) {
    if (e.key === 'console-locked') {
      info = { ...info, unlocked: false };
      return true;
    }
    return false;
  }

  async function load(older = false) {
    if (!info?.unlocked) return;
    loading = true;
    try {
      const p = new URLSearchParams({ since: String(since) });
      if (query.trim()) p.set('q', query.trim());
      if (older && lines.length) p.set('before', String(lines[lines.length - 1].key));
      const data = await api('GET', '/console/history?' + p);
      if (older) {
        lines = [...lines, ...data.lines].slice(0, KEEP);
      } else {
        lines = data.lines;
        last = data.last;
      }
      more = data.more;
      error = '';
    } catch (e) {
      if (!locked(e)) error = errorText(e);
    } finally {
      loading = false;
    }
  }

  async function poll() {
    if (!info?.unlocked || loading || document.hidden) return;
    try {
      const result = await api('GET', '/console/log?after=' + last);
      if (result.last < last) {
        // The server was restarted - read the files again.
        load();
        return;
      }
      last = result.last;
      const q = query.trim().toLowerCase();
      const now = Date.now();
      const fresh = result.lines
        .filter((l) => !q || l.text.toLowerCase().includes(q))
        .map((l) => ({ key: 'live-' + l.seq, time: now, text: l.text }))
        .reverse();
      if (fresh.length) lines = [...fresh, ...lines].slice(0, KEEP);
    } catch (e) {
      locked(e);
    }
  }

  $effect(() => {
    untrack(loadState);
  });

  // Again when the filters change (the text after a short pause) or after unlocking.
  let timer;
  $effect(() => {
    const unlocked = info?.unlocked;
    since;
    const q = query;
    if (!unlocked) return;
    clearTimeout(timer);
    timer = setTimeout(() => load(), q ? 300 : 0);
  });

  $effect(() => {
    if (!info?.unlocked) return;
    const id = setInterval(poll, 1000);
    return () => clearInterval(id);
  });

  // "[12:00:00] [Server thread/WARN]: …" in the files, "[12:00:00 WARN]: …" in the console
  const level = (text) => (/[/ ](ERROR|FATAL)\]/.test(text) ? 'bad' : /[/ ]WARN\]/.test(text) ? 'warn' : '');

  const days = $derived.by(() => {
    const out = [];
    for (const l of lines) {
      const day = new Date(l.time).toDateString();
      if (!out.length || out[out.length - 1].day !== day) out.push({ day, time: l.time, items: [] });
      out[out.length - 1].items.push(l);
    }
    return out;
  });
</script>

{#if error}<div class="error">{error}</div>{/if}

{#if info && !info.unlocked}
  <div class="locked">
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
    <p>{t('logs.server.locked')}</p>
    <button type="button" onclick={() => go('/console')}>{t('logs.server.unlock')}</button>
  </div>
{:else if info}
  <p class="muted hint">{t('logs.server.hint')}</p>
  {#if days.length}
    {#each days as d (d.day)}
      <section class="day">
        <h2>{new Date(d.time).toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
        <ol class="mono">
          {#each d.items as l (l.key)}
            <li class={level(l.text)}>{l.text}</li>
          {/each}
        </ol>
      </section>
    {/each}
    {#if more}
      <button type="button" class="ghost more" disabled={loading} onclick={() => load(true)}>{t('logs.more')}</button>
    {/if}
  {:else if !loading}
    <p class="muted">{t('logs.server.empty')}</p>
  {/if}
{/if}

<style>
  .locked {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--muted);
  }

  .locked p {
    flex: 1;
    min-width: 200px;
    margin: 0;
  }

  .hint {
    margin: 0;
    font-size: 0.86rem;
  }

  .day h2 {
    margin: 4px 0 8px;
    font-size: 0.9rem;
    color: var(--muted);
  }

  ol {
    list-style: none;
    margin: 0;
    padding: 8px 0;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    font-size: 0.8rem;
    line-height: 1.45;
  }

  li {
    padding: 1px 14px;
    border-left: 3px solid transparent;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  li.warn {
    border-left-color: #f59e0b;
    background: color-mix(in srgb, #f59e0b 8%, transparent);
  }

  li.bad {
    border-left-color: #e5484d;
    background: color-mix(in srgb, #e5484d 9%, transparent);
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
</style>
