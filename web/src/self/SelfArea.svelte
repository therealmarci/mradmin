<script>
  // One area of a plugin on the player's own page (e.g. MRchunks' chunk loaders): its blocks, buttons go back to the
  // plugin, which checks everything again and answers with the reason if it refuses.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import Blocks from './Blocks.svelte';

  let { module, id, onChanged } = $props();

  let area = $state(null);
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
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
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
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page">
  {#if area}<h1>{area.icon} {area.title}</h1>{/if}
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}
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

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }
</style>
