<script>
  // Tab "Map" of the player's own page: MRadmin's own map over squaremap's pictures with the layers the plugins offer
  // this player (MRwaypoints' waypoints, MRchunks' chunk loaders ...). Their actions go back to the plugin, which checks
  // everything again and answers with the reason if it refuses.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import MapView from '../components/MapView.svelte';
  import Toast from '../components/Toast.svelte';

  let data = $state(null);
  let error = $state('');
  let message = $state('');

  async function load() {
    try {
      data = await api('GET', `/self/map?lang=${lang}`);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    untrack(load);
    // The waypoints of others and the chunk loaders change in game too.
    return poll(load, 15_000);
  });

  $effect(() => {
    if (!message && !error) return;
    const timer = setTimeout(() => {
      message = '';
      error = '';
    }, 5000);
    return () => clearTimeout(timer);
  });

  const tileUrl = (folder, zoom, x, z) => `/api/self/map/tiles/${folder}/${zoom}/${x}_${z}.png`;

  async function action(module, layer, id, args) {
    error = '';
    try {
      await api('POST', '/self/map/action', { module, layer, action: id, args, lang });
      message = t('map.done');
      await load();
    } catch (e) {
      error = errorText(e);
    }
  }
</script>

<div class="page">
  <header class="head">
    <div>
      <h1>{t('map.title')}</h1>
      <p class="muted">{t('map.subtitle')}</p>
    </div>
  </header>
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />
  {#if data}
    {#if data.worlds.length}
      <MapView worlds={data.worlds} layers={data.layers} {tileUrl} onaction={action} storageKey="mradmin.self.map" />
    {:else}
      <p class="muted">{t('map.noWorlds')}</p>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 14px;
  }

  .head h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  .head p {
    margin: 2px 0 0;
  }
</style>
