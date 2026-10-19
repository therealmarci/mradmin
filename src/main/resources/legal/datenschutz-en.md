# Privacy policy

> **MRadmin template – please fill in and check.** It describes what MRadmin's web interface processes technically.
> Replace everything in [square brackets] and adapt it to your server (other services, host, retention periods from
> the config). This is not legal advice. Delete this box afterwards.
> File: `plugins/MRadmin/web/datenschutz.md` – changes apply immediately.

## Controller
[Name]
[Address]
E-mail: [admin@yourserver.com]

## Purpose
The web interface lets the team of the Minecraft server [server name] manage it (tickets, server state ...). Only
people the team has enabled can use it.

## What is processed
**When the page is loaded (server log):** IP address, time, requested address, browser identification - processed by
[your host / reverse proxy] to deliver and protect the site.

**Account:** Minecraft name and UUID, password (stored only as an irreversible hash), the two-factor key, recovery
codes (only as hashes), time of setup and last sign-in.

**Signed-in devices:** per device the IP address, browser identification and the times of sign-in and last use - so
you can see and sign out your devices.

**Log:** sign-ins (including failed ones) and actions in the web interface with time, account and IP address - to
prevent abuse and to keep track of changes.

**Content:** tickets, messages and other data of the Minecraft server you view or edit.

## Cookies
Only one technically necessary cookie is set (`mradmin_session`), which keeps you signed in. No tracking, no ads,
no analytics.

## Player heads
Player heads are made by the Minecraft server itself from the players' skins. Your browser loads nothing from other
services.

## Retention
- Signed-in devices: until signed out, at most [30] days
- Log: [90] days, then deleted automatically
- Account: until it is deleted
- Closed tickets: [7] days

## Your rights
You have the right of access, rectification, erasure, restriction, data portability and objection - contact the
address above. You may also complain to a data protection authority.
