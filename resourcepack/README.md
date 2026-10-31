# XuroSMP Resource-Pack

Enthält (Minecraft 1.21.4, Java):
- lila **Crystal**-Ausrüstung (Helm, Brustplatte, Hose, Stiefel, Schwert, Axt, Spitzhacke), roter **Hammer**, oranger **Excavator**
- Icons für **Scoreboard** (Geld, Gems, Stars, Kills, Tode, Clan, Ping, Keyall, Zeit) und ein **XUROSMP-Logo** für die **TAB-Liste**

## Einrichten
1. `XuroSMP-Pack.zip` hosten, der Link muss **direkt** die Datei laden. Am einfachsten: diese Datei im **öffentlichen** GitHub-Repo lassen und den Raw-Link nutzen:
   `https://raw.githubusercontent.com/<DEIN-NAME>/<REPO>/main/resourcepack/XuroSMP-Pack.zip`
2. In `plugins/SMPCore/config.yml` unter `resourcepack:` eintragen: `enabled: true`, `url: "<Link>"` und `sha1` (steht in `SHA1.txt`).
3. Server neu starten oder `/smpcore reload`. Spieler bekommen das Pack beim Join automatisch.

## Hinweise
- Bedrock-Spieler können Java-Resource-Packs nicht laden. Sie sehen automatisch die normalen Symbole (Fallbacks aus der config.yml).
- Neu erzeugen/ändern: `python3 make_pack.py` (ändert die SHA-1, dann in der config.yml aktualisieren).
- Eigene Texturen: PNG-Dateien in der ZIP unter `assets/smp/textures/item/` ersetzen (16x16) und die SHA-1 neu berechnen.
