<script>
  // One tile on a player's overview, in the look of the start page's tiles (DashTile): symbol and title, big value,
  // the values as lines, link. A click opens the full section.
  import { t } from '../lib/i18n.js';
  import DashTile from './DashTile.svelte';

  /**
   * icon: key of ICONS; value / unit: the big value and its small unit; small: a word instead of a number (smaller
   * type); tone: 'good' | 'warn' | 'bad' | 'info' | '' colours the value; facts: [{ label, value, tone }].
   */
  let { label, icon = 'box', value = '', unit = '', small = false, tone = '', facts = [], loading = false, error = '', onOpen } = $props();
</script>

<DashTile {icon} hue={210} title={label} value={error ? '–' : loading ? '…' : value} unit={loading || error ? '' : unit}
  small={small || loading || !!error} tone={loading || error ? '' : tone}
  rows={loading || error ? [] : facts.map((f) => ({ label: f.label, value: f.value, tone: f.tone ?? '' }))}
  note={error} link={t('player.open')} onclick={onOpen} />
