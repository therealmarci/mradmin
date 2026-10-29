<script>
  // One area of a plugin on the player's own page (e.g. MRchunks' chunk loaders): its blocks, buttons go back to the
  // plugin, which checks everything again and answers with the reason if it refuses.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import Blocks from './Blocks.svelte';
  import AreaHeader from './AreaHeader.svelte';
  import Toast from '../components/Toast.svelte';

  let { module, id, onChanged } = $props();

  let area = $state(null);
  const hue = $derived([...module].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7));
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      area = await api('GET', `/self/areas/${module}/${id}?lang=${lang}`);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    module;
    id;
    untrack(load);
    // Every 10 seconds again while the tab is visible.
    return poll(load, 10_000);
  });

  // The note disappears again after a few seconds.
  $effect(() => {
    if (!message && !error) return;
    const timer = setTimeout(() => {
      message = '';
      error = '';
    }, error ? 8000 : 3500);
    return () => clearTimeout(timer);
  });

  async function action(name, args) {
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', `/self/areas/${module}/${id}/action`, { action: name, args, lang });
      message = t('self.done');
      await load();
      onChanged?.();
      return true;
    } catch (e) {
      error = errorText(e);
      return false;
    } finally {
      busy = false;
    }
  }
</script>

<!-- The plugin's colour (same as its tile on the start page) for header, cards and bars. -->
<div class="page" style="--hue: {hue}; --tint: hsl({hue} 70% 55%)">
  {#if area}<AreaHeader {area} />{/if}
  <!-- Result of an action as a small bubble at the bottom - also visible above an open card. -->
  <Toast text={error || message} tone={error ? 'bad' : 'good'} />
  {#if area}
    <Blocks blocks={area.blocks} onAction={action} {busy} />
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }
</style>
