# MRadmin - Web interface

Use MRadmin from your phone, tablet or PC: answer tickets, manage players, schedule maintenance, keep an eye on the server status - with the same permissions as in game. This page describes the web interface in detail. What players see on their own page is described under [Player page](spielerseite.md), updates and restarts under [Plugins & updates](plugins.md).

## Layout
Since 1.5 there is **one** web interface for players and team. Team members switch at the top right with **Player | Administration** (on the phone in the menu under the name). Every area has tabs at the top.

| Player area | Content |
|---|---|
| **Home** (`/`) | start page made of widgets - one for visitors and one for signed-in players |
| **My game** (`/game/…`) | one tab per plugin (chunk loaders, duels, waypoints …) |
| **Map** (`/map`) | the map full screen |
| **Statistics** (`/stats`) | own values and server rankings |
| **Inbox** (`/inbox`, letter at the top right) | everything on one page, laid out like the plugin areas: a head with the number of new entries, below tiles for warnings, **My tickets**, **News** and **Changelogs** with cards in status colours (new blue, open ticket green, read/closed grey). A click opens the card over the page (read a post, a changelog, a ticket conversation with reply field, a new ticket). The number on the letter shows what is new since the last visit (new posts, answers of the team) |
| **Account** (`/account`, menu under the name) | sign-ins, devices, notifications for the team |

