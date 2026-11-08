<script>
  // The start page - the same for everyone (since 1.5): on top the hero (signed in: greeting and the slideshow with
  // the player's own things; visitors: only general things like maintenance, restart and posts), below the blocks
  // the team arranged under Server -> Appearance -> Start page.
  import { t } from '../lib/i18n.js';
  import { head } from '../lib/format.js';
  import SelfHero from '../self/SelfHero.svelte';
  import Landing from './Landing.svelte';

  // player: the signed-in player (null = visitor); onsignin: opens the sign-in window
  let { player = null, onsignin = () => {} } = $props();
</script>

<div class="start">
  {#if player}
    <section class="card hello">
      <img src={head(player.uuid, 64)} alt="" width="64" height="64" />
      <div>
        <h1>{t('self.hello', { name: player.name })}</h1>
        <p class="muted">{t('self.helloSub')}</p>
      </div>
    </section>
    {#if player.news}<SelfHero />{/if}
  {:else}
    <SelfHero guest />
  {/if}

  <Landing embedded signedIn={!!player} {onsignin} />
</div>

<style>
  .start {
    display: grid;
    gap: 16px;
  }

  .hello {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 18px 20px;
  }

  .hello img {
    border-radius: 10px;
    image-rendering: pixelated;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .hello p {
    margin: 2px 0 0;
  }
</style>
