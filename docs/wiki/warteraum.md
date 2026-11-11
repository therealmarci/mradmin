# Warteraum (Proxy)

Neustarts und Wartungen sind für das Team wichtig, für Spieler aber eine Unterbrechung. Mit einem **Proxy** (Velocity) und einem kleinen zweiten Server mit **[MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** fliegt dabei niemand mehr raus: Die Spieler warten im Warteraum, sehen Grund und Restzeit und kommen von selbst zurück.

Der Warteraum ist **freiwillig** und standardmäßig aus. Ohne Proxy läuft MRadmin wie bisher.

## Was MRadmin dabei macht
- **Vor einem Neustart oder Stopp** (Countdown aus dem Browser, `/mradmin restart|stop`, geplante Neustarts) schickt MRadmin alle Spieler kurz vor dem Herunterfahren in den Warteraum (`waiting-room.on-restart`).
- **Bei Wartungsbeginn** wechseln Spieler ohne Wartungs-Bypass in den Warteraum, statt gekickt zu werden - auch wer während einer Wartung joint (`waiting-room.on-maintenance`).
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
Ist der Warteraum verbunden, steht **MRwaitroom** unter **Server → Plugins** - mit dem Hinweis „auf dem Warteraum-Server“:

- **Konfiguration:** wie bei jedem Plugin über **Konfiguration** (Permission `mradmin.config`). Beim Speichern holt sich der Warteraum die neue `config.yml` bei seiner nächsten Abfrage (wenige Sekunden), legt eine Sicherung an (`config.backup-remote.yml`) und lädt neu.
- **Updates:** „Aktualisieren“ (Permission `mradmin.versions.install`, oder automatisch nach `versions.auto-download`) bestellt die neue Version. Der Warteraum lädt sie **selbst** - nur aus den offiziellen Releases und nur mit passender Prüfsumme - und startet neu, sobald niemand wartet und dein Server bereit ist.
- Meldet sich der Warteraum länger nicht, steht dort „Meldet sich nicht“.

> [!NOTE]
> Die Verbindung geht immer vom Warteraum aus - MRadmin verbindet sich nie zu ihm. Die Erstinstallation machst du von Hand ([Anleitung von MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/)).

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

## Häufige Fragen
**Was passiert bei einem Absturz?** Velocity schickt die Spieler von selbst in den Warteraum, wenn die Verbindung zum Hauptserver unerwartet abbricht (`failover-on-unexpected-server-disconnect = true`, Standard). Der Warteraum zeigt dann „Server startet neu · seit …“.

**Und bei `/stop` in der Konsole?** Das ist kein Countdown von MRadmin - Paper wirft die Spieler raus. Nutze `/mradmin stop` bzw. `/mradmin restart`, dann wechseln sie vorher in den Warteraum.
