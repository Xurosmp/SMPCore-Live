package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.HomesMenu;
import de.smpcore.teleport.TeleportManager;
import de.smpcore.util.Locs;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** spawn, setspawn, sethome, home, delhome, homes, tpa, tpaccept, tpdeny, rtp */
public final class TeleportCommands implements TabExecutor {
    private final SMPCore plugin;

    public TeleportCommands(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return true;
        TeleportManager tp = plugin.teleports();
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                Location sp = plugin.data().spawn();
                if (sp == null) Msg.error(p, "Der Spawn wurde noch nicht gesetzt.");
                else tp.teleport(p, sp, "Spawn");
            }
            case "setspawn" -> {
                plugin.data().setSpawn(p.getLocation());
                plugin.data().save(true);
                Msg.ok(p, "Spawn gesetzt.");
            }
            case "sethome" -> {
                String name = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "home";
                if (!name.matches("[a-z0-9_-]{1,16}")) { Msg.error(p, "Ungültiger Name (a-z, 0-9, _ -, max. 16 Zeichen)."); return true; }
                int max = plugin.ranks().homes(p);
                if (!d.homes.containsKey(name) && d.homes.size() >= max) { Msg.error(p, "Du kannst maximal " + max + " Homes haben."); return true; }
                d.homes.put(name, Locs.str(p.getLocation()));
                Msg.ok(p, "Home '" + name + "' gesetzt.");
            }
            case "delhome" -> {
                if (a.length < 1) { Msg.error(p, "Nutzung: /delhome <Name>"); return true; }
                if (d.homes.remove(a[0].toLowerCase(Locale.ROOT)) != null) Msg.ok(p, "Home gelöscht.");
                else Msg.error(p, "Dieses Home gibt es nicht.");
            }
            case "homes" -> new HomesMenu(p).open();
            case "home" -> {
                if (d.homes.isEmpty()) { Msg.error(p, "Du hast noch kein Home. Nutze /sethome."); return true; }
                String name = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : (d.homes.size() == 1 ? d.homes.keySet().iterator().next() : null);
                if (name == null) { new HomesMenu(p).open(); return true; }
                String raw = d.homes.get(name);
                Location loc = raw == null ? null : Locs.parse(raw);
                if (loc == null) { Msg.error(p, "Dieses Home gibt es nicht."); return true; }
                tp.teleport(p, loc, name);
            }
            case "tpa" -> {
                if (a.length < 1) { Msg.error(p, "Nutzung: /tpa <Spieler>"); return true; }
                Player t = Players.online(a[0]);
                if (t == null || t.equals(p)) { Msg.error(p, "Spieler nicht gefunden."); return true; }
                tp.request(p, t);
                Msg.send(p, "Anfrage an <white>" + Msg.esc(t.getName()) + " <gray>gesendet (60s gültig).");
                t.sendMessage(Msg.c("<gray>» <white>" + Msg.esc(p.getName()) + " <gray>möchte sich zu dir teleportieren. "
                        + "<click:run_command:/tpaccept><green><underlined>[Annehmen]</underlined></green></click> "
                        + "<click:run_command:/tpdeny><red><underlined>[Ablehnen]</underlined></red></click>"
                        + " <dark_gray>(/tpaccept, /tpdeny)"));
            }
            case "tpaccept" -> {
                TeleportManager.Request r = tp.takeRequest(p.getUniqueId());
                Player from = r == null ? null : plugin.getServer().getPlayer(r.from());
                if (from == null) { Msg.error(p, "Keine offene Anfrage."); return true; }
                Msg.send(p, "Anfrage angenommen.");
                Msg.send(from, "<white>" + Msg.esc(p.getName()) + " <gray>hat deine Anfrage angenommen.");
                tp.teleport(from, p.getLocation(), p.getName());
            }
            case "tpdeny" -> {
                TeleportManager.Request r = tp.takeRequest(p.getUniqueId());
                if (r == null) { Msg.error(p, "Keine offene Anfrage."); return true; }
                Msg.send(p, "Anfrage abgelehnt.");
                Player from = plugin.getServer().getPlayer(r.from());
                if (from != null) Msg.send(from, "<red>" + Msg.esc(p.getName()) + " hat deine Anfrage abgelehnt.");
            }
            case "rtp" -> {
                if (a.length == 0) { new de.smpcore.gui.RtpMenu(p).open(); return true; }
                String dim = a[0].toLowerCase(Locale.ROOT);
                if (dim.equals("world") || dim.equals("oberwelt")) dim = "overworld";
                if (!List.of("overworld", "nether", "end").contains(dim)) { Msg.error(p, "Nutzung: /rtp [overworld|nether|end]"); return true; }
                tp.rtp(p, dim);
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && n.equals("tpa")) return Players.names(a[0]);
        if (a.length == 1 && n.equals("rtp")) return Players.complete(a[0], List.of("overworld", "nether", "end"));
        if (a.length == 1 && (n.equals("home") || n.equals("delhome")) && s instanceof Player p) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            if (d != null) return Players.complete(a[0], d.homes.keySet());
        }
        return List.of();
    }
}
