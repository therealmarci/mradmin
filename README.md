# MRadmin

**Der Werkzeugkasten für dein Server-Team:** Wartungen mit Countdown, Tickets und Reports, Admin-Modus, Spieler-Werkzeuge, Ankündigungen, Staff-Chat - im Spiel über Menüs und vom Handy über eine eigene Weboberfläche. Außerdem verbindet MRadmin alle anderen MRtechnology-Plugins miteinander.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mradmin/)** · **[📖 Wiki](https://mrtechnology.marcelr.at/wiki/mradmin/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mradmin/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - LuckPerms empfohlen, Uptime Kuma, und LibertyBans optional (squaremap für die Karte installiert MRadmin selbst) |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | stabil (1.x) |

## Was kann MRadmin?
- **Wartungen** mit Countdown, auf Wunsch live aus Uptime Kuma.
- **Tickets & Reports** per `/report`, das Team antwortet im Spiel oder im Web.
- **Admin-Modus**, Spieler-Werkzeuge, Serverzustand, Staff-Chat, Ankündigungen.
- **Weboberfläche** fürs Team mit 2FA und Passkeys, **Spielerseite** (`/web`) mit Karte für alle Spieler.
- **Alle MRtechnology-Plugins im Browser** einstellen, aktualisieren und installieren - und die Verbindung zwischen ihnen.

## Schnellstart
1. **[MRadmin herunterladen](https://mrtechnology.marcelr.at/download/mradmin/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten.
3. Rechte geben:
   ```text
   /lp group default permission set mradmin.player true
   /lp group supporter permission set mradmin.groups.support true
   /lp group staff permission set mradmin.groups.staff true
   /lp group admin permission set mradmin.groups.admin true
   ```
4. Fertig: **`/mradmin`** öffnet das Dashboard, Spieler erstellen Tickets mit **`/report`**.

**Update:** alte JAR durch die neue ersetzen, Server neu starten - die Config passt sich selbst an.

## Ganze Anleitung im Wiki
| Für Spieler | Für Admins |
|---|---|
| [Tickets & Reports](https://mrtechnology.marcelr.at/wiki/mradmin/report/) · [Spielerseite](https://mrtechnology.marcelr.at/wiki/mradmin/spielerseite/) | [Dashboard & Admin-Modus](https://mrtechnology.marcelr.at/wiki/mradmin/dashboard/) · [Tickets](https://mrtechnology.marcelr.at/wiki/mradmin/tickets/) · [Wartung](https://mrtechnology.marcelr.at/wiki/mradmin/wartung/) · [Ankündigungen](https://mrtechnology.marcelr.at/wiki/mradmin/ankuendigungen/) |
| | [Weboberfläche](https://mrtechnology.marcelr.at/wiki/mradmin/web/) · [Plugins & Updates](https://mrtechnology.marcelr.at/wiki/mradmin/plugins/) · [Karte](https://mrtechnology.marcelr.at/wiki/mradmin/karte/) |
| | [Befehle](https://mrtechnology.marcelr.at/wiki/mradmin/befehle/) · [Permissions](https://mrtechnology.marcelr.at/wiki/mradmin/permissions/) · [Permissions aller Plugins](https://mrtechnology.marcelr.at/wiki/mradmin/alle-permissions/) · [Konfiguration](https://mrtechnology.marcelr.at/wiki/mradmin/konfiguration/) · [Häufige Fragen](https://mrtechnology.marcelr.at/wiki/mradmin/faq/) |

Die Seiten liegen als Markdown in [`docs/wiki/`](docs/wiki/) (Seitenbaum: [`docs/wiki/index.yml`](docs/wiki/index.yml)), dazu [`docs/WEBOBERFLAECHE.md`](docs/WEBOBERFLAECHE.md) und [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md).

## Lizenz
MRadmin steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/` (inkl. Unit-Tests und der Weboberfläche aus `web/`, Node lädt Gradle selbst). Zum Entwickeln der Oberfläche in `web/` `npm run dev` (leitet `/api` an ein laufendes MRadmin auf Port 8125 weiter). Modul-API für andere Plugins: [docs/MODULE-API.md](docs/MODULE-API.md), Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md).
