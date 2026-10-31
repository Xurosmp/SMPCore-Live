package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.listeners.DimensionListener;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

/** /dimension [nether|end] [on|off] */
public final class DimensionCommand implements TabExecutor {
    private final SMPCore plugin;

    public DimensionCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
        if (a.length < 2) {
            Msg.send(s, "Nether: " + (DimensionListener.enabled(plugin, "nether") ? "<green>offen" : "<red>geschlossen")
                    + " <gray>| End: " + (DimensionListener.enabled(plugin, "end") ? "<green>offen" : "<red>geschlossen"));
            Msg.send(s, "Nutzung: <white>/dimension <nether|end> <on|off>");
            return true;
        }
        String dim = a[0].toLowerCase(Locale.ROOT);
        if (!dim.equals("nether") && !dim.equals("end")) { Msg.error(s, "Dimension: nether oder end."); return true; }
        boolean on;
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "on", "an", "open", "true" -> on = true;
            case "off", "aus", "close", "false" -> on = false;
            default -> { Msg.error(s, "Nutze on oder off."); return true; }
        }
        DimensionListener.setEnabled(plugin, dim, on);
        Msg.ok(s, (dim.equals("nether") ? "Nether" : "End") + " ist jetzt " + (on ? "geöffnet." : "geschlossen."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], List.of("nether", "end"));
        if (a.length == 2) return Players.complete(a[1], List.of("on", "off"));
        return List.of();
    }
}
