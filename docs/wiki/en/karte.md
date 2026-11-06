# Map (squaremap)

The [player page](spielerseite.md) has a tab **Map** with real Minecraft terrain. On it the other plugins show their **layers** - e.g. your own chunk loaders (MRchunks) or the server waypoints (MRwaypoints), each matching the signed-in player, with right-click actions. The pictures for it are rendered by **[squaremap](https://modrinth.com/plugin/squaremap)**, which you do not have to take care of.

## What MRadmin does by itself
- **Installed automatically:** if squaremap is missing, MRadmin downloads the official release for your Minecraft version from Modrinth (only if the SHA-512 checksum matches). It is active after the next restart.
- **Updated automatically:** new squaremap versions go to `plugins/update/`, Paper swaps them on the next restart (checked every `map.squaremap.check-hours`, default 12 hours). Can be switched off with `map.squaremap.auto-install` and `auto-update`.
- **Rendered automatically:** after the installation and after every squaremap update MRadmin renders all worlds of the map once completely (all generated chunks, in the background; `map.squaremap.full-render`, remembered in `plugins/MRadmin/map-render.yml`). Worlds without map pictures are always rendered.
- **Set up automatically:** on every start MRadmin brings the squaremap config to the right state: squaremap's own web page off, fast updates, no players on the map, the Nether from inside instead of the bedrock roof, names of the dimensions. Worlds that belong to a plugin (e.g. MRpvp's arena world) stay off the map (`map.hide-module-worlds`). **What you change yourself in `plugins/squaremap/config.yml` stays.** `/mradmin map setup` also overwrites your changes, `map.auto-configure: false` leaves the squaremap config alone completely.
- **Version at a glance:** under **Server → Plugins**, MRadmin also shows the squaremap version (and a waiting update).

## Nether roof as its own map
squaremap shows the Nether from inside. The bedrock roof and what is built on it is rendered by MRadmin itself as its own map **"Nether · Roof"** (pictures in `plugins/MRadmin/map-roof/`): once completely in the background, after that every chunk in which something changes at the top. If a player stands on the roof (from Y 128), the map jumps there by itself with "To me" and "Follow", otherwise via the world selection. Waypoints and chunk loaders appear on the map they are on. Settings under `map.nether-roof` (`enabled`, `from-y`, `full-render`, `chunks-per-second`).

## Only in MRadmin
The map **only exists in MRadmin** - squaremap's own web page (port 8080) is off, you do not need to open a port or run your own proxy. MRadmin serves the map pictures itself, only to signed-in players. For this the player page has to be switched on (`player-web.enabled`) and `web.public-url` has to be right, so that `/mradmin map` can give a link.

`/mradmin map` gives players the link to the map (`mradmin.map`). Admins (`mradmin.map.admin`) additionally see the squaremap version there and whether an update waits for the restart or the installation failed.

## Players on the map
> [!TIP]
> You set **players on the map** (yourself and the other online players), opening at your own location, "Follow me" and the chunk grid under `map.player-page`. For a manhunt e.g. `map.player-page.players.others: false` - then everyone only sees themselves. Players in admin mode, in spectator mode and invisible players never see others (can be switched off), worlds named in `players.hidden-worlds` show nobody. Only whoever has `mradmin.map.players` sees other players. With `players.allow-hide` players may hide themselves.

## Switch the map off for everyone
On the page **Map** in the admin portal (with MRwaypoints also categories and waypoints) there are two switches (`mradmin.map.admin`): **Map of the player page** (off = the tab "Map" disappears for all players) and **Players on the map** (off = nobody sees other players or can search for them). Both take effect right away and survive restarts.

> [!NOTE]
> Up to MRadmin 1.1, MRchunks connected squaremap, and the map was a public squaremap web page. Update MRadmin and MRchunks together - MRadmin takes over the setup from the old MRchunks config. A reverse proxy or an opened port for squaremap can go afterwards.
