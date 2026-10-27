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
| Neustart (Knopf, `announce restart`, `mc.sh`) | Countdown wie heute, dann schickt MRadmin auf main alle **im normalen Spiel** in die Lobby. Wer im **Admin-Modus** ist, wird getrennt (der Admin-Modus bleibt gespeichert, `admin-mode.end-on-quit: false`). Erst dann stoppt der Server. Lobby zeigt „Server startet neu“ und holt alle zurück, sobald main antwortet. |
| Wartung beginnt | Statt Kick: alle **im normalen Spiel** gehen in die Lobby - auch Teammitglieder. Wer im **MRadmin-Admin-Modus** ist, bleibt auf main. Lobby zeigt Thema und Ende aus Uptime Kuma (Bossbar, pro Spieler in seiner Sprache). |
| Join während Wartung | Nur wer `mradmin.maintenance.bypass` hat **und** im Admin-Modus ist (gespeicherter Zustand), kommt auf main. Alle anderen - auch Team ohne Admin-Modus - lehnt main ab, Velocity versucht den nächsten Server der `try`-Liste → Lobby. |
| Join, während main aus ist | Velocity kann main nicht erreichen → Lobby, auch für Admins im Admin-Modus. |
| Wartung endet | Lobby holt die Spieler gestaffelt zurück (z.B. 5 pro Sekunde). |
| Absturz / VPS 1 weg | Velocity leitet nicht von selbst um (Standard: trennen bzw. nur Meldung). Dafür ein fertiges Fallback-Plugin auf dem Proxy (Musterkonfiguration). Lobby holt zurück, sobald main wieder antwortet. |
| Admin in der Lobby will auf main | MRadmin läuft auch in der Lobby, `/mra` funktioniert dort wie gewohnt. `/mra mode` in der Lobby schickt den Admin auf main und schaltet dort sofort den Admin-Modus ein (auch während einer Wartung). Ist main nicht erreichbar, gibt es eine Meldung. Signal an main über die gemeinsame Datenbank (siehe Teil 1). |

## Teil 1: MRadmin (optional, `proxy.enabled: false`)
Neuer Config-Abschnitt `proxy:`:
- `enabled`, `role: main | lobby`
- `servers.main`, `servers.lobby` - Namen wie in der `velocity.toml`
- Rolle lobby: `main-address` (interne Adresse zum Anpingen), `check-seconds`, `return-per-second`, Anzeige (Bossbar/Actionbar)

Rolle **main**:
- Neustart/Stopp über MRadmin: Spieler per BungeeCord-Kanal (`Connect`, versteht Velocity) in die Lobby, dann stoppen.
- Wartungsbeginn: Lobby statt Kick für alle außerhalb des Admin-Modus (bisherige `kick-on-start`-Logik bleibt ohne Proxy).
- Join während Wartung: zulassen nur mit Bypass **und** gespeichertem Admin-Modus oder mit gültigem Wechsel-Eintrag aus der Lobby, sonst ablehnen → Velocity schickt in die Lobby.
- **Wechsel aus der Lobby:** `/mra mode` in der Lobby legt in der gemeinsamen Datenbank einen Eintrag „Spieler X will in den Admin-Modus“ an (30 s gültig). main sieht ihn beim Verbinden, lässt durch und schaltet den Admin-Modus ein. (Minecraft-Cookies wären die Alternative ohne Datenbank - nicht nötig, weil MySQL ohnehin Pflicht ist.)
- Prüfungen (Web + `/mradmin status`): Velocity-Forwarding in `paper-global.yml` an, Spieler kommen über den Proxy, Lobby-Name gesetzt.

Rolle **lobby**:
- Pingt main regelmäßig an (Server-List-Ping), liest die Wartung aus Uptime Kuma (gleiche Einstellungen wie main).
- Zeigt den Grund des Wartens: Neustart, Wartung bis X, Server nicht erreichbar.
- Holt Spieler zurück, sobald main antwortet und keine Wartung läuft.
- Rest von MRadmin (Tickets, Staff-Chat, Web) läuft normal; mit MySQL teilen sich beide Server die Daten (Server-Spalte gibt es).

Dazu: Dashboard-Kachel „Proxy“, Texte DE/EN, README, CHANGELOG, `config-version`.

## Gemeinsame Daten (MySQL ist im Proxy-Betrieb Pflicht)
Ergebnis der Prüfung von MRadmin (2026-10-27): Ohne gemeinsame Datenbank geht es nicht sauber.
- **Wartung:** Neben Uptime Kuma gibt es manuelle Wartungen (`/mra maintenance`) und vorzeitig beendete Kuma-Wartungen. Beides steht nur im Speicher von main - die Lobby wüsste nichts davon und würde Spieler falsch zurückholen bzw. festhalten.
- **Tickets:** Werden beim Start in den Speicher geladen, die Nummer zählt jeder Server selbst, und die Ticket-Nummer allein ist der Schlüssel der Tabelle. Zwei Server würden sich Tickets gegenseitig überschreiben, ein `/report` aus der Lobby wäre auf main unsichtbar.
- **Web-Konten, Sitzungen, Entbannungsanträge, Push:** pro Server gespeichert - ohne gemeinsame Datenbank zwei getrennte Weboberflächen.

