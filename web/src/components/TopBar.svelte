<script>
  // The bar of the team panel and the player area: the places on the left, language flags and the own name (a click
  // opens a small menu) on the right. Phone: flags and name stay on top, the places move to a bar at the bottom - at
  // most five, with more the first four plus a menu button for the rest.
  import { head } from '../lib/format.js';
  import LangFlags from './LangFlags.svelte';

  /**
   * items: [{path, label, icon}] - icon is a key of ICONS or an emoji; current: path of the highlighted place;
   * onnav(path); user: {uuid, name}; menu: [{label, icon, active?, danger?, onclick}];
   * moreLabel: text of the menu button for the rest on the phone;
   * live: null (no dot) or true/false (connection of the team panel).
   */
  let { items, current, onnav, user, menu, moreLabel, live = null, liveTitle = '' } = $props();

  let menuOpen = $state(false);
  let moreOpen = $state(false);

  const MAX_TABS = 5;
  const tabs = $derived(items.length > MAX_TABS ? items.slice(0, MAX_TABS - 1) : items);
  const extra = $derived(items.length > MAX_TABS ? items.slice(MAX_TABS - 1) : []);

  const ICONS = {
    more: 'M4 6h16M4 12h16M4 18h16',
    terminal: 'M4 5h16v14H4zM7 9l3 3-3 3M12 15h5',
    box: 'M12 3 4 7.5v9L12 21l8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9',
    news: 'M4 5h13v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM17 9h3v10a2 2 0 0 1-2 2M8 9h5M8 13h5M8 17h3',
    tool: 'M14.7 6.3a4 4 0 0 0-5.4 5.2L4 16.8V20h3.2l5.3-5.3a4 4 0 0 0 5.2-5.4l-2.5 2.5-2.4-.6-.6-2.4z',
    home: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
    ticket: 'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z',
    pulse: 'M3 12h4l2-6 4 12 2-6h6',
    grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z',
    swords: 'M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5 21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4M5 21l-2-2',
    users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c0-3.3 3.1-6 7-6s7 2.7 7 6M16 3.5a4 4 0 0 1 0 7M18 14c2.4.6 4 2.6 4 5',
    chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  };

  function go(path) {
    moreOpen = false;
    onnav(path);
  }
</script>

<svelte:window onclick={(e) => menuOpen && !e.target.closest('.account') && (menuOpen = false)}
  onkeydown={(e) => e.key === 'Escape' && ((menuOpen = false), (moreOpen = false))} />

