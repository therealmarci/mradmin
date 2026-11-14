<script>
  // The bar of the team panel and the player area: the places on the left; on the right the switch player area /
  // administration (team only, one small button), the letter of the inbox and the own head + name (a click opens a
  // small menu with the language flags, account and sign-out). Visitors see the flags next to the sign-in button. Phone: flags and name stay on top, the places move to a bar at the bottom - at
  // most five, with more the first four plus a menu button for the rest.
  import { head } from '../lib/format.js';
  import LangFlags from './LangFlags.svelte';
  import { ICONS } from '../lib/icons.js';
  import { brand, picture } from '../lib/branding.svelte.js';
  import { t } from '../lib/i18n.js';

  /**
   * items: [{path, label, icon}] - icon is a key of ICONS or an emoji; current: path of the highlighted place;
   * onnav(path); user: {uuid, name}; menu: [{label, icon, active?, danger?, onclick}];
   * moreLabel: text of the menu button for the rest on the phone;
   * live: null (no dot) or true/false (connection of the team panel);
   * modes: null or {current: 'player' | 'admin', player, admin (labels), onswitch(mode)} - the switch between the
   * player area and the administration (team only) - one button the size of the letter, filled while in the administration.
   */
  // user null = visitor: instead of the name menu a sign-in button (onlogin)
  // inbox: null (no letter) or {count, alarm, active, label} - the letter opens /inbox, the number = what is new,
  // alarm = an unread team alarm (crash): the letter turns red
  // onmenu: null or a function - the administration: a menu button on the left (phone) opens its sidebar
  let { items, current, onnav, user, menu, moreLabel, live = null, liveTitle = '', modes = null, onlogin = null,
    loginLabel = '', inbox = null, onmenu = null, menuLabel = '' } = $props();

  let menuOpen = $state(false);

  // The height of the bar for everything that sticks below it (the sidebar of the administration): --topbar-h.
  let barHeight = $state(0);
  $effect(() => {
    if (barHeight) document.documentElement.style.setProperty('--topbar-h', barHeight + 'px');
  });
  let moreOpen = $state(false);

  const MAX_TABS = 5;
  const tabs = $derived(items.length > MAX_TABS ? items.slice(0, MAX_TABS - 1) : items);
  const extra = $derived(items.length > MAX_TABS ? items.slice(MAX_TABS - 1) : []);

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

<header class="top mr-topbar" bind:offsetHeight={barHeight}>
  {#if onmenu}
    <button class="icon-btn burger" aria-label={menuLabel} title={menuLabel} onclick={onmenu}>
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true"><path d={ICONS.more} /></svg>
    </button>
  {/if}
  <!-- The server: logo and name (Server -> Appearance) -->
  <span class="brand"><img src={picture('icon')} alt="" width="24" height="24" /><span class="brand-name">{brand.name}</span></span>
  <nav class="desktop">
    {#each items as item (item.path)}
      <button class="link" class:active={current === item.path} aria-current={current === item.path ? 'page' : undefined}
        onclick={() => go(item.path)}>{item.label}</button>
    {/each}
  </nav>
  <div class="right">
    {#if modes}
      {@const other = modes.current === 'admin' ? 'player' : 'admin'}
      <button class="icon-btn mode" class:on={modes.current === 'admin'} aria-pressed={modes.current === 'admin'}
        aria-label={modes[other]} title={modes[other]} onclick={() => modes.onswitch(other)}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round" aria-hidden="true"><path d={ICONS.shield} /></svg>
      </button>
    {/if}
    {#if inbox}
      <button class="icon-btn inbox" class:active={inbox.active} class:has={inbox.count > 0} class:alarm={inbox.alarm} onclick={() => go('/inbox')}
        aria-label={inbox.count ? `${inbox.label} (${inbox.count})` : inbox.label} title={inbox.label}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round" aria-hidden="true"><path d={ICONS.mail} /></svg>
        {#if inbox.count > 0}<span class="badge">{inbox.count > 99 ? '99+' : inbox.count}</span>{/if}
      </button>
    {/if}
    {#if !user}
      <LangFlags />
      <button class="login" onclick={onlogin}>{loginLabel}</button>
    {:else}
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
          <div class="lang-row">
            <span>{t('self.language')}</span>
            <LangFlags />
          </div>
          {#each menu as entry}
            <button role="menuitem" class:active={entry.active} class:out={entry.danger}
              onclick={() => { menuOpen = false; entry.onclick(); }}>
              <span aria-hidden="true">{entry.icon}</span> {entry.label}
            </button>
          {/each}
        </div>
      {/if}
    </div>
    {/if}
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
{#if items.length}
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
{/if}

<style>
  /* Small square buttons on the right: the switch to the administration and the letter of the inbox */
  .icon-btn {
    position: relative;
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    padding: 0;
    border-radius: 10px;
    border: 1px solid transparent;
    color: var(--muted);
    background: transparent;
  }

  .icon-btn:hover,
  .icon-btn.active {
    color: var(--text);
    background: var(--surface-2);
    border-color: var(--border);
  }

  .inbox.has {
    color: var(--accent);
  }

  /* An unread team alarm (server crashed): the letter and its number red */
  .inbox.alarm {
    color: var(--bad);
  }

  .inbox.alarm .badge {
    background: var(--bad);
  }

  /* In the administration the switch is filled, so it is always clear where you are */
  .mode.on,
  .mode.on:hover {
    color: #fff;
    background: var(--accent);
    border-color: var(--accent);
  }

  .badge {
    position: absolute;
    top: 1px;
    right: 0;
    min-width: 17px;
    height: 17px;
    padding: 0 4px;
    border-radius: 999px;
    font-size: 0.68rem;
    font-weight: 800;
    line-height: 17px;
    color: #fff;
    background: var(--bad);
    box-shadow: 0 0 0 2px var(--surface);
  }

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

  .login {
    padding: 7px 16px;
    font-weight: 600;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: none;
    margin-right: 6px;
    font-weight: 700;
  }

  .brand img {
    border-radius: 6px;
    object-fit: contain;
  }

  .brand-name {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* Language in the name menu */
  .lang-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 6px 6px 8px 12px;
    margin-bottom: 4px;
    border-bottom: 1px solid var(--border);
    color: var(--muted);
    font-size: 0.88rem;
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

  .burger {
    display: none;
  }

  @media (max-width: 860px) {
    .burger {
      display: grid;
      margin-right: -4px;
    }
  }

  /* Phone: flags and name on top, the places in a bar at the bottom (the rest behind the menu button). */
  @media (max-width: 1100px) {
    .brand-name {
      display: none;
    }
  }

  @media (max-width: 759px) {
    .brand-name {
      display: inline;
    }

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
