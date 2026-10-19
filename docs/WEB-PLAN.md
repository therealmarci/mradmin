# MRadmin Web – Plan

Stand: 2026-10-19. Ziel-Version: **MRadmin 0.2.0** (Phasen 1–3 als ein Release, Phase 1 darf vorher allein erscheinen).

## Stand
| Phase | Stand |
|---|---|
| 1 – Speicher-Schicht | ✅ fertig (YAML, SQLite, MySQL/MariaDB, Übernahme der YAML-Daten) |
| 2 – Web-Grundgerüst und Anmeldung | ✅ Webserver, Einrichtungslinks, Passwort + 2FA, Wiederherstellungscodes, Sitzungen, Rechte, Protokoll, Frontend-Grundgerüst · ✅ Passkeys |
| 3 – Erste Seiten | ✅ Übersicht (Schnappschuss 1×/s), Tickets mit Live-Aktualisierung (SSE) |

### Weitere Reihenfolge (Stand 2026-10-19)
1. ✅ Eigene Permission für jede Funktion (ingame und Web gleich, Pakete support/staff/admin; alte entfernt)
2. ✅ Recht & Datenschutz: Fußzeile mit Impressum und Datenschutz (Texte in MRadmin, Vorlage), Spielerköpfe ohne
   Drittanbieter (über MRadmin ausgeliefert und zwischengespeichert), Löschfristen für Protokoll und IPs
3. ✅ Health-Dashboard (`mradmin.status.health`): TPS/MSPT (Ø, 95 %, max), CPU, RAM, GC, Spieler, Chunks, Entities
   als Verlauf; Aufbewahrung in der Config `24h` / `7d` (Standard) / `30d`, mit SQLite/MySQL über Neustarts hinweg
4. ✅ MRchunks im Web (MRadminHook v4: `web(request, args)`) · 5. ✅ MRsleep im Web (dto.)
6. ✅ Spielerverwaltung: Liste, Spielerseite mit MRadmin-, MRsleep- und MRchunks-Teil (Hook-Anfragen `player`,
   `flags`, MRchunks `playerAction`) · 6b. ✅ LibertyBans als weiterer Teil der Spielerseite (dessen Permissions, API 1.1.4)
7. ✅ Web-Konsole: Live-Log (`mradmin.web.console.view`) und Befehle (`.execute`), beide nicht in Paketen,
   erneuter 2FA-Code vor dem Öffnen, alles im Protokoll und Discord-Log, Sperrliste für Befehle, Links/IPs im Log
   ausgeblendet
8. ✅ Passkeys (Yubico WebAuthn 2.10.0, in der JAR; Anmeldung ohne Name, Hinzufügen mit 2FA-Code)

Umsetzungsdetails, die vom ursprünglichen Plan abweichen:
- Web-Konten und Sitzungen sind (wie Wartung/Freeze) Dokumente im Speicher (`state`: `web-accounts`,
  `web-sessions`), keine eigenen Tabellen – wenige, kleine Datensätze. Das Protokoll hat eine eigene Tabelle
  `audit_log` (YAML: `web-audit.log`, eine JSON-Zeile pro Eintrag).
- Webserver: der in Java eingebaute `com.sun.net.httpserver` mit virtuellen Threads (keine Server-Bibliothek nötig).
- Passwörter: PBKDF2-HMAC-SHA256, 600.000 Runden (JDK). 2FA: TOTP nach RFC 6238 (eigene, getestete Umsetzung).
- Frontend: `web/` (Svelte 5 + Vite), Gradle lädt Node selbst (node-gradle) und packt das Ergebnis als `web/` in die JAR.

## Ziele
- MRadmin vom Handy, Tablet und PC bedienen: zuerst Tickets und Server-Status, später Wartung, Ankündigungen,
  MRchunks und MRsleep.
- Anmeldung ohne Ingame-Chat, trotzdem sicher.
- Speicher wahlweise YAML (wie bisher), SQLite oder MySQL/MariaDB – vorbereitet für ein späteres Netzwerk.
- Ausgeschaltet kostet es nichts; alle Plugins laufen weiter ohne Web.

**Bewusst nicht:** Konsole/beliebige Befehle im Browser, Web ohne laufenden Minecraft-Server, mehrere Server (später).

## Entscheidungen
| Frage | Entscheidung |
|---|---|
| Oberfläche | Modernes Frontend (Svelte, beim Gradle-Build mitgebaut), responsive für Handy, Tablet und PC |
| Discord-Login | später (nicht in Phase 2) |
| 2FA bei Passwort-Login | Pflicht |
| Standard-Speicher neuer Installationen | `yaml`; beim Einschalten des Webs wird SQLite empfohlen |
| Version | MRadmin 0.2.0 |

