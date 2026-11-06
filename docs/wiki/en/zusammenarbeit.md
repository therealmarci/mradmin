# Working with other plugins

MRadmin, MRsleep, MRchunks, MRtoolbar, MRpvp and MRwaypoints each run **on their own as well**. If they are installed together, MRadmin detects them automatically and adapts menus and web by itself:

- **Dashboard:** one button per plugin with live info.
- **Player menu:** the player's info from every plugin.
- **Server status:** conspicuous and locked chunk loaders (MRchunks), waiting duel reviews (MRpvp).
- **Web:** own pages **Chunk loaders**, **Sleep**, **Duels** and **Map** (with waypoints) for the team, on the player page (`/web`) the sections **Chunk loaders** and **Duels** and the layers on the **map** for the players themselves.
- **Plugins:** configure, update and install all of them in the browser; their events in the **log**.

## MRadmin as the link
Without MRadmin the other plugins know nothing about each other:

| With MRadmin | Also needs |
|---|---|
| Players in a duel are not counted for the sleep quota | MRsleep + MRpvp |
| The tool bar pauses during a duel, the belts are saved along | MRtoolbar + MRpvp |
| No chunk loaders can be placed in the arena world | MRchunks + MRpvp |
| Chunk loaders and waypoints on the map of the player page, the arena world not | MRchunks / MRwaypoints / MRpvp |
| Players in admin mode are not counted for the sleep quota, have the tool bar paused and cannot start a duel | MRsleep / MRtoolbar / MRpvp |

There is nothing to set up. You switch off a connection under `integrations` (e.g. `mrsleep: false`). The console shows on startup which plugins are connected. For developers: [module API](/docs/MODULE-API.md).

**LibertyBans:** if it is installed, MRadmin shows punishments in the player menu and in the web and makes unban requests possible via the web interface.

## Several worlds (Multiverse)
MRadmin works with any number of worlds. Multiverse-Core and Multiverse-Inventories are not needed but are taken into account:

- **Admin mode and Multiverse-Core:** Multiverse sets the world's game mode when changing worlds. In admin mode this is rejected, you stay in creative or spectator. `/gamemode` still works.
- **Admin mode and Multiverse-Inventories:** while admin mode runs, Multiverse-Inventories does not swap the inventory. When it ends, your own inventory comes back.

> [!WARNING]
> Keep `admin-mode.return-to-start` at `true` with Multiverse-Inventories. Otherwise you stay in the other world after it ends, and your inventory ends up in that world's group.
