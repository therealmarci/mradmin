# Spielerseite (/web)

Neben dem Team-Bereich hat jeder **Spieler** seine eigene Seite in der Weboberfläche - eigene Tickets, Statistik, Neuigkeiten, die Karte und alles, was die anderen Plugins anbieten.

## Anmelden
Die Seite hat immer dieselbe Adresse (`<Adresse des Servers>/me`) - am besten als **Lesezeichen** speichern. Zum Anmelden fragt sie nach einem **6-stelligen Code**:

1. Im Spiel **`/web`** eingeben (auch `/me`).
2. Der Chat zeigt den Link zur Seite und den Code - ein Klick auf den Code kopiert ihn.
3. Code auf der Seite eingeben. Er gilt 2 Minuten und nur einmal.

Danach bleibt das Gerät **höchstens 14 Tage** angemeldet und wird schon nach **7 Tagen ohne Besuch** abgemeldet - dann fragt die Seite einfach wieder nach einem Code. `/web devices` zeigt, auf wie vielen Geräten du angemeldet bist, `/web logout` meldet alle ab. Einzelne Geräte gehen auch auf der Seite unter **Anmeldungen & Geräte** (Klick auf deinen Namen oben rechts), dort steht auch, wie lange die Anmeldung noch gilt.

**Ohne Spiel anmelden:** Unter **Anmeldungen & Geräte** kannst du dir eine eigene Anmeldung einrichten - ein **Passwort** (Anmeldung mit deinem Spielernamen oder deiner bestätigten E-Mail-Adresse), einen **Passkey** (Fingerabdruck, Face ID, Windows Hello) oder **Discord**. Dann kommst du auch vom Handy rein, ohne vorher `/web` einzugeben. Freiwillig dazu: **2FA** mit einer Authenticator-App. Hast du eine E-Mail-Adresse hinterlegt, hilft „Passwort vergessen?“ auf der Anmeldeseite.

## Was auf der Seite steht
Was es gibt, hängt davon ab, was auf dem Server installiert und eingeschaltet ist:

| Bereich | Was du siehst und tust |
|---|---|
| **Tickets** | eigene Tickets ansehen, neue öffnen, antworten, schließen - wie mit `/report` |
| **Neuigkeiten** | **Neuigkeiten** (die neueste groß, die anderen als Karten) und **Changelog** (Zeitleiste der Versionen mit Neu / Geändert / Behoben / Entfernt); aktive **Warnungen** stehen rot ganz oben. Auf der Startseite ein Karussell mit Warnungen, Wartung, Neustart, Hinweisen der Plugins, Ticket-Antworten und den neuesten Beiträgen. |
| **Meine Statistik** | Kachel auf der Startseite: eigene Spielzeit, abgebaute Blöcke, besiegte Mobs, Tode - führt zur Statistik-Seite |
| **Statistik** | **Persönlich** (jeder eigene Wert mit Platz, Medaille für die Top 3 und Vergleich zum Besten) und **Server** (Summen aller Spieler, Ranglisten mit Siegertreppchen, auch von den Plugins). Wer nicht in Ranglisten stehen will, trägt sich dort aus. |
| **Chunkloader** | mit MRchunks: Budget, jeder Loader als Karte mit An/Aus, Grenzwerte, Mitglieder, Einladen - Größe nur im Spiel |
| **Duelle** | mit MRpvp: eigene Duell-Statistik und letzte Runden, Duell-Ranglisten |
| **Karte** | Karte im Vollbild mit echtem Gelände (siehe unten) |

Die Seite gibt es auf Deutsch und Englisch (Umschalter oben). Jedes Plugin prüft bei jeder Aktion dieselben Rechte und Regeln wie im Spiel.

## Die Karte
- Suche nach Punkten, Ebenen gegliedert ein- und ausblenden, Chunkraster, Welt wählen, ziehen und zoomen (am Handy auch mit zwei Fingern). Ein Klick auf einen Punkt zeigt seine Einzelheiten.
- Bist du online, siehst du dich selbst (und je nach Server die anderen Spieler). Die Karte öffnet an deinem Standort und folgt dir auf Wunsch („Mir folgen“).
- Die Plugins bringen ihre Ebenen mit - z.B. alle Server-Wegpunkte (MRwaypoints) und deine Chunkloader (MRchunks).
- **Rechtsklick** (am Handy lange drücken) auf einen Punkt zeigt seine Aktionen (eigenen Wegpunkt bearbeiten oder löschen, Wegweiser im Spiel starten, Chunkloader an/aus), auf eine freie Stelle z.B. „Wegpunkt erstellen“.
- Alle Felder der Karte lassen sich am Griff verschieben und bleiben im Browser gespeichert.

## Für Admins: einschalten
Standardmäßig ist die Spielerseite aus.

1. Die [Weboberfläche](/docs/WEBOBERFLAECHE.md) muss laufen (`web.enabled`, `web.public-url`).
2. In der Config `player-web.enabled: true`, dann `/mradmin reload`.
3. Spieler brauchen `mradmin.web.self` - das steckt in `mradmin.player`.

| Einstellung | Wofür |
|---|---|
| `player-web.code-seconds` · `session-days` · `idle-days` | Gültigkeit des Codes (120 s) · längste Anmeldung (14 Tage) · Abmeldung ohne Besuch (7 Tage) |
| `player-web.news` | Neuigkeiten an/aus, Chat-Vorschau, Warnungen, Karussell (`seconds`, `slides`, `show-maintenance`, `show-restart`, `show-plugin-notices`, `show-tickets`) |
| `player-web.personal-stats` | Werte der Kachel „Meine Statistik“ (der erste groß, leer = keine Kachel) |
| `player-web.rankings` | Statistik-Seite an/aus und welche Ranglisten |
| `map.player-page` | Karte: Spieler auf der Karte, Chunkraster, Öffnen am Standort, „Mir folgen“ - siehe [Karte](karte.md) |

- Gesperrte Spieler (LibertyBans) kommen nicht hinein.
- **Für das Team:** Wer im Team-Bereich angemeldet ist, kommt über **Zur Spielerseite** im Menü hinter dem eigenen Namen oben rechts (oder auf dem eigenen Eintrag unter Spieler) ohne Code zur eigenen Spielerseite. Das braucht ebenfalls `mradmin.web.self` und steht im Protokoll.
- **Neuigkeiten, Changelogs und Warnungen** schreibt das Team unter **Kommunikation** - siehe [Weboberfläche](/docs/WEBOBERFLAECHE.md#kommunikation-mradminnews-mradminannounce).
