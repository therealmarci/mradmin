# Permissions

Every feature has its own permission - **the same in game and in the web**. The easiest way is to give the **groups**. They are built the same in all MRtechnology plugins (`<plugin>.player` and `<plugin>.groups.support|staff|admin`), every group contains the one below. By default only OPs have them.

> [!TIP]
> **Set up all MRtechnology plugins at once:** [Permissions of all plugins](/docs/PERMISSIONS.md) - ready-made LuckPerms groups to copy and what support, staff and admin may do in every plugin.

## Groups
<!-- auto:permission-groups -->

```text
/lp group default permission set mradmin.player true
/lp group supporter permission set mradmin.groups.support true
/lp group staff permission set mradmin.groups.staff true
/lp group admin permission set mradmin.groups.admin true
```

The groups also bring the needed **LibertyBans** permissions (if installed): support sees punishments, history and the ban and mute lists (`libertybans.commands`, `libertybans.list.*`), staff may kick, mute and warn, only admins ban. These permissions then also apply to the LibertyBans commands in game.

> [!WARNING]
> `mradmin.groups.admin` includes running commands in the web console - as powerful as the server console. Only give the group to people you trust with the server. Before opening it, the web additionally asks for the 2FA code.

## All single permissions
"Included in" names the smallest group that brings the permission.

<!-- auto:permissions -->

The permissions of the other MRtechnology plugins apply in the web the same way for their sections.

> [!TIP]
> You give single people additional single permissions, e.g. `/lp user <name> permission set mradmin.announce true`. You take one out of a group with e.g. `/lp group staff permission set mradmin.player.kick false`.
