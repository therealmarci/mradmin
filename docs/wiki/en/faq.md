# FAQ

### A team member is kicked during maintenance anyway.
They need `mradmin.maintenance.bypass` (part of `mradmin.groups.support`). Without LuckPerms MRadmin only checks permissions on join.

### I was in admin mode, and the server crashed.
Nothing is lost - after the restart you are simply still in admin mode. `/mradmin mode off` restores everything. Please do not delete the files in `plugins/MRadmin/admin-mode/`.

### A right click with the menu item triggers another plugin.
Set an item in `admin-mode.compass-material` that no other plugin uses. The compass, for example, is WorldEdit's navigation tool and teleports you on right click - that is why the structure void (`STRUCTURE_VOID`) is the default. It cannot be placed.

### In spectator mode I cannot click anything in menus.
That is normal: Minecraft drops inventory clicks of spectators before a plugin sees them. Switch to creative for the menu (`/gamemode creative`). For the same reason `admin-mode.gamemode: SPECTATOR` is only suitable if you work without menus. Commands and the web interface still work then.

### The web interface shows only abbreviations instead of pictures for items.
MRadmin could not load the pictures from Mojang (the console says why on startup - usually no internet on the server) or `web.item-icons` is set to `false`. After fixing it, `/mradmin reload` - they are only loaded once per Minecraft version.

### The map is missing on the player page.
squaremap is installed on the first start and only active after the next restart. After that MRadmin renders the worlds in the background. Also check `player-web.enabled`, `map.enabled` and the switches on the page **Map** in the admin portal - see [Map](karte.md).

### Where do I see which plugins are connected?
In the console on startup. If a plugin is too old for the connection, a warning is shown there - then update all MRtechnology plugins together.

### I found a bug or have a wish.
Gladly via the **[form on the website](https://mrtechnology.marcelr.at/report/mradmin/)** - ideally with the output of `/mradmin version`.
