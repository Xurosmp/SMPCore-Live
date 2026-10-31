package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /afk (teleportiert in die AFK-Zone) + Admin: pos1, pos2, setspawn, info */
public final class AfkCommand implements TabExecutor {
    private final SMPCore plugin;

    public AfkCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (!sub.isEmpty()) {
            if (!p.hasPermission("smpcore.admin")) { Msg.error(p, "Keine Rechte."); return true; }
            switch (sub) {
                case "pos1" -> { plugin.data().setAfk(1, p.getLocation()); plugin.data().save(true); Msg.ok(p, "AFK-Zone: Position 1 gesetzt."); }
                case "pos2" -> { plugin.data().setAfk(2, p.getLocation()); plugin.data().save(true); Msg.ok(p, "AFK-Zone: Position 2 gesetzt."); }
                case "setspawn" -> { plugin.data().setAfk(3, p.getLocation()); plugin.data().save(true); Msg.ok(p, "AFK-Spawn gesetzt."); }
                case "info" -> Msg.send(p, "pos1: " + (plugin.data().afk(1) != null ? "<green>ok" : "<red>fehlt")
                        + " <gray>pos2: " + (plugin.data().afk(2) != null ? "<green>ok" : "<red>fehlt")
                        + " <gray>spawn: " + (plugin.data().afk(3) != null ? "<green>ok" : "<red>fehlt")
                        + " <gray>| du in der Zone: " + (plugin.afk().inZone(p.getLocation()) ? "<green>ja" : "<red>nein"));
                default -> Msg.error(p, "Nutzung: /afk [pos1|pos2|setspawn|info]");
            }
            return true;
        }
        Location l = plugin.data().afk(3);
        if (l == null) { Msg.error(p, "Die AFK-Zone wurde noch nicht eingerichtet."); return true; }
        plugin.teleports().teleport(p, l, "AFK-Zone");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 && s.hasPermission("smpcore.admin") ? Players.complete(a[0], List.of("pos1", "pos2", "setspawn", "info")) : List.of();
    }
}
