# Datenschutzerklärung

> **Vorlage von MRadmin – bitte ausfüllen und prüfen.** Sie beschreibt, was die Weboberfläche von MRadmin technisch
> verarbeitet. Alles in [eckigen Klammern] ersetzen und an deinen Server anpassen (z.B. weitere Dienste, Hoster,
> andere Speicherdauern aus der Config). Das ist keine Rechtsberatung. Diesen Kasten danach löschen.
> Datei: `plugins/MRadmin/web/datenschutz.md` – Änderungen gelten sofort.

## Verantwortlicher
[Name]
[Anschrift]
E-Mail: [admin@deinserver.de]

## Wofür diese Seite da ist
Die Weboberfläche dient dem Team des Minecraft-Servers [Servername] zur Verwaltung (z.B. Tickets, Serverzustand).
Nutzen kann sie nur, wer vom Team dafür freigeschaltet wurde.

## Welche Daten verarbeitet werden

**Beim Aufruf der Seite (Server-Protokoll):** IP-Adresse, Zeitpunkt, aufgerufene Adresse, Browser-Kennung. Diese Daten
verarbeitet [der Hoster / Reverse Proxy, z.B. nginx] zur Auslieferung und Absicherung der Seite.

**Konto:** Minecraft-Name und UUID, Passwort (nur als nicht umkehrbarer Hash gespeichert), der Schlüssel für die
Zwei-Faktor-Anmeldung, Wiederherstellungscodes (nur als Hash), Zeitpunkt der Einrichtung und der letzten Anmeldung.

**Angemeldete Geräte:** für jedes angemeldete Gerät die IP-Adresse, die Browser-Kennung und die Zeitpunkte der
Anmeldung und der letzten Nutzung – damit du deine Geräte sehen und abmelden kannst.

**Protokoll:** Anmeldungen (auch fehlgeschlagene) und Aktionen in der Weboberfläche mit Zeitpunkt, Konto und
IP-Adresse – zum Schutz vor Missbrauch und zur Nachvollziehbarkeit von Änderungen. [Optional: IP-Adressen werden nur
gekürzt gespeichert.]

**Inhalte:** Tickets, Nachrichten und weitere Daten des Minecraft-Servers, die du in der Oberfläche ansiehst oder
bearbeitest.

[Falls Discord-Logs aktiv sind: Anmeldungen und Einrichtungen werden zusätzlich als Nachricht in einen internen
Discord-Kanal des Teams geschickt (Name, Gerät, IP-Adresse). Anbieter: Discord Inc., USA.]

## Cookies
Es wird nur ein technisch notwendiges Cookie gesetzt (`mradmin_session`), das dich nach der Anmeldung angemeldet
hält. Es gibt kein Tracking, keine Werbung und keine Analyse-Werkzeuge.

## Spielerköpfe
Spielerköpfe werden vom Minecraft-Server selbst aus den Skins der Spieler erzeugt. Dein Browser lädt dabei keine
Inhalte von fremden Diensten.

## Rechtsgrundlage
[Art. 6 Abs. 1 lit. f DSGVO – berechtigtes Interesse an einer sicheren Verwaltung des Servers; ggf. lit. b, wenn
eine Vereinbarung über die Mitarbeit im Team besteht.]

## Speicherdauer
- Angemeldete Geräte: bis zur Abmeldung, höchstens [30] Tage
- Protokoll: [90] Tage, danach automatisch gelöscht
- Konto: bis es gelöscht wird (z.B. beim Ausscheiden aus dem Team)
- Geschlossene Tickets: [7] Tage

## Deine Rechte
Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Datenübertragbarkeit und
Widerspruch. Wende dich dafür an die oben genannte Adresse. Außerdem kannst du dich bei einer Datenschutzbehörde
beschweren ([in Österreich: Datenschutzbehörde, Barichgasse 40–42, 1030 Wien, dsb.gv.at]).
