<script>
  // /anfrage/<token>: the private status page of an unban or contact request (the token is the access).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import RequestThread from '../components/RequestThread.svelte';

  let { token } = $props();

  let status = $state(null);
  let info = $state({ minText: 20, maxText: 3000 });
  let error = $state('');

  api('GET', '/public/info').then((i) => (info = i)).catch(() => {});
  $effect(() => {
    status = null;
    error = '';
    api('POST', '/public/status', { token })
      .then((s) => (status = s))
      .catch((e) => (error = e.status === 404 ? t('pub.linkInvalid') : errorText(e)));
  });
</script>

<div class="page">
  {#if error}
    <section class="card"><p>{error}</p></section>
  {:else if !status}
    <p class="muted">{t('common.loading')}</p>
  {:else}
    <div class="notice small save">{t('pub.saveLink')}</div>
    <RequestThread bind:status auth={{ token }} minText={info.minText} maxText={info.maxText} />
  {/if}
</div>

<style>
  .page {
    max-width: 760px;
    margin: 0 auto;
    padding: 20px 16px 40px;
  }

  p {
    margin: 0;
  }

  .save {
    margin-bottom: 12px;
  }
</style>
