// One live connection per page (Server-Sent Events). MRadmin only says "ticket X changed" - pages that show it load
// it again. The browser reconnects by itself if the connection drops.
export const live = $state({ connected: false, ticket: { id: 0, tick: 0 } });

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
}

export function disconnect() {
  source?.close();
  source = null;
  live.connected = false;
}
