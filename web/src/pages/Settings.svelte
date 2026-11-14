<script>
  // Settings -> Configuration (since the sidebar, 1.6): the config.yml of MRadmin and of every plugin that can be set up
  // in the browser - one tab per plugin, MRadmin first. Before 1.6 this sat in Server -> Plugins -> plugin -> tab.
  // The list comes from GET /api/versions (field config per plugin), the form is ConfigEditor.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import PageHead from '../components/PageHead.svelte';
  import PageTabs from '../components/PageTabs.svelte';
  import ConfigEditor from '../components/ConfigEditor.svelte';

  let { plugin = null } = $props();

  let names = $state(null);
  let error = $state('');

  $effect(() => {
    api('GET', '/versions')
      .then((d) => {
        const list = d.plugins.filter((p) => p.config && p.installed).map((p) => p.name);
        names = list.sort((a, b) => (a === 'MRadmin' ? -1 : b === 'MRadmin' ? 1 : a.localeCompare(b)));
      })
      .catch((e) => (error = errorText(e)));
  });

  const current = $derived(names && (names.includes(plugin) ? plugin : names[0]));
  // Without a plugin in the address: the first one (MRadmin).
  $effect(() => {
    if (names?.length && !names.includes(plugin)) go('/admin/settings/config/' + names[0], true);
  });
  const tabs = $derived((names ?? []).map((n) => ({ id: n, label: n, path: '/admin/settings/config/' + n })));
</script>

<PageHead title={t('nav.config')} />
{#if error}<div class="error">{error}</div>{/if}
{#if names && !names.length}
  <p class="muted">{t('settings.noConfig')}</p>
{:else if current}
  <PageTabs {tabs} {current} />
  {#key current}<ConfigEditor plugin={current} />{/key}
{:else if !error}
  <p class="muted">{t('common.loading')}</p>
{/if}
