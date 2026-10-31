package de.smpcore.live.holo;

import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import de.smpcore.live.util.Tab;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** /hololb create|remove|list|types|movehere|tp|settype|settitle|setlimit|refresh */
public final class HoloCommand implements TabExecutor {
    private static final String M = "holo";
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,24}");
    private final SMPCoreLive plugin;

    public HoloCommand(SMPCoreLive plugin) { this.plugin = plugin; }

    private void help(CommandSender s, String l) {
        Msg.send(s, M, "<white>/" + l + " create [id] [typ] [anzahl] <gray>- Leaderboard an deiner Position");
        Msg.send(s, M, "<white>/" + l + " remove [id] <gray>- löschen");
        Msg.send(s, M, "<white>/" + l + " list <gray>- alle Leaderboards");
        Msg.send(s, M, "<white>/" + l + " types <gray>- verfügbare Typen");
        Msg.send(s, M, "<white>/" + l + " movehere [id] <gray>- an deine Position verschieben");
        Msg.send(s, M, "<white>/" + l + " tp [id] <gray>- zum Leaderboard teleportieren");
        Msg.send(s, M, "<white>/" + l + " settype [id] [typ] <gray>- Typ ändern");
        Msg.send(s, M, "<white>/" + l + " settitle [id] [text|reset] <gray>- Titel ändern (MiniMessage)");
        Msg.send(s, M, "<white>/" + l + " setlimit [id] [anzahl] <gray>- Anzahl der Plätze (1-20)");
        Msg.send(s, M, "<white>/" + l + " refresh <gray>- sofort aktualisieren");
    }

    private Location here(Player p) {
        double h = plugin.getConfig().getDouble("holograms.spawn-height", 2.5);
        return p.getLocation().clone().add(0, h, 0);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        HologramManager hm = plugin.holograms();
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s, label);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "types" -> Msg.send(s, M, "Typen: <white>" + String.join("<gray>, <white>", HologramManager.TYPES));
            case "list" -> {
                List<HologramManager.Holo> all = hm.all();
                if (all.isEmpty()) {
                    Msg.send(s, M, "Es gibt noch kein Leaderboard. Erstelle eins mit /" + label + " create [id] [typ]");
                    return true;
                }
                for (HologramManager.Holo h : all) {
                    Msg.send(s, M, "<white>" + Msg.esc(h.id) + " <dark_gray>| <yellow>" + h.type + " <dark_gray>| <gray>" + Msg.esc(h.world)
                            + " " + Math.round(h.x) + " " + Math.round(h.y) + " " + Math.round(h.z));
                }
            }
            case "refresh" -> {
                hm.refreshAll();
                Msg.ok(s, M, "Alle Leaderboards aktualisiert.");
            }
            case "create" -> {
                if (!(s instanceof Player p)) { Msg.error(s, M, "Nur als Spieler."); return true; }
                if (a.length < 3) { Msg.error(s, M, "Benutzung: /" + label + " create [id] [typ] [anzahl]"); return true; }
                String id = a[1].toLowerCase(Locale.ROOT);
                String type = a[2].toLowerCase(Locale.ROOT);
                if (!ID.matcher(id).matches()) { Msg.error(s, M, "Die ID darf nur a-z, 0-9, _ und - enthalten (max. 24 Zeichen)."); return true; }
                if (!HologramManager.TYPES.contains(type)) { Msg.error(s, M, "Unbekannter Typ. /" + label + " types"); return true; }
                int limit = 0;
                if (a.length > 3) {
                    try { limit = Integer.parseInt(a[3]); } catch (NumberFormatException e) { Msg.error(s, M, "Die Anzahl muss eine Zahl sein."); return true; }
                    if (limit < 1 || limit > 20) { Msg.error(s, M, "Die Anzahl muss zwischen 1 und 20 liegen."); return true; }
                }
                if (hm.create(id, type, here(p), limit) == null) { Msg.error(s, M, "Die ID gibt es schon."); return true; }
                Msg.ok(s, M, "Leaderboard <white>" + Msg.esc(id) + " <green>erstellt.");
            }
            default -> {
                // alle weiteren Befehle brauchen eine bestehende ID
                if (a.length < 2) { Msg.error(s, M, "Bitte eine ID angeben. /" + label + " list"); return true; }
                HologramManager.Holo h = hm.get(a[1]);
                if (h == null) { Msg.error(s, M, "Dieses Leaderboard gibt es nicht. /" + label + " list"); return true; }
                switch (sub) {
                    case "remove", "delete" -> {
                        hm.remove(h.id);
                        Msg.ok(s, M, "Leaderboard <white>" + Msg.esc(h.id) + " <green>gelöscht.");
                    }
                    case "movehere", "move" -> {
                        if (!(s instanceof Player p)) { Msg.error(s, M, "Nur als Spieler."); return true; }
                        hm.move(h, here(p));
                        Msg.ok(s, M, "Leaderboard verschoben.");
                    }
                    case "tp" -> {
                        if (!(s instanceof Player p)) { Msg.error(s, M, "Nur als Spieler."); return true; }
                        org.bukkit.World w = plugin.getServer().getWorld(h.world);
                        if (w == null) { Msg.error(s, M, "Die Welt ist nicht geladen."); return true; }
                        p.teleport(new Location(w, h.x, h.y - plugin.getConfig().getDouble("holograms.spawn-height", 2.5), h.z));
                    }
                    case "settype" -> {
                        String type = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                        if (!HologramManager.TYPES.contains(type)) { Msg.error(s, M, "Unbekannter Typ. /" + label + " types"); return true; }
                        h.type = type;
                        hm.changed(h);
                        Msg.ok(s, M, "Typ geändert auf <white>" + type + "<green>.");
                    }
                    case "settitle" -> {
                        if (a.length < 3) { Msg.error(s, M, "Benutzung: /" + label + " settitle [id] [text|reset]"); return true; }
                        String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                        h.title = text.equalsIgnoreCase("reset") ? null : text;
                        hm.changed(h);
                        Msg.ok(s, M, "Titel geändert.");
                    }
                    case "setlimit" -> {
                        int n;
                        try { n = Integer.parseInt(a.length > 2 ? a[2] : ""); } catch (NumberFormatException e) { Msg.error(s, M, "Die Anzahl muss eine Zahl sein."); return true; }
                        if (n < 1 || n > 20) { Msg.error(s, M, "Die Anzahl muss zwischen 1 und 20 liegen."); return true; }
                        h.limit = n;
                        hm.changed(h);
                        Msg.ok(s, M, "Es werden jetzt <white>" + n + " <green>Plätze angezeigt.");
                    }
                    default -> Msg.error(s, M, "Unbekannter Befehl. /" + label + " help");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) {
            return Tab.filter(a[0], List.of("create", "remove", "list", "types", "movehere", "tp", "settype", "settitle", "setlimit", "refresh", "help"));
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && !List.of("create", "list", "types", "refresh", "help").contains(sub)) {
            List<String> ids = new ArrayList<>();
            for (HologramManager.Holo h : plugin.holograms().all()) ids.add(h.id);
            return Tab.filter(a[1], ids);
        }
        if (a.length == 3 && (sub.equals("create") || sub.equals("settype"))) return Tab.filter(a[2], HologramManager.TYPES);
        return List.of();
    }
}
