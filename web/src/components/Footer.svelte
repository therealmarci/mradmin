<script>
  import { api } from '../lib/api.js';
  import { t } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';

  // Own website (from the config) or the texts in MRadmin.
  let links = $state({ imprint: '', privacy: '', contact: false });
  api('GET', '/legal').then((l) => (links = l)).catch(() => {});
</script>

<footer>
  {#if links.imprint}
    <a href={links.imprint} target="_blank" rel="noopener noreferrer">{t('footer.imprint')}</a>
  {:else}
    <a href="/impressum" onclick={(e) => { e.preventDefault(); go('/impressum'); }}>{t('footer.imprint')}</a>
  {/if}
  <span>·</span>
  {#if links.privacy}
    <a href={links.privacy} target="_blank" rel="noopener noreferrer">{t('footer.privacy')}</a>
  {:else}
    <a href="/datenschutz" onclick={(e) => { e.preventDefault(); go('/datenschutz'); }}>{t('footer.privacy')}</a>
  {/if}
  {#if links.contact}
    <span>·</span>
    <a href="/kontakt" onclick={(e) => { e.preventDefault(); go('/kontakt'); }}>{t('footer.contact')}</a>
  {/if}
</footer>

<style>
  footer {
    display: flex;
    gap: 8px;
    justify-content: center;
    font-size: 0.82rem;
    color: var(--muted);
    padding: 16px 0 4px;
  }

  a {
    color: var(--muted);
  }
</style>
