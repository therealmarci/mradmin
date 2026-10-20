<script>
  import { api } from '../lib/api.js';
  import { t } from '../lib/i18n.js';
  import { markdown } from '../lib/markdown.js';
  import { go } from '../lib/router.svelte.js';

  let { page } = $props();

  let html = $state(null);
  let contact = $state(false);

  // Below the imprint: the contact form (if switched on).
  api('GET', '/legal').then((l) => (contact = l.contact)).catch(() => {});

  $effect(() => {
    html = null;
    api('GET', '/legal/' + page)
      .then((r) => (html = r.text.trim() ? markdown(r.text) : ''))
      .catch(() => (html = ''));
  });
</script>

<div class="page">
  <article class="card legal">
    <button class="back" onclick={() => history.length > 1 ? history.back() : (location.href = '/')}>← {t('legal.back')}</button>
    {#if html === null}
      <p class="muted">{t('common.loading')}</p>
    {:else if html === ''}
      <h1>{page === 'imprint' ? t('footer.imprint') : t('footer.privacy')}</h1>
      <p class="muted">{t('legal.empty')}</p>
    {:else}
      {@html html}
    {/if}
    {#if page === 'imprint' && contact}
      <div class="contact">
        <p class="muted small">{t('pub.contactLead')}</p>
        <button onclick={() => go('/kontakt')}>{t('pub.contactButton')}</button>
      </div>
    {/if}
  </article>
</div>

<style>
  .page {
    max-width: 760px;
    margin: 0 auto;
    padding: 20px 16px 40px;
  }

  .back {
    background: transparent;
    color: var(--accent);
    padding: 0 0 12px;
  }

  .legal :global(h1) {
    font-size: 1.5rem;
  }

  .legal :global(h2) {
    font-size: 1.1rem;
    margin-top: 1.4em;
  }

  .legal :global(blockquote) {
    margin: 0 0 1em;
    padding: 10px 14px;
    border-left: 4px solid var(--warn);
    background: var(--surface-2);
    border-radius: 8px;
  }

  .legal :global(ul) {
    padding-left: 1.3em;
  }

  .contact {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid var(--border);
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  .contact p {
    margin: 0;
  }
</style>
