<script>
  // The sidebar of the administration (since 1.6): every page in groups, small counters where something waits, the state
  // of the main server and the waiting room at the bottom. On a PC it folds to a slim row of symbols, every group folds
  // on its own (both remembered in the browser). Phone: hidden, the menu button in the bar opens it as a drawer.
  //
  // groups: [{id, label, items: [{id, label, path, icon, n?, tone?}]}]; current: id of the open page ('overview' =
  // the dashboard); home: {label, path, n, tone} - the head tile with the server's logo and name and the overview,
  // n = what is open; servers: [{label, tone, text}] - tone good | warn | bad | info | off; open (bindable): drawer.
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import { t } from '../lib/i18n.js';
  import { brand, picture } from '../lib/branding.svelte.js';

  let { groups, current, home = null, servers = [], open = $bindable(false) } = $props();

  const read = (key, fallback) => {
    try {
      return JSON.parse(localStorage.getItem(key)) ?? fallback;
    } catch {
      return fallback;
    }
  };
  const write = (key, value) => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // private mode: only for this visit
    }
  };

  let slim = $state(read('mradmin.sidebar.slim', false));
  let folded = $state(read('mradmin.sidebar.folded', []));

  function toggleSlim() {
    slim = !slim;
    write('mradmin.sidebar.slim', slim);
  }

  function toggleGroup(id) {
    folded = folded.includes(id) ? folded.filter((g) => g !== id) : [...folded, id];
    write('mradmin.sidebar.folded', folded);
  }

  function nav(path) {
    open = false;
    go(path);
  }

  // A folded group still shows its open page and the sum of its counters.
  const groupCount = (g) => g.items.reduce((sum, i) => sum + (i.n ?? 0), 0);
  const groupTone = (g) => (g.items.some((i) => i.n && i.tone === 'bad') ? 'bad' : 'warn');
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && (open = false)} />

