# MRadmin - Weboberfläche

MRadmin vom Handy, Tablet oder PC bedienen: Tickets beantworten, Spieler verwalten, Wartungen planen, den Serverzustand im Blick behalten - mit denselben Rechten wie im Spiel. Diese Seite beschreibt die Weboberfläche im Detail, die Einrichtung in Kurzform steht auch in der [README](../README.md#weboberfläche).

## Einrichten
1. In der Config `web.enabled: true` und `web.public-url` auf deine Adresse setzen (z.B. `https://admin.deinserver.de`), dann `/mradmin reload`. Für viele Tickets empfiehlt sich `storage.type: sqlite`.
2. **Reverse Proxy** einrichten: Domain mit HTTPS → `http://127.0.0.1:8125`. Der eingebaute Webserver lauscht nur lokal und hat selbst kein HTTPS.
3. **Konto anlegen:** im Spiel `/mradmin web setup` (Permission `mradmin.web`) → Link öffnen → Passwort festlegen, Authenticator-App per QR-Code verbinden → Wiederherstellungscodes sichern. Für andere: `/mradmin web invite <spieler>` (auch aus der Konsole).

| Befehl | Was er tut | Permission |
|---|---|---|
| `/mradmin web` | Status (läuft, Adresse, Konten, Geräte) | `mradmin.web` |
| `/mradmin web setup` | Einrichtungslink für dich | `mradmin.web` |
| `/mradmin web sessions [spieler]` | Angemeldete Geräte | `mradmin.web` (andere: `.manage`) |
| `/mradmin web logout [spieler]` | Alle Geräte abmelden | `mradmin.web` (andere: `.manage`) |
| `/mradmin web invite <spieler>` | Einrichtungslink für jemand anderen | `mradmin.web.manage` |
| `/mradmin web remove <spieler>` | Web-Konto löschen | `mradmin.web.manage` |

**Passwort vergessen oder Handy weg?** Einfach einen neuen Einrichtungslink holen. Der setzt Passwort und 2FA neu und meldet alle Geräte ab.

## Sicherheit
- Anmeldung immer mit Passwort **und** Code aus der Authenticator-App (oder einem Wiederherstellungscode).
  Passwörter werden mit PBKDF2 (600.000 Runden) gespeichert, Sitzungs-Cookies nur als Hash.
- **Passkeys** (Fingerabdruck, Face ID, Windows Hello, Passwort-Manager): unter **Konto** hinzufügen - zur Sicherheit
  mit einem frischen Code aus der App. Danach geht die Anmeldung ohne Name, Passwort und Code; die Prüfung des Geräts
  (Fingerabdruck, PIN) ist immer Pflicht und ersetzt beide Faktoren. Passkeys sind an die Adresse aus
  `web.public-url` gebunden (ohne sie nur `http://localhost` zum Testen). Gespeichert werden nur öffentliche
  Schlüssel. Wer sein Konto per Einrichtungslink zurücksetzt, verliert alle Passkeys (verlorenes Handy).
- **2FA ausschalten** geht unter **Konto**, sobald ein Passkey da ist. Dann meldet sich das Konto nur noch per Passkey
  an - mit Name und Passwort ist 2FA immer Pflicht. Schritte, die sonst einen frischen Code verlangen (Konsole
  entsperren, Passkey hinzufügen), bestätigt man dann mit einem Passkey. Der letzte Passkey lässt sich ohne 2FA nicht
  entfernen; 2FA kann jederzeit wieder eingeschaltet werden (neuer QR-Code, neue Wiederherstellungscodes).
  Abschalten: `web.login.passkeys: false`.
- **Mit Discord anmelden** (`web.login.discord`): Discord-Anwendung anlegen, Weiterleitung
  `<public-url>/api/discord/callback` eintragen, Client ID und Secret in die Config. Jeder verknüpft sein Discord
  unter **Konto → Sicherheit** (nur angemeldet, damit niemand ein fremdes Konto übernimmt). Danach Anmeldung mit
  einem Klick; zählt wie ein Passkey - 2FA lässt sich ausschalten, Bestätigungen (Konsole, 2FA, Passkey hinzufügen)
  gehen auch per Discord. Abgefragt wird nur `identify` (ID, Name, Avatar).
- Nach 5 Fehlversuchen ist das Konto 15 Minuten gesperrt, dazu höchstens 10 Anmeldeversuche pro Minute und IP.
  Damit das mit der echten IP der Besucher funktioniert, trägst du deinen Proxy unter `web.trusted-proxies` ein.
- Cookies: `HttpOnly`, `SameSite=Strict`, über HTTPS `Secure`. Änderungen nur mit eigenem Header und passender
  Herkunft (kein Auslösen über fremde Seiten), strenge Sicherheits-Header.
- Im Web gelten dieselben Rechte wie ingame. Mit LuckPerms auch für Offline-Spieler, ohne LuckPerms über
  `web.roles` und `web.players`. Wer `mradmin.web` verliert, ist sofort überall abgemeldet.
- Jede Anmeldung und Aktion landet im Protokoll (Web: **Protokoll**, mit `mradmin.web.audit`), Anmeldungen und
  Einrichtungen auch im Discord-Log (`web-login`, `web-setup`).
- Passwort vergessen oder Handy weg: einfach einen neuen Einrichtungslink holen. Der setzt Passwort und 2FA neu
  und meldet alle Geräte ab.

## Übersicht (Dashboard)
- Oben die **Lage**: Läuft etwas aus dem Ruder, steht hier jede Meldung mit Symbol und Wort, die dringendsten
  zuerst: niedrige TPS, hohe Tick-Zeit, voller Arbeitsspeicher, wenig Speicherplatz, hohe CPU-Last, hoher Ping,
  unbeantwortete und dir zugewiesene Tickets, gesperrte, auffällige oder pausierte Chunkloader, /nosleep-Blockaden,
  Wartung. Läuft
  alles rund, stehen dort die wichtigsten aktuellen Werte. Aktualisiert sich alle 2 Sekunden.
- Darunter Kacheln für Server, Spieler, Tickets, Chunkloader, Schlaf und Wartung mit Klick zur jeweiligen Seite.
- Jede Meldung und jede Kachel erscheint nur mit der Permission für ihren Bereich: Server und Spieler mit
  `mradmin.status`, CPU mit `mradmin.status.health`, Tickets mit `mradmin.tickets.view` (zugewiesene mit
  `mradmin.tickets.claim`), Chunkloader mit `mrchunks.admin.gui`, Schlaf mit `mrsleep.admin.gui`, Wartung mit `mradmin.maintenance.view`. Ist ein
  Plugin oder Feature nicht da, fehlt der Teil einfach.
- Das **Konto** (Geräte, Wiederherstellungscodes) öffnet ein Klick auf deinen Kopf bzw. Namen oben rechts.

## Health-Dashboard (`mradmin.status.health`)
- Wie der Health-Report von spark: TPS, MSPT (Durchschnitt, 95 %, Spitze), CPU (Server und System),
  Arbeitsspeicher, Garbage Collection, Spieler, Ø Ping, Chunks (mit MRchunks auch „davon Chunkloader“) und Entities
  als Verlauf über 1 Minute bis 30 Tage, dazu Welten, Systeminfos, Servergröße (alle 10 Minuten im Hintergrund
  gemessen), Größe der MRadmin-Datenbank und die 5 Chunks mit den meisten Entities.
- Gemessen wird jede Sekunde aus Werten, die Java und Paper ohnehin führen (kein Lag), die Seite aktualisiert sich
  live. Die letzte Stunde ist sekündlich da (ab 1 Minute wählbar), 24 Stunden im 10-Sekunden-Takt, ältere als
  5-Minuten-Mittel (Spitzen bleiben als Höchstwert erhalten, bei der Garbage Collection als eigene Linie
  „Spitze“). Aufbewahrung `health.history`: `24h`, `7d` oder `30d`, mit SQLite/MySQL bzw. `health.log` über
  Neustarts hinweg. Nach einem Neustart füllen die gespeicherten 5-Minuten-Werte die Zeit davor auf.
- Die Zeitachse zeigt immer genau den gewählten Zeitraum; wo keine Werte sind (Server aus), bleibt sie leer.
- Jedes Diagramm lässt sich mit **⤢** im Vollbild öffnen (schließen mit Esc, Klick daneben oder „Schließen“) und
  als Tabelle anzeigen.
- `health.alert`: Warnung an das Team (ingame) und ins Discord-Log, wenn die TPS länger unter einer Grenze liegt,
  und Entwarnung, sobald sie sich erholt hat.

## Spieler (`mradmin.player.view`)
- Alle, die schon auf dem Server waren: online zuerst, dann nach letztem Besuch, mit Suche, Filter (online,
  auffällig) und Markierungen (eingefroren, gemeldet, blockiert /nosleep, Chunkloader gesperrt ...). Keine IP-Adressen.
- Pro Spieler eine Seite: oben eine kurze Vorschau mit Ort, Spielmodus, Ping, Leben, erstem Besuch und Spielzeit;
  Einfrieren (`mradmin.player.freeze`) und Kicken mit Grund (`mradmin.player.kick`, Discord-Log `player-kick`).
- Darunter eine **Kachel pro Bereich** mit seinem Status auf einen Blick (z.B. „2 offen“, „3 / 5 Loader“, „im Duell
  #12“, „gesperrt bis …“). Ein Klick öffnet den Bereich mit allen Einzelheiten und Aktionen (Adresse
  `/players/<uuid>/<bereich>`, lässt sich also auch als Lesezeichen speichern). Bereiche: seine Tickets
  (`mradmin.tickets.view`) und ein Bereich pro Plugin, jeweils nur mit dessen Permissions - fehlt das Plugin, fehlt
  die Kachel:
  - **MRsleep:** /nosleep-Blockaden, Cooldown, Bypass, Sperre (`mrsleep.admin.gui`, `.clear`, `.bypass`, `.ban`).
  - **MRchunks:** seine Loader, Limit und Aktiv-Limit mit Herkunft (Standard, Permission, Admin), Limits setzen
    (`mrchunks.admin.limit`), für Chunkloader sperren (`mrchunks.admin.ban`), alle Loader löschen
    (`mrchunks.admin.manage`), Einladungen und mit ihm geteilte Loader (`mrchunks.admin.gui`). Verknüpfte Accounts
    ansehen, lösen und neu verknüpfen - per Spielername als Zweit- oder Haupt-Account, ohne Zustimmung der Spieler
    (`mrchunks.admin.manage`, wie `/mrchunks link`).
  - **MRpvp:** Duell-Statistik, die laufende Runde (Klick → Duellsteuerung) und die letzten Runden als Kacheln
    (`mrpvp.admin.games`), von Duellen sperren mit Grund und Schnellauswahl der Dauer (`mrpvp.admin.ban`, Dauern aus
    `gui.ban-durations` in der MRpvp-Config), gesicherte Stände zurückgeben (`mrpvp.admin.restore`), Statistik
    zurücksetzen (`mrpvp.admin.reset`).
  - **LibertyBans:** aktive Strafen und Verlauf (`libertybans.commands` + `libertybans.list.history`), bannen, muten,
    verwarnen und kicken (`libertybans.<typ>.do.target.uuid`), aufheben (`libertybans.<typ>.undo.target.uuid`). Die
    Dauer kommt aus der Schnellauswahl `libertybans.durations` in der MRadmin-Config; hat LibertyBans seine
    Dauer-Permissions eingeschaltet (`duration-permissions`), gelten sie auch hier. Ausgeführt wird über die API von
    LibertyBans - also mit dessen Kick-Bildschirm, Meldungen und Benachrichtigungen. In der Spielerliste erscheinen
    gebannte (`libertybans.list.banlist`) und gemutete (`libertybans.list.mutelist`) Spieler markiert.
- Spieler in Tickets, Chunkloadern und auf der Schlafseite verlinken direkt auf ihre Seite.

## Chunkloader (mit MRchunks)
Permissions von MRchunks: `mrchunks.admin.gui` ansehen, `mrchunks.admin.manage` handeln.

- Alle Chunkloader mit Status (OK, aus, auffällig, gesperrt, pausiert - immer mit Symbol und Wort), Probleme
  zuerst, Suche nach Name, Spieler, Welt oder Koordinaten, Server-Pause als Hinweis.
- Pro Loader: Ort, Sperrgrund, alle Messwerte des Lag-Schutzes mit Warn- und Sperrgrenze, Mitglieder. Aktionen:
  ein-/ausschalten, Sperre aufheben, löschen - der Owner bekommt wie ingame eine Nachricht, alles landet im
  Discord- und im Web-Protokoll.

## Schlaf (mit MRsleep)
Permissions: `mrsleep.admin.gui` ansehen, Blockaden aufheben mit `mrsleep.admin.clear`.

- Jede Welt mit Tag/Nacht, wie viele schlafen und wie viele nötig sind, dazu wer gerade mit /nosleep blockiert -
  einzeln oder alle aufheben. Alles zu einem einzelnen Spieler steht auf seiner Seite unter **Spieler**.

## Duelle (mit MRpvp)
Permissions von MRpvp: `mrpvp.admin.gui` ansehen, `.games` beendete Runden, `.stop`, `.pause`, `.ban`, `.reset`, `.restore`, `.review` handeln, `.kits` Kits bearbeiten.

- **Kits** (`.kits`, Chip „Kits →“): alle Kits als Karten (Symbol, Schnellleiste, Regeln), Reihenfolge mit ↑/↓, kopieren,
  neu anlegen. Im Kit-Editor das Inventar wie im Spiel - Klick auf einen Platz öffnet den Item-Editor, mit „Verschieben“
  tauschen zwei Plätze. Dazu ID (eine andere ID benennt um), Name und Beschreibung auf Deutsch und Englisch (gleich =
  für alle Sprachen), Symbol, Permission und die Regeln: kein Schaden, Treffer bis zum Sieg, Regeneration und Hunger
  (fest an/aus oder Spielerwahl), braucht Bau-Arena, nur bestimmte Arenen. Eine neue Arena erscheint beim nächsten
  Öffnen; Kits ohne Arena-Auswahl nutzen sie sofort. Gelöschte Arenen, auf die ein Kit noch festgelegt ist, stehen rot
  da und müssen abgehakt werden. Gespeichert wird erst mit „Speichern“, neue Runden bekommen das Kit sofort.

- Laufende Duelle live: Herzen beider Spieler, Zeit, Kit, Arena. Steuern: pausieren und fortsetzen (`.pause`),
  einen Sieger bestimmen oder abbrechen (`.stop`). Pro Runde eine Seite mit Mini-Karte (der
  Punkt leuchtet beim Treffen und Getroffenwerden), Inventaren und Kampf-Log; beendete Runden mit den gesicherten
  Inventaren (beim Betreten und am Ende, Enderchest, MRtoolbar-Gürtel) und einer Wiedergabe der Positionen.
- **Prüfen:** auffällige Runden, die noch nicht zählen - werten oder verwerfen. Gewertete Runden lassen sich auf
  ihrer Seite nachträglich aus der Wertung nehmen (mit optionalem Grund) und wieder werten (`.review`). Arenen mit ihren Kopien,
  Auffälligkeiten der letzten 24 Stunden. Alles zu einem einzelnen Spieler (Statistik, Runden, Sperre) steht auf
  seiner Seite unter **Spieler**.

## Items: Bilder, Namen und Editor
Überall, wo die Weboberfläche Gegenstände zeigt (Inventare bei Duellen, Kits), sehen sie aus wie im Spiel: das Bild
aus dem Inventar, Anzahl, Haltbarkeitsbalken, Verzauberungs-Glanz und beim Darüberfahren (am Handy: Antippen) der
Tooltip mit Name, Verzauberungen, Trank-Effekten und Beschreibung - in der Sprache des Browsers.

- **Woher die Bilder kommen:** MRadmin lädt beim ersten Start mit eingeschalteter Weboberfläche **einmal** den
  offiziellen Spiel-Client deiner Minecraft-Version von Mojang (rund 40 MB, mit Prüfsumme kontrolliert), zeichnet daraus
  alle Items und speichert nur das fertige Bild (rund 1,3 MB) und die Item-Namen unter `plugins/MRadmin/items/<version>/`.
  Der Client selbst wird nicht gespeichert. In der Plugin-JAR steckt nichts davon - Mojang erlaubt das Weitergeben nicht.
- **Neue Minecraft-Version:** Die Bilder werden für die neue Version einmal neu geladen, neue Items sind dann dabei.
- **Ohne Internet oder mit `web.item-icons: false`:** Items zeigen ein Kürzel statt eines Bildes, alles andere geht.
- **Item-Editor** (z.B. im Kit-Editor von MRpvp): Item suchen (Name oder ID, mit Bild), Anzahl, eigener Name und
  Beschreibung (MiniMessage: Farben und Stil wie `<red>`, `<#ff8800>`, `<b>`, `<gradient:red:gold>`),
  Verzauberungen (passende zuerst, auf Wunsch alle, Stufe 1-255, Hinweis bei Kombinationen, die es im Spiel nicht
  gibt), Glanz an/aus, unzerstörbar, Haltbarkeit, Trank (Grund-Trank, Farbe, eigene Effekte mit Stufe und Dauer),
  Farbe bei Lederrüstung, Rüstungsbesatz und Flugdauer bei Raketen. Rechts steht live der Tooltip. Beim Übernehmen
  baut MRadmin das echte Item und prüft alle Werte; was der Editor nicht anzeigt, bleibt am Item erhalten.

## Wartung & Ankündigungen
`mradmin.maintenance.view` ansehen, `.manage` ändern, `mradmin.announce` ankündigen.

- **Mit Uptime Kuma** (`uptime-kuma` eingerichtet): Die Wartungen werden direkt in Kuma verwaltet - Liste der
  Wartungen dieses Servers mit Status (läuft, geplant, pausiert, vorbei), neue anlegen (jetzt bis zum Beenden, jetzt
  mit Dauer oder geplant), verlängern, pausieren/fortsetzen, löschen. Neue Wartungen hängt MRadmin an
  `uptime-kuma.monitor-ids`, damit sie für diesen Server gelten (und auf deren Status-Seiten erscheinen); geändert
  werden können nur Wartungen dieses Servers. Wiederkehrende Wartungen werden angezeigt, bearbeitet in Kuma.
- **Ohne Uptime Kuma**: die eigene Wartung von MRadmin wie `/mradmin maintenance` - jetzt starten oder mit Countdown
  planen (Dauer oder offenes Ende, Titel), verlängern, beenden bzw. absagen.
- Wie `/mradmin announce`: Vorlagen aus der Sprachdatei (mit Werten) oder eigener Text („Titel | Untertitel“), auf
  Wunsch auch in den Community-Discord. Alles landet im Web-Protokoll.

## Versionen (`mradmin.versions.view`)
- Eine Kachel pro MRtechnology-Plugin: installierte und neueste Version (für die eigene Minecraft-Version), Status
  (aktuell / Update verfügbar / wartet auf Neustart), was neu ist, Links zur Website und zu GitHub; bei MRtoolbar
  auch die neueste Version des Client-Mods (nur zur Info, den installieren die Spieler selbst).
- Erkannt werden die Plugins an der `build-info.properties` in ihrer JAR - es braucht dafür keine bestimmte Version
  der anderen Plugins. Geprüft wird alle `versions.check-interval-hours` und mit **Jetzt prüfen** (höchstens einmal pro
  Minute, GitHub erlaubt ohne Anmeldung 60 Anfragen pro Stunde).
- **Herunterladen** (`mradmin.versions.install`): nur aus dem Release des eigenen GitHub-Repos, nur mit passender
  SHA-256-Prüfsumme und nur, wenn die Datei wirklich dieses Plugin enthält. Sie wird unter dem Namen der installierten
  JAR in `plugins/update/` abgelegt; Paper tauscht sie beim nächsten Start aus. Der Dateiname bleibt deshalb der alte, nur
  der Inhalt ist neu. `versions.auto-download`
  (`off`, `patch`, `minor`, `all`) lädt Updates von selbst.
- **Server neu starten** (`mradmin.server.restart`): nur, wenn `versions.restart-method` eingestellt ist (Standard
  `none` - ein Server kann sich nicht selbst wieder starten). Countdown aus `versions.restart-delays`, angekündigt mit
  den Vorlagen `restart` und `restart-now`, abbrechbar. Danach stoppt der Server (`stop` - ein Start-Skript mit
  Schleife, ein Wächter oder das Panel startet ihn wieder) oder startet über das `restart-script` der `spigot.yml` neu
  (`script`). Ohne Knopf zeigt die Seite nach dem Herunterladen „Neustart nötig“. Beispiel-Skripte: README.
- Auf der Übersicht zeigt eine Kachel, ob Updates warten oder ein Neustart nötig ist.

## Push-Benachrichtigungen
Unter **Konto → Benachrichtigungen**.

- Meldungen direkt aufs Gerät, auch bei geschlossener Seite: neue Tickets, Nachrichten in meinen Tickets, alle
  Ticket-Nachrichten, gesperrte Chunkloader, Lag-Alarm, Wartungen. Jeder legt selbst fest, welche er bekommt - und
  bekommt nur, wofür er die Rechte hat.
- Pro Gerät einschaltbar (mehrere Geräte möglich, „Test senden“). Ein Gerät, das abgemeldet wird, bekommt nichts mehr.
- Ohne Drittanbieter: Standard-Web-Push (VAPID, verschlüsselt - der Push-Dienst des Browsers kann nichts mitlesen).
  Auf iPhone/iPad nur als installierte App (Teilen → „Zum Home-Bildschirm“) und über HTTPS.

## Konsole
`mradmin.web.console.view` ansehen, `mradmin.web.console.execute` Befehle ausführen - beide in **keinem** Paket, also nur gezielt vergeben.

- Die Server-Konsole live (aus `logs/latest.log`, auch Minecrafts eigene Ausgaben), mit Filter, Warnungen und Fehler
  farbig. Links und IP-Adressen sind ausgeblendet (`web.console.hide-links` / `hide-ips`).
- Vor dem Öffnen noch einmal der Code aus der Authenticator-App (keine Wiederherstellungscodes), dann
  `web.console.unlock-minutes` lang offen (pro Gerät). 5 falsche Codes sperren die Konsole 15 Minuten.
- Befehle laufen als Konsole. `web.console.blocked` sperrt Befehle komplett - auch mit Namespace
  (`minecraft:op`) und versteckt in `execute ... run`. Jeder Befehl landet im Web-Protokoll und im Discord-Log
  (`web-console`), gesperrte Versuche im Protokoll.

## Entbannungsanträge & Kontaktformular
Zwei öffentliche Seiten, ohne Anmeldung, beide landen als Tickets beim Team (`web.requests` in der Config).

- **Entbannung** (`<public-url>/entbannung`, braucht LibertyBans): Wer gebannt ist und joinen will, sieht in der
  Ban-Meldung die Adresse und einen **persönlichen Code** (z. B. `1-K7F2QX`). Nur mit Name und Code sieht man den
  Bangrund und kann einen Antrag stellen, so kann niemand für andere Anträge stellen. Der Code wird nicht gespeichert,
  sondern aus Spieler, Ban und einem Server-Geheimnis berechnet.
  - **Ein Antrag pro Ban.** Im Ticket stehen die Stellungnahme und die Ban-Details. Das Team antwortet wie gewohnt
    und entscheidet mit **Entbannen** (hebt den Ban in LibertyBans auf, braucht `libertybans.ban.undo.target.uuid`)
    oder **Ablehnen**. Ein normales Schließen zählt als Ablehnung.
  - Nach einer Ablehnung ist **einmal Einspruch** möglich, dann öffnet sich das Ticket wieder. Danach ist die
    Entscheidung endgültig.
  - Entbannungsanträge sind auch **ingame** im Ticket-Menü sichtbar.
- **Kontakt** (`<public-url>/kontakt`, verlinkt unter dem Impressum und in der Fußzeile): Name, Mail-Adresse, Betreff,
  Nachricht. Diese Anfragen gibt es **nur im Web**, ingame tauchen sie nie auf.
- Nach dem Absenden bekommt man einen **privaten Statuslink** (`/anfrage/...`): Stand, Verlauf, antworten,
  Einspruch.
- **Mails** (optional, Abschnitt `mail`): Eingangsbestätigung mit Statuslink, Antworten des Teams und die
  Entscheidung. Testen mit `/mradmin mail test <adresse>`. Ohne Mailversand sieht man Antworten über den Statuslink,
  und bei Kontaktanfragen öffnet ein Knopf im Ticket das eigene Mailprogramm.
- **Spamschutz ohne externe Dienste:** unsichtbares Lockfeld, Mindest-Ausfüllzeit, eine kleine Rechenaufgabe im
  Browser (Proof of Work, ca. 1 s), Limits pro IP und Minute/Stunde, Obergrenze offener Kontaktanfragen.

## Datenschutz & Impressum
- Jede Seite (auch die Anmeldung) hat eine Fußzeile mit **Impressum** und **Datenschutz**. Beim ersten Start legt
  MRadmin Vorlagen in `plugins/MRadmin/web/impressum.md` und `datenschutz.md` an. Die Datenschutz-Vorlage beschreibt,
  was die Oberfläche verarbeitet. Bitte ausfüllen und prüfen (keine Rechtsberatung). Alternativ eigene Seiten unter
  `web.legal` verlinken.
- Keine Drittanbieter: Auch die Spielerköpfe erzeugt MRadmin selbst aus den Skins, der Browser lädt nichts von
  fremden Diensten. Nur ein technisch notwendiges Cookie, kein Tracking.
- Das Protokoll wird nach `web.privacy.audit-retention` (Standard 90 Tage) gelöscht. Mit `web.privacy.mask-ip` werden
  IP-Adressen nur gekürzt gespeichert.
