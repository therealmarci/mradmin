// Stand-in for lib/i18n.js in the viewer bundle: only the texts the chart (components/LineChart.svelte) asks for, so
// the bundle does not carry all of MRadmin's texts. Same wording as in lib/i18n.js.
const TEXTS = {
  de: { 'common.close': 'Schließen', 'health.expand': 'Vergrößern', 'health.table': 'Tabelle', 'health.chart': 'Diagramm',
    'health.time': 'Zeit', 'health.noData': 'Noch keine Messwerte - sie kommen jede Sekunde.' },
  en: { 'common.close': 'Close', 'health.expand': 'Enlarge', 'health.table': 'Table', 'health.chart': 'Chart',
    'health.time': 'Time', 'health.noData': 'No measurements yet - they come every second.' },
};
export let lang = 'de';
export function setLang(value) {
  lang = value === 'en' ? 'en' : 'de';
}
export function t(key) {
  return TEXTS[lang][key] ?? key;
}
