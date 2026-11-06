# Permissions

Jede Funktion hat eine eigene Permission - **im Spiel und im Web gleich**. Am einfachsten vergibst du die **Gruppen**. Sie sind in allen MRtechnology-Plugins gleich aufgebaut (`<plugin>.player` und `<plugin>.groups.support|staff|admin`), jede Gruppe enthält die darunter. Standardmäßig haben sie nur OPs.

> [!TIP]
> **Alle MRtechnology-Plugins auf einmal einrichten:** [Permissions aller Plugins](/docs/PERMISSIONS.md) - fertige LuckPerms-Gruppen zum Kopieren und was Support, Staff und Admin in jedem Plugin dürfen.

## Gruppen
| Gruppe | Enthält |
|---|---|
| `mradmin.player` | `/report` - Tickets erstellen und die eigenen ansehen, beantworten, schließen · `/web` - eigene Spielerseite · `/mradmin map` - Link zur Karte, andere Spieler darauf (`mradmin.map.players`) · `/mradmin version` |
| `mradmin.groups.support` | Spieler + Übersicht, Serverzustand und Health, Tickets komplett bearbeiten (auch Teleport zum Ticket), Spieler ansehen, zu Spielern teleportieren, Inventar ansehen, Staff-Chat, **Admin-Modus**, Weboberfläche, Wartung ansehen und währenddessen joinen, Seite Plugins ansehen (ohne „Jetzt prüfen“) |
| `mradmin.groups.staff` | Support + Spieler herholen, einfrieren und kicken, die Konsole im Protokoll mitlesen · mit LibertyBans: kicken, muten, verwarnen (und Mutes/Verwarnungen aufheben) |
| `mradmin.groups.admin` | Alles: Staff + Wartung steuern, Ankündigungen, Admin-Modus anderer, Web-Konten und -Protokoll, Reload, Speicher, Discord, Update-Hinweis, Updates herunterladen, Plugins im Browser einstellen, Server neu starten, Karte (squaremap) einrichten, **Befehle in der Web-Konsole** · mit LibertyBans: bannen und entbannen (auch Entbannungsanträge entscheiden) |

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
Standard ist `op`, außer anders angegeben.

| Bereich | Permissions |
|---|---|
| Allgemein | `mradmin.version` (`/mradmin version`) · `mradmin.web.self` (`/web`, eigene Spielerseite) |
| Dashboard | `mradmin.dashboard` · `mradmin.status` · `mradmin.status.health` |
| Tickets | `mradmin.tickets.create` (`/report`) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` |
| Wartung | `mradmin.maintenance.view` · `.manage` · `.bypass` · `.notify` |
| Ankündigungen, Modus, Chat | `mradmin.announce` · `mradmin.announce.templates` (Ankündigungs-Vorlagen im Web) · `mradmin.news` (Kommunikation im Web) · `mradmin.mode` · `mradmin.mode.others` · `mradmin.staffchat` |
| Spieler | `mradmin.player.view` · `.teleport` · `.summon` · `.inventory` · `.freeze` · `.kick` |
| Weboberfläche | `mradmin.web` · `mradmin.web.audit` · `mradmin.web.manage` · `mradmin.web.console.view` · `mradmin.web.console.execute` (Konsole im Protokoll mitlesen in `groups.staff`, Seite Konsole mit Befehlen in `groups.admin`) |
| Verwaltung | `mradmin.admin.reload` · `.storage` · `.discord` · `.update` |
| Karte | `mradmin.map` (`/mradmin map`, Link) · `mradmin.map.players` (andere Online-Spieler auf der Karte der Spielerseite) · `mradmin.map.admin` (Zustand von squaremap, `/mradmin map setup`, Schalter Karte / Spieler auf der Karte im Adminportal) |
| Plugins & Neustart (Web) | `mradmin.versions.view` (nur ansehen) · `mradmin.versions.install` (jetzt prüfen, herunterladen, installieren) · `mradmin.config` (Konfiguration im Browser) · `mradmin.server.restart` (neu starten) |

Die Permissions der anderen MRtechnology-Plugins gelten im Web genauso für deren Bereiche.

> [!TIP]
> Einzelnen Leuten gibst du zusätzlich einzelne Rechte, z.B. `/lp user <name> permission set mradmin.announce true`. Aus einer Gruppe nimmst du eins heraus mit z.B. `/lp group staff permission set mradmin.player.kick false`.
