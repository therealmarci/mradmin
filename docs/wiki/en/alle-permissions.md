# Permissions of all MRtechnology plugins

**All plugins are built the same way:** one package for players and three groups for the team. Every group contains the one below - so staff can do everything support can, admin everything staff can. By default only OPs have the packages: you decide yourself who gets what.

| Package | For whom |
|---|---|
| `<plugin>.player` | Players - the normal features of the plugin and `/<plugin> version` |
| `<plugin>.groups.support` | Support - view and help |
| `<plugin>.groups.staff` | Staff - step in |
| `<plugin>.groups.admin` | Admins - everything, including settings |

The single permissions stay usable next to them. Use them to give single people something extra or to take something out of a group (examples below). What exactly a plugin puts into its groups is described in its guide.

## Ready-made groups for LuckPerms
Four groups that build on each other - just copy the commands into the console. You only need the plugins you use; lines for missing plugins do no harm.

```text
# Create the groups, each inherits from the one below
/lp creategroup supporter
/lp creategroup staff
/lp creategroup admin
/lp group supporter parent add default
/lp group staff parent add supporter
/lp group admin parent add staff

# Players (group default)
/lp group default permission set mradmin.player true
/lp group default permission set mrchunks.player true
/lp group default permission set mrpvp.player true
/lp group default permission set mrsleep.player true
/lp group default permission set mrtoolbar.player true
/lp group default permission set mrwaypoints.player true

# Support
/lp group supporter permission set mradmin.groups.support true
/lp group supporter permission set mrchunks.groups.support true
/lp group supporter permission set mrpvp.groups.support true
/lp group supporter permission set mrsleep.groups.support true
/lp group supporter permission set mrtoolbar.groups.support true
/lp group supporter permission set mrwaypoints.groups.support true

# Staff
/lp group staff permission set mradmin.groups.staff true
/lp group staff permission set mrchunks.groups.staff true
/lp group staff permission set mrpvp.groups.staff true
/lp group staff permission set mrsleep.groups.staff true
/lp group staff permission set mrtoolbar.groups.staff true
/lp group staff permission set mrwaypoints.groups.staff true

# Admin
/lp group admin permission set mradmin.groups.admin true
/lp group admin permission set mrchunks.groups.admin true
/lp group admin permission set mrpvp.groups.admin true
/lp group admin permission set mrsleep.groups.admin true
/lp group admin permission set mrtoolbar.groups.admin true
/lp group admin permission set mrwaypoints.groups.admin true
```

Team members then simply go into their group: `/lp user Steve parent set staff`.

## What the groups may do

| | Support | Staff | Admin |
|---|---|---|---|
| **MRadmin** | Overview, server status and health, tickets completely, view players, teleport to players, view inventories, staff chat, admin mode, web interface, view maintenance and join during it, view the Plugins page, view punishments (LibertyBans), link to the map | + summon, freeze, kick players, with LibertyBans kick, mute, warn, read the console in the log | + control maintenance, announcements and their templates, news for the player page, web accounts, check for updates, install updates and plugins, configure plugins in the browser, restart, reload, set up the map (squaremap), **commands in the web console**, with LibertyBans ban and unban |
| **MRchunks** | Admin menu (all loaders and their status), teleport to loaders, switch conspicuous loaders (above a warn limit) and their dummy off and on, live view in the web | + other players' loaders on/off, manage invitations, link and unlink accounts | + delete, rename, change the size of other players' loaders, dummies, lift lag locks, suspend the limit lock of single loaders, chunk budget and dummy limit, bans, pause all loaders, staged shutdown on/off (web), web map, Discord log, reload |
| **MRpvp** | Admin menu, view all rounds, review and score suspicious rounds, spectate, cancel duels and decide the winner, pause, player management | + edit kits and arenas, ban | + reset statistics, give back saved states, restore defaults, Discord log, reload |
| **MRsleep** | Admin menu, status, teleport, bypass up to 30 min, ban up to 1 day | + bypass and ban up to 1 day, lift blocks, reset cooldown | + without limit, ban permanently, Discord log, reload |
| **MRtoolbar** | Admin menu, view other players' belts | + `/mrtoolbar debug` | + Discord log, reload |
| **MRwaypoints** | MRwaypoints in the dashboard, view the waypoints page in the web | + create waypoints in every category, edit and delete all, without limit | + teleport to waypoints, manage categories in the web, Discord log, reload |

You set the durations of MRsleep under `team-limits` in its `config.yml`. The MRadmin groups bring the LibertyBans permissions (support: `libertybans.commands`, `libertybans.list.history|banlist|mutelist`; staff: `libertybans.kick|mute|warn.do.target.uuid`, `mute|warn.undo.target.uuid`; admin: `libertybans.ban.do|undo.target.uuid`) - they also apply to the LibertyBans commands in game. Bans in the plugins (duels, `/nosleep`, chunk loaders) are separate from that.

> [!WARNING]
> `mradmin.groups.admin` includes running commands in the web console - as powerful as the server console.
> Only give the admin group to people you trust with the server.

## In no group - single on purpose
These permissions change how a player is treated and therefore belong in no group:

| Permission | What for |
|---|---|
| `mrchunks.exempt` | Completely excluded from chunk loaders - existing loaders are deleted |
| `mrchunks.budget.<n>` · `dummies.<n>` (or `.unlimited`) | Chunk budget (how many chunks all active loaders load together) and number of dummies |
| `mrsleep.exempt` | Not counted for the sleep quota (bots, cam accounts) |
| `mrsleep.nosleep.nocooldown` | `/nosleep` without a cooldown |
| `mrpvp.bypass.commands` | All commands also during a duel |
| Kit permissions from MRpvp's `kits.yml` | Certain kits only for certain players (e.g. VIP) |
| `mrtoolbar.slots.<n>` · `mrtoolbar.belt.<n>` | Number of tool slots and places per belt |

> [!WARNING]
> **Wildcards:** `*` (or `mrchunks.*`, `mrsleep.*`) also contains `mrchunks.exempt` and `mrsleep.exempt` in LuckPerms.
> Whoever gives admins `*` explicitly sets `/lp group admin permission set mrchunks.exempt false` and
> `/lp group admin permission set mrsleep.exempt false` there - otherwise their chunk loaders are deleted and they are
> not counted for the sleep quota.

## Adjusting

```text
# Additionally allow a supporter to make announcements
/lp user Steve permission set mradmin.announce true

# Staff without kicking
/lp group staff permission set mradmin.player.kick false

# VIPs: 300 chunks budget (e.g. six 7x7 loaders) and a VIP kit
/lp group vip permission set mrchunks.budget.300 true
/lp group vip permission set mrpvp.kit.vip true      # if the kit has permission: mrpvp.kit.vip in kits.yml
```

## Moving from older versions
Up to the versions of October 2026 the packages had other names (`mradmin.support`, `mradmin.staff`, `mradmin.admin`, `mrchunks.admin`, `mrpvp.admin`, `mrsleep.admin`, `mrtoolbar.admin`). They no longer exist - after the update give the groups as above again and remove the old entries, e.g. `/lp group admin permission unset mrchunks.admin`.
Also, players no longer have `/report`, `/duel`, `/nosleep` and the version commands (`/<plugin> version`) automatically: give the `.player` packages for that.
