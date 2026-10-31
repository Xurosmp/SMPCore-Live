package de.smpcore.live;

import de.smpcore.live.combat.CombatManager;
import de.smpcore.live.util.Msg;
import de.smpcore.live.util.Tab;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /smpcorelive reload | combat [Spieler] | untag [Spieler] | live off [Spieler] */
public final class AdminCommand implements TabExecutor {
    private static final String M = "admin";
    private final SMPCoreLive plugin;

    public AdminCommand(SMPCoreLive plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            Msg.send(s, M, "<white>/" + label + " reload <gray>- Config neu laden");
            Msg.send(s, M, "<white>/" + label + " combat [Spieler] <gray>- Kampf-Status & Combat-Logs");
            Msg.send(s, M, "<white>/" + label + " untag [Spieler] <gray>- Kampf-Modus beenden");
            Msg.send(s, M, "<white>/" + label + " unlive [Spieler] <gray>- Live-Status beenden");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                Msg.ok(s, M, "Config neu geladen.");
            }
            case "combat" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) { Msg.error(s, M, "Spieler nicht online."); return true; }
                CombatManager cm = plugin.combat();
                long left = cm.secondsLeft(t.getUniqueId());
                Msg.send(s, M, "<white>" + Msg.esc(t.getName()) + "<gray>: " + (left > 0 ? "<red>im Kampf (" + left + "s)" : "<green>nicht im Kampf")
                        + " <dark_gray>| <gray>Combat-Logs: <white>" + cm.logCount(t.getUniqueId()));
            }
            case "untag" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) { Msg.error(s, M, "Spieler nicht online."); return true; }
                plugin.combat().untag(t, true);
                Msg.ok(s, M, Msg.esc(t.getName()) + " ist nicht mehr im Kampf.");
            }
            case "unlive" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) { Msg.error(s, M, "Spieler nicht online."); return true; }
                if (plugin.live().stop(t, true)) Msg.ok(s, M, Msg.esc(t.getName()) + " ist nicht mehr live.");
                else Msg.error(s, M, Msg.esc(t.getName()) + " ist nicht live.");
            }
            default -> Msg.error(s, M, "Unbekannter Befehl. /" + label + " help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return Tab.filter(a[0], List.of("reload", "combat", "untag", "unlive", "help"));
        if (a.length == 2 && List.of("combat", "untag", "unlive").contains(a[0].toLowerCase(Locale.ROOT))) {
            return Tab.filter(a[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }
}
