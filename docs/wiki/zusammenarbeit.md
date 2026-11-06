# Zusammenarbeit mit anderen Plugins

MRadmin, MRsleep, MRchunks, MRtoolbar, MRpvp und MRwaypoints laufen jeweils **auch allein**. Sind sie zusammen installiert, erkennt MRadmin sie automatisch und passt Menüs und Web von selbst an:
- **Dashboard:** ein Knopf je Plugin mit Live-Infos.
- **Spieler-Menü:** die Infos des Spielers aus jedem Plugin.
- **Serverzustand:** auffällige und gesperrte Chunkloader (MRchunks), wartende Duell-Prüfungen (MRpvp).
- **Web:** eigene Seiten **Chunkloader**, **Schlaf**, **Duelle** und **Karte** (mit Wegpunkten) fürs Team, auf der Spielerseite (`/web`) die Bereiche **Chunkloader** und **Duelle** und die Ebenen auf der **Karte** für die Spieler selbst.
- **Plugins:** alle im Browser einstellen, aktualisieren und installieren; ihre Ereignisse im **Protokoll**.

## MRadmin als Verbindung
Ohne MRadmin bekommen die anderen Plugins voneinander nichts mit:

| Mit MRadmin | Braucht außerdem |
|---|---|
| Spieler im Duell zählen nicht zur Schlafquote | MRsleep + MRpvp |
| Die Werkzeugleiste pausiert im Duell, die Gürtel werden mitgesichert | MRtoolbar + MRpvp |
| In der Arena-Welt lassen sich keine Chunkloader setzen | MRchunks + MRpvp |
| Chunkloader und Wegpunkte auf der Karte der Spielerseite, die Arena-Welt nicht | MRchunks / MRwaypoints / MRpvp |
| Spieler im Admin-Modus zählen nicht zur Schlafquote, haben die Werkzeugleiste pausiert und können kein Duell starten | MRsleep / MRtoolbar / MRpvp |

Einrichten musst du dafür nichts. Eine Verbindung abschalten geht unter `integrations` (z.B. `mrsleep: false`). Die Konsole zeigt beim Start, welche Plugins verbunden sind. Für Entwickler: [Modul-API](/docs/MODULE-API.md).

**LibertyBans:** Ist es installiert, zeigt MRadmin Strafen im Spieler-Menü und im Web und ermöglicht Entbannungsanträge über die Weboberfläche.

## Mehrere Welten (Multiverse)
MRadmin arbeitet mit beliebig vielen Welten. Multiverse-Core und Multiverse-Inventories sind nicht nötig, werden aber berücksichtigt:

- **Admin-Modus und Multiverse-Core:** Multiverse setzt beim Weltwechsel den Spielmodus der Welt. Im Admin-Modus wird das abgewiesen, du bleibst in Creative bzw. Spectator. `/gamemode` funktioniert weiter.
- **Admin-Modus und Multiverse-Inventories:** Solange der Admin-Modus läuft, tauscht Multiverse-Inventories das Inventar nicht. Beim Beenden kommt dein eigenes Inventar zurück.

> [!WARNING]
> Lass `admin-mode.return-to-start` mit Multiverse-Inventories auf `true`. Sonst bleibst du nach dem Beenden in der anderen Welt, und dein Inventar landet in deren Gruppe.
