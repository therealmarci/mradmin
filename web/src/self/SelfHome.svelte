<script>
  // Start page of the player area: greeting, the slideshow (news, changelogs, maintenance) and one tile per area the
  // installed plugins offer (chunk loaders, duels ...) with its key figures; a click opens the area. The bookmark tip
  // is only a quiet line at the end.
  import { t } from '../lib/i18n.js';
  import { head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Blocks from './Blocks.svelte';
  import SelfHero from './SelfHero.svelte';

  let { player } = $props();
</script>

<div class="page">
  <section class="card hello">
    <img src={head(player.uuid, 64)} alt="" width="64" height="64" />
    <div>
      <h1>{t('self.hello', { name: player.name })}</h1>
      <p class="muted">{t('self.helloSub')}</p>
    </div>
  </section>

  {#if player.news}<SelfHero />{/if}

  {#if player.areas.length === 0}
    <p class="muted">{t('self.noAreas')}</p>
  {:else}
    <div class="tiles">
      {#each player.areas as a (a.module + '/' + a.id)}
        <button class="card tile" onclick={() => go(`/me/${a.module}/${a.id}`)}>
          <span class="label">{a.icon} {a.title}</span>
          <Blocks blocks={a.summary} />
          <span class="small link">{t('self.open')} →</span>
        </button>
      {/each}
    </div>
  {/if}

  <p class="tip muted small" title={t('self.bookmarkText')}>🔖 {t('self.bookmarkShort')}</p>
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .hello {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .hello img {
    border-radius: 10px;
    image-rendering: pixelated;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  p {
    margin: 4px 0 0;
  }

  .tiles {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  }

  .tile {
    display: grid;
    gap: 10px;
    text-align: left;
    color: inherit;
    font: inherit;
  }

  .label {
    font-weight: 700;
  }

  .tip {
    margin: 4px 0 0;
    text-align: center;
    font-size: 0.8rem;
    opacity: 0.8;
  }

  .link {
    color: var(--accent);
  }
</style>
