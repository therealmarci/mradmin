# Permissions aller MRtechnology-Plugins

**Alle Plugins sind gleich aufgebaut:** ein Paket für Spieler und drei Gruppen fürs Team. Jede Gruppe enthält die
darunter - Staff kann also alles, was Support kann, Admin alles von Staff. Standardmäßig haben die Pakete nur OPs:
Du entscheidest selbst, wer was bekommt.

| Paket | Für wen |
|---|---|
| `<plugin>.player` | Spieler - die normalen Funktionen des Plugins und `/<plugin> version` |
| `<plugin>.groups.support` | Support - ansehen und helfen |
| `<plugin>.groups.staff` | Staff - eingreifen |
| `<plugin>.groups.admin` | Admins - alles, auch Einstellungen |

Die einzelnen Permissions bleiben daneben bestehen. Damit gibst du einzelnen Leuten etwas dazu oder nimmst aus einer
Gruppe etwas heraus (Beispiele unten). Was ein Plugin genau in seine Gruppen packt, steht in seiner Anleitung.

## Fertige Gruppen für LuckPerms
Vier Gruppen, die aufeinander aufbauen - einfach die Befehle in die Konsole kopieren. Nur die Plugins, die du nutzt,
brauchst du; Zeilen für fehlende Plugins schaden nicht.

```text
# Gruppen anlegen, jede erbt von der darunter
/lp creategroup supporter
/lp creategroup staff
/lp creategroup admin
/lp group supporter parent add default
/lp group staff parent add supporter
/lp group admin parent add staff

# Spieler (Gruppe default)
/lp group default permission set mradmin.player true
/lp group default permission set mrchunks.player true
/lp group default permission set mrpvp.player true
/lp group default permission set mrsleep.player true
/lp group default permission set mrtoolbar.player true

# Support
/lp group supporter permission set mradmin.groups.support true
/lp group supporter permission set mrchunks.groups.support true
/lp group supporter permission set mrpvp.groups.support true
/lp group supporter permission set mrsleep.groups.support true
/lp group supporter permission set mrtoolbar.groups.support true

# Staff
/lp group staff permission set mradmin.groups.staff true
/lp group staff permission set mrchunks.groups.staff true
/lp group staff permission set mrpvp.groups.staff true
/lp group staff permission set mrsleep.groups.staff true
/lp group staff permission set mrtoolbar.groups.staff true

# Admin
/lp group admin permission set mradmin.groups.admin true
/lp group admin permission set mrchunks.groups.admin true
/lp group admin permission set mrpvp.groups.admin true
/lp group admin permission set mrsleep.groups.admin true
/lp group admin permission set mrtoolbar.groups.admin true
```

Teammitglieder kommen dann einfach in ihre Gruppe: `/lp user Steve parent set staff`.

## Was die Gruppen dürfen

| | Support | Staff | Admin |
|---|---|---|---|
| **MRadmin** | Übersicht, Serverzustand und Health, Tickets komplett, Spieler ansehen, zu Spielern teleportieren, Inventar ansehen, Staff-Chat, Weboberfläche, Wartung ansehen und währenddessen joinen, Versionen und Web-Konsole ansehen, Strafen ansehen (LibertyBans) | + Spieler herholen, einfrieren, kicken, Admin-Modus, mit LibertyBans kicken, muten, verwarnen | + Wartung steuern, Ankündigungen, Web-Konten, Updates, Neustart, Reload, **Befehle in der Web-Konsole**, mit LibertyBans bannen und entbannen |
| **MRchunks** | Admin-Menü (alle Loader und ihr Status), zu Loadern teleportieren | + fremde Loader an/aus, Einladungen verwalten, Accounts verknüpfen und lösen | + löschen, umbenennen, Dummies, Lag-Sperren aufheben, Limits, Sperren, Webkarte, Reload |
| **MRpvp** | Admin-Menü, alle Runden ansehen, auffällige Runden prüfen und werten, zusehen, Spieler-Verwaltung | + Kits und Arenen bearbeiten, Duelle abbrechen und Sieger bestimmen, pausieren, sperren | + Statistik zurücksetzen, gesicherte Stände zurückgeben, Standard wiederherstellen, Reload |
| **MRsleep** | Admin-Menü, Status, Teleport, Bypass bis 30 Min, Sperre bis 1 Tag | + Bypass und Sperre bis 1 Tag, Blockaden aufheben, Cooldown zurücksetzen | + ohne Grenze, dauerhaft sperren, Reload |
| **MRtoolbar** | Admin-Menü, Gürtel anderer ansehen | + `/mrtoolbar debug` | + Reload |

