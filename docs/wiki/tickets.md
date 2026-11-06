# Tickets bearbeiten

Wie Spieler Tickets erstellen, steht unter [Tickets & Reports](report.md). Fürs Team:

- Das **Team** bekommt neue Tickets mit **[Öffnen] [Teleport] [Alle Tickets]** im Chat (`mradmin.tickets.notify`).
- Unter Dashboard → Tickets oder `/mradmin tickets`: filtern, antworten, **übernehmen**, schließen, wieder öffnen. Wer zuerst antwortet, übernimmt das Ticket automatisch.
- Im Ticket steht der Verlauf wie in einem Chat. Wer offline ist, erfährt beim nächsten Join von neuen Antworten.
- **Kategorien** legst du unter `tickets.categories` an (in der Weboberfläche als Tabelle). Standard: Cheating, Chat, Griefing, Betrug, Bug, Frage/Sonstiges.
- **Automatische Tickets von Plugins:** Meldet ein Plugin ein Problem eines Spielers, bekommt er automatisch ein Ticket - z.B. **mit MRchunks**, wenn sein Chunkloader wegen Lag gesperrt wird, mit allen Messwerten. Schließen kann es nur das Team. Abschalten mit `tickets.plugin-reports: false`.
- Weitere Einstellungen unter `tickets`: Cooldowns, Limits, wie lange geschlossene Tickets bleiben.

Im Browser gibt es dieselben Tickets unter **Tickets** in der [Weboberfläche](/docs/WEBOBERFLAECHE.md) - dazu Entbannungsanträge und das Kontaktformular.

## Permissions
`mradmin.tickets.create` (Spieler) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` - alle in `mradmin.groups.support`, siehe [Permissions](permissions.md).
