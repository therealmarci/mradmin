# Player page (/web)

Besides the team area, every **player** has their own page in the web interface - own tickets, statistics, news, the map and everything the other plugins offer.

## Sign in
The page always has the same address (`<address of the server>/me`) - best save it as a **bookmark**. To sign in it asks for a **6-digit code**:

1. Enter **`/web`** in game (also `/me`).
2. The chat shows the link to the page and the code - a click on the code copies it.
3. Enter the code on the page. It is valid for 2 minutes and only once.

After that the device stays signed in for **at most 14 days** and is signed out after **7 days without a visit** - then the page simply asks for a code again. `/web devices` shows on how many devices you are signed in, `/web logout` signs out all of them. Single devices can also be signed out on the page under **Sign-ins & devices** (click your name at the top right), which also shows how long the sign-in is still valid.

**Sign in without the game:** under **Sign-ins & devices** you can set up an own sign-in - a **password** (sign in with your player name or your confirmed e-mail address), a **passkey** (fingerprint, Face ID, Windows Hello) or **Discord**. Then you also get in from your phone without typing `/web` first. Optional on top: **2FA** with an authenticator app. With an e-mail address, "Forgot password?" on the sign-in page helps.

## What is on the page
What exists depends on what is installed and switched on on the server:

| Section | What you see and do |
|---|---|
| **Tickets** | view your own tickets, open new ones, answer, close - like with `/report` |
| **News** | **News** (the newest one big, the others as cards) and **Changelog** (timeline of versions with New / Changed / Fixed / Removed); active **warnings** are shown in red at the very top. On the start page a carousel with warnings, maintenance, restart, notices of the plugins, ticket answers and the newest posts. |
| **My statistics** | tile on the start page: own play time, mined blocks, killed mobs, deaths - leads to the statistics page |
| **Statistics** | **Personal** (every own value with rank, a medal for the top 3 and a comparison to the best) and **Server** (totals of all players, rankings with a podium, also from the plugins). Whoever does not want to be in the rankings opts out there. |
| **Chunk loaders** | with MRchunks: budget, every loader as a card with on/off, limits, members, inviting - size only in game |
| **Duels** | with MRpvp: own duel statistics and last rounds, duel rankings |
| **Map** | full-screen map with real terrain (see below) |

The page is available in German and English (switch at the top). Every plugin checks the same permissions and rules as in game for every action.

## The map
- Search for points, show and hide grouped layers, chunk grid, choose the world, drag and zoom (on a phone also with two fingers). A click on a point shows its details.
- If you are online, you see yourself (and, depending on the server, the other players). The map opens at your location and follows you on request ("Follow me").
- The plugins bring their own layers - e.g. all server waypoints (MRwaypoints) and your chunk loaders (MRchunks).
- **Right click** (on a phone: long press) on a point shows its actions (edit or delete your own waypoint, start the guide in game, chunk loader on/off), on an empty spot e.g. "Create waypoint".
- All panels of the map can be moved by their handle and stay saved in the browser.

## For admins: switch it on
The player page is off by default.

1. The [web interface](/docs/WEBOBERFLAECHE.md) has to be running (`web.enabled`, `web.public-url`).
2. In the config set `player-web.enabled: true`, then `/mradmin reload`.
3. Players need `mradmin.web.self` - it is part of `mradmin.player`.

| Setting | What for |
|---|---|
| `player-web.code-seconds` · `session-days` · `idle-days` | Validity of the code (120 s) · longest sign-in (14 days) · sign-out without a visit (7 days) |
| `player-web.news` | News on/off, chat preview, warnings, carousel (`seconds`, `slides`, `show-maintenance`, `show-restart`, `show-plugin-notices`, `show-tickets`) |
| `player-web.personal-stats` | Values of the tile "My statistics" (the first one big, empty = no tile) |
| `player-web.rankings` | Statistics page on/off and which rankings |
| `map.player-page` | Map: players on the map, chunk grid, opening at the location, "Follow me" - see [Map](karte.md) |

- Banned players (LibertyBans) do not get in.
- **For the team:** whoever is signed in to the team area gets to their own player page without a code via **To the player page** in the menu behind their own name at the top right (or on their own entry under Players). This also needs `mradmin.web.self` and is recorded in the log.
- The team writes **news, changelogs and warnings** under **Communication** - see [Web interface](/docs/WEBOBERFLAECHE.md#communication-mradminnews-mradminannounce).
