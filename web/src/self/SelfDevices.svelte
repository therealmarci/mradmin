<script>
  // The player's signed-in browsers: sign out a single one (e.g. a lost phone). /web logout in game signs out all.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';

  let devices = $state(null);
  let error = $state('');

  async function load() {
    try {
      devices = await api('GET', '/self/devices');
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  async function revoke(id) {
    try {
      await api('POST', '/self/devices/' + id + '/revoke');
      await load();
    } catch (e) {
      error = errorText(e);
    }
  }

  // "Firefox on Windows" from the browser's description - only a hint for recognising the device.
  function device(agent) {
    const a = agent || '';
    const browser = /Edg\//.test(a) ? 'Edge' : /OPR\//.test(a) ? 'Opera' : /Firefox\//.test(a) ? 'Firefox'
      : /Chrome\//.test(a) ? 'Chrome' : /Safari\//.test(a) ? 'Safari' : t('self.browser');
    const os = /Android/.test(a) ? 'Android' : /iPhone|iPad/.test(a) ? 'iOS' : /Windows/.test(a) ? 'Windows'
      : /Mac OS X/.test(a) ? 'macOS' : /Linux/.test(a) ? 'Linux' : '';
    return os ? t('self.deviceOn', { browser, os }) : browser;
  }
</script>

<div class="page">
  <h1>{t('self.devicesTitle')}</h1>
  <p class="muted small">{t('self.devicesHint')}</p>
  {#if error}<div class="error">{error}</div>{/if}
  {#if devices}
    <ul>
      {#each devices as d (d.id)}
        <li class="card">
          <div>
            <strong>{device(d.agent)}</strong>
            {#if d.current}<span class="tag">{t('self.thisDevice')}</span>{/if}
            <div class="muted small">{t('self.lastSeen', { time: dateTime(d.lastSeen) })} · {t('self.since', { time: dateTime(d.created) })}</div>
          </div>
          {#if !d.current}
            <button class="secondary small" onclick={() => revoke(d.id)}>{t('self.revoke')}</button>
          {/if}
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 12px;
  }

  h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  p {
    margin: 0;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
</style>
