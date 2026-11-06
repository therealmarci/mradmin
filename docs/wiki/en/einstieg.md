# Overview

**The toolbox for your server team:** maintenance with a countdown, tickets and reports, admin mode, player tools, announcements, staff chat - in game via menus and on your phone via its own web interface. MRadmin also connects all other MRtechnology plugins with each other.

| At a glance | |
|---|---|
| Server | Paper 26.3 (or a fork like Purpur), Java 25 |
| Requires | nothing - LuckPerms recommended, Uptime Kuma and LibertyBans optional (MRadmin installs squaremap for the map itself) |
| Languages | German, English - every player automatically in the language of their Minecraft client |
| Status | stable (1.x) |

## What can MRadmin do?
- **Maintenance:** during maintenance only the team gets onto the server. Scheduled maintenance can come live from **Uptime Kuma**, before it a **boss bar with a countdown** runs. → [Maintenance](wartung.md)
- **Tickets & reports:** players report rule violations, bugs and questions with `/report` via a menu. The team answers in game or in the web - like in a chat. → [For players](report.md), [for the team](tickets.md)
- **Admin mode:** creative and a menu item. When it ends, everything is exactly as before - inventory, XP, health, position. → [Dashboard & admin mode](dashboard.md)
- **Player tools:** teleport to, summon, view inventory and ender chest, freeze, kick.
- **Announcements** on the screen of all players, with templates (e.g. for the restart script) and optionally on the community Discord. → [Announcements](ankuendigungen.md)
- **Admin dashboard** with live tiles, **server status** and **staff chat**.
- **Web interface** for your phone: tickets, players, server status, maintenance, push notifications - with 2FA and passkeys. → [Web interface](/docs/WEBOBERFLAECHE.md)
- **Player page in the web (`/web`):** every player gets their own page - own tickets, rankings and everything the other plugins offer. → [Player page](spielerseite.md)
- **Map** on the player page with the layers of the other plugins - squaremap renders it, MRadmin installs, updates and sets it up itself. → [Map](karte.md)
- **Plugins, updates & settings:** configure, update and install all MRtechnology plugins in the web, restart the server with a countdown. → [Plugins & updates](plugins.md)
- **Discord logs**, **update check**, a permission for every feature, every feature can be switched off on its own.

## Where to start?
| You are … | Read first |
|---|---|
| **a player** | [Tickets & reports](report.md) and [Player page](spielerseite.md) |
| **a server owner** | [Quick start](schnellstart.md), then [Permissions](permissions.md), [Web interface](/docs/WEBOBERFLAECHE.md) and [Configuration](konfiguration.md) |
| **in the team** | [Dashboard & admin mode](dashboard.md), [Handling tickets](tickets.md), [Commands](befehle.md) |
