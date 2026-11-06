# Häufige Fragen

### Ein Teammitglied wird bei der Wartung trotzdem gekickt.
Es braucht `mradmin.maintenance.bypass` (in `mradmin.groups.support` enthalten). Ohne LuckPerms prüft MRadmin die Rechte erst beim Join.

### Ich war im Admin-Modus, und der Server ist abgestürzt.
Nichts verloren - du bist nach dem Neustart einfach noch im Admin-Modus. `/mradmin mode off` stellt alles wieder her. Die Dateien in `plugins/MRadmin/admin-mode/` bitte nicht löschen.

### Ein Rechtsklick mit dem Menü-Item löst ein anderes Plugin aus.
Stell in `admin-mode.compass-material` ein Item ein, das kein anderes Plugin nutzt. Der Kompass ist zum Beispiel das Navigationswerkzeug von WorldEdit und teleportiert dich beim Rechtsklick - darum ist die Strukturleere (`STRUCTURE_VOID`) der Standard. Sie lässt sich nicht platzieren.

### Im Spectator-Modus kann ich in Menüs nichts anklicken.
Das ist normal: Minecraft verwirft Inventar-Klicks von Zuschauern, bevor ein Plugin sie sieht. Wechsle für das Menü kurz in Creative (`/gamemode creative`). Aus demselben Grund eignet sich `admin-mode.gamemode: SPECTATOR` nur, wenn du ohne Menüs arbeitest. Die Befehle und die Weboberfläche funktionieren auch dann.

### In der Weboberfläche stehen bei Items nur Kürzel statt Bildern.
MRadmin konnte die Bilder nicht von Mojang laden (die Konsole sagt beim Start, warum - meist kein Internet am Server) oder `web.item-icons` steht auf `false`. Nach dem Beheben `/mradmin reload` - geladen wird nur einmal pro Minecraft-Version.

### Die Karte fehlt auf der Spielerseite.
squaremap wird beim ersten Start installiert und ist erst nach dem nächsten Neustart aktiv. Danach rendert MRadmin die Welten im Hintergrund. Prüf außerdem `player-web.enabled`, `map.enabled` und die Schalter auf der Seite **Karte** im Adminportal - siehe [Karte](karte.md).

### Wo sehe ich, welche Plugins verbunden sind?
In der Konsole beim Start. Ist ein Plugin zu alt für die Verbindung, steht dort eine Warnung - dann alle MRtechnology-Plugins zusammen aktualisieren.

### Ich habe einen Fehler gefunden oder einen Wunsch.
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mradmin/)** - am besten mit der Ausgabe von `/mradmin version`.
