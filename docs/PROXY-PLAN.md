# Plan: Proxy-Betrieb mit Wartelobby

Stand: 2026-10-27, **zur Abnahme durch den User**. Die Umstellung eines konkreten Servers steht nicht hier, sondern in einer privaten Datei des Users.

## Ziel
Spieler bleiben bei Neustart, Absturz und Wartung verbunden. Statt eines Kicks warten sie in einer kleinen Lobby, auf Wunsch mit Minigames (z.B. MRpvp-Duelle), und kommen automatisch zurück, sobald der Hauptserver wieder offen ist. Der Hauptserver darf dabei sogar komplett abgeschaltet sein.

**Alles ist optional.** Ohne Proxy laufen alle Plugins genau wie bisher.

## Begriffe

```
Spieler ──► Proxy (Velocity, öffentlicher Port)
               ├─► main   = Hauptserver (Paper, nur intern erreichbar)
               └─► lobby  = Wartelobby (Paper, nur intern erreichbar)
            MySQL/MariaDB - gemeinsam für alle (LuckPerms, LibertyBans, MRadmin, MRpvp)
```

- `main` und `lobby` sind die Namen der Server in der `velocity.toml`.
- Im Alltag landet jeder auf main (`try = ["main", "lobby"]`), die Lobby sieht man nur, wenn main nicht offen ist.

## Entscheidungen des Users (2026-10-27)
1. Alles rund um den Proxy ist **komplett optional**.
2. Lobby ist **nur Warteraum** (Neustart, Absturz, Wartung). Minigames sind optional (nicht jeder hat MRpvp), weitere kommen später.
3. **Wartung:** Nur wer im MRadmin-Admin-Modus ist, bleibt auf main. Alle anderen - auch Team im normalen Spiel - gehen in die Lobby.
4. **Neustart:** Spieler im normalen Spiel gehen in die Lobby, Admins im Admin-Modus werden **getrennt** (der Admin-Modus bleibt gespeichert und geht beim nächsten Join weiter).
5. **Join während Wartung:** mit Bypass-Permission **und** Admin-Modus → main; ohne Admin-Modus → Lobby. Ist main aus → Lobby, auch im Admin-Modus.
6. Admins nutzen `/mra` auch in der Lobby wie gewohnt. `/mra mode` in der Lobby = auf main wechseln und dort den Admin-Modus einschalten (auch während Wartung). Kein eigener Knopf, keine neue Permission.
7. MRpvp-Duelle in der Lobby zählen in **dieselbe Statistik** wie auf main.
8. Im Proxy-Betrieb ist **MySQL/MariaDB Pflicht** (MRadmin, für die gemeinsame MRpvp-Statistik auch MRpvp).

## Abläufe

| Fall | Was passiert |
|---|---|
| Normaler Join | Velocity → main. |
| Neustart (Neustart-Knopf, `/mra announce restart`, Konsole `stop`, Start-Skript) | Countdown wie bisher. Am Ende fängt MRadmin auf main den Stopp ab: Spieler im normalen Spiel → Lobby, Admin-Modus → getrennt mit Hinweis. Erst wenn main leer ist (höchstens 10 s), fährt der Server herunter. |
| Wartung beginnt | Statt Kick: alle außerhalb des Admin-Modus → Lobby. |
| Join während Wartung | main lehnt ab (außer Bypass + Admin-Modus oder Wechsel-Eintrag aus der Lobby), Velocity versucht den nächsten Server → Lobby. |
| Join, während main aus ist | Velocity erreicht main nicht → Lobby. |
| Absturz / main-Rechner weg | Velocity schickt die Spieler bei Verbindungsabbruch in die Lobby (`failover-on-unexpected-server-disconnect`, `try`-Liste). Ob das auch beim „Server closed“-Kick eines abstürzenden Servers greift, wird im Testaufbau geprüft - sonst ein fertiges Fallback-Plugin auf dem Proxy. |
| In der Lobby | Bossbar mit dem Grund: „Server startet neu“, „Wartung: <Thema> bis 18:00“, „Server nicht erreichbar“. Pro Spieler in seiner Sprache. |
| main wieder offen | Lobby holt alle gestaffelt zurück (Standard 5 pro Sekunde), sobald main „bereit“ meldet und keine Wartung läuft. Wer gerade ein Lobby-Duell spielt, kommt nach der Runde. |
| `/mra mode` in der Lobby | Lobby legt in der Datenbank einen Wechsel-Eintrag an (30 s gültig, nur dieser Spieler) und schickt den Admin auf main. main lässt ihn durch und schaltet sofort den Admin-Modus ein. Ist main nicht erreichbar: Meldung, kein Wechsel. |

## Teil A - Plugins

