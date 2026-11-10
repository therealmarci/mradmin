<script>
  import { api } from '../lib/api.js';
  import { t } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { brand } from '../lib/branding.svelte.js';

  // Own website (from the config) or the texts in MRadmin.
  let links = $state({ imprint: '', privacy: '', contact: false });
  api('GET', '/legal').then((l) => (links = l)).catch(() => {});
</script>

{#if brand.footer || brand.links.length}
  <!-- Text and links of the server (Server -> Appearance) -->
  <div class="own">
    {#if brand.footer}<span>{brand.footer}</span>{/if}
    {#each brand.links as link (link.url)}
      <a href={link.url} target="_blank" rel="noopener noreferrer">{link.label}</a>
    {/each}
  </div>
{/if}
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

  .own {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 6px 14px;
    padding: 18px 0 0;
    font-size: 0.86rem;
    color: var(--muted);
  }

  .own a {
    color: var(--text);
  }
</style>
