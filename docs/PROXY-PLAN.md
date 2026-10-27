# Plan: Proxy und Wartelobby

Stand: 2026-10-27, **Entwurf - noch nicht vom User abgenommen.**

## Ziel
Spieler bleiben bei Neustarts, Wartungen, Abstürzen und sogar bei abgeschaltetem Hauptserver (VPS 1) verbunden. Sie warten in einer kleinen Lobby mit Minigames und kommen automatisch zurück, sobald der Hauptserver wieder offen ist. Ohne Proxy ändert sich an MRadmin nichts (Funktion standardmäßig aus).

## Aufbau

```
Spieler ──► VPS 2: Velocity (öffentlicher Port, bisherige Adresse)
                 ├─► VPS 1: Paper "main"   (Hauptserver, Port nur für VPS 2 offen)
                 └─► VPS 2: Paper "lobby"  (Wartelobby, nur lokal erreichbar)
            VPS 2: MariaDB (LibertyBans, optional MRadmin-Speicher der Lobby)
```

- **Adresse bleibt:** Die Domain zeigt künftig auf VPS 2, der Proxy übernimmt den bisherigen Port. Spieler müssen nichts ändern, Xaero findet seine vorhandenen Karten (Ordner nach Serveradresse).
- **Komplett optional:** Ohne Proxy läuft alles wie bisher. Proxy, Lobby und `proxy:`-Abschnitt sind ein Zusatz für Server, die das wollen.
- **Verbindung VPS 2 → VPS 1:** Beide VPS bei Hetzner, Verbindung über das **interne Hetzner-Netz** (kein Tunnel nötig). Paper läuft hinter dem Proxy mit `online-mode=false` und Velocity-Forwarding (signiert mit geheimem Schlüssel), lauscht nur auf der internen IP (`server-ip`), zusätzlich Hetzner-Firewall ohne öffentlichen Paper-Port.
- **Gemeinsame Daten:** LuckPerms und LibertyBans laufen auf **MySQL/MariaDB**, gemeinsam für main, lobby und Proxy - Gruppen, Rechte und Bans sind überall gleich (bei LuckPerms ggf. mit `server`-Kontext main/lobby).
- **Normalbetrieb:** Velocity `try = ["main", "lobby"]` - wer joint, landet wie bisher direkt auf dem Hauptserver. Die Lobby sieht man nur, wenn der Hauptserver nicht da ist.
- **VPS 1 offline:** Neue Spieler landen gleich in der Lobby, die Serverliste zeigt die Wartung bzw. „Server startet neu“.
- **Wartung an VPS 2 selbst** (Proxy-Neustart) trennt weiterhin alle - selten und kurz.

## Abläufe

| Fall | Was passiert |
|---|---|
| Neustart (Knopf, `announce restart`, `mc.sh`) | Countdown wie heute, dann schickt MRadmin auf main alle in die Lobby und stoppt erst, wenn der Server leer ist. Lobby zeigt „Server startet neu“ und holt alle zurück, sobald main antwortet. |
| Wartung beginnt | Statt Kick: alle **im normalen Spiel** gehen in die Lobby - auch Teammitglieder. Wer im **MRadmin-Admin-Modus** ist, bleibt auf main. Lobby zeigt Thema und Ende aus Uptime Kuma (Bossbar, pro Spieler in seiner Sprache). |
| Join während Wartung | main lehnt wie heute ab, Velocity versucht den nächsten Server der `try`-Liste → Lobby. |
| Wartung endet | Lobby holt die Spieler gestaffelt zurück (z.B. 5 pro Sekunde). |
| Absturz / VPS 1 weg | Velocity leitet nicht von selbst um (Standard: trennen bzw. nur Meldung). Dafür ein fertiges Fallback-Plugin auf dem Proxy (Musterkonfiguration). Lobby holt zurück, sobald main wieder antwortet. |
| Admin will vorher rüber | Gegenstand/Befehl in der Lobby „Zum Hauptserver“ (nur mit Bypass). |

## Teil 1: MRadmin (optional, `proxy.enabled: false`)
Neuer Config-Abschnitt `proxy:`:
- `enabled`, `role: main | lobby`
- `servers.main`, `servers.lobby` - Namen wie in der `velocity.toml`
- Rolle lobby: `main-address` (interne Adresse zum Anpingen), `check-seconds`, `return-per-second`, Anzeige (Bossbar/Actionbar)

