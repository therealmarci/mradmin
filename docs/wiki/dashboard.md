# Dashboard & Admin-Modus

## Admin-Dashboard
**`/mradmin`** (oder Rechtsklick mit dem Menü-Item) öffnet das Dashboard:
- **Live-Kacheln** für Spieler, Server, Wartung und Tickets. Darunter zeigt eine farbige Scheibe den Zustand, z.B. TPS (grün ab 19,5, gelb ab 15), Wartung (gelb = geplant, rot = aktiv), Tickets (rot = warten auf Antwort).
- **Ankündigen:** die eigenen Vorlagen als Knöpfe (die eingebauten aus der Sprachdatei stehen nicht im Menü), „Eigener Text“ öffnet einen Dialog.
- **Plugin-Knöpfe:** Admin-Menüs der anderen MRtechnology-Plugins mit Live-Infos. Eigene Knöpfe (z.B. für Chunky) gehen über `admin-mode.menu.buttons`.
- **Schalter** für Admin-Modus und Staff-Chat.

Fehlt eine Permission, fehlt einfach der Knopf. Alle Befehle zeigt `/mradmin help`.

## Admin-Modus
- **`/mradmin mode on`** (oder der Schalter im Dashboard): Inventar, Rüstung, XP, Leben, Hunger, Effekte, Flugstatus und Position werden gespeichert. Danach bist du im Creative-Modus mit dem **Menü-Item** (Strukturleere, `admin-mode.compass-material`).
- **`/mradmin mode off`:** Alles wird exakt wiederhergestellt, und du landest wieder am Ausgangspunkt.
- Gespeichert wird **vor** jeder Änderung - auch ein Absturz verliert nichts. Du bist danach einfach noch im Admin-Modus.
- Im Admin-Modus kann man nichts fallen lassen oder aufheben, damit keine Creative-Items ins Survival gelangen.
- `/mradmin mode list` zeigt, wer im Admin-Modus ist. `/mradmin mode off <spieler>` beendet ihn für andere.
- Andere MRtechnology-Plugins berücksichtigen den Admin-Modus: Du zählst nicht zur Schlafquote, die Werkzeugleiste pausiert, Duelle gehen nicht.

## Spieler-Menü
Menü-Item → Spieler teleportieren → Kopf anklicken, oder `/mradmin player <spieler>`:
- Infos: Position, Spielmodus, Leben, Ping, Spielzeit, erster Join
- **Hinteleportieren** und **zu mir holen** (Shift-Klick auf einen Kopf teleportiert direkt)
- **Inventar** und **Enderchest** ansehen - live und nur lesend
- **Einfrieren:** Der Spieler kann sich nicht bewegen, bauen oder kämpfen und nimmt keinen Schaden. Ausloggen hilft nicht.
- Seine Reports und die Infos aus den anderen MRtechnology-Plugins; mit LibertyBans auch seine Strafen

## Serverzustand
Dashboard → Server oder `/mradmin status`: TPS, MSPT, RAM, Chunks, Entities und Spieler pro Welt. Darunter mit MRchunks die auffälligen und gesperrten Chunkloader, ohne MRchunks die Chunks mit den meisten Entities - ein Klick teleportiert hin.

## Staff-Chat
`/sc <nachricht>` schreibt an alle mit `mradmin.staffchat`, auch aus der Konsole. `/sc` ohne Text schaltet um: Dann geht dein normaler Chat ans Team. Spiegeln in den Admin-Discord ist optional (Ereignis `staff-chat`).
