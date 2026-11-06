# Schnellstart

1. **[MRadmin herunterladen](https://mrtechnology.marcelr.at/download/mradmin/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. Dabei entstehen `plugins/MRadmin/config.yml` und die Sprachdateien in `plugins/MRadmin/lang/`.
3. **Rechte geben** - es gibt fertige Gruppen (in allen MRtechnology-Plugins gleich aufgebaut):
   ```text
   /lp group default permission set mradmin.player true
   /lp group supporter permission set mradmin.groups.support true
   /lp group staff permission set mradmin.groups.staff true
   /lp group admin permission set mradmin.groups.admin true
   ```
4. Fertig: **`/mradmin`** öffnet das Dashboard, Spieler erstellen Tickets mit **`/report`**.

Alles andere ist optional: [Uptime Kuma](wartung.md#uptime-kuma) für geplante Wartungen, ein [Discord-Webhook](konfiguration.md#discord) für Logs, die [Weboberfläche](/docs/WEBOBERFLAECHE.md) fürs Handy, die [Spielerseite](spielerseite.md) für alle Spieler.

## Update
Die alte JAR durch die neue ersetzen und den Server neu starten. Die Config wird automatisch angepasst: Deine Werte bleiben, neue Optionen kommen dazu, die alte Datei wird als `config.v<N>.backup.yml` gesichert. Mit der Weboberfläche geht das auch per Klick - siehe [Plugins & Updates](plugins.md).

## Funktionen ein- und ausschalten
Alles, was du nicht brauchst, schaltest du unter `features` in der Config ab (`/mradmin reload` genügt):

```yaml
features:
  maintenance: true    # Wartungsmodus inkl. Uptime Kuma
  announce: true       # Ankündigungen
  admin-mode: true     # Admin-Modus und Menü-Item
  teleport: true       # zu Spielern teleportieren
  summon: true         # Spieler herholen
  inventory: true      # Inventar/Enderchest ansehen
  freeze: true         # Einfrieren
  server-status: true  # Serverzustand
  tickets: true        # Tickets & Reports (/report)
  staff-chat: true     # Staff-Chat (/sc)
```

Eine ausgeschaltete Funktion verschwindet komplett aus Befehlen, Menüs und Web. Ihre Daten (Tickets, geplante Wartungen) bleiben erhalten.