Rolle **main**:
- Neustart/Stopp über MRadmin: Spieler per BungeeCord-Kanal (`Connect`, versteht Velocity) in die Lobby, dann stoppen.
- Wartungsbeginn: Lobby statt Kick für alle außerhalb des Admin-Modus (bisherige `kick-on-start`-Logik bleibt ohne Proxy).
- Prüfungen (Web + `/mradmin status`): Velocity-Forwarding in `paper-global.yml` an, Spieler kommen über den Proxy, Lobby-Name gesetzt.

Rolle **lobby**:
- Pingt main regelmäßig an (Server-List-Ping), liest die Wartung aus Uptime Kuma (gleiche Einstellungen wie main).
- Zeigt den Grund des Wartens: Neustart, Wartung bis X, Server nicht erreichbar.
- Holt Spieler zurück, sobald main antwortet und keine Wartung läuft.
- Rest von MRadmin (Tickets, Staff-Chat, Web) läuft normal; mit MySQL teilen sich beide Server die Daten (Server-Spalte gibt es).

Dazu: Permission `mradmin.proxy.switch` (aus der Lobby zum Hauptserver während Wartung), Dashboard-Kachel „Proxy“, Texte DE/EN, README, CHANGELOG, `config-version`.

## Teil 2: Wartelobby
- Kleiner Paper-Server auf VPS 2 (ca. 1 GB, kleine Sichtweite), Abenteuermodus, eigene Spielerdaten (Inventar vom Hauptserver unberührt).
- **Xaero:** Lobby-Welt mit eigenem Dimensionsschlüssel (z.B. `minecraft:lobby`), damit Xaero sie getrennt speichert. Proxy-Plugins für Xaero-Welt-IDs nur nach Test - sie könnten vorhandene Karten einer neuen ID zuordnen (Karte wäre „weg“).
- **Minigames:** MRpvp für Duelle (eigene Statistik in der Lobby), dazu fertige Plugins (z.B. Parkour) mit Musterkonfiguration. Eigenes Plugin („MRgames“) erst, wenn etwas fehlt.

## Teil 3: Musterkonfiguration (`docs/proxy/` + Anleitung als Website-Tab)
- `velocity.toml`-Auszug (Ports, Forwarding, `try`-Liste, Server)
- Fallback-Plugin für Abstürze (Auswahl nach Test)
- Paper main + lobby: `server.properties`, `paper-global.yml`, MRadmin-`proxy:`-Abschnitt
- LuckPerms und LibertyBans auf Proxy + Servern mit gemeinsamer MySQL (MRadmin nutzt die LibertyBans-API auf main weiter, Entbannungs-Codes bleiben)
- Firewall-Hinweise (internes Netz, `server-ip`), Reihenfolge der Umstellung
- Start-Skripte bleiben Sache des Users (`mc.sh` auf VPS 1 unverändert, für VPS 2 ein eigenes Skript)

## Umsetzung in Schritten
1. Lokaler Testaufbau: Velocity + zwei Paper-Server auf dem Testrechner.
2. MRadmin Rolle main (Lobby statt Kick, Neustart über Lobby, Prüfungen).
3. MRadmin Rolle lobby (Anzeige, Zurückholen).
4. Musterkonfiguration + Anleitung, Website-Tab.
5. Test mit Xaero (Karte aufdecken, Neustart, vergleichen), MRtoolbar-Mod (Handshake nach Wechsel), DE/EN-Client.
6. User richtet VPS 2 ein, Umstellung der Domain.

## Entscheidungen des Users (2026-10-27)
- Alles rund um den Proxy ist **komplett optional**.
- Proxy + Lobby auf VPS 2, Hauptserver auf VPS 1, beide bei Hetzner über das interne Netz.
- LuckPerms und LibertyBans werden auf MySQL umgestellt (gemeinsam für alle Server).
- Lobby ist **nur Warteraum** (Neustart, Absturz, Wartung), mit MRpvp-Duellen zum Zeitvertreib.
- Bei Wartung bleibt nur, wer im MRadmin-Admin-Modus ist; alle anderen (auch Team im normalen Spiel) gehen in die Lobby.

## Offene Fragen an den User
1. Neustart: auch Admins im Admin-Modus in die Lobby schicken? (Vorschlag: ja - der Server geht ja aus, sonst würden sie getrennt.)
2. Join während Wartung: darf Team mit `mradmin.maintenance.bypass` weiter auf main joinen (um dort in den Admin-Modus zu gehen)? (Vorschlag: ja, Lobby-Rückweg über `mradmin.proxy.switch`.)
3. Weitere Minigames außer Duellen - jetzt oder später?
4. Verbinden sich die Spieler über eine Domain (für Xaero und die Umstellung wichtig)?
