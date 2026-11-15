# Tickets bearbeiten

Wie Spieler Tickets erstellen, steht unter [Tickets & Reports](report.md). Fürs Team:

- Das **Team** bekommt neue Tickets mit **[Öffnen] [Teleport] [Alle Tickets]** im Chat (`mradmin.tickets.notify`).
- Unter Dashboard → Tickets oder `/mradmin tickets`: filtern, antworten, **übernehmen**, **lösen**, wieder öffnen. Wer zuerst antwortet, übernimmt das Ticket automatisch.
- **Lösen statt schließen:** Ist ein Anliegen erledigt, markiert das Team das Ticket als **gelöst**. Es steht dann im **Archiv** (mit „Gelöst“ gekennzeichnet), beim Spieler aber noch unter seinen aktiven Tickets: Ist die Sache doch nicht erledigt, kann er es **wieder öffnen** - das Team bekommt dann eine Meldung wie bei einer neuen Nachricht. Nach `tickets.reopen-days` (Standard 30 Tage) ist es **endgültig geschlossen** und lässt sich nur noch vom Team wieder öffnen (`.reopen`). Schließt der Spieler sein Ticket selbst, ist es sofort endgültig zu. Entbannungsanträge und Bewerbungen werden wie bisher mit Annehmen/Ablehnen entschieden. `tickets.reopen-days: 0` = gelöste Tickets sind sofort endgültig geschlossen.
- Im Ticket steht der Verlauf wie in einem Chat. Wer offline ist, erfährt beim nächsten Join von neuen Antworten.
- **Kategorien** legst du unter `tickets.categories` an (in der Weboberfläche als Tabelle). Standard: Cheating, Chat, Griefing, Betrug, Bug, Frage/Sonstiges.
- **Automatische Tickets von Plugins:** Meldet ein Plugin ein Problem eines Spielers, bekommt er automatisch ein Ticket - z.B. **mit MRchunks**, wenn sein Chunkloader wegen Lag gesperrt wird, mit allen Messwerten. Schließen kann es nur das Team. Abschalten mit `tickets.plugin-reports: false`.
- Weitere Einstellungen unter `tickets`: Cooldowns, Limits, wie lange geschlossene Tickets bleiben (`keep-closed` zählt bei gelösten Tickets erst nach Ablauf von `reopen-days`).

Im Browser gibt es dieselben Tickets unter **Tickets** in der [Weboberfläche](/docs/WEBOBERFLAECHE.md) - dazu Entbannungsanträge und das Kontaktformular.

## Permissions
`mradmin.tickets.create` (Spieler) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` - alle in `mradmin.groups.support`, siehe [Permissions](permissions.md).
