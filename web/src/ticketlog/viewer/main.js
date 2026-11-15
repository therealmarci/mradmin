// Entry of the ticket log viewer bundle for the MRtechnology website (built with vite.viewer.config.js, copied to the
// website by ##OTHER/Scripts/sync-log-viewer.sh). It mounts exactly the component MRadmin uses, so both always show the
// same. Usage: mountTicketLog(element, log, 'de' | 'en') -> { destroy() }
import { mount, unmount } from 'svelte';
import TicketLog from '../TicketLog.svelte';
import { setLang } from './i18n.js';
import './viewer.css';

export function mountTicketLog(target, log, lang = 'de') {
  setLang(lang);
  target.classList.add('mr-tlog-root');
  const app = mount(TicketLog, { target, props: { log, lang } });
  return { destroy: () => unmount(app) };
}
