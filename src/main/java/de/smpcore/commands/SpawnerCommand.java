package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** spawner, tool (nur Admin) */
public final class SpawnerCommand implements TabExecutor {
    private final SMPCore plugin;

    public SpawnerCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
        boolean spawner = c.getName().equalsIgnoreCase("spawner");
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            if (spawner) Msg.send(s, "Typen: <white>" + String.join(", ", plugin.spawners().mobs().stream().map(m -> m.type().name()).toList()));
            else Msg.send(s, "Tools: <white>" + String.join(", ", plugin.tools().all().stream().map(ToolManager.Tool::id).toList()));
            Msg.send(s, "Nutzung: <white>/" + c.getName() + " give <Spieler> <" + (spawner ? "Typ" : "Tool") + "> [Anzahl]");
            return true;
        }
        if (!a[0].equalsIgnoreCase("give") || a.length < 3) { Msg.error(s, "Nutzung: /" + c.getName() + " give <Spieler> <" + (spawner ? "Typ" : "Tool") + "> [Anzahl]"); return true; }
        Player t = Players.online(a[1]);
        if (t == null) { Msg.error(s, "Spieler muss online sein."); return true; }
        int amount = 1;
        if (a.length > 3) {
            try { amount = Math.max(1, Math.min(2304, Integer.parseInt(a[3]))); } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Anzahl."); return true; }
        }
        if (spawner) {
            EntityType type;
            try { type = EntityType.valueOf(a[2].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { Msg.error(s, "Unbekannter Typ."); return true; }
            if (plugin.spawners().mob(type) == null) { Msg.error(s, "Dieser Typ ist in spawners.yml nicht konfiguriert."); return true; }
            plugin.spawners().give(t, type, amount);
            Msg.ok(s, amount + "x " + SpawnerManager.prettyType(type) + " Spawner an " + Msg.esc(t.getName()) + " gegeben.");
        } else {
            ToolManager.Tool tool = plugin.tools().get(a[2]);
            if (tool == null) { Msg.error(s, "Unbekanntes Tool."); return true; }
            for (int i = 0; i < amount; i++) {
                t.getInventory().addItem(plugin.tools().create(tool, 1)).values().forEach(it -> t.getWorld().dropItemNaturally(t.getLocation(), it));
            }
            Msg.ok(s, amount + "x " + tool.id() + " an " + Msg.esc(t.getName()) + " gegeben.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("smpcore.admin")) return List.of();
        boolean spawner = c.getName().equalsIgnoreCase("spawner");
        if (a.length == 1) return Players.complete(a[0], List.of("give", "list"));
        if (a.length == 2) return Players.names(a[1]);
        if (a.length == 3) {
            return Players.complete(a[2], spawner
                    ? plugin.spawners().mobs().stream().map(m -> m.type().name()).toList()
                    : plugin.tools().all().stream().map(ToolManager.Tool::id).toList());
        }
        return List.of();
    }
}
