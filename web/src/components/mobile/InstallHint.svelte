<script>
  // "Add to home screen" (phone layout, since 1.7): once, can be closed for good (stored in the browser). Android/Chrome
  // gets the browser's own install button, iPhone/iPad the steps in Safari. Also opened from the profile (show).
  import { t } from '../../lib/i18n.js';
  import { device, install } from '../../lib/mobile.svelte.js';
  import { brand, picture } from '../../lib/branding.svelte.js';
  import Sheet from './Sheet.svelte';

  // here: the banner may show on this page (only the start pages)
  let { show = $bindable(false), here = true } = $props();

  const KEY = 'mradmin.mobile.installHint';
  let dismissed = $state(true);
  try {
    dismissed = localStorage.getItem(KEY) === 'done';
  } catch {
    // private mode: no hint
  }
  // Only if it can be installed at all: iPhone/iPad (Safari) or a browser that offered it.
  const possible = $derived(!device.standalone && (device.ios || !!install.prompt));
  const banner = $derived(possible && !dismissed);

  function done() {
    dismissed = true;
    show = false;
    try {
      localStorage.setItem(KEY, 'done');
    } catch {
      // private mode
    }
  }

  async function installNow() {
    if (!install.prompt) return;
    install.prompt.prompt();
    const choice = await install.prompt.userChoice.catch(() => null);
    install.prompt = null;
    if (choice?.outcome === 'accepted') done();
  }
</script>

{#if banner && here && !show}
  <div class="hint">
    <img src={picture('icon')} alt="" width="36" height="36" />
    <div class="text">
      <strong>{t('mobile.install.menu')}</strong>
      <span>{t('mobile.install.short')}</span>
    </div>
    <button class="go" onclick={() => (install.prompt ? installNow() : (show = true))}>{t('mobile.install.button')}</button>
    <button class="x" aria-label={t('common.close')} onclick={done}>✕</button>
  </div>
{/if}

<Sheet open={show} onclose={() => (show = false)} title={t('mobile.install.title', { name: brand.name })}>
  {#if install.prompt}
    <p>{t('mobile.install.android')}</p>
    <button class="block big" onclick={installNow}>{t('mobile.install.button')}</button>
  {:else}
    <ol class="steps">
      <li>
        <span class="n">1</span>
        <span>{t('mobile.install.ios1')}
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
            stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12M8 7l4-4 4 4M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" /></svg>
        </span>
      </li>
      <li><span class="n">2</span><span>{t('mobile.install.ios2')}</span></li>
      <li><span class="n">3</span><span>{t('mobile.install.ios3')}</span></li>
    </ol>
  {/if}
  <button class="secondary block" onclick={done}>{t('mobile.install.never')}</button>
</Sheet>

<style>
  .hint {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    padding: 12px;
    border-radius: 16px;
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow);
  }

  .hint img {
    flex: none;
    border-radius: 10px;
    object-fit: contain;
  }

  .text {
    flex: 1;
    min-width: 0;
    display: grid;
    line-height: 1.3;
  }

  .text span {
    color: var(--muted);
    font-size: 0.85rem;
  }

  .go {
    flex: none;
    padding: 8px 14px;
    border-radius: 999px;
  }

  .x {
    flex: none;
    width: 30px;
    height: 30px;
    padding: 0;
    border-radius: 50%;
    color: var(--muted);
    background: var(--surface-2);
    font-size: 0.8rem;
  }

  .steps {
    display: grid;
    gap: 14px;
    margin: 4px 0 20px;
    padding: 0;
    list-style: none;
  }

  .steps li {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .steps li > span:last-child {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  .steps svg {
    color: var(--accent);
  }

  .n {
    flex: none;
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    font-weight: 800;
    color: var(--accent-text);
    background: var(--accent);
  }

  .big {
    min-height: 50px;
    margin-bottom: 10px;
  }
</style>
