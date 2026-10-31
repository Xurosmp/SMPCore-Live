package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.rank.RankManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /rank list | set <Spieler> <Rang> | info [Spieler] */
public final class RankCommand implements TabExecutor {
    private final SMPCore plugin;

    public RankCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                Msg.send(s, "Ränge:");
                for (RankManager.Rank r : plugin.ranks().all()) s.sendMessage(Msg.c("<dark_gray>- " + r.display + " <gray>(" + r.id + ")"));
            }
            case "set" -> {
                if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return true; }
                if (a.length < 3) { Msg.error(s, "Nutzung: /rank set <Spieler> <Rang>"); return true; }
                PlayerData d = plugin.data().find(a[1]);
                RankManager.Rank r = plugin.ranks().get(a[2]);
                if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return true; }
                if (r == null) { Msg.error(s, "Rang nicht gefunden. /rank list"); return true; }
                plugin.ranks().setRank(d.uuid, r.id);
                Msg.ok(s, Msg.esc(d.name) + " hat jetzt den Rang " + r.id + ".");
            }
            default -> {
                PlayerData d = a.length > 1 ? plugin.data().find(a[1]) : (s instanceof Player p ? plugin.data().get(p.getUniqueId()) : null);
                if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return true; }
                Msg.send(s, "<white>" + Msg.esc(d.name) + "<gray>: " + plugin.ranks().rankOf(d.uuid).display);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], s.hasPermission("smpcore.admin") ? List.of("list", "set", "info") : List.of("list", "info"));
        if (a.length == 2) return Players.names(a[1]);
        if (a.length == 3 && a[0].equalsIgnoreCase("set")) return Players.complete(a[2], plugin.ranks().all().stream().map(r -> r.id).toList());
        return List.of();
    }
}
