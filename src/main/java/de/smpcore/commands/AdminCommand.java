package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.AdminMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** adminpanel, smpcore */
public final class AdminCommand implements TabExecutor {
    private final SMPCore plugin;

    public AdminCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("keyall")) {
            if (a.length > 0 && a[0].equalsIgnoreCase("now")) {
                if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
                plugin.keyall().run();
            } else if (a.length > 1 && a[0].equalsIgnoreCase("set")) {
                if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
                try {
                    plugin.getConfig().set("keyall.interval-minutes", Math.max(1, Integer.parseInt(a[1])));
                    plugin.saveConfig();
                    plugin.keyall().reset();
                    Msg.ok(s, "Keyall-Intervall gesetzt.");
                } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Zahl."); }
            } else {
                Msg.send(s, "Nächster Keyall in <white>" + de.smpcore.util.Num.mmss(plugin.keyall().secondsLeft()));
            }
            return true;
        }
        if (c.getName().equalsIgnoreCase("adminpanel")) {
            if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
            if (!p.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
            if (a.length > 0) {
                de.smpcore.data.PlayerData d = plugin.data().find(a[0]);
                if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return true; }
                new AdminMenus.Target(p, d.uuid).open();
            } else {
                new AdminMenus.Main(p).open();
            }
            return true;
        }
        // /smpcore
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
            plugin.reloadAll();
            Msg.ok(s, "SMPCore neu geladen.");
        } else if (a.length > 0 && a[0].equalsIgnoreCase("save")) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
            plugin.saveAll(true);
            Msg.ok(s, "Daten gespeichert.");
        } else {
            Msg.send(s, "SMPCore <white>v" + plugin.getPluginMeta().getVersion() + " <gray>– Admin: <white>/ap, /smpcore reload|save");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("smpcore") && a.length == 1) return Players.complete(a[0], List.of("reload", "save"));
        if (c.getName().equalsIgnoreCase("adminpanel") && a.length == 1) return Players.names(a[0]);
        if (c.getName().equalsIgnoreCase("keyall") && a.length == 1) return Players.complete(a[0], List.of("now", "set"));
        return List.of();
    }
}
