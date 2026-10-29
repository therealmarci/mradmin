<script>
  // The bar at the top of the team panel and the player area: the places on the left, language flags and the own name
  // (a click opens a small menu) on the right. Phone: the name row on top, below it the places as one strip that
  // scrolls sideways - the current place is always moved into view.
  import { tick } from 'svelte';
  import { head } from '../lib/format.js';
  import LangFlags from './LangFlags.svelte';

  /**
   * items: [{path, label}]; current: path of the highlighted place; onnav(path);
   * user: {uuid, name}; menu: [{label, icon, active?, danger?, onclick}];
   * live: null (no dot) or true/false (connection of the team panel).
   */
  let { items, current, onnav, user, menu, live = null, liveTitle = '' } = $props();

  let menuOpen = $state(false);
  let strip = $state();
  let fadeStart = $state(false);
  let fadeEnd = $state(false);

  // Shadows at the edges show that the strip goes on.
  function edges() {
    if (!strip) return;
    fadeStart = strip.scrollLeft > 4;
    fadeEnd = strip.scrollLeft + strip.clientWidth < strip.scrollWidth - 4;
  }

  $effect(() => {
    current;
    items.length;
    tick().then(() => {
      strip?.querySelector('.active')?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
      edges();
    });
  });
</script>

<svelte:window onclick={(e) => menuOpen && !e.target.closest('.account') && (menuOpen = false)}
  onkeydown={(e) => e.key === 'Escape' && (menuOpen = false)} onresize={edges} />

<header class="top">
  <div class="strip-wrap" class:fade-start={fadeStart} class:fade-end={fadeEnd}>
    <nav bind:this={strip} onscroll={edges}>
      {#each items as item (item.path)}
        <button class="link" class:active={current === item.path} aria-current={current === item.path ? 'page' : undefined}
          onclick={() => onnav(item.path)}>{item.label}</button>
      {/each}
    </nav>
  </div>
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

  .strip-wrap {
    position: relative;
    min-width: 0;
    flex: 1;
  }

  nav {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
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

  /* Phone: name row on top, the places below as a strip that scrolls sideways (no wrapping into several rows). */
  @media (max-width: 759px) {
    .top {
      flex-direction: column-reverse;
      align-items: stretch;
      gap: 8px;
      padding: 8px 0 0;
      padding-top: max(8px, env(safe-area-inset-top));
    }

    .right {
      margin-left: 0;
      padding: 0 12px;
      justify-content: flex-end;
    }

    .strip-wrap {
      flex: none;
    }

    nav {
      flex-wrap: nowrap;
      overflow-x: auto;
      scrollbar-width: none;
      scroll-padding: 0 24px;
      padding: 0 12px 8px;
      gap: 6px;
    }

    nav::-webkit-scrollbar {
      display: none;
    }

    .link {
      flex-shrink: 0;
      padding: 7px 14px;
      border-radius: 999px;
      border: 1px solid var(--border);
      background: var(--surface-2);
    }

    .link.active {
      color: #fff;
      background: var(--accent);
      border-color: var(--accent);
    }

    .strip-wrap::before,
    .strip-wrap::after {
      content: '';
      position: absolute;
      top: 0;
      bottom: 8px;
      width: 28px;
      pointer-events: none;
      opacity: 0;
      transition: opacity 0.2s;
      z-index: 1;
    }

    .strip-wrap::before {
      left: 0;
      background: linear-gradient(to right, var(--surface), transparent);
    }

    .strip-wrap::after {
      right: 0;
      background: linear-gradient(to left, var(--surface), transparent);
    }

    .strip-wrap.fade-start::before,
    .strip-wrap.fade-end::after {
      opacity: 1;
    }
  }
</style>
