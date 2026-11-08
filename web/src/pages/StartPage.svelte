<script>
  // The start page - the same for everyone (since 1.5): the portal header (signed in with "Hello <name>"), below it the
  // hint slideshow (signed in: the player's own things, visitors: only general ones like maintenance, restart and
  // posts), for signed-in players their tiles (one per area of the installed plugins plus their game statistics),
  // then the blocks in two columns (Landing.svelte).
  import { t } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import SelfHero from '../self/SelfHero.svelte';
  import SelfTile from '../self/SelfTile.svelte';
  import Landing from './Landing.svelte';

  // player: the signed-in player (null = visitor); onsignin: opens the sign-in window
  let { player = null, onsignin = () => {} } = $props();

  // MRadmin's own tile next to the plugins': the player's game statistics (player-web.personal-stats).
  const personal = $derived(player?.personal?.length ? {
    module: 'mradmin',
    id: 'personal',
    title: t('self.personal.title'),
    icon: '📊',
    summary: [{ type: 'stats', stats: player.personal.map((p) => ({ label: t('self.stats.' + p.key), value: p.value, tone: '' })) }],
  } : null);
</script>

<Landing {player} {onsignin}>
  {#snippet between()}
    {#if player}
      {#if player.news}<SelfHero />{/if}
      {#if player.areas.length || personal}
        <div class="tiles">
          {#each player.areas as a (a.module + '/' + a.id)}
            <SelfTile area={a} onclick={() => go(`/game/${a.module}/${a.id}`)} />
          {/each}
          {#if personal}
            <SelfTile area={personal} onclick={player.stats ? () => go('/server/stats') : null} />
          {/if}
        </div>
      {/if}
    {:else}
      <SelfHero guest />
    {/if}
  {/snippet}
</Landing>

<style>
  .tiles {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }
</style>