### A1. MRadmin: Speicher für mehrere Server
Grund: Heute laden Tickets beim Start in den Speicher, jeder Server zählt Ticket-Nummern selbst und die Nummer allein ist der Tabellenschlüssel. Manuelle Wartungen und vorzeitig beendete Kuma-Wartungen kennt nur der Server, auf dem sie gesetzt wurden. Web-Konten liegen pro Server.

| Daten | Künftig mit MySQL |
|---|---|
| Tickets | gemeinsam: Nummer vergibt die Datenbank, Änderungen werden je Ticket geschrieben, andere Server laden Änderungen nach (Spalte „geändert“, Abfrage alle paar Sekunden) |
| Wartung (manuell, Kuma-Ausnahmen, angekündigter Stand) | gemeinsam - main führt, die Lobby liest nur |
| Web-Konten, Sitzungen, Entbannungsanträge, Push-Abos | gemeinsam |
| Proxy-Zustand (main bereit / stoppt, Wechsel-Einträge) | neue Tabelle, gemeinsam |
| Protokoll, Leistungsdaten | pro Server (Server-Spalte gibt es), Web zeigt sie je Server |
| Freeze, Admin-Modus-Zustand, Discord-Ankündigungsstand | pro Server |

- Tabellenversion 3 mit Migration. yaml/sqlite ohne Proxy unverändert.
- **Umzug sqlite → MySQL:** Heute übernimmt MRadmin beim ersten Start nur YAML-Dateien. Neu: Sind die MySQL-Tabellen leer und liegt eine `mradmin.db`, wird sie einmalig übernommen (wie bei YAML, Datei bleibt liegen). Wichtig: zuerst main umstellen, dann die Lobby.

### A2. MRadmin: Proxy-Betrieb
Neuer Config-Abschnitt (Standard aus):
```yaml
proxy:
  enabled: false
  role: main            # main | lobby
  servers:
    main: main          # Namen wie in der velocity.toml
    lobby: lobby
  # nur Rolle lobby:
  main-address: "10.0.0.2:25565"   # zum Anpingen von main
  return-per-second: 5
  display: BOSSBAR      # BOSSBAR | ACTIONBAR | NONE
  teleport-to-spawn: true
```
- Voraussetzung: `storage.type: mysql`, sonst bleibt der Proxy-Teil aus (Warnung in Konsole, `/mra status` und Web).
- Spieler werden über den BungeeCord-Kanal von Velocity verschoben (`Connect`) - auf dem Proxy ist dafür nur `bungee-plugin-message-channel = true` nötig (Standard).

**Rolle main**
- Stopp abfangen (Konsole/Spieler `stop` und `restart`, Neustart-Knopf, Countdown-Ende): verschieben bzw. Admin-Modus trennen, warten bis leer (max. 10 s), dann herunterfahren. Start-Skripte brauchen dafür keine Änderung.
- Wartungsbeginn: Lobby statt Kick (`maintenance.kick-on-start` gilt dann für „verschieben“).
- Join während Wartung: Prüfung wie in der Tabelle oben (Admin-Modus-Zustand bzw. Wechsel-Eintrag).
- Meldet alle 5 s in der Datenbank „bereit“, beim Stopp sofort „stoppt“.
- Discord-Ankündigungen nur von main.

**Rolle lobby**
- Wartung nur anzeigen (Kuma + gemeinsamer Wartungsstand), nie kicken, keine Discord-Posts, keine eigene Wartungs-MOTD.
- Prüft alle 2 s: main meldet „bereit“ (nicht älter als 15 s) **und** antwortet auf einen Ping **und** keine Wartung → zurückholen, gestaffelt.
- Beim Join: an den Spawn (abschaltbar), Bossbar mit Grund.
- `/mra mode`: Wechsel-Eintrag + verschieben (siehe Abläufe). Alle anderen `/mra`-Funktionen laufen normal.

**Befehle und Permissions**
- `/mra proxy` - Status (Rolle, main bereit?, Spieler in der Lobby, Prüfungen). Permission `mradmin.proxy.view`.
- `/mra proxy send <spieler|all> <main|lobby>` - von Hand verschieben. Permission `mradmin.proxy.send`.
- Beide in den bestehenden Paketen (`mradmin.staff` bzw. `mradmin.admin`).

**Prüfungen** (Konsole beim Start, `/mra proxy`, Web): Velocity-Forwarding in `paper-global.yml` aktiv, `online-mode=false`, MySQL verbunden, main von der Lobby erreichbar, Servernamen gesetzt.

**Web:** Dashboard-Kachel „Proxy“ (Rolle, Zustand von main, Spieler in der Lobby, Warnungen). Web-Konten sind gemeinsam, die Weboberfläche kann auf main und/oder der Lobby laufen (jede zeigt ihre Live-Daten, Tickets usw. sind gleich).

**Sonst:** Texte DE/EN (Bossbar, Verschieben, Trennung im Admin-Modus beim Neustart, Prüfungen), `config-version` + `ConfigUpdater`, `lang-version`, README (eigener Abschnitt + Anleitung `docs/PROXY.md` als Website-Tab), CHANGELOG. Keine Änderung der Modul-API nötig.

