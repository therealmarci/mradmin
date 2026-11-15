<script>
  // The own profile on the phone (since 1.7, the head at the top right): name, connection, the switch player area /
  // administration (team), language, account and sign-out.
  // open, onclose(), user: {uuid, name}; live: null | true/false; modes: null | {current, player, admin, onswitch(mode)};
  // onaccount(), onlogout(), oninstall: null | function (offers the app).
  import { head } from '../../lib/format.js';
  import { t } from '../../lib/i18n.js';
  import LangFlags from '../LangFlags.svelte';
  import Sheet from './Sheet.svelte';

  let { open, onclose, user, live = null, modes = null, onaccount, onlogout, oninstall = null } = $props();

  const act = (fn) => () => {
    onclose();
    fn();
  };
</script>

<Sheet {open} {onclose}>
  <div class="who">
    <img src={head(user.uuid)} alt="" width="56" height="56" />
    <div>
      <strong>{user.name}</strong>
      {#if live !== null}
        <span class="state" class:on={live}><span class="dot"></span>{live ? t('common.live') : t('common.offline')}</span>
      {/if}
    </div>
  </div>

  {#if modes}
    <div class="seg" role="tablist">
      {#each ['player', 'admin'] as m (m)}
        <button role="tab" aria-selected={modes.current === m} class:on={modes.current === m}
          onclick={act(() => modes.current !== m && modes.onswitch(m))}>{modes[m]}</button>
      {/each}
    </div>
  {/if}

  <ul class="list">
    <li class="lang"><span>{t('self.language')}</span><LangFlags /></li>
    <li><button onclick={act(onaccount)}>{t('nav.account')}<span class="chev" aria-hidden="true">›</span></button></li>
    {#if oninstall}
      <li><button onclick={act(oninstall)}>{t('mobile.install.menu')}<span class="chev" aria-hidden="true">›</span></button></li>
    {/if}
  </ul>
  <ul class="list">
    <li><button class="out" onclick={act(onlogout)}>{t('nav.logout')}</button></li>
  </ul>
</Sheet>

<style>
  .who {
    display: flex;
    align-items: center;
    gap: 14px;
    margin: 4px 0 16px;
  }

  .who img {
    border-radius: 12px;
    image-rendering: pixelated;
  }

  .who strong {
    display: block;
    font-size: 1.2rem;
  }

  .state {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--muted);
    font-size: 0.85rem;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--muted);
  }

  .on .dot {
    background: var(--good);
  }

  .seg {
    display: flex;
    gap: 4px;
    padding: 4px;
    margin-bottom: 16px;
    border-radius: 12px;
    background: var(--surface-2);
  }

  .seg button {
    flex: 1;
    min-height: 40px;
    border: 0;
    border-radius: 9px;
    color: var(--muted);
    background: transparent;
  }

  .seg button.on {
    color: var(--text);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .list {
    margin: 0 0 14px;
    padding: 0;
    list-style: none;
    border-radius: 14px;
    overflow: hidden;
    background: var(--surface-2);
  }

  .list li + li {
    border-top: 1px solid var(--border);
  }

  .lang {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 52px;
    padding: 6px 10px 6px 14px;
  }

  .list button {
    width: 100%;
    min-height: 52px;
    justify-content: space-between;
    padding: 8px 14px;
    border: 0;
    border-radius: 0;
    color: var(--text);
    background: transparent;
    font-weight: 500;
  }

  .list button:active {
    transform: none;
    background: var(--border);
  }

  .chev {
    color: var(--muted);
    font-size: 1.4rem;
    line-height: 1;
  }

  .list button.out {
    justify-content: center;
    color: var(--bad);
  }
</style>
