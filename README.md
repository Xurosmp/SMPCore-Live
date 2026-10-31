# SMPCore

All-in-one SMP-Plugin für **Paper 1.21.4+** (Java 21) mit **Bedrock-Support** (Geyser/Floodgate).

## Features
- **Eigene Economy** (Geld + Kristalle), `/balance`, `/pay`, `/top`, `/eco` (Admin), Kurzformen: `1k`, `2.5m`, `1b`
- **Shop** (Kategorien -> Item -> Kauf/Verkauf 1/16/64/Alles), `/sell hand|all`
- **Auktionshaus** `/ah`, `/ah sell <preis>`, `/ah search <text>`, Sortierung, Ablauf, Steuer, "Meine Items"
- **Kopfgelder** `/bounty`, `/bounty <spieler> <betrag>`, Auszahlung beim Kill
- **Kristalle (Shards)**: Kills, Spielzeit, Daily, Kristall-Shop `/crystalshop`
- **Crates mit Keys**: Vorschau (Linksklick), Öffnen mit Animation (Rechtsklick), Partikel, Broadcast bei seltenen Gewinnen
- **Admin Panel** `/ap`: Spieler (Geld/Kristalle/Keys/TP/Heilen/Kick), Kopfgelder, Crates, Feature-Schalter, Reload
- **Extras**: Scoreboard, Homes, `/tpa`, `/rtp`, Spawn, `/daily` mit Streak, `/stats`, Bestenlisten

## Addon: SMPCoreLive (Ordner `live/`)
Separates Plugin, das über SMPCore läuft: `/live` (roter Punkt + Stream-Link für MEDIA), Combat-Log und Hologramm-Leaderboards. Details in `live/README.md`.

## Neu in v3
- **Zwei Währungen:** **Kristalle = AFK-Währung** (verdient in der AFK-Zone, Shop: `/crystalshop`) und **Gems = Store-Währung** (Shop: `/gemshop`).
  Gems vergibst du per Befehl, z.B. aus Tebex: `gems give <Spieler> <Menge>`.
- **AFK-Zone:** Admin setzt sie mit `/afk pos1`, `/afk pos2`, `/afk setspawn`. Spieler kommen mit `/afk` hin und bekommen alle X Sekunden Kristalle (Rang-Bonus über `afk-bonus` in `ranks.yml`).
- **Clan-System:** `/clan` öffnet das Menü (Bedrock-freundlich). Erstellen, Einladen per Tap, Rollen (Leader/Offizier/Mitglied), Bank, Home, Clan-Chat, Friendly-Fire, Top-Clans, Clan-Tag im Chat und `%clan%` im Scoreboard.

## Neu in v2
- **Chat-Prefix pro Befehl** (`AH ▸`, `HOME ▸`, `SHOP ▸` ... max. 4 Zeichen) – Einstellung unter `prefixes:` in der config.yml
- **Ränge** (`ranks.yml`): Prefix im Chat/Tab, Permissions mit Vererbung, Home-/AH-Limits. `/rank list|set|info`
- **SmartSpawner** (`spawners.yml`): Spawner lagern Loot + XP virtuell, Menü per Rechtsklick (Lager, XP, Verkaufen, Stapeln). `/spawner give <Spieler> <Typ> [Anzahl]`
- **3x3-Tools** (`tools.yml`): Hammer, Excavator, Mega-Hammer (5x5), Holzfäller-Axt. Sneaken = 1 Block. `/tool give <Spieler> <id> [Anzahl]`
- **Keyall** mit Countdown im Scoreboard (`keyall:` in der config.yml), `/keyall`, `/keyall now`, `/keyall set <min>`
- **Crate-Editor** im Admin Panel: Crates erstellen/löschen, Rewards hinzufügen (Item aus der Hand, Geld, Kristalle), Chancen ändern, Modus **RANDOM** oder **CHOOSE** (Spieler wählt seinen Reward selbst)
- **Admin Panel (`/ap`, `/ap <Name>`)**: Spieler (inkl. Rang ändern), Kopfgelder, Crate-Editor, Spawner/Tools geben, Keyall, Server-Tools (Features, Wartungsmodus, Reload)
- **Shop im HugoSMP-Look** (3 Reihen, Kategorien mittig, kein Füll-Glas) und **Scoreboard** im HugoSMP-Look

## Bedrock
Alle Menüs sind normale Kisten-GUIs und mit **einem Tap** bedienbar (kein Shift-Klick, kein Chat-Input nötig).
Käufe im AH/Kristall-Shop haben ein Bestätigungsmenü. Spielerdaten laufen über UUIDs, Floodgate-Namen (`.Name`)
werden mit und ohne Prefix gefunden (`bedrock-prefix` in der config.yml).

## JAR über GitHub bekommen
1. Neues Repo auf GitHub anlegen und diesen Ordnerinhalt hochladen (Push auf `main`).
2. Tab **Actions** -> der Workflow "Build SMPCore" läuft automatisch.
3. Fertige JAR: **Releases -> "Latest Build"** (wird bei jedem Push neu gebaut) oder unter dem Run als Artifact.
4. Für feste Versionen: Tag pushen, z.B. `git tag v1.0.0 && git push --tags` -> eigenes Release.

## Installation
JAR in `plugins/`, Server starten, Configs unter `plugins/SMPCore/` anpassen (`config.yml`, `shop.yml`, `crates.yml`, `crystalshop.yml`).
Danach: `/setspawn`, Crate-Block anschauen und `/crate set common`, Keys geben mit `/crate give <spieler> common 5`.

## Rechte
`smpcore.admin` (OP), `smpcore.tp.bypass` (kein Teleport-Delay). Alles andere ist für alle erlaubt.

## Eigene Plugins anbinden
`SMPCore.get().economy().add(uuid, 100)` usw. (Vault-Bridge ist nicht enthalten).