## Aufbau
```
Browser ──HTTPS──> Reverse Proxy (Domain, Zertifikat)
                         │ http://127.0.0.1:<port>
                         ▼
               MRadmin: Webserver (eigene Threads), Login, Sitzungen, Rechte, JSON-Schnittstelle
                         │  Schnappschüsse ◄─ Server-Thread (1×/s)
                         │  Aktionen ──────► Server-Thread (kurz)
                         ▼
               Speicher-Schicht (Hintergrund-Thread) ─► YAML | SQLite | MySQL/MariaDB
```
Datenbank-Treiber lädt Paper über `libraries` in der plugin.yml. Die Passkey-Bibliothek steckt mit festen Versionen in
der JAR (umbenannt) - ihre Versionsbereiche hätten Paper beim Start hunderte Dateien laden lassen.

Performance-Regel: **Nichts vom Web oder der Datenbank blockiert den Server-Thread.** Das Web liest nur
Schnappschüsse bzw. die Daten im Speicher, Datenbank-Zugriffe laufen im Hintergrund.

## Phase 1 – Speicher-Schicht
```yaml
storage:
  type: yaml            # yaml | sqlite | mysql
  mysql: { host: localhost, port: 3306, database: mradmin, username: "", password: "", pool-size: 4 }
  server-id: "main"     # Vorbereitung für mehrere Server
  table-prefix: "mradmin_"
```
| Daten | bisher | neu |
|---|---|---|
| Tickets, Nachrichten | `tickets.yml` (komplett neu geschrieben bei jeder Nachricht) | `tickets`, `ticket_messages` |
| Wartungen, eingefrorene Spieler, Status der Discord-Ankündigungen | eigene YAML-Dateien | `state` (Name → Inhalt) |
| Web-Konten, Passkeys, Sitzungen, Protokoll (Phase 2) | – | `web_accounts`, `web_credentials`, `web_sessions`, `audit_log` |
| Admin-Modus-Inventare | `admin-mode/*.yml` | bleiben Dateien |

- Daten liegen weiter im Speicher (das Spiel liest von dort), jede Änderung wird im Hintergrund gespeichert –
  bei SQL nur der geänderte Eintrag.
- Umstieg: `type` ändern, neu starten. Ist die Datenbank leer, werden die YAML-Daten übernommen
  (alte Dateien bleiben als `*.migrated.yml`).
- `schema_version` für spätere Anpassungen der Tabellen; jede Zeile mit `server`-Spalte.

## Phase 2 – Web-Grundgerüst und Anmeldung
```yaml
web:
  enabled: false
  bind: "127.0.0.1"
  port: 8125
  public-url: ""                 # z.B. https://admin.deinserver.de (Links, Passkeys)
  trusted-proxies: ["127.0.0.1"] # nur denen wird X-Forwarded-For geglaubt
  session-days: 30
  login: { passkeys: true, password: true }   # Passwort immer mit 2FA-App
  roles: {}                      # nur ohne LuckPerms
```
- **Konto anlegen:** `/mradmin web setup` (ingame) oder `mradmin web invite <spieler>` (Konsole/Panel) →
  Einrichtungslink (10 min, einmalig, an den Spieler gebunden) → Passkey und/oder Passwort + 2FA (QR) →
  8 Wiederherstellungscodes.
- **Anmelden:** Passkey oder Passwort + 2FA-Code.
- **Sitzungen:** Cookie `HttpOnly`, `Secure`, `SameSite=Strict`, gespeichert nur als Hash; Geräteliste im Web,
  einzeln abmeldbar; ingame `/mradmin web sessions`, `/mradmin web logout <spieler>`.
- **Rechte:** `mradmin.web` für den Zugang, sonst dieselben Permissions wie ingame (LuckPerms auch offline,
  60 s Cache); ohne LuckPerms `web.roles`. Verliert jemand `mradmin.web`, sind seine Sitzungen ungültig.
- **Schutz:** Begrenzung pro IP und Konto, Sperre nach Fehlversuchen, CSRF-Token, strenge Header, nur fest
  definierte Aktionen, Protokoll aller Web-Aktionen (optional Discord: `web-login`, `web-action`, neues Gerät).

## Phase 3 – Erste Seiten
Login/Einrichtung · Übersicht (TPS, MSPT, Spieler, Wartung, Tickets) · Tickets (Filter, Suche) · Ticket (Chat,
antworten, übernehmen, schließen, wieder öffnen; live) · Mein Konto (Geräte, Passkeys, 2FA, Codes) ·
Protokoll (Admins). Live-Aktualisierung per Server-Push, als App auf dem Startbildschirm installierbar.

## Spätere Phasen
4. Wartung und Ankündigungen · 5. MRchunks (Schnittstelle v4 mit Daten-Methoden) · 6. MRsleep ·
7. Spieler (Liste, Infos, einfrieren) · Discord-Login · Push-Benachrichtigungen.