### A3. MRpvp: MySQL
- Neuer Abschnitt `storage:` (`type: sqlite | mysql`, Zugangsdaten, `server-id`, `table-prefix`), Standard sqlite wie bisher.
- Gemeinsam: Spiele (Nummer von der Datenbank, neue Spalte `server`), Statistik, Bewertungen, Protokoll der Wertungsänderungen.
- Pro Server: Arenen/Vorlagen, gespeicherte Spielerzustände (bleiben als Datei - wer auf main während eines Duells abstürzt, bekommt seine Sachen auf main zurück), laufende Partien.
- Statistik-Neuberechnung und Wertungsänderungen in Transaktionen (zwei Server schreiben gleichzeitig).
- Tagesgrenze pro Gegner und Anti-Farming zählen über beide Server (aus der gemeinsamen Datenbank).
- Umzug: Sind die MySQL-Tabellen leer und liegt eine `mrpvp.db`, wird sie einmalig übernommen. Zuerst main umstellen.
- Web: Verlauf/Statistik gemeinsam; Live-Partien sieht man in der Weboberfläche des Servers, auf dem sie laufen (Spalte „Server“ in der Liste).
- `config-version`, README, CHANGELOG.

### A4. Andere Plugins prüfen
- **MRtoolbar:** Handshake nach Serverwechsel (Lobby → main) im Test prüfen; die Mod muss in der Lobby ohne Plugin ruhig bleiben und auf main neu verbinden. Falls nötig: Mod setzt ihren Zustand beim Serverwechsel zurück (Protokoll unverändert, `COMPATIBILITY.md` prüfen).
- **MRchunks, MRsleep:** laufen nur auf main, keine Änderung. In der Lobby nicht installieren.
- **MRpvp in der Lobby:** Arena-Welt `mrpvp_arenas` liegt dort getrennt; Kits wie auf main.

### A5. Musterkonfiguration (`mradmin/docs/proxy/` + Anleitung `docs/PROXY.md`)
- `velocity.toml`-Auszug: Forwarding `modern`, `[servers]`, `try`, `failover-on-unexpected-server-disconnect`, `bungee-plugin-message-channel`.
- Paper main und lobby: `server.properties` (`online-mode=false`, `server-ip` intern), `paper-global.yml` (`proxies.velocity`), MRadmin-`proxy:` je Rolle, MRpvp-`storage:`.
- Lobby: kleine Sichtweite, Abenteuermodus, Gamerules (kein Tageszeit-/Wetterwechsel, kein Fallschaden), Spawn-Schutz.
- LuckPerms und LibertyBans auf Proxy + allen Servern mit gemeinsamer MySQL (LuckPerms `messaging-service: sql`).
- Xaero-Hinweis: Lobby weit weg von 0/0 bauen bzw. in eigener Welt (siehe Test).
- Firewall: nur der Proxy-Port öffentlich, Paper- und Datenbank-Ports nur intern.
- Reihenfolge der Umstellung und Rückweg.

## Teil B - Testaufbau und Tests (lokal)
1. Velocity + main + lobby auf dem Testrechner, MariaDB lokal.
2. Prüfen, bevor Code entsteht: Verschieben per BungeeCord-Kanal; was Velocity beim „Server closed“-Kick und bei Prozess-Abbruch macht; Join-Ablehnung → Lobby.
3. Nach jedem Schritt: Neustart, Wartung (Kuma + manuell), Absturz (Prozess beenden), `/mra mode` aus der Lobby, Tickets von beiden Servern, Lobby-Duell zählt auf main.
4. **Xaero:** Karte auf main aufdecken, Neustart über die Lobby, Karte vergleichen (Lobby weit draußen und als eigene Welt ausprobieren).
5. MRtoolbar-Mod: Wechsel Lobby → main, DE- und EN-Client.

## Reihenfolge der Umsetzung
1. Testaufbau + Vorab-Prüfungen (B1, B2).
2. A1 MRadmin-Speicher (inkl. Umzug sqlite → MySQL).
3. A2 MRadmin Rolle main.
4. A2 MRadmin Rolle lobby.
5. A3 MRpvp MySQL.
6. A4 MRtoolbar-Prüfung, ggf. Mod-Fix.
7. A5 Musterkonfiguration + Anleitung + Website-Tab.
8. Gesamttest (B3-B5), danach Release (Versionen legt der User fest).

## Später / optional
- MOTD am Proxy während Wartung (heute statisch in der `velocity.toml`; Alternativen: Maintenance-Plugin von kennytv oder ein kleines eigenes Velocity-Plugin).
- Staff-Chat und Ankündigungen über beide Server.
- Eine Weboberfläche mit Server-Umschalter für Live-Daten beider Server.
- Weitere Minigames für die Lobby.
