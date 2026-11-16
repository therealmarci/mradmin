<script>
  // The log of a ticket as its own page (Tickets › #n › Log, since the two-column ticket view): the shared ticket log view
  // over the full width. A finally closed ticket only offers the ZIP file (and the viewer on the MRtechnology website).
  import { api } from '../lib/api.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import PageHead from '../components/PageHead.svelte';
  import TicketLog from '../ticketlog/TicketLog.svelte';

  let { id } = $props();
  let log = $state(null);
  let archived = $state(false);
  let error = $state('');

  $effect(() => {
    const ticket = id;
    log = null;
    archived = false;
    error = '';
    api('GET', `/tickets/${ticket}/log`)
      .then((data) => (log = data))
      .catch((e) => {
        if (e?.status === 410) archived = true;
        else error = errorText(e);
      });
  });
</script>

<PageHead title={t('tlog.page')} crumbs={[{ label: t('tickets.title'), path: '/admin/community/tickets' }, { label: '#' + id, path: `/admin/community/tickets/${id}` }]}>
  {#snippet actions()}
    <a class="button secondary small dl" href={`/api/tickets/${id}/log/download`} download>{t('tlog.download')} ↓</a>
  {/snippet}
</PageHead>

{#if error}
  <div class="error">{error}</div>
{:else if archived}
  <section class="card">
    <p>{t('tlog.archivedHint')}</p>
    <p><a href="https://mrtechnology.marcelr.at/log/" target="_blank" rel="noopener noreferrer">{t('tlog.viewer')} ↗</a></p>
  </section>
{:else if log}
  <TicketLog {log} {lang} />
{:else}
  <p class="muted">{t('common.loading')}</p>
{/if}

<style>
  .dl {
    text-decoration: none;
  }
</style>