Die Zeiten bei MRsleep stellst du unter `limits` in dessen `config.yml` ein. Die LibertyBans-Rechte bringen die MRadmin-Gruppen mit (Support: `libertybans.commands`, `libertybans.list.history|banlist|mutelist`; Staff: `libertybans.kick|mute|warn.do.target.uuid`, `mute|warn.undo.target.uuid`; Admin: `libertybans.ban.do|undo.target.uuid`) - sie gelten auch für die LibertyBans-Befehle im Spiel. Sperren in den Plugins (Duelle, `/nosleep`, Chunkloader) sind davon getrennt.

> [!WARNING]
> `mradmin.groups.admin` enthält das Ausführen von Befehlen in der Web-Konsole - so mächtig wie die Server-Konsole.
> Gib die Admin-Gruppe nur Leuten, denen du den Server anvertraust.

## In keiner Gruppe - bewusst einzeln
Diese Rechte verändern, wie ein Spieler behandelt wird, und gehören deshalb in keine Gruppe:

| Permission | Wofür |
|---|---|
| `mrchunks.exempt` | Komplett von Chunkloadern ausgeschlossen - vorhandene Loader werden gelöscht |
| `mrchunks.limit.<n>` · `active.<n>` · `dummies.<n>` (bzw. `.unlimited`) | Wie viele Chunkloader, aktive Loader und Dummies |
| `mrsleep.exempt` | Zählt nicht zur Schlafquote (Bots, Cam-Accounts) |
| `mrsleep.nosleep.nocooldown` | `/nosleep` ohne Cooldown |
| `mrpvp.bypass.commands` | Alle Befehle auch im Duell |
| Kit-Permissions aus `kits.yml` von MRpvp | Bestimmte Kits nur für bestimmte Spieler (z.B. VIP) |
| `mrtoolbar.slots.<n>` · `mrtoolbar.belt.<n>` | Anzahl Tool-Slots und Plätze pro Gürtel |

> [!WARNING]
> **Wildcards:** `*` (oder `mrchunks.*`, `mrsleep.*`) enthält in LuckPerms auch `mrchunks.exempt` und `mrsleep.exempt`.
> Wer Admins `*` gibt, setzt dort ausdrücklich `/lp group admin permission set mrchunks.exempt false` und
> `/lp group admin permission set mrsleep.exempt false` - sonst werden ihre Chunkloader gelöscht und sie zählen nicht
> zur Schlafquote.

## Anpassen

```text
# Einem Supporter zusätzlich Ankündigungen erlauben
/lp user Steve permission set mradmin.announce true

# Staff ohne Kicken
/lp group staff permission set mradmin.player.kick false

# VIPs: 10 Chunkloader und ein VIP-Kit
/lp group vip permission set mrchunks.limit.10 true
/lp group vip permission set mrpvp.kit.vip true      # wenn das Kit in kits.yml permission: mrpvp.kit.vip hat
```

## Umstieg von älteren Versionen
Bis zu den Versionen von Oktober 2026 hießen die Pakete anders (`mradmin.support`, `mradmin.staff`, `mradmin.admin`,
`mrchunks.admin`, `mrpvp.admin`, `mrsleep.admin`, `mrtoolbar.admin`). Die gibt es nicht mehr - nach dem Update die
Gruppen wie oben neu vergeben und die alten Einträge entfernen, z.B. `/lp group admin permission unset mrchunks.admin`.
Außerdem haben Spieler `/report`, `/duel`, `/nosleep` und die Versionsbefehle (`/<plugin> version`) nicht mehr automatisch: dafür die `.player`-Pakete vergeben.
