# Permissions

Jede Funktion hat eine eigene Permission - **im Spiel und im Web gleich**. Am einfachsten vergibst du die **Gruppen**. Sie sind in allen MRtechnology-Plugins gleich aufgebaut (`<plugin>.player` und `<plugin>.groups.support|staff|admin`), jede Gruppe enthält die darunter. Standardmäßig haben sie nur OPs.

> [!TIP]
> **Alle MRtechnology-Plugins auf einmal einrichten:** [Permissions aller Plugins](/docs/PERMISSIONS.md) - fertige LuckPerms-Gruppen zum Kopieren und was Support, Staff und Admin in jedem Plugin dürfen.

## Gruppen
<!-- auto:permission-groups -->

```text
/lp group default permission set mradmin.player true
/lp group supporter permission set mradmin.groups.support true
/lp group staff permission set mradmin.groups.staff true
/lp group admin permission set mradmin.groups.admin true
```

Die Gruppen bringen auch die nötigen **LibertyBans**-Rechte mit (falls installiert): Support sieht Strafen, Verlauf und die Bann- und Mute-Listen (`libertybans.commands`, `libertybans.list.*`), Staff darf kicken, muten und verwarnen, nur Admins bannen. Diese Rechte gelten dann auch für die LibertyBans-Befehle im Spiel.

> [!WARNING]
> `mradmin.groups.admin` enthält das Ausführen von Befehlen in der Web-Konsole - so mächtig wie die Server-Konsole. Gib die Gruppe nur Leuten, denen du den Server anvertraust. Vor dem Öffnen fragt das Web zusätzlich den 2FA-Code ab.

## Alle einzelnen Permissions
„Enthalten in“ nennt die kleinste Gruppe, die die Permission mitbringt.

<!-- auto:permissions -->

Die Permissions der anderen MRtechnology-Plugins gelten im Web genauso für deren Bereiche.

> [!TIP]
> Einzelnen Leuten gibst du zusätzlich einzelne Rechte, z.B. `/lp user <name> permission set mradmin.announce true`. Aus einer Gruppe nimmst du eins heraus mit z.B. `/lp group staff permission set mradmin.player.kick false`.
