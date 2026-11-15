<script>
  // The bar at the top of the phone layout (since 1.7): on the left the back button of a detail page (otherwise the
  // server's symbol), in the middle the small title (appears once the big title has scrolled away), on the right the
  // letter of the inbox and the own head (opens the profile sheet). Below the bar the big title of the page.
  //
  // title, back: {label, path, onnav?} | null; user: {uuid} | null (visitor: sign-in button); inbox: null |
  // {count, alarm, active, label}; live: null | true/false; onprofile(); onlogin(); oninbox(); onback(back).
  import { head } from '../../lib/format.js';
  import { ICONS } from '../../lib/icons.js';
  import { picture } from '../../lib/branding.svelte.js';

  let { title, back = null, user = null, inbox = null, live = null, liveTitle = '', onprofile, onlogin = null,
    loginLabel = '', oninbox = null, onback = null, profileLabel = '', large = true } = $props();

  let scrolled = $state(false);
  let barHeight = $state(0);
  $effect(() => {
    if (barHeight) document.documentElement.style.setProperty('--topbar-h', barHeight + 'px');
  });
  $effect(() => {
    const check = () => (scrolled = scrollY > 38);
    check();
    addEventListener('scroll', check, { passive: true });
    return () => removeEventListener('scroll', check);
  });
  const small = $derived(!!back || !large || scrolled);
</script>

<header class="bar mr-topbar" class:line={scrolled} bind:offsetHeight={barHeight}>
  <div class="side left">
    {#if back}
      <button class="back" onclick={() => onback(back)} aria-label={back.label}>
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"
          stroke-linejoin="round" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        <span>{back.label}</span>
      </button>
    {:else}
      <img class="logo" src={picture('icon')} alt="" width="28" height="28" />
    {/if}
  </div>
  <div class="mid" class:show={small}>{title}</div>
  <div class="side right">
    {#if inbox}
      <button class="icon" class:active={inbox.active} class:alarm={inbox.alarm} class:has={inbox.count > 0} onclick={oninbox}
        aria-label={inbox.count ? `${inbox.label} (${inbox.count})` : inbox.label}>
        <svg viewBox="0 0 24 24" width="23" height="23" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round" aria-hidden="true"><path d={ICONS.mail} /></svg>
        {#if inbox.count > 0}<span class="badge">{inbox.count > 99 ? '99+' : inbox.count}</span>{/if}
      </button>
    {/if}
    {#if user}
      <button class="me" onclick={onprofile} aria-label={profileLabel}>
        <img src={head(user.uuid)} alt="" width="30" height="30" />
        {#if live !== null}<span class="dot" class:on={live} title={liveTitle}></span>{/if}
      </button>
    {:else if onlogin}
      <button class="login" onclick={onlogin}>{loginLabel}</button>
    {/if}
  </div>
</header>
{#if !back && large && title}
  <h1 class="large" class:gone={scrolled}>{title}</h1>
{/if}

<style>
  .bar {
    position: sticky;
    top: 0;
    z-index: 20;
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
    gap: 8px;
    min-height: 52px;
    padding: 6px 10px;
    padding-top: max(6px, env(safe-area-inset-top));
    padding-left: max(10px, env(safe-area-inset-left));
    padding-right: max(10px, env(safe-area-inset-right));
    background: color-mix(in srgb, var(--bg) 80%, transparent);
    backdrop-filter: saturate(1.6) blur(16px);
    -webkit-backdrop-filter: saturate(1.6) blur(16px);
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s, background 0.2s;
  }

  .bar.line {
    border-bottom-color: var(--border);
    background: color-mix(in srgb, var(--surface) 82%, transparent);
  }

  .side {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
  }

  .right {
    justify-content: flex-end;
  }

  .mid {
    max-width: 52vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 700;
    font-size: 1.02rem;
    opacity: 0;
    transform: translateY(4px);
    transition: opacity 0.18s, transform 0.18s;
  }

  .mid.show {
    opacity: 1;
    transform: none;
  }

  .logo {
    margin-left: 4px;
    border-radius: 8px;
    object-fit: contain;
  }

  button {
    background: transparent;
    color: var(--text);
    border: 0;
    padding: 0;
    -webkit-tap-highlight-color: transparent;
  }

  button:active {
    transform: none;
    opacity: 0.6;
  }

  .back {
    gap: 0;
    min-width: 0;
    height: 40px;
    color: var(--accent);
    font-weight: 500;
    font-size: 1rem;
    margin-left: -4px;
  }

  .back span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .icon {
    position: relative;
    width: 42px;
    height: 42px;
    border-radius: 50%;
    color: var(--muted);
  }

  .icon.has {
    color: var(--accent);
  }

  .icon.alarm {
    color: var(--bad);
  }

  .badge {
    position: absolute;
    top: 3px;
    right: 1px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: 0.68rem;
    font-weight: 800;
    line-height: 18px;
    color: #fff;
    background: var(--bad);
    box-shadow: 0 0 0 2px var(--bg);
  }

  .me {
    position: relative;
    width: 42px;
    height: 42px;
  }

  .me img {
    border-radius: 9px;
    image-rendering: pixelated;
  }

  .dot {
    position: absolute;
    right: 3px;
    bottom: 3px;
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: var(--muted);
    border: 2px solid var(--bg);
  }

  .dot.on {
    background: var(--good);
  }

  .login {
    padding: 8px 16px;
    border-radius: 999px;
    color: var(--accent-text);
    background: var(--accent);
    font-weight: 600;
  }

  .large {
    margin: 2px 0 12px;
    padding: 0 max(16px, env(safe-area-inset-left));
    font-size: 2rem;
    font-weight: 800;
    letter-spacing: -0.02em;
    line-height: 1.15;
    overflow-wrap: anywhere;
    transition: opacity 0.18s;
  }

  .large.gone {
    opacity: 0;
  }
</style>
