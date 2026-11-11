# Warteraum (Proxy)

Neustarts und Wartungen sind für das Team wichtig, für Spieler aber eine Unterbrechung. Mit einem **Proxy** (Velocity) und einem kleinen zweiten Server mit **[MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** fliegt dabei niemand mehr raus: Die Spieler warten im Warteraum, sehen Grund und Restzeit und kommen von selbst zurück.

Der Warteraum ist **freiwillig** und standardmäßig aus. Ohne Proxy läuft MRadmin wie bisher.

## Was MRadmin dabei macht
- **Vor einem Neustart oder Stopp** (Countdown aus dem Browser, `/mradmin restart|stop`, geplante Neustarts) schickt MRadmin alle Spieler kurz vor dem Herunterfahren in den Warteraum (`waiting-room.on-restart`).
- **Bei Wartungsbeginn** wechseln alle, die laut [Rechten](#rechte-im-warteraum) nicht bleiben dürfen, in den Warteraum, statt gekickt zu werden - auch wer während einer Wartung joint (`waiting-room.on-maintenance`).
- **Die Ankündigungen sagen es dazu:** Der Countdown zu Neustart und Stopp und die Wartungsankündigungen im Chat enden mit „Du musst nicht rausgehen.“ (Platzhalter `<stay>`, nur mit Warteraum) - so verlässt niemand vorher den Server.
- **Kein doppeltes Willkommen:** Wer aus dem Warteraum zurückkommt, gilt nicht als neu angekommen - Hinweise wie „Noch im Admin-Modus“, Team-Meldungen, Tickets und Update-Hinweise kommen nicht noch einmal.
- **Infos für den Warteraum:** Über eine eigene kleine Schnittstelle (nicht die Weboberfläche) erfährt der Warteraum Neustart-Countdown und Grund, laufende und geplante Wartung und aktive Warnungen aus **Neuigkeiten**. Er merkt sich die letzte Antwort, damit die Infos auch dann noch da sind, wenn dein Server aus ist.

> [!NOTE]
> Was der Warteraum anzeigt und wann er die Spieler zurückschickt, steht in der [Anleitung von MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/).

## Einrichten
1. **Proxy:** In der `velocity.toml` den Hauptserver und den Warteraum unter `[servers]` eintragen, `try = ["main", "waitroom"]` (Hauptserver zuerst) und `bungee-plugin-message-channel = true` setzen. Auf beiden Paper-Servern Velocity-Weiterleitung einschalten - Einzelheiten in der [Anleitung von MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/).
2. **MRadmin:** in der `config.yml` (oder im Browser unter **Server → Plugins → MRadmin → Konfiguration → Warteraum**):
   ```yaml
   waiting-room:
     enabled: true
     server: "waitroom"        # Name des Warteraums in der velocity.toml
     link:
       bind: "10.0.0.5"        # interne Adresse dieses Rechners
       port: 8126
       key: ""                 # wird beim ersten Start erzeugt
   ```
3. `/mradmin reload` - MRadmin erzeugt den Schlüssel (`waiting-room.link.key`) und meldet in der Konsole `Waiting room link listening on …`.
4. Adresse (`http://10.0.0.5:8126`) und Schlüssel in die `config.yml` von MRwaitroom übernehmen (`main.link-url`, `main.link-key`).

> [!WARNING]
> `link.bind` gehört auf die **interne** Adresse (LAN), nie auf eine öffentliche. Die Schnittstelle verrät zwar nur, was die Spieler im Warteraum ohnehin sehen, aber sie gehört nicht ins Internet. Falsche Schlüssel werden nach 10 Versuchen pro Minute abgewiesen.

## Warteraum aus MRadmin verwalten
Ist der Warteraum verbunden, steht **MRwaitroom** unter **Server → Plugins** - gekennzeichnet mit „Warteraum-Server“:

- **Konfiguration:** wie bei jedem Plugin über **Konfiguration** (Permission `mradmin.config`). Beim Speichern holt sich der Warteraum die neue `config.yml` bei seiner nächsten Abfrage (wenige Sekunden), legt eine Sicherung an (`config.backup-remote.yml`) und lädt neu.
- **Updates:** „Aktualisieren“ (Permission `mradmin.versions.install`, oder automatisch nach `versions.auto-download`) bestellt die neue Version. Der Warteraum lädt sie **selbst** - nur aus den offiziellen Releases und nur mit passender Prüfsumme - und startet neu, sobald niemand wartet und dein Server bereit ist.
- Meldet sich der Warteraum länger nicht, steht dort „Meldet sich nicht“.

> [!NOTE]
> Die Verbindung geht immer vom Warteraum aus - MRadmin verbindet sich nie zu ihm. Die Erstinstallation machst du von Hand ([Anleitung von MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/)).

## Warteraum-Server neu starten
Läuft der Warteraum mit dem Starter (`mrserver setup --role waitroom`, siehe [Starter](starter.md#warteraum-server-einrichten)), zeigt **Server → Übersicht** die Kachel **Warteraum-Server**: läuft er, seit wann, Paper-Build, wartende Spieler, nächster geplanter Neustart. **Neu starten** (Permission `mradmin.server.restart`) startet ihn neu, **sobald er darf**:

- dein Server läuft und ist bereit,
- **keine Wartung** läuft,
- **kein Neustart deines Servers** in den nächsten 15 Minuten ansteht,
- **niemand im Warteraum** wartet.

Ist etwas davon nicht erfüllt, steht der Grund in der Kachel. Geplante Neustarts stellt man in der Konfiguration von MRwaitroom ein (`server.restart-times`) - sie warten genauso, bis es passt.

**Solange der Warteraum neu startet**, lehnt MRadmin Neustart und Stopp deines Servers ab (Browser, `/mradmin restart|stop`, `mrserver`) - die Spieler hätten sonst keinen Platz zum Warten. Das dauert meist nur ein paar Sekunden.

## Rechte im Warteraum
Was das Team rund um den Warteraum darf, legst du in einer **Tabelle** fest: Konfiguration von MRadmin → **Warteraum** → **Rechte pro Rolle** (`waiting-room.rights`). Zeilen sind die Team-Rollen **Admin**, **Staff** und **Supporter** (es zählt die höchste von `mradmin.groups.admin`, `.staff`, `.support`), jede Zelle ist **Nein**, **Ja** oder **Nur im Admin-Modus**. Normale Spieler haben keines der Rechte.

| Recht | Bedeutung | Admin | Staff | Supporter |
|---|---|---|---|---|
| **Bei Wartung auf den Server** | Bleibt bei einer Wartung auf deinem Server bzw. kommt hinein - sonst wartet man im Warteraum | Nur im Admin-Modus | Nein | Nein |
| **Warteraum besuchen** | Knopf **In den Warteraum** im Admin-Menü (`/mradmin`), auch wenn nichts los ist | Nur im Admin-Modus | Nein | Nein |
| **Im Warteraum bleiben** | Wird nicht automatisch zurückgeschickt; zurück mit dem Item **Zurück zum Server** | Nur im Admin-Modus | Nein | Nein |
| **Admin-Modus im Warteraum** | Item **Admin-Modus** in der Hotbar des Warteraums | Ja | Nein | Nein |

Mit den Standardwerten ist ein **Admin ohne Admin-Modus ein normaler Spieler**: Bei einer Wartung landet er im Warteraum und wird danach wie alle zurückgeschickt. **Bei Neustarts wechseln immer alle** in den Warteraum - das ist kein Recht. Die Tabelle gilt nur mit eingeschaltetem Warteraum; ohne ihn bleibt bei der Wartung alles beim Wartungs-Bypass (`mradmin.maintenance.bypass`). Der Warteraum bekommt die Tabelle von MRadmin, dort stellst du nichts davon ein.

### Admin-Modus im ganzen Netzwerk
Der Admin-Modus gilt auf **beiden** Servern: Wer ihn im Warteraum einschaltet, kommt auf deinem Server im Admin-Modus an (Inventar gesichert wie gewohnt); wer ihn dort ausschaltet, bekommt beim Zurückkommen sein Inventar wieder. Umgekehrt ist, wer bei dir im Admin-Modus ist, auch im Warteraum im Admin-Modus. `admin-mode.end-on-quit` wirkt mit Warteraum nicht - der Wechsel in den Warteraum ist kein Verlassen.

> [!TIP]
> Wartung läuft und du willst auf den Server? Im Warteraum mit dem Item links in der Hotbar den Admin-Modus einschalten, dann mit **Zurück zum Server** (rechts) hinüber.

> [!TIP]
> Umgekehrt passt MRadmin auf: Startet der Warteraum gerade selbst neu, wenn der Countdown deines Servers abläuft, wartet MRadmin bis zu 2 Minuten auf ihn, bevor es herunterfährt.

## Einstellungen
| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `waiting-room.enabled` | `false` | Warteraum nutzen |
| `waiting-room.server` | `waitroom` | Name des Warteraums im Proxy |
| `waiting-room.on-restart` | `true` | Vor Neustart und Stopp alle in den Warteraum schicken |
| `waiting-room.on-maintenance` | `true` | Bei Wartung in den Warteraum statt Kick |
| `waiting-room.link.bind` | `127.0.0.1` | Adresse der Schnittstelle (leer = keine) |
| `waiting-room.link.port` | `8126` | Port der Schnittstelle |
| `waiting-room.link.key` | (erzeugt) | Gemeinsamer Schlüssel mit dem Warteraum |
| `waiting-room.rights` | siehe oben | Rechte pro Team-Rolle ([Rechte im Warteraum](#rechte-im-warteraum)) |

## Häufige Fragen
**Was passiert bei einem Absturz?** Velocity schickt die Spieler von selbst in den Warteraum, wenn die Verbindung zum Hauptserver unerwartet abbricht (`failover-on-unexpected-server-disconnect = true`, Standard). Der Warteraum zeigt dann „Server startet neu · seit …“.

**Und bei `/stop` in der Konsole?** Das ist kein Countdown von MRadmin - Paper wirft die Spieler raus. Nutze `/mradmin stop` bzw. `/mradmin restart`, dann wechseln sie vorher in den Warteraum.