Deshalb: `proxy.enabled: true` setzt `storage.type: mysql` voraus (gleiche Datenbank, eigene `server-id` je Server), sonst bleibt der Proxy-Teil mit Warnung aus. Ohne Proxy ändert sich nichts (yaml/sqlite wie bisher).

Umbau in MRadmin (Speicherschicht, kein Neubau):
| Daten | Künftig |
|---|---|
| Tickets | gemeinsam: Nummer von der Datenbank vergeben, Änderungen je Ticket schreiben statt alles, andere Server laden Änderungen nach (alle paar Sekunden über eine Änderungszeit) |
| Wartung (manuell, Kuma-Ausnahmen) | gemeinsam, main führt, Lobby liest nur |
| Web-Konten, Sitzungen, Entbannungsanträge, Push | gemeinsam |
| Discord-Ankündigungen | nur main postet |
| Protokoll, Leistungsdaten | pro Server (wie bisher über die Server-Spalte), Web zeigt beide |
| Freeze, Admin-Modus-Zustand | pro Server |

**MRpvp:** Duelle in der Lobby zählen in **dieselbe Statistik** (User-Entscheidung). MRpvp bekommt dafür MySQL/MariaDB als zweite Speicherart neben SQLite (Spiel-IDs von der Datenbank, Statistik und Bewertungen gemeinsam; Arenen, Spielerzustände und laufende Partien bleiben pro Server). Eigener Schritt, ebenfalls optional.

## Teil 2: Wartelobby
- Kleiner Paper-Server auf VPS 2 (ca. 1 GB, kleine Sichtweite), Abenteuermodus, eigene Spielerdaten (Inventar vom Hauptserver unberührt).
- **Xaero:** Lobby-Welt mit eigenem Dimensionsschlüssel (z.B. `minecraft:lobby`), damit Xaero sie getrennt speichert. Proxy-Plugins für Xaero-Welt-IDs nur nach Test - sie könnten vorhandene Karten einer neuen ID zuordnen (Karte wäre „weg“).
- **Minigames sind optional:** Die Lobby funktioniert auch ohne (nicht jeder mit MRadmin hat MRpvp). Wer will, installiert MRpvp für Duelle (mit MySQL dieselbe Statistik wie auf main). Weitere Minigames kommen später.

## Teil 3: Musterkonfiguration (`docs/proxy/` + Anleitung als Website-Tab)
- `velocity.toml`-Auszug (Ports, Forwarding, `try`-Liste, Server)
- Fallback-Plugin für Abstürze (Auswahl nach Test)
- Paper main + lobby: `server.properties`, `paper-global.yml`, MRadmin-`proxy:`-Abschnitt
- LuckPerms und LibertyBans auf Proxy + Servern mit gemeinsamer MySQL (MRadmin nutzt die LibertyBans-API auf main weiter, Entbannungs-Codes bleiben)
- Firewall-Hinweise (internes Netz, `server-ip`), Reihenfolge der Umstellung
- Start-Skripte bleiben Sache des Users (`mc.sh` auf VPS 1 unverändert, für VPS 2 ein eigenes Skript)

## Umsetzung in Schritten
1. Lokaler Testaufbau: Velocity + zwei Paper-Server auf dem Testrechner.
2. MRadmin Speicher für mehrere Server (Tickets, Wartung, Web-Konten gemeinsam).
3. MRadmin Rolle main (Lobby statt Kick, Neustart über Lobby, Prüfungen).
4. MRadmin Rolle lobby (Anzeige, Zurückholen).
5. MRpvp mit MySQL.
6. Musterkonfiguration + Anleitung, Website-Tab.
7. Test mit Xaero (Karte aufdecken, Neustart, vergleichen), MRtoolbar-Mod (Handshake nach Wechsel), DE/EN-Client.
8. User richtet VPS 2 ein, Umstellung der Domain.

## Entscheidungen des Users (2026-10-27)
- Alles rund um den Proxy ist **komplett optional**.
- Proxy + Lobby auf VPS 2, Hauptserver auf VPS 1, beide bei Hetzner über das interne Netz.
- LuckPerms und LibertyBans werden auf MySQL umgestellt (gemeinsam für alle Server).
- Lobby ist **nur Warteraum** (Neustart, Absturz, Wartung). Minigames sind optional (z.B. MRpvp), weitere kommen später.
- Bei Wartung bleibt nur, wer im MRadmin-Admin-Modus ist; alle anderen (auch Team im normalen Spiel) gehen in die Lobby.
- Neustart: Admins im Admin-Modus werden getrennt, nicht in die Lobby geschickt.
- Join während Wartung: Admin mit Admin-Modus → main, ohne Admin-Modus → Lobby. Ist main aus → Lobby, auch im Admin-Modus.
- Die Domain stellt der User selbst auf VPS 2 um.
- Admins nutzen `/mra` auch in der Lobby; `/mra mode` dort = auf main in den Admin-Modus (kein eigener Knopf, keine eigene Permission).
- MRpvp-Duelle in der Lobby zählen in dieselbe Statistik wie auf main.

## Offene Fragen an den User
1. MySQL als Pflicht für den Proxy-Betrieb von MRadmin - einverstanden?
