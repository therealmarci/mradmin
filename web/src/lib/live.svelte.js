// One live connection per page (Server-Sent Events). MRadmin only says "ticket X changed" (or for other plugins:
// "mrpvp game 12 changed") - pages that show it load it again. The browser reconnects by itself if the
// connection drops.
export const live = $state({
  connected: false,
  ticket: { id: 0, tick: 0 },
  plugin: { plugin: '', type: '', id: '', tick: 0 },
});

let source = null;

export function connect() {
  if (source) return;
  source = new EventSource('/api/events');
  source.onopen = () => (live.connected = true);
  source.onerror = () => (live.connected = false);
  source.addEventListener('ticket', (e) => {
    const { id } = JSON.parse(e.data);
    live.ticket = { id, tick: live.ticket.tick + 1 };
  });
  source.addEventListener('plugin', (e) => {
    const { plugin, type, id } = JSON.parse(e.data);
    live.plugin = { plugin, type, id, tick: live.plugin.tick + 1 };
  });
}

export function disconnect() {
  source?.close();
  source = null;
  live.connected = false;
}
