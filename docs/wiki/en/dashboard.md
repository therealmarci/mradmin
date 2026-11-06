# Dashboard & admin mode

## Admin dashboard
**`/mradmin`** (or a right click with the menu item) opens the dashboard:
- **Live tiles** for players, server, maintenance and tickets. Below them a colored pane shows the state, e.g. TPS (green from 19.5, yellow from 15), maintenance (yellow = scheduled, red = active), tickets (red = waiting for an answer).
- **Announce:** your own templates as buttons (the built-in ones from the language file are not in the menu), "Own text" opens a dialog.
- **Plugin buttons:** admin menus of the other MRtechnology plugins with live info. Your own buttons (e.g. for Chunky) go via `admin-mode.menu.buttons`.
- **Switches** for admin mode and staff chat.

If a permission is missing, the button is simply missing. `/mradmin help` shows all commands.

## Admin mode
- **`/mradmin mode on`** (or the switch in the dashboard): inventory, armor, XP, health, hunger, effects, flight status and position are saved. After that you are in creative mode with the **menu item** (structure void, `admin-mode.compass-material`).
- **`/mradmin mode off`:** everything is restored exactly, and you are back at your starting point.
- Saving happens **before** every change - even a crash loses nothing. You are simply still in admin mode afterwards.
- In admin mode you cannot drop or pick up anything, so no creative items get into survival.
- `/mradmin mode list` shows who is in admin mode. `/mradmin mode off <player>` ends it for others.
- Other MRtechnology plugins respect admin mode: you are not counted for the sleep quota, the tool bar pauses, duels are not possible.

## Player menu
Menu item → teleport to players → click a head, or `/mradmin player <player>`:
- Info: position, game mode, health, ping, play time, first join
- **Teleport to** and **summon** (shift click on a head teleports directly)
- View **inventory** and **ender chest** - live and read only
- **Freeze:** the player cannot move, build or fight and takes no damage. Logging out does not help.
- Their reports and the info from the other MRtechnology plugins; with LibertyBans also their punishments

## Server status
Dashboard → Server or `/mradmin status`: TPS, MSPT, RAM, chunks, entities and players per world. Below that, with MRchunks the conspicuous and locked chunk loaders, without MRchunks the chunks with the most entities - a click teleports there.

## Staff chat
`/sc <message>` writes to everyone with `mradmin.staffchat`, also from the console. `/sc` without text toggles: then your normal chat goes to the team. Mirroring to the admin Discord is optional (event `staff-chat`).
