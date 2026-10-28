<script>
  // Start page of the player area: greeting and the areas the installed plugins offer (chunk loaders, duels ...).
  import { t } from '../lib/i18n.js';
  import { head } from '../lib/format.js';

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

  {#if player.areas.length === 0}
    <p class="muted">{t('self.noAreas')}</p>
  {/if}

  <section class="card tip">
    <strong>{t('self.bookmarkTitle')}</strong>
    <p class="muted small">{t('self.bookmarkText')}</p>
  </section>
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
</style>
