# SMPCoreLive

Addon für **SMPCore** (eigenes Plugin, läuft über SMPCore, aber als separate JAR).
Paper 1.21.4+ / Java 21. SMPCore muss installiert sein (`depend`).

## Features

### /live – Streamer-Modus
- `/live <Link>` startet den Live-Status, `/live off` beendet ihn, `/live list` zeigt alle aktuellen Streams.
- Erlaubt für die SMPCore-Ränge **media, mod, srmod, admin, sradmin, owner** (`live.allowed-ranks`) oder mit der Permission `smpcorelive.live`.
- Roter Punkt **● hinter dem Namen** im Chat und in der TAB-Liste. Klick auf den Namen im Chat öffnet den Stream.
- Ankündigung im Chat mit klickbarem `[Zuschauen]`.
- Nur Links zu erlaubten Seiten (Standard: twitch.tv, youtube.com, youtu.be, kick.com, tiktok.com), Cooldown, automatisches Ende nach X Minuten und beim Verlassen des Servers.

### Combat-Log
- Nach einem PvP-Treffer (Nahkampf oder Projektil) sind beide Spieler 15 Sekunden im Kampf (Actionbar-Countdown).
- Ausloggen im Kampf = Spieler stirbt (Items droppen), der Gegner bekommt den Kill inklusive SMPCore-Bounty und Stars.
  Kicks und Server-Stop werden nie bestraft.
- Im Kampf gesperrt: Befehle (Whitelist/Blacklist in der config), Teleports, Portale, Fliegen, Elytra. Enderperlen bleiben erlaubt.
- Bypass: `smpcorelive.combat.bypass`. Beim nächsten Join gibt es eine Meldung. Die Anzahl der Combat-Logs wird in `combatlogs.yml` gezählt.

### Hologramm-Leaderboards
- `/hololb create <id> <typ> [anzahl]` stellt ein Leaderboard an deine Position.
- Typen: `money, crystals, gems, kills, deaths, kd, playtime` (Daten direkt aus SMPCore).
- Weitere Befehle: `remove, list, types, movehere, tp, settype, settitle, setlimit, refresh`.
- Aktualisiert sich automatisch (`holograms.refresh-seconds`).
- `holograms.mode: display` (TextDisplay, schön) oder `armorstand` (funktioniert auch für Bedrock/Geyser).

## Befehle & Rechte
| Befehl | Recht |
|---|---|
| `/live` | Rang aus der config oder `smpcorelive.live` |
| `/hololb ...` | `smpcorelive.holo.admin` |
| `/smpcorelive reload / combat <Spieler> / untag <Spieler> / unlive <Spieler>` | `smpcorelive.admin` |

## Bauen
Erst SMPCore bauen, dann das Addon (im Hauptordner):
```
mvn package                      # -> target/SMPCore.jar
mvn -f live/pom.xml package      # -> live/target/SMPCoreLive.jar
```
Über GitHub baut der Workflow beides automatisch und hängt beide JARs an das Release.

## Installation
Beide JARs in `plugins/`, Server neu starten, danach `plugins/SMPCoreLive/config.yml` anpassen.
