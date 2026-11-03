<script>
  // Tab "Map" of the player's own page: MRadmin's own map over squaremap's pictures with the layers the plugins offer
  // this player (MRwaypoints' waypoints, MRchunks' chunk loaders ...). Their actions go back to the plugin, which checks
  // everything again and answers with the reason if it refuses. The map fills the whole screen below the bar at the
  // top (and above the bar at the bottom on phones), without a page head.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import MapView from '../components/MapView.svelte';
  import Toast from '../components/Toast.svelte';

  let data = $state(null);
  let loadError = $state('');
  // A new object per result, so the same text shows the bubble again.
  let toast = $state({ text: '', tone: 'good' });

  async function load() {
    try {
      data = await api('GET', `/self/map?lang=${lang}`);
      loadError = '';
    } catch (e) {
      loadError = errorText(e);
    }
  }

  $effect(() => {
    untrack(load);
    // The waypoints of others and the chunk loaders change in game too.
    return poll(load, 15_000);
  });

  // Where the player and the others stand - only if the server offers it (map.player-page), as often as it says.
  let positions = $state(null);
  const every = $derived(data?.page?.positions ? Math.max(1, data.page.updateSeconds ?? 2) * 1000 : 0);

  async function locate() {
    try {
      positions = await api('GET', '/self/map/players');
    } catch {
      // the next round tries again; the map stays usable without positions
    }
  }

  $effect(() => {
    if (!every) return;
    untrack(locate);
    return poll(locate, every);
  });

  // Room between the bar at the top and (phone) the bar at the bottom; the page itself does not scroll here.
  let top = $state(0);
  let bottom = $state(0);

  $effect(() => {
    const measure = () => {
      top = document.querySelector('.mr-topbar')?.getBoundingClientRect().bottom ?? 0;
      const bar = document.querySelector('.mr-tabbar');
      bottom = bar && getComputedStyle(bar).position === 'fixed' ? Math.max(0, window.innerHeight - bar.getBoundingClientRect().top) : 0;
    };
    measure();
    const observer = new ResizeObserver(measure);
    for (const el of document.querySelectorAll('.mr-topbar, .mr-tabbar')) observer.observe(el);
    window.addEventListener('resize', measure);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.scrollTo(0, 0);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
      document.body.style.overflow = overflow;
    };
  });

  // ?world=minecraft:overworld&x=..&z=.. from links in game (e.g. the web map button of /chunkloader).
  const params = new URLSearchParams(window.location.search);
  const initial = params.has('world') && params.has('x') && params.has('z')
    ? { world: params.get('world'), x: Number(params.get('x')), z: Number(params.get('z')) }
    : null;

  const tileUrl = (folder, zoom, x, z) => `/api/self/map/tiles/${folder}/${zoom}/${x}_${z}.png`;

  async function action(module, layer, id, args) {
    try {
      await api('POST', '/self/map/action', { module, layer, action: id, args, lang });
      toast = { text: t('map.done'), tone: 'good' };
      await load();
    } catch (e) {
      toast = { text: errorText(e), tone: 'bad' };
    }
  }
</script>

<div class="full" style="top:{top}px;bottom:{bottom}px" aria-label={t('map.title')}>
  {#if data?.worlds.length}
    <MapView worlds={data.worlds} layers={data.layers} {tileUrl} {initial} onaction={action} storageKey="mradmin.self.map"
      page={data.page ?? {}} {positions} />
  {:else}
    <div class="state">
      {#if loadError}
        <div class="error">{loadError}</div>
      {:else if data}
        <p class="muted">{t('map.noWorlds')}</p>
      {:else}
        <p class="muted">{t('common.loading')}</p>
      {/if}
    </div>
  {/if}
  {#if data?.worlds.length && loadError}<div class="lost error">{loadError}</div>{/if}
</div>
{#key toast}<Toast text={toast.text} tone={toast.tone} seconds={toast.tone === 'bad' ? 6 : 4} />{/key}

<style>
  .full {
    position: fixed;
    left: 0;
    right: 0;
    z-index: 1;
  }

  .state {
    display: grid;
    place-items: center;
    height: 100%;
    padding: 16px;
    text-align: center;
  }

  /* The map stays usable when one reload fails; the reason floats on top until the next one works. */
  .lost {
    position: absolute;
    left: 50%;
    bottom: 16px;
    translate: -50% 0;
    z-index: 8;
    max-width: min(90%, 480px);
    box-shadow: var(--shadow);
    background: var(--surface);
  }
</style>
