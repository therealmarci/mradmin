<script>
  // MRpvp: all kits as cards (icon, name, rules, hotbar) in the order the game shows them. Open one to edit it, copy
  // it, or move it up and down. Needs mrpvp.admin.kits (like the kit editor in the game).
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import ItemIcon from '../components/ItemIcon.svelte';
  import KitTransfer from '../components/KitTransfer.svelte';

  let kits = $state(null);
  let arenas = $state([]);
  let transfer = $state(null); // 'export' | 'import'
  let error = $state('');
  let busy = $state(false);

  async function load() {
    try {
      const data = await moduleData('mrpvp', 'kits');
      kits = data.kits;
      arenas = data.arenas;
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  async function move(index, step) {
    const ids = kits.map((k) => k.id);
    const [id] = ids.splice(index, 1);
    ids.splice(index + step, 0, id);
    busy = true;
    try {
      await moduleAction('mrpvp', { action: 'kitOrder', ids });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function ruleBadges(r) {
    const badges = [];
    if (!r.damage) badges.push(t('pvp.kits.badge.noDamage'));
    if (r.hitsToWin > 0) badges.push(t('pvp.kits.badge.hits', { n: r.hitsToWin }));
    if (r.regen === false) badges.push(t('pvp.kits.badge.noRegen'));
    if (r.regen === true) badges.push(t('pvp.kits.badge.regen'));
    if (r.hunger === false) badges.push(t('pvp.kits.badge.noHunger'));
    if (r.hunger === true) badges.push(t('pvp.kits.badge.hunger'));
    if (r.build) badges.push(t('pvp.kits.badge.build'));
    if (r.arenas?.length) badges.push(t('pvp.kits.badge.arenas', { n: r.arenas.length }));
    if (r.minutes > 0) badges.push(t('pvp.kits.badge.minutes', { n: r.minutes }));
    if (r.maxHearts > 0) badges.push(t('pvp.kits.badge.hearts', { n: r.maxHearts }));
    if (r.effects?.length) badges.push(t('pvp.kits.badge.effects', { n: r.effects.length }));
    if (r.cooldowns?.length) badges.push(t('pvp.kits.badge.cooldowns', { n: r.cooldowns.length }));
    return badges;
  }

  load();
</script>

<div class="page">
  <button class="back" onclick={() => go('/pvp')}>← {t('pvp.back')}</button>

  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.swords} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.swords} /></svg>
      </span>
      <div>
        <h1>{t('pvp.kits.title')}{#if kits}<span class="count">{kits.length}</span>{/if}</h1>
        <p class="muted">{t('pvp.kits.subtitle')}</p>
      </div>
    </div>
    <div class="top-buttons">
      <button class="secondary" onclick={() => (transfer = 'import')}>{t('pvp.kits.import.button')}</button>
      <button class="secondary" onclick={() => (transfer = 'export')} disabled={!kits?.length}>{t('pvp.kits.export.button')}</button>
      <button onclick={() => go('/pvp/new-kit')}>+ {t('pvp.kits.new')}</button>
    </div>
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if !kits}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else if kits.length === 0}
    <section class="card empty">
      <span aria-hidden="true">🗡️</span>
      <p>{t('pvp.kits.none')}</p>
      <button onclick={() => go('/pvp/new-kit')}>+ {t('pvp.kits.new')}</button>
    </section>
  {:else}
    <ul class="list">
      {#each kits as kit, i (kit.id)}
        {@const badges = ruleBadges(kit.rules)}
        <li class="kit">
          <div class="k-top">
            <button class="k-open" onclick={() => go('/pvp/kits/' + kit.id)}>
              <span class="k-icon"><ItemIcon item={{ type: kit.icon, amount: 1 }} size={40} /></span>
              <span class="k-title">
                <strong>{kit.name}</strong>
                <span class="muted small mono">{kit.id}</span>
              </span>
            </button>
            <span class="order">
              <span class="pos" title={t('pvp.kits.position')}>{i + 1}</span>
              <button class="secondary tiny" disabled={busy || i === 0} onclick={() => move(i, -1)} aria-label={t('pvp.kits.up')}>↑</button>
              <button class="secondary tiny" disabled={busy || i === kits.length - 1} onclick={() => move(i, 1)} aria-label={t('pvp.kits.down')}>↓</button>
            </span>
          </div>

          <div class="hotbar">
            {#each Array.from({ length: 9 }, (_, s) => kit.hotbar.find((h) => h.slot === s)) as item}
              <ItemIcon {item} size={30} />
            {/each}
          </div>

          <div class="badges">
            {#if kit.permission}<span class="badge perm mono" title={t('pvp.kits.permission')}>{kit.permission}</span>{/if}
            {#each badges as b}<span class="badge">{b}</span>{/each}
            {#if !badges.length && !kit.permission}<span class="muted small">{t('pvp.kits.noRules')}</span>{/if}
          </div>

          <div class="k-foot">
            <span class="muted small">{t('pvp.kits.itemCount', { n: kit.items })}</span>
            <span class="k-actions">
              <button class="secondary mini" onclick={() => go('/pvp/new-kit/' + kit.id)}>{t('pvp.kits.copy')}</button>
              <button class="mini" onclick={() => go('/pvp/kits/' + kit.id)}>{t('pvp.kits.edit')}</button>
            </span>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</div>

{#if transfer && kits}
  <KitTransfer mode={transfer} {kits} {arenas} onclose={() => (transfer = null)} ondone={load} />
{/if}

<style>
  .page {
    --pv: var(--accent);
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--pv);
    padding: 4px 0;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pv) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pv) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--pv);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pv) 35%, transparent);
  }

  h1 {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0;
    font-size: 1.45rem;
  }

  .count {
    font-size: 0.8rem;
    font-weight: 800;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 14%, transparent);
  }

  .intro p {
    margin: 2px 0 0;
  }

  .top-buttons {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
  }

  /* One kit: icon and name, the hotbar as in the game, its rules, the buttons. */
  .kit {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--pv) 8%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
    transition: transform 0.15s, border-color 0.15s;
  }

  .kit:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--pv) 40%, var(--border));
  }

  .k-top {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .k-open {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
    padding: 0;
    background: transparent;
    border: 0;
    color: var(--text);
    font: inherit;
    font-weight: 400;
    text-align: left;
  }

  .k-open:hover {
    filter: none;
  }

  .k-open:hover strong {
    color: var(--pv);
  }

  .k-icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    flex-shrink: 0;
    border-radius: 12px;
    background: color-mix(in srgb, var(--pv) 12%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pv) 30%, transparent);
  }

  .k-title {
    display: grid;
    min-width: 0;
  }

  .k-title strong {
    font-size: 1.08rem;
    overflow-wrap: anywhere;
  }

  .order {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .pos {
    min-width: 26px;
    text-align: center;
    font-size: 0.8rem;
    font-weight: 800;
    color: var(--muted);
  }

  .tiny {
    padding: 4px 9px;
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.85rem;
  }

  /* The hotbar on a dark strip like in the game */
  .hotbar {
    display: flex;
    gap: 3px;
    flex-wrap: wrap;
    justify-content: center;
    padding: 6px;
    border-radius: 10px;
    background: color-mix(in srgb, #000 22%, var(--surface-2));
    border: 1px solid var(--border);
  }

  .badges {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    align-items: center;
    flex: 1;
    align-content: flex-start;
  }

  .badge {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .badge.perm {
    font-weight: 600;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 12%, transparent);
  }

  .k-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }

  .k-actions {
    display: flex;
    gap: 6px;
  }

  .empty {
    display: grid;
    justify-items: center;
    gap: 8px;
    padding: 30px 16px;
    color: var(--muted);
  }

  .empty span {
    font-size: 2.2rem;
  }

  .empty p {
    margin: 0;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .kit {
      transition: none;
    }
  }
</style>
