<script>
  // The start page - the same for everyone (since 1.5): the portal header (signed in with "Hello <name>"), below it the
  // hint slideshow (signed in: the player's own things, visitors: only general ones like maintenance, restart and
  // posts), then the blocks in two columns (Landing.svelte).
  import SelfHero from '../self/SelfHero.svelte';
  import Landing from './Landing.svelte';

  // player: the signed-in player (null = visitor); onsignin: opens the sign-in window
  let { player = null, onsignin = () => {} } = $props();
</script>

<Landing {player} {onsignin}>
  {#snippet between()}
    {#if player}
      {#if player.news}<SelfHero />{/if}
    {:else}
      <SelfHero guest />
    {/if}
  {/snippet}
</Landing>
