# Befehle

| Befehl | Was er tut | Permission |
|---|---|---|
| `/mradmin` (`/mra`) | Admin-Dashboard · `/mradmin help` zeigt alle Befehle | `mradmin.dashboard` |
| `/report [spieler]` (`/melden`, `/ticket`, `/support`) | Ticket-Menü: Spieler melden, Fehler melden, sonstige Anliegen, eigene Tickets | `mradmin.tickets.create` |
| `/web [devices\|logout]` (`/me`) | Link und 6-stelliger Code für die eigene Spielerseite · eigene Geräte · abmelden | `mradmin.web.self` |
| `/sc [nachricht]` | Staff-Chat (ohne Nachricht: an/aus) | `mradmin.staffchat` |
| `/mradmin mode [on\|off\|toggle] [spieler]` · `mode list` | Admin-Modus für dich · für andere · wer ist drin | `mradmin.mode` · `.mode.others` |
| `/mradmin announce [-d] [-q] <text\|vorlage> [werte]` | Ankündigung als Titel, Chat und Actionbar (`-d` auch in Discord, `-q` ohne Ton) | `mradmin.announce` |
| `/mradmin maintenance [status\|on\|schedule\|extend\|off\|refresh]` | Wartung ansehen, starten, planen, verlängern, beenden, Uptime Kuma neu abfragen | `mradmin.maintenance.view` · `.manage` |
| `/mradmin tickets` | Alle Tickets | `mradmin.tickets.view` |
| `/mradmin player <spieler>` · `tp <spieler>` | Spieler-Menü · zu einem Spieler teleportieren | `mradmin.player.view` · `.teleport` |
| `/mradmin status` | Serverzustand | `mradmin.status` |
| `/mradmin web [setup\|invite\|sessions\|logout\|remove]` | Web-Zugang einrichten, Team einladen, Sitzungen ansehen und beenden, Konten löschen | `mradmin.web` · `mradmin.web.manage` |
| `/mradmin mail test <adresse>` | Test-Mail über den eingestellten SMTP-Server | `mradmin.web.manage` |
| `/mradmin discord [test]` | Discord-Status · Testnachricht | `mradmin.admin.discord` |
| `/mradmin storage` | Speicher-Status (yaml, sqlite, mysql) | `mradmin.admin.storage` |
| `/mradmin map [setup]` | Link zur Karte der Spielerseite · für Admins Zustand von squaremap, squaremap neu einrichten | `mradmin.map` · `mradmin.map.admin` |
| `/mradmin restart [zeit\|now\|cancel]` · `stop [zeit\|now\|cancel]` | Server mit Countdown neu starten bzw. stoppen, Countdown abbrechen | `mradmin.server.restart` |
| `/mradmin starter [setup [modus]]` | Zustand des [Starters](starter.md), nächster geplanter Neustart, Paper-Update · Starter im Serverordner einrichten | `mradmin.server.restart` · `mradmin.server.setup` |
| `/mradmin reload` · `version` | Config und Sprachdateien neu laden · Version | `mradmin.admin.reload` · `mradmin.version` |

Einzelheiten: [Wartungsarbeiten](wartung.md), [Ankündigungen](ankuendigungen.md), [Dashboard & Admin-Modus](dashboard.md), Web-Befehle unter [Weboberfläche](/docs/WEBOBERFLAECHE.md#einrichten).
