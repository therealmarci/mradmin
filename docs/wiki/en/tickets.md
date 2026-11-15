# Handling tickets

How players create tickets is described under [Tickets & reports](report.md). For the team:

- The **team** gets new tickets with **[Open] [Teleport] [All tickets]** in chat (`mradmin.tickets.notify`).
- Under dashboard → Tickets or `/mradmin tickets`: filter, answer, **claim**, **solve**, reopen. Whoever answers first claims the ticket automatically.
- **Solve instead of close:** when a matter is settled, the team marks the ticket as **solved**. It then shows in the **archive** (marked "Solved"), but for the player it is still among their active tickets: if the matter is not settled after all, they can **open it again** - the team is notified like about a new message. After `tickets.reopen-days` (default 30 days) it is **closed for good** and only the team can open it again (`.reopen`). If the player closes their ticket themselves, it is closed for good right away. Unban requests and applications are still decided with accept/reject. `tickets.reopen-days: 0` = solved tickets are closed for good at once.
- **Change category** (`mradmin.tickets.category`): an open ticket can be moved to another category of the same kind - in the browser with the selection on the category in the ticket header, in game with **Change category**. Player reports stay player reports; requests from the web and reports of plugins keep their category. The conversation shows who changed it.
- **Log of the ticket** (`mradmin.tickets.log`): new tickets of the categories in `tickets.attach-log.categories` (default `bug`) get a snapshot of the last minutes when they are created - server log, events, performance per second, server data and the surroundings of the player. It is kept as long as the ticket. In the browser **Show log** in the ticket opens charts, a timeline (a click on a bar shows only that time), tiles and the searchable log. Players never see the log. Plus a map of the 16 × 16 chunks around the player with entities, despawned items, deaths, the way and the inventory; the charts can zoom to 1, 10 or 30 minutes. Automatic tickets from plugins (e.g. a chunk loader lock from MRchunks) also have an own part at the top - it stays on the server.
- **Forward to MRtechnology** (browser only, `mradmin.tickets.forward`): for bug tickets (categories in `tickets.forward-categories`, default `bug`) the button in the ticket opens a window - choose the plugin, the installed version and the server version are filled in, below the **whole ticket conversation**. **Open on GitHub** shows the plugin's bug form prefilled; you send it with your own GitHub account (MRadmin needs no token). Then enter the link of the issue and **Link** - the ticket then shows "GitHub #123" as a link. If the ticket has a log, it goes along **encrypted**: only MRtechnology can open it. You drag the file into the issue; with `web.public-url` the issue also gets a link.

> [!WARNING]
> Issues on GitHub are public. What you forward (also player names in the conversation) can be read by anyone - shorten the text in the window if needed.

- The ticket shows the history like a chat. Whoever is offline learns about new answers at the next join.
- You create **categories** under `tickets.categories` (in the web interface as a table). Default: cheating, chat, griefing, scam, bug, question/other.
- **Automatic tickets from plugins:** if a plugin reports a problem of a player, they automatically get a ticket - e.g. **with MRchunks**, when their chunk loader is locked because of lag, with all measurements. Only the team can close it. Switch off with `tickets.plugin-reports: false`.
- More settings under `tickets`: cooldowns, limits, how long closed tickets are kept (for solved tickets `keep-closed` only starts after `reopen-days`).

In the browser the same tickets are under **Tickets** in the [web interface](/docs/WEBOBERFLAECHE.md) - plus unban requests and the contact form.

## Permissions
`mradmin.tickets.create` (players) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` - all in `mradmin.groups.support`; `.forward` (forwarding) and `.category` (change category) and `.log` (view log) in `mradmin.groups.staff`, see [Permissions](permissions.md).