{#snippet glyph(icon, size)}
  {#if ICONS[icon]}
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2"
      stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d={ICONS[icon]} /></svg>
  {:else}
    <span class="emoji" style="font-size: {size - 3}px" aria-hidden="true">{icon || '•'}</span>
  {/if}
{/snippet}

<header class="top">
  <nav class="desktop">
    {#each items as item (item.path)}
      <button class="link" class:active={current === item.path} aria-current={current === item.path ? 'page' : undefined}
        onclick={() => go(item.path)}>{item.label}</button>
    {/each}
  </nav>
  <div class="right">
    <LangFlags />
    <div class="account">
      <button class="who" aria-haspopup="menu" aria-expanded={menuOpen} onclick={() => (menuOpen = !menuOpen)}>
        <span class="face">
          <img class="head" src={head(user.uuid)} alt="" width="26" height="26" />
          {#if live !== null}<span class="dot" class:on={live} title={liveTitle}></span>{/if}
        </span>
        <strong>{user.name}</strong>
        <span class="caret" aria-hidden="true">▾</span>
      </button>
      {#if menuOpen}
        <div class="menu" role="menu">
          {#each menu as entry}
            <button role="menuitem" class:active={entry.active} class:out={entry.danger}
              onclick={() => { menuOpen = false; entry.onclick(); }}>
              <span aria-hidden="true">{entry.icon}</span> {entry.label}
            </button>
          {/each}
        </div>
      {/if}
    </div>
  </div>
</header>

{#if moreOpen}
  <!-- The rest of the places on the phone, above the bottom bar. -->
  <button class="scrim" aria-label={moreLabel} onclick={() => (moreOpen = false)}></button>
  <div class="more-sheet" role="menu">
    {#each extra as item (item.path)}
      <button role="menuitem" class:active={current === item.path} onclick={() => go(item.path)}>
        {@render glyph(item.icon, 20)}
        {item.label}
      </button>
    {/each}
  </div>
{/if}
<nav class="tabs mr-tabbar">
  {#each tabs as item (item.path)}
    <button class:active={current === item.path} aria-current={current === item.path ? 'page' : undefined}
      onclick={() => go(item.path)}>
      {@render glyph(item.icon, 22)}
      <span>{item.label}</span>
    </button>
  {/each}
  {#if extra.length}
    <button class:active={moreOpen || extra.some((i) => i.path === current)} aria-expanded={moreOpen}
      onclick={() => (moreOpen = !moreOpen)}>
      {@render glyph('more', 22)}
      <span>{moreLabel}</span>
    </button>
  {/if}
</nav>

<style>
  .top {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 8px 16px;
    padding: 10px 16px;
    padding-top: max(10px, env(safe-area-inset-top));
    background: color-mix(in srgb, var(--surface) 85%, transparent);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border);
  }

  .desktop {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
    min-width: 0;
  }

  .link {
    background: transparent;
    color: var(--muted);
    padding: 6px 10px;
    white-space: nowrap;
  }

  .link.active {
    color: var(--text);
    background: var(--surface-2);
  }

  .right {
    align-items: center;
    margin-left: auto;
    display: flex;
    gap: 8px;
  }

  .who {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 10px 4px 4px;
    border-radius: 10px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-weight: 400;
  }

  .who:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .who strong {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .face {
    position: relative;
    display: grid;
  }

  .head {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  /* Connection of the team panel, as a small dot on the head. */
  .dot {
    position: absolute;
    right: -3px;
    bottom: -3px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--muted);
    border: 2px solid var(--surface-2);
  }

  .dot.on {
    background: var(--good);
  }

  .caret {
    color: var(--muted);
    font-size: 0.75rem;
  }

  .account {
    position: relative;
  }

  .menu {
    position: absolute;
    right: 0;
    top: calc(100% + 6px);
    z-index: 30;
    min-width: 190px;
    display: grid;
    gap: 2px;
    padding: 6px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow);
  }

  .menu button {
    display: flex;
    align-items: center;
    gap: 10px;
    justify-content: flex-start;
    padding: 9px 12px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    text-align: left;
  }

  .menu button:hover,
  .menu button.active {
    filter: none;
    background: var(--surface-2);
  }

  .menu .out {
    color: var(--bad);
  }

  .emoji {
    line-height: 1;
  }

  .tabs,
  .scrim,
  .more-sheet {
    display: none;
  }

  /* Phone: flags and name on top, the places in a bar at the bottom (the rest behind the menu button). */
  @media (max-width: 759px) {
    .desktop {
      display: none;
    }

    .top {
      padding: 8px 12px;
      padding-top: max(8px, env(safe-area-inset-top));
    }

    /* Room at the bottom of the page for the bar (also below the footer). */
    :global(body:has(.mr-tabbar)) {
      padding-bottom: calc(66px + env(safe-area-inset-bottom));
    }

    .tabs {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 10;
      display: flex;
      background: color-mix(in srgb, var(--surface) 92%, transparent);
      backdrop-filter: blur(12px);
      border-top: 1px solid var(--border);
      padding-bottom: env(safe-area-inset-bottom);
    }

    .tabs button {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 3px;
      background: transparent;
      color: var(--muted);
      border-radius: 0;
      padding: 8px 4px 10px;
      font-size: 0.72rem;
      font-weight: 600;
    }

    .tabs button.active {
      color: var(--accent);
    }

    .tabs span {
      max-width: 100%;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .scrim {
      display: block;
      position: fixed;
      inset: 0;
      z-index: 9;
      background: rgb(0 0 0 / 0.25);
      border-radius: 0;
      padding: 0;
    }

    .more-sheet {
      position: fixed;
      right: 8px;
      bottom: calc(70px + env(safe-area-inset-bottom));
      z-index: 11;
      display: grid;
      gap: 2px;
      min-width: 210px;
      padding: 6px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
    }

    .more-sheet button {
      display: flex;
      align-items: center;
      justify-content: flex-start;
      gap: 12px;
      background: transparent;
      color: var(--text);
      padding: 11px 12px;
    }

    .more-sheet button.active {
      color: var(--accent);
      background: var(--surface-2);
    }
  }
</style>