{#snippet glyph(icon)}
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true"><path d={ICONS[icon] ?? ICONS.box} /></svg>
{/snippet}

{#if open}<button class="scrim" aria-label={t('nav.closeMenu')} onclick={() => (open = false)}></button>{/if}

<aside class="sidebar" class:slim class:open aria-label={t('nav.admin')}>
  {#if home}
    <div class="head-tile">
      <div class="brand">
        <img src={picture('icon')} alt="" width="30" height="30" />
        <span class="label"><strong>{brand.name}</strong><small>{t('nav.admin')}</small></span>
      </div>
      <a class="home" class:on={current === 'overview'} href={home.path} title={slim ? home.label : undefined}
        aria-current={current === 'overview' ? 'page' : undefined} onclick={(e) => { e.preventDefault(); nav(home.path); }}>
        <span class="ico">{@render glyph('home')}{#if slim && home.n}<span class="pip {home.tone}"></span>{/if}</span>
        <span class="label">{home.label}</span>
        {#if home.n}<span class="open-n {home.tone}">{t('nav.openCount', { n: home.n })}</span>{/if}
      </a>
    </div>
  {/if}
  <nav class="groups">
    {#each groups as g (g.id)}
      {#if g.items.length}
        <div class="group">
          {#if g.label}
            <button class="group-head" aria-expanded={!folded.includes(g.id)} onclick={() => toggleGroup(g.id)}>
              <span>{g.label}</span>
              {#if folded.includes(g.id) && groupCount(g)}<span class="n {groupTone(g)}">{groupCount(g)}</span>{/if}
              <span class="caret" class:shut={folded.includes(g.id)} aria-hidden="true">▾</span>
            </button>
          {/if}
          {#each g.items as item (item.id)}
            {#if !g.label || !folded.includes(g.id) || item.id === current || slim}
              <a class="item" class:on={item.id === current} href={item.path} title={slim ? item.label : undefined}
                aria-current={item.id === current ? 'page' : undefined}
                onclick={(e) => { e.preventDefault(); nav(item.path); }}>
                <span class="ico">{@render glyph(item.icon)}{#if slim && item.n}<span class="pip {item.tone ?? 'warn'}"></span>{/if}</span>
                <span class="label">{item.label}</span>
                {#if item.n}<span class="n {item.tone ?? 'warn'}">{item.n > 99 ? '99+' : item.n}</span>{/if}
              </a>
            {/if}
          {/each}
        </div>
      {/if}
    {/each}
  </nav>

  {#if servers.length}
    <div class="servers">
      {#each servers as s (s.label)}
        <div class="server" title={slim ? `${s.label}: ${s.text}` : undefined}>
          <span class="dot {s.tone}"></span>
          <span class="label"><strong>{s.label}</strong><span class="state">{s.text}</span></span>
        </div>
      {/each}
    </div>
  {/if}

  <button class="fold" onclick={toggleSlim} title={t(slim ? 'nav.expand' : 'nav.collapse')} aria-label={t(slim ? 'nav.expand' : 'nav.collapse')}>
    {@render glyph('sidebar')}<span class="label">{t('nav.collapse')}</span>
  </button>
</aside>

<style>
  .sidebar {
    position: sticky;
    top: var(--topbar-h, 57px);
    align-self: start;
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 232px;
    height: calc(100dvh - var(--topbar-h, 57px));
    padding: 10px 10px 8px;
    border-right: 1px solid var(--border);
    background: var(--surface);
    overflow-y: auto;
    scrollbar-width: thin;
  }

  .sidebar.slim {
    width: 60px;
  }

  /* Head tile: the server's logo and name, the overview below as the first stop */
  .head-tile {
    display: grid;
    gap: 8px;
    padding: 10px;
    border-radius: 12px;
    border: 1px solid var(--border);
    background: var(--surface-2);
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 2px;
  }

  .brand img {
    flex: none;
    border-radius: 8px;
  }

  .brand .label {
    display: grid;
    line-height: 1.25;
  }

  .brand strong {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 0.95rem;
  }

  .brand small {
    color: var(--muted);
    font-size: 0.75rem;
  }

  .home {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 7px 10px;
    border-radius: 9px;
    color: var(--text);
    font-weight: 600;
    font-size: 0.92rem;
    text-decoration: none;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .home:hover {
    border-color: color-mix(in srgb, var(--accent) 40%, var(--border));
  }

  .home.on {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
    background: color-mix(in srgb, var(--accent) 11%, var(--surface));
  }

  .home.on .ico {
    color: var(--accent);
  }

  .open-n {
    flex: none;
    padding: 0 7px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    line-height: 18px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .open-n.bad {
    color: #fff;
    background: var(--bad);
  }

  .slim .head-tile {
    padding: 6px 0;
    justify-items: center;
    border: 0;
    background: none;
  }

  .slim .brand {
    padding: 0;
  }

  .slim .brand .label,
  .slim .open-n {
    display: none;
  }

  .slim .home {
    justify-content: center;
    padding: 8px 0;
    width: 100%;
  }

  .groups {
    display: grid;
    gap: 6px;
  }

  .group {
    display: grid;
    gap: 1px;
  }

  .group-head {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 4px 10px 2px;
    border: 0;
    background: none;
    color: var(--muted);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }

  .group-head span:first-child {
    flex: 1;
    text-align: left;
  }

  .caret {
    transition: transform 0.15s;
  }

  .caret.shut {
    transform: rotate(-90deg);
  }

  .slim .group-head {
    display: none;
  }

  .slim .group + .group {
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 5px 10px;
    border-radius: 9px;
    color: var(--text);
    text-decoration: none;
    font-size: 0.9rem;
  }

  .item:hover {
    background: var(--surface-2);
  }

  .item.on {
    color: var(--accent);
    font-weight: 600;
    background: color-mix(in srgb, var(--accent) 11%, transparent);
  }

  .ico {
    position: relative;
    display: grid;
    place-items: center;
    flex: none;
    color: var(--muted);
  }

  .item.on .ico {
    color: var(--accent);
  }

  .label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .slim .label,
  .slim .item .n,
  .slim .server .label {
    display: none;
  }

  .slim .item {
    justify-content: center;
    padding: 9px 0;
  }

  .n {
    min-width: 20px;
    padding: 0 6px;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    line-height: 18px;
    text-align: center;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .n.bad {
    color: #fff;
    background: var(--bad);
  }

  .n.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .pip {
    position: absolute;
    top: -3px;
    right: -4px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--warn);
    box-shadow: 0 0 0 2px var(--surface);
  }

  .pip.bad {
    background: var(--bad);
  }

  .pip.info {
    background: var(--accent);
  }

  .servers {
    display: grid;
    gap: 6px;
    margin-top: auto;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .slim .servers {
    justify-items: center;
    padding: 10px 0;
  }

  .server {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 0.82rem;
  }

  .server .label {
    display: grid;
    line-height: 1.3;
  }

  .state {
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .dot {
    flex: none;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--muted);
  }

  .dot.good {
    background: var(--good);
  }

  .dot.warn {
    background: var(--warn);
  }

  .dot.bad {
    background: var(--bad);
  }

  .dot.info {
    background: var(--accent);
  }

  .fold {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    padding: 5px 10px;
    border: 0;
    border-radius: 9px;
    background: none;
    color: var(--muted);
    font-size: 0.85rem;
  }

  .fold .label {
    text-align: left;
  }

  .fold:hover {
    color: var(--text);
    background: var(--surface-2);
  }

  .slim .fold {
    justify-content: center;
    padding: 9px 0;
  }

  .scrim {
    display: none;
  }

  /* Phone: a drawer from the left, opened by the menu button in the bar */
  @media (max-width: 860px) {
    .sidebar,
    .sidebar.slim {
      position: fixed;
      top: 0;
      left: 0;
      bottom: 0;
      z-index: 40;
      width: min(280px, 84vw);
      height: 100dvh;
      transform: translateX(-105%);
      transition: transform 0.2s ease;
      box-shadow: var(--shadow);
    }

    .sidebar.open {
      transform: none;
    }

    .slim .label {
      display: block;
    }

    .slim .item .n {
      display: inline-block;
    }

    .slim .group-head {
      display: flex;
    }

    .slim .server .label,
    .slim .brand .label {
      display: grid;
    }

    .slim .open-n {
      display: inline-block;
    }

    .slim .head-tile {
      padding: 10px;
      justify-items: stretch;
      border: 1px solid var(--border);
      background: var(--surface-2);
    }

    .slim .home {
      justify-content: flex-start;
      padding: 7px 10px;
    }

    .slim .item {
      justify-content: flex-start;
      padding: 7px 10px;
    }

    .fold {
      display: none;
    }

    .scrim {
      display: block;
      position: fixed;
      inset: 0;
      z-index: 39;
      border: 0;
      border-radius: 0;
      background: rgb(0 0 0 / 45%);
    }
  }
</style>