| Administration | Tabs |
|---|---|
| **Overview** (`/admin`) | dashboard |
| **Community** (`/admin/community/…`) | players · tickets · punishments · posts · announcements |
| **Features** (`/admin/features/…`) | one tab per plugin: chunk loaders · duels · sleep · map |
| **Server** (`/admin/server/…`) | control · performance · maintenance · plugins · log · console · appearance (MRadmin's config: plugins → MRadmin) |

Everyone sees only what they have the permissions for. Old addresses (`/me/…`, `/tickets`, `/chunks`, `/server/plugins` …) lead to the new ones - bookmarks and old links keep working. Whoever was in the administration last lands there again after signing in.

## Setup
1. In the config set `web.enabled: true` and `web.public-url` to your address (e.g. `https://admin.yourserver.com`), then `/mradmin reload`. For many tickets `storage.type: sqlite` is recommended.
2. Set up a **reverse proxy**: domain with HTTPS → `http://127.0.0.1:8125`. The built-in web server only listens locally and has no HTTPS of its own.
3. **Sign in:** everyone gets in with the code from `/web` in game - the account is made then. Team members (permission `mradmin.web`) then set up an own sign-in under **Account → Sign-ins**: password, passkey or Discord. Only then the administration opens.

| Command | What it does | Permission |
|---|---|---|
| `/web` | Code to sign in (players and team) | `mradmin.web.self` or `mradmin.web` |
| `/mradmin web` | Status (running, address, accounts with an own sign-in, devices) | `mradmin.web` |
| `/mradmin web sessions [player]` | Signed-in devices | `mradmin.web` (others: `.manage`) |
| `/mradmin web logout [player]` | Sign out all devices | `mradmin.web` (others: `.manage`) |
| `/mradmin web reset <player>` | Remove own sign-ins (password, 2FA, passkeys, Discord, e-mail) and sign out all devices - e.g. lost phone | `mradmin.web.manage` |
| `/mradmin web remove <player>` | Delete the account | `mradmin.web.manage` |

**Forgot the password?** With a confirmed e-mail address, "Forgot password?" sends a link by mail (needs `mail.*`). And `/web` in game always works.

## Sign-in and security
- **One sign-in for everyone** (since 1.5): players and team use the same account (one per Minecraft UUID). Everyone gets in with the 6-digit code from `/web` (valid 2 minutes). A device stays signed in for `player-web.session-days` (default 14 days), without use at most `player-web.idle-days` (7 days).
- **Own sign-in** under **Account → Sign-ins** (player area: **Sign-ins & devices**), to get in without the game:
  - **Password** - sign in with the player name or a confirmed **e-mail address**. The address counts only after the click on the link in the confirmation mail; it makes "forgot password" possible. Passwords are stored with PBKDF2 (600,000 rounds).
  - **Passkey** (fingerprint, Face ID, Windows Hello, password manager) - bound to the address from `web.public-url` (without it only `http://localhost` for testing). Only public keys are stored. Switch off: `web.login.passkeys: false`.
  - **Discord** (`web.login.discord`): create a Discord application, add the redirect `<public-url>/api/discord/callback`, client ID and secret into the config; only `identify` is asked for.
  - **2FA** (code from an authenticator app) is optional: when on, the password sign-in also asks for the code (or a recovery code).
- The **first** own sign-in is set up just like that. Every further one - and changing or removing one - is confirmed with an existing one (password, 2FA code or passkey).
- **Team:** the administration opens with `mradmin.web` **and** at least one own sign-in. `web.login.require-2fa: true` also requires 2FA (default off). A `/web` sign-in is enough once the own sign-in is set up. Without it the page shows the **Sign-ins** card right away.
- **Sensitive steps** (unlocking the web console, emergency restart) are confirmed with an own sign-in: 2FA code, passkey, Discord or password.
- After 5 failed attempts the account is locked for 15 minutes, plus at most 10 sign-in attempts per minute and IP. For this to work with the visitors' real IP, enter your proxy under `web.trusted-proxies`.
- Cookies: `HttpOnly`, `SameSite=Strict`, over HTTPS `Secure`; session cookies, recovery codes and mail links only as hashes. Changes only with an own header and a matching origin (no triggering from other sites), strict security headers.
- The web has the same permissions as in game. With LuckPerms also for offline players, without LuckPerms via `web.roles` and `web.players`. Whoever loses `mradmin.web` no longer sees the administration (but stays signed in as a player).
- Every sign-in and action goes into the log (web: **Log**, with `mradmin.web.audit`), sign-ins also into the Discord log (`web-login`).
- **After the update to 1.5:** existing team accounts keep their password, 2FA, passkeys and Discord, signed-in devices stay signed in. Setup links (`/mradmin web setup`, `invite`) are gone.

## Appearance (Server, `mradmin.appearance`)
The web interface carries the name and colours of your server - players never see "MRadmin".
- **Name** and **tagline** (sign-in page), **accent colour** (buttons, highlights; the text colour on it is chosen by the page), **text and up to 4 links** in the footer (e.g. Discord, website, rules).
- **Logo** (sign-in page, mails) and **browser icon** (tab, bar, "add to home screen"): PNG, WebP or SVG, at most 512 KB. Without an own icon the logo is used. SVGs with scripts or embedded content are refused.
- On the right a **preview** of the sign-in and start page, light and dark. After saving everything applies to everyone right away.
- The name shows in the browser tab, on the sign-in page, in the bar, in mails, in the 2FA app and for passkeys.
- Saved in `config.yml` (section `branding`, also editable in the file), the pictures under `plugins/MRadmin/branding/`.
- **Mails** (confirmation, forgot password, unban, contact, test mail) come in the same look: header with logo and name in the accent colour, links as a button, footer with text, links, imprint and privacy - plus always a plain text version. For the logo in mails `web.public-url` is needed.

### Start page (widgets)
There are **two start pages** (`/`): one for **visitors** (not signed in) and one for **signed-in players**. Both are made of **widgets** in a grid with 12 columns; each widget has a width (1-12 columns) and a height (1-8 rows). Personal widgets exist only on the signed-in page, the log-in call only on the visitors' page. On tablets small widgets get half the width and bigger ones the full width, on phones all are one below the other.

**Edit** right on the start page (`mradmin.appearance`, **Edit** button at the top; switch between **Visitors** and **Signed in** at the top, both are saved together):
- **Move:** drag the ⠿ grip and drop it on another widget (left half = before, right half = after).
- **Size:** drag the handle at the bottom right or enter width/height in the settings.
- **Insert:** **+ Insert widget** (goes after the selected one), **settings** ⚙, **remove** ✕.
- **Save** or **Discard**; **Load template** replaces the selected page with the template (only when saving).

| Group | Widgets |
|---|---|
| Server & status | **Banner** (logo, name, tagline, background picture, address, players online - signed in with "Hello <name>", for visitors with Log in), **Notices** (maintenance, restart, warnings, posts - personal when signed in), **Server address**, **Players online** |
| Personal (signed-in only) | **Greeting**, **My areas** (one tile per area of the plugins), **My statistics** |
| Content | **News**, **Text** (with formatting), **Picture** (PNG, WebP, SVG up to 512 KB), **Links**, **Discord** (invite), **Log-in call** |

Widgets without content (empty text, no picture, no links) stay hidden outside the edit mode. **Log in** is at the top right of the bar and opens a window over the page (directly: `/login`). The earlier start page is split onto both pages by itself on the update (everyone → both, visitors only → visitors, signed-in only → signed in). Saved in MRadmin's storage (`landing`), the pictures under `plugins/MRadmin/branding/landing/`. Name, logo, colour and footer are set under Server → Appearance.

## Punishments (Community, with LibertyBans)
- All punishments of all players at a glance, newest first (at most 300): **Active** shows what is in force right now - like LibertyBans' `/banlist` and `/mutelist` -, **All** also expired, lifted punishments and kicks.
- Filter by type (ban, mute, warning, kick) and search for player or reason; a click on the player opens their page.
- **Lift** right in the list, with the same permissions as in game (`libertybans.<type>.undo.target.uuid`).
- Permissions like in LibertyBans: `libertybans.commands` plus `libertybans.list.banlist` (bans), `libertybans.list.mutelist` (mutes) or `libertybans.list.history` (warnings and "All"); plus `mradmin.player.view`. Without LibertyBans there is no such tab.

## Overview (dashboard)
- At the top the **situation**: if something gets out of hand, every message is listed here with an icon and a word, the most urgent first: low TPS, high tick time, full memory, little disk space, high CPU load, high ping, unanswered tickets and tickets assigned to you, locked, conspicuous or paused chunk loaders, /nosleep blocks, maintenance. If everything runs smoothly, the most important current values are shown there. Updates every 2 seconds.
- Below that tiles for server, players, tickets, chunk loaders, sleep and maintenance with a click to the page in question.
- Every message and every tile only appears with the permission for its section: server and players with `mradmin.status`, CPU with `mradmin.status.health`, tickets with `mradmin.tickets.view` (assigned ones with `mradmin.tickets.claim`), chunk loaders with `mrchunks.admin.gui`, sleep with `mrsleep.admin.gui`, maintenance with `mradmin.maintenance.view`. If a plugin or feature is not there, the part is simply missing.
- A click on your head or name at the top right opens the **account** (devices, recovery codes).

## Health dashboard (`mradmin.status.health`)
- Like spark's health report: TPS, MSPT (average, 95 %, peak), CPU (server and system), memory, garbage collection, players, average ping, chunks (with MRchunks also "of which chunk loaders") and entities as a history from 1 minute to 30 days, plus worlds, system info, server size (measured every 10 minutes in the background), size of the MRadmin database and the 5 chunks with the most entities.
- It measures every second from values Java and Paper keep anyway (no lag), the page updates live. The last hour is available per second (selectable from 1 minute), 24 hours in a 10-second interval, older data as 5-minute averages (peaks are kept as maximum, for garbage collection as an own line "peak"). Retention `health.history`: `24h`, `7d` or `30d`, with SQLite/MySQL or `health.log` across restarts. After a restart the stored 5-minute values fill in the time before.
- The time axis always shows exactly the chosen range; where there are no values (server off), it stays empty.
- Every chart can be opened full screen with **⤢** (close with Esc, a click beside it or "Close") and shown as a table.
- `health.alert`: warning to the team (in game) and to the Discord log when the TPS stays below a limit for a while, and an all-clear as soon as it has recovered.

## Players (`mradmin.player.view`)
- Everyone who has been on the server: online first, then by last visit, with search, filters (online, conspicuous) and markers (frozen, reported, blocking /nosleep, chunk loader locked ...). No IP addresses.
- One page per player: at the top a short preview with location, game mode, ping, health, first visit and play time; freeze (`mradmin.player.freeze`) and kick with a reason (`mradmin.player.kick`, Discord log `player-kick`).
- Below that **one tile per section** with its status at a glance (e.g. "2 open", "3 / 5 loaders", "in duel #12", "banned until …"). A click opens the section with all details and actions (address `/players/<uuid>/<section>`, so it can also be bookmarked). Sections: their tickets (`mradmin.tickets.view`) and one section per plugin, each only with its permissions - if the plugin is missing, the tile is missing:
  - **MRsleep:** /nosleep blocks, cooldown, bypass, ban (`mrsleep.admin.gui`, `.clear`, `.bypass`, `.ban`).
  - **MRchunks:** their loaders (with size), chunk budget and dummy limit with their origin (default, permission, admin), set the budget (`mrchunks.admin.limit`); on a loader's page change its size (`mrchunks.admin.resize`) and suspend its limit lock for up to 24 hours (`mrchunks.admin.suspendlock`); the charts of the measurements are there as well (1m to 12h, `mrchunks.admin.gui`) with a live view (`mrchunks.admin.live`). Ban from chunk loaders (`mrchunks.admin.ban`), delete all loaders (`mrchunks.admin.manage`), invitations and loaders shared with them (`mrchunks.admin.gui`). View, remove and create linked accounts - by player name as alt or main account, without the players' consent (`mrchunks.admin.manage`, like `/mrchunks link`).
  - **MRpvp:** duel statistics, the running round (click → duel control) and the last rounds as tiles (`mrpvp.admin.games`), ban from duels with a reason and quick-select of the duration (`mrpvp.admin.ban`, durations from `gui.ban-durations` in the MRpvp config), give back saved states (`mrpvp.admin.restore`), reset statistics (`mrpvp.admin.reset`).
  - **LibertyBans:** active punishments and history (`libertybans.commands` + `libertybans.list.history`), ban, mute, warn and kick (`libertybans.<type>.do.target.uuid`), lift (`libertybans.<type>.undo.target.uuid`). The duration comes from the quick-select `libertybans.durations` in the MRadmin config; if LibertyBans has its duration permissions switched on (`duration-permissions`), they apply here as well. It runs via LibertyBans' API - so with its kick screen, messages and notifications. In the player list banned (`libertybans.list.banlist`) and muted (`libertybans.list.mutelist`) players are marked.
- Players in tickets, chunk loaders and on the sleep page link straight to their page.

## Chunk loaders (with MRchunks)
MRchunks permissions: `mrchunks.admin.gui` to view, `mrchunks.admin.manage` to act.

- All chunk loaders with status (OK, off, conspicuous, locked, paused - always with an icon and a word), problems first, search by name, player, world or coordinates, server pause as a notice.
- Per loader: location, lock reason, all measurements of the lag protection with warn and lock limit, members. Actions: switch on/off, lift the lock, delete - the owner gets a message like in game, everything goes to the Discord and web log.

## Sleep (with MRsleep)
Permissions: `mrsleep.admin.gui` to view, lifting blocks with `mrsleep.admin.clear`.

- Every world with day/night, how many sleep and how many are needed, plus who is blocking with /nosleep right now - lift one or all. Everything about a single player is on their page under **Players**.

## Duels (with MRpvp)
MRpvp permissions: `mrpvp.admin.gui` to view, `.games` finished rounds, `.stop`, `.pause`, `.ban`, `.reset`, `.restore`, `.review` to act, `.kits` to edit kits.

- **Kits** (`.kits`, chip "Kits →"): all kits as cards (icon, hotbar, rules), order with ↑/↓, copy, create. In the kit editor the inventory like in game - a click on a slot opens the item editor, dragging with the mouse swaps two slots (Ctrl = copy), right click opens copy/cut/paste/clear; on phone and tablet hold a slot = menu, hold and drag = move. Plus ID (another ID renames), name and description in German and English (equal = for all languages), icon, permission and the rules: no damage, hits to win, regeneration and hunger (fixed on/off or player's choice), needs a build arena, fixed time limit, hearts, effects at the start (effect + level), cooldowns for the kit's items (seconds), only certain arenas. A new arena appears the next time it is opened; kits without an arena selection use it right away. Deleted arenas a kit is still set to are shown in red and have to be unticked. Nothing is saved before "Save", new rounds get the kit right away.
- **Export / import** (kit list): select kits and download them as `.json` - e.g. for another server. On import the dialog shows per kit what happens: new, or already existing (create under a new ID or replace), arenas that do not exist here (map them to one of your own or leave them out), and with which Minecraft version the file was created. Every kit is saved like in the kit editor (same checks). The same file also works in game (MRpvp kit list, folder `plugins/MRpvp/imports/`).

- Running duels live: both players' hearts, time, kit, arena. Control: pause and resume (`.pause`), decide a winner or cancel (`.stop`). One page per round with a mini map (the dot lights up when hitting and being hit), inventories and combat log; finished rounds with the saved inventories (on entering and at the end, ender chest, MRtoolbar belts) and a replay of the positions.
- **Review:** suspicious rounds that do not count yet - score or discard them. Scored rounds can later be taken out of scoring on their page (with an optional reason) and scored again (`.review`). Arenas with their copies, suspicious events of the last 24 hours. Everything about a single player (statistics, rounds, ban) is on their page under **Players**.

## Items: pictures, names and editor
Wherever the web interface shows items (inventories in duels, kits), they look like in game: the picture from the inventory, amount, durability bar, enchantment glint and, on hover (on a phone: tap), the tooltip with name, enchantments, potion effects and lore - in the browser's language.

- **Where the pictures come from:** on the first start with the web interface switched on, MRadmin downloads the official game client of your Minecraft version from Mojang **once** (about 40 MB, verified with a checksum), draws all items from it and only stores the finished picture (about 1.3 MB) and the item names under `plugins/MRadmin/items/<version>/`. The client itself is not stored. Nothing of this is in the plugin JAR - Mojang does not allow passing it on.
- **New Minecraft version:** the pictures are loaded again once for the new version, new items are then included.
- **Without internet or with `web.item-icons: false`:** items show an abbreviation instead of a picture, everything else works.
- **Item editor** (e.g. in MRpvp's kit editor): search for an item (name or ID, with picture), amount, own name and lore (MiniMessage: colors and style like `<red>`, `<#ff8800>`, `<b>`, `<gradient:red:gold>`), enchantments (fitting ones first, all on request, level 1-255, a note for combinations that do not exist in game), glint on/off, unbreakable, durability, potion (base potion, color, own effects with level and duration), color of leather armor, armor trim and flight duration of rockets. The tooltip is shown live on the right. When applying, MRadmin builds the real item and checks all values; what the editor does not show stays on the item.

## Maintenance
`mradmin.maintenance.view` to view, `.manage` to change. Announcements are under [Communication](#communication-mradminnews-mradminannounce).

- The **current maintenance** is shown big at the top: if it runs, with remaining time and a progress bar, if it is scheduled, with the countdown to the start; below that extend/shorten and end or cancel.
- **With Uptime Kuma** (`uptime-kuma` set up): maintenance is managed directly in Kuma - create new ones (now until ended, now with a duration or scheduled), extend the current one, pause/resume, delete. MRadmin attaches new maintenance to `uptime-kuma.monitor-ids`, so it applies to this server (and appears on their status pages); only maintenance of this server can be changed. Recurring maintenance is edited in Kuma (link "Open in Uptime Kuma").
- **Without Uptime Kuma**: MRadmin's own maintenance like `/mradmin maintenance` - start now or schedule with a countdown (duration or open end, title), extend, end or cancel. A preview shows beforehand when it starts and ends.
- **Upcoming maintenance**: the next three after the current one, also mixed from Uptime Kuma and local with origin, start, end and duration.
- **Last maintenance**: the three most recently ended or cancelled maintenance windows, no matter whether from Uptime Kuma or local (the origin is shown), with time range and duration. MRadmin remembers the last ten.

## Communication (`mradmin.news`, `mradmin.announce`)
Two sections: **Posts** for the player page (`mradmin.news`) and **Announcements** on the screen in game (`mradmin.announce`, see below). Whoever has only one of the two permissions sees only that section.

### Posts
Three kinds of posts for the players, texts separately in German and English (everyone sees their language, if it is missing, the other one):

- **News** (announcements, events): title and text, **Pin** always puts it in front.
- **Changelog**: version, optionally title and introduction, plus **entries** with a kind (New, Changed, Fixed, Removed) and **headings** on three levels (1 main heading, e.g. "Plugins"; 2 sub heading, e.g. "MRpvp"; 3 a further level). Entries belong to the heading above them and are indented below it, under every level all kinds are possible - add, reorder, remove. On the player page a timeline, on Discord the sections in order.
- **Critical warning**: title and text. When sent, all players who are online get the warning in chat and as a title on the screen (`player-web.news.alerts`); whoever joins later sees it in chat as long as it is active. It runs until "Active until" or until **End**.

**Also post on Discord** (per post): in the channel of the announcement webhook (`discord-announce`, where maintenance is posted as well), with a link to the whole message on the player page. Warnings mention `ping-role-id` (`discord-announce.alerts.ping`), news and changelogs do not (`discord-announce.news.ping`). For an existing post this can be done once later.

Every player with `mradmin.web.self` gets new news and changelogs **once as a preview in chat** with a link "[Read]": right away if they are online, otherwise at the next join (`player-web.news.chat-preview`).

**Warnings from Uptime Kuma:** incidents pinned on a Kuma status page (`uptime-kuma.status-pages`, `uptime-kuma.url` is enough for this; read every `uptime-kuma.incident-seconds`, default 15) appear on the player page, in the carousel and at the top of this page. The link in chat leads to the player page. What happens in game depends on the style in Kuma:

| Style in Kuma | Color | In game |
|---|---|---|
| danger | red | new: chat to everyone, plus a big "ATTENTION / Critical warning"; on join in chat as long as it is pinned |
| warning | yellow | new: chat to everyone (no title); on join in chat as long as it is pinned |
| info, primary, light, dark | blue | once per player in chat, like a news post |

What is already pinned at server start is not announced to everyone again. Incidents are changed and ended in Uptime Kuma. In the Discord channel of the announcements (`discord-announce.kuma`) every incident is posted once (red pings the role), with a link "More" to the news page of the player page (not to Kuma), edited when it changes in Kuma and set to "✅ Resolved" at the end.

**Visible until** hides a post after this day (it stays in the list, gray). Creating, editing, ending and deleting are recorded in the web log. It is stored in MRadmin's storage under `news` (who already got which preview: `news-seen`).

### Announcements
**Send** (`mradmin.announce`): one of your own templates (with values if it has placeholders) or an own text ("title | subtitle") on the screen, optionally also on the community Discord. Everything goes to the web log.

**Templates** (`mradmin.announce.templates`): create, edit, delete - usable in the web, in the announcement menu in game and with `/mradmin announce <name>`.
- **Texts** each in German and English: title, subtitle, chat, action bar (MiniMessage, placeholders `<args>`, `<arg1>` …), with a **preview** of the screen. Empty fields are not shown.
- **Discord**: on/off and an own text (Markdown, `{args}`).
- **Display**: fade-in, stay and fade-out time of the title in seconds.
- **Sound**: like in the config, none or an own one (all sounds of the game to choose from) with volume and pitch.
- **Audience**: players, bots, support, staff or admin. Every group also gets what is meant for the ones below - "players" reaches everyone, "support" support, staff and admin (by `mradmin.groups.*`), "bots" additionally the NPCs.

The built-in templates of the language file (`restart`, `restart-now`, `update`, `event`) are not listed here and in the game menu - MRadmin uses `restart` itself. They keep working with `/mradmin announce` and in scripts. They are stored in MRadmin's storage under `announce-templates`.

## Server
Everything around the running server is under one entry in the bar: **Server**. At the top tabs, each only with its permission:

| Tab | What is there | Permission |
|---|---|---|
| **Overview** | Four values side by side (details on hover): up for, Paper build with a coloured dot (green up to date, yellow newer build available - checked every 15 minutes, also without the starter), [starter](starter.md) active or not, next planned restart. Below **Restart** and **Stop** with a countdown (`versions.restart-delays`, default 15/30/60 minutes), shorter times through the red **Emergency** button (window: first 2FA code or passkey, then restart/stop, a time from `versions.emergency-delays` and a required reason), or the running countdown (with its reason) and **Cancel** (for a planned restart: **Skip this restart**); optionally an own **Reason**, otherwise MRadmin names one. With planned restarts also **Pause planned restarts for 24 h** / **Switch on again**. Below a short view of the plugins and the latest events. | `mradmin.versions.view`, buttons `mradmin.server.restart` |
| **Plugins** | Versions, updates, installing, configuration in the browser - see [Plugins](#plugins-mradminversionsview) | `mradmin.versions.view` |
| **Log** | Events and console output - see [Log](#log-mradminwebaudit) | `mradmin.web.audit` or `mradmin.web.console.view` |
| **Console** | Run commands - see [Console](#console-mradminwebconsoleexecute) | `mradmin.web.console.execute` |

Restarting only works if something brings the server back: the starter or `versions.restart-method`. Stopping always works - without the starter the server then stays down until someone starts it. Old addresses (`/plugins`, `/logs`, `/console`) lead to the matching tab.

## Plugins (`mradmin.versions.view`)
Page "Plugin configuration" under `/plugins` (the old address `/versions` redirects).
- **On this server:** one card per installed MRtechnology plugin with version, state (up to date / update available / waiting for restart / error) and update button. A click opens the plugin's page (`/plugins/<name>`) with installed and newest version, release date, "What's new", links to the website and to GitHub; for MRtoolbar also the newest version of the client mod (for information only, players install it themselves), for MRadmin the squaremap version.
- **More MRtechnology plugins:** all plugins MRadmin knows but that are not running - with **Install** (`mradmin.versions.install`). The file comes from the official release (same checks as for downloads, if there are only pre-releases, the newest of them) under its release name into `plugins/`; it is loaded on the next start. If the JAR has been in `plugins/` since the start and the plugin still does not run, it is shown as "not loaded" (Paper rejected it - check the console).
- Installed plugins are detected by the `build-info.properties` in their JAR - this needs no particular version of the other plugins; MRadmin knows the ones that are not installed from a fixed list (`versions/Catalog.java`). It is checked every `versions.check-interval-hours` and with **Check now** (only with `mradmin.versions.install`, at most once per minute, GitHub allows 60 requests per hour without sign-in).
- **Configuration** (`mradmin.config`, tab on a plugin's page, `/plugins/<name>/config`): the plugin's `config.yml` as a form. On the left the sections and a search (name, explanation or key), on the right per setting name, short explanation and the matching control; "Restart needed" marks values that only take effect after a restart, "Default" resets to the shipped value, dependent fields only appear when they have an effect. Changed fields are marked yellow, the bar at the bottom shows the number of changes. Saving: MRadmin checks all values (on an error nothing is saved and the field is marked red), writes the file and has the plugin reload; an error while reloading appears as a red notice. If someone changed the file by hand meanwhile, "Reload" is offered instead of saving. Secrets (webhook URLs, passwords) are never shown on the page, only whether they are set. Which plugins support this is described in `docs/MODULE-API.md` (`webConfig`, from API 12) - MRadmin itself and all MRtechnology plugins.
- **Download** (`mradmin.versions.install`): only from the release of the plugin's own GitHub repository, only with a matching SHA-256 checksum and only if the file really contains this plugin. It is stored under the name of the installed JAR in `plugins/update/`; Paper swaps it on the next start. That is why the file name stays the old one, only the content is new. `versions.auto-download` (`off`, `patch`, `minor`, `all`) downloads updates by itself.
- **Restart the server** (`mradmin.server.restart`): only when an update was downloaded or a plugin installed but not loaded yet, and `versions.restart-method` is set (default `none` - a server cannot start itself again). Countdown from `versions.restart-delays`, announced with the templates `restart` and `restart-now`, can be cancelled. After that the server stops (`stop` - a start script with a loop, a watchdog or the panel starts it again) or restarts via the `restart-script` of `spigot.yml` (`script`). Without a button the page shows "Restart needed" after a download. Example scripts: [Plugins & updates](plugins.md#restarting-the-server).
- On the overview a tile shows whether updates are waiting or a restart is needed.

## Push notifications
Under **Account → Notifications**.

- Notifications straight to the device, even with the page closed: new tickets, messages in my tickets, all ticket messages, locked chunk loaders, lag alarm, maintenance. Everyone decides which ones they get - and only gets what they have the permissions for.
- Can be switched on per device (several devices possible, "Send test"). A device that is signed out gets nothing anymore.
- Without third parties: standard web push (VAPID, encrypted - the browser's push service cannot read along). On iPhone/iPad only as an installed app (Share → "Add to Home Screen") and over HTTPS.

## Log (`mradmin.web.audit`)
Everything that happened on the server on one page. At the top sources (menu, several at once), search, time range (1 hour to 14 days) and **Live/Pause**. Below that the **activity chart**: bars over the time range, stacked by events, console, warnings and errors; dragging or a click on a bar shows only that section (✕ removes it). Below that the **table** with 50 entries per page and page numbers ("1-50 of 873") - a click expands the whole text and the details. Live runs on page 1 without a section; otherwise the page only counts new entries ("3 new entries - show").

- **Events:** the same events MRadmin and the other MRtechnology plugins send to their Discord logs (also when Discord is off), in the color from `discord.events`, plus every action in the web interface (who, what, from which account). Filter by source (MRadmin, web interface, per plugin - several at once), search in text and event, "Load older". New entries appear right away.
- Kept for `logging.keep-days` days (at most 14) in `plugins/MRadmin/logs/events-<date>.log` (one line per event). Plugins only write into it from the version with module API 13.
- **Console** (source in the same list, with `mradmin.web.console.view` - in `mradmin.groups.staff`), read only: the lines of the server console by time between the events - new ones live, older ones from Paper's `logs/latest.log` and `logs/<date>-<n>.log.gz` (up to 14 days), level on the right (info, warning yellow, error red; stack trace lines belong to the line before). Links and IP addresses are hidden like in the web console. No unlocking needed. MRadmin stores nothing for this - Paper keeps the files itself. Staff without `mradmin.web.audit` only sees this source.

## Console (`mradmin.web.console.execute`)
Only for admins (in `mradmin.groups.admin` - as powerful as the server console). Whoever should only read along gets `mradmin.web.console.view` and sees the console in the **log**.

- The server console live (from `logs/latest.log`, also Minecraft's own output) with an input line, filter, warnings and errors in color. Links and IP addresses are hidden (`web.console.hide-links` / `hide-ips`).
- Before opening, the code from the authenticator app once more (no recovery codes), then open for `web.console.unlock-minutes` (per device). 5 wrong codes lock the console for 15 minutes.
- Commands run as the console. `web.console.blocked` blocks commands completely - also with a namespace (`minecraft:op`) and hidden in `execute ... run`. Every command goes to the web log and the Discord log (`web-console`), blocked attempts to the log.

## Unban requests & contact form
Two public pages, without sign-in, both end up as tickets for the team (`web.requests` in the config).

- **Unban** (`<public-url>/entbannung`, needs LibertyBans): whoever is banned and wants to join sees the address and a **personal code** (e.g. `1-K7F2QX`) in the ban message. Only with name and code one sees the ban reason and can file a request, so nobody can file requests for others. The code is not stored but calculated from player, ban and a server secret.
  - **One request per ban.** The ticket contains the statement and the ban details. The team answers as usual and decides with **Unban** (lifts the ban in LibertyBans, needs `libertybans.ban.undo.target.uuid`) or **Decline**. A normal close counts as declined.
  - After a decline **one appeal** is possible, then the ticket opens again. After that the decision is final.
  - Unban requests are also visible **in game** in the ticket menu.
- **Contact** (`<public-url>/kontakt`, linked below the imprint and in the footer): name, mail address, subject, message. These requests **only exist in the web**, they never show up in game.
- After sending, you get a **private status link** (`/anfrage/...`): state, history, answer, appeal.
- **Mails** (optional, section `mail`): confirmation of receipt with the status link, the team's answers and the decision. Test with `/mradmin mail test <address>`. Without sending mails, answers can be seen via the status link, and for contact requests a button in the ticket opens your own mail program.
- **Spam protection without external services:** invisible honeypot field, minimum fill-in time, a small calculation in the browser (proof of work, about 1 s), limits per IP and minute/hour, a maximum of open contact requests.

## Privacy & imprint
- Every page (also the sign-in) has a footer with **imprint** and **privacy**. On the first start MRadmin creates templates in `plugins/MRadmin/web/impressum.md` and `datenschutz.md`. The privacy template describes what the interface processes. Please fill them in and check them (no legal advice). Alternatively link your own pages under `web.legal`.
- No third parties: MRadmin even creates the player heads itself from the skins, the browser loads nothing from other services. Only one technically necessary cookie, no tracking.
- The log is deleted after `web.privacy.audit-retention` (default 90 days). With `web.privacy.mask-ip` IP addresses are only stored shortened.
