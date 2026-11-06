# Konfiguration

Am einfachsten im Browser: Weboberfläche → **Plugins → MRadmin → Konfiguration** - jede Einstellung mit Erklärung, Speichern lädt MRadmin sofort neu (Permission `mradmin.config`). Alles steht außerdem kommentiert in `plugins/MRadmin/config.yml`, im selben Aufbau wie bei allen MRtechnology-Plugins; Änderungen von Hand übernimmst du mit `/mradmin reload`. Nur in der Datei: eigene Dashboard-Knöpfe (`admin-mode.menu.buttons`), `web.roles` / `web.players` und neue Ticket-Kategorien.

| Abschnitt | Wofür | Mehr dazu |
|---|---|---|
| `general` | `language`, `fallback-language`, `server-language` (Sprachen, pro Spieler automatisch) und `timezone` (Zeitzone aller angezeigten Zeiten, leer = die des Servers) | [Sprache](#sprache) |
| `features` · `integrations` · `libertybans` | Funktionen und Plugin-Verbindungen an/aus, Strafen-Schnellauswahl | [Schnellstart](schnellstart.md#funktionen-ein--und-ausschalten), [Zusammenarbeit](zusammenarbeit.md) |
| `maintenance` · `uptime-kuma` | Ablauf der Wartung, Anbindung an Uptime Kuma | [Wartungsarbeiten](wartung.md) |
| `announce` | Ankündigungen im Spiel | [Ankündigungen](ankuendigungen.md) |
| `admin-mode` | Admin-Modus, Menü-Item, eigene Dashboard-Knöpfe | [Dashboard & Admin-Modus](dashboard.md) |
| `tickets` | Kategorien, Cooldowns, Limits, wie lange geschlossene Tickets bleiben | [Tickets bearbeiten](tickets.md) |
| `health` | Verlauf und TPS-Alarm | |
| `versions` | Seite Plugins: wie oft geprüft wird, automatische Downloads, Vorabversionen, wie der Neustart-Knopf neu startet | [Plugins & Updates](plugins.md) |
| `storage` | Speicher: `yaml` (Standard), `sqlite` oder `mysql` | [Speicher](#speicher-yaml-sqlite-oder-mysql) |
| `web` · `mail` | Weboberfläche, Mails für Kontakt- und Entbannungsanfragen | [Weboberfläche](/docs/WEBOBERFLAECHE.md) |
| `player-web` | Spielerseite (`/web`): an/aus, Anmeldung per Code, Ranglisten, Neuigkeiten, eigene Statistik-Kachel | [Spielerseite](spielerseite.md) |
| `map` | Karte: squaremap automatisch installieren, aktualisieren und einrichten, Welten von Plugins ausblenden, `nether-roof`, `player-page` | [Karte](karte.md) |
| `logging` | Wie viele Tage das Protokoll aufgehoben wird (`keep-days`, 1-14) | |
| `discord` · `discord-announce` | Admin-Log per Webhook, Ankündigungen im Community-Discord | [Discord](#discord) |
| `updates` · `gui` | Update-Hinweise, Menüs | |

## Sprache
Alle Texte, Vorlagen und Discord-Nachrichten stehen in `plugins/MRadmin/lang/<sprache>.yml`. Neue Texte aus Updates werden automatisch ergänzt. Allgemeines dazu unter [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/).
- **Server-Sprache:** Discord-Logs und -Ankündigungen, die Serverliste während einer Wartung, Push-Benachrichtigungen, Mails und Notizen in Tickets haben keinen einzelnen Empfänger - sie kommen in `general.server-language` (Standard `en`). Auf einem deutschen Server also `server-language: de` setzen.
- Ankündigungen, Wartungs-Countdown, Bossbars und Titel sieht jeder Spieler in seiner Sprache. Die Weboberfläche richtet sich nach der Sprache des Browsers - oder nach der Sprache, die man dort umgeschaltet hat.

## Speicher: YAML, SQLite oder MySQL
| `storage.type` | Wo | Wofür |
|---|---|---|
| `yaml` (Standard) | Dateien im Plugin-Ordner | kleine Server |
| `sqlite` | `plugins/MRadmin/mradmin.db` | viele Tickets, die Weboberfläche |
| `mysql` | MySQL oder MariaDB | mehrere Server mit gemeinsamen Tickets |

Umstellen: `type` ändern und den Server neu starten - die bisherigen Daten werden übernommen. Die Datenbank-Treiber lädt Paper beim ersten Start selbst herunter. Den Zustand zeigt `/mradmin storage`.

## Discord
Für das **Admin-Log** einen Webhook anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), unter `discord` aktivieren und die URL eintragen. Welche Ereignisse gesendet werden (Tickets, Wartung, Admin-Modus, Web-Anmeldungen …), stellst du unter `discord.events` ein. Test: `/mradmin discord test`.

Die anderen MRtechnology-Plugins können ihre Logs über diesen Webhook schicken (`discord.use-mradmin-webhook` in ihrer Config). Alle Ereignisse stehen außerdem im **Protokoll** der Weboberfläche. Für Ankündigungen im öffentlichen Channel gibt es den getrennten Webhook `discord-announce` - siehe [Ankündigungen](ankuendigungen.md#im-community-discord).
