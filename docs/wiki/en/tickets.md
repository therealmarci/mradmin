# Handling tickets

How players create tickets is described under [Tickets & reports](report.md). For the team:

- The **team** gets new tickets with **[Open] [Teleport] [All tickets]** in chat (`mradmin.tickets.notify`).
- Under dashboard → Tickets or `/mradmin tickets`: filter, answer, **claim**, **solve**, reopen. Whoever answers first claims the ticket automatically.
- **Solve instead of close:** when a matter is settled, the team marks the ticket as **solved**. It then shows in the **archive** (marked "Solved"), but for the player it is still among their active tickets: if the matter is not settled after all, they can **open it again** - the team is notified like about a new message. After `tickets.reopen-days` (default 30 days) it is **closed for good** and only the team can open it again (`.reopen`). If the player closes their ticket themselves, it is closed for good right away. Unban requests and applications are still decided with accept/reject. `tickets.reopen-days: 0` = solved tickets are closed for good at once.
- The ticket shows the history like a chat. Whoever is offline learns about new answers at the next join.
- You create **categories** under `tickets.categories` (in the web interface as a table). Default: cheating, chat, griefing, scam, bug, question/other.
- **Automatic tickets from plugins:** if a plugin reports a problem of a player, they automatically get a ticket - e.g. **with MRchunks**, when their chunk loader is locked because of lag, with all measurements. Only the team can close it. Switch off with `tickets.plugin-reports: false`.
- More settings under `tickets`: cooldowns, limits, how long closed tickets are kept (for solved tickets `keep-closed` only starts after `reopen-days`).

In the browser the same tickets are under **Tickets** in the [web interface](/docs/WEBOBERFLAECHE.md) - plus unban requests and the contact form.

## Permissions
`mradmin.tickets.create` (players) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` - all in `mradmin.groups.support`, see [Permissions](permissions.md).
