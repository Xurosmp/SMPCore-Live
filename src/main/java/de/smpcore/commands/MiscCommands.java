package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** daily, stats, scoreboard */
public final class MiscCommands implements TabExecutor {
    private static final long DAY = 24L * 3600 * 1000;
    private final SMPCore plugin;

    public MiscCommands(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "daily" -> daily(s);
            case "stats" -> stats(s, a);
            case "scoreboard" -> {
                if (!(s instanceof Player p)) return true;
                PlayerData d = plugin.data().get(p.getUniqueId());
                if (d == null) return true;
                d.scoreboardOff = !d.scoreboardOff;
                Msg.send(p, "Scoreboard: " + (d.scoreboardOff ? "<red>aus" : "<green>an"));
            }
            default -> { }
        }
        return true;
    }

    private void daily(CommandSender s) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        long now = System.currentTimeMillis();
        long since = now - d.lastDaily;
        if (d.lastDaily != 0 && since < DAY) {
            Msg.error(p, "Du kannst deine tägliche Belohnung in " + Num.duration(DAY - since) + " abholen.");
            return;
        }
        d.dailyStreak = (d.lastDaily != 0 && since < 2 * DAY) ? d.dailyStreak + 1 : 1;
        d.lastDaily = now;
        int cap = plugin.getConfig().getInt("daily.max-streak", 7);
        int streak = Math.min(d.dailyStreak, cap);
        double money = plugin.getConfig().getDouble("daily.money", 500) + plugin.getConfig().getDouble("daily.streak-bonus", 100) * (streak - 1);
        long base = plugin.getConfig().getLong("daily.crystals", 0);
        long crystals = base > 0 ? base + (streak / 3) : 0;
        plugin.economy().add(p.getUniqueId(), money);
        if (crystals > 0) plugin.economy().addCrystals(p.getUniqueId(), crystals);
        Msg.send(p, "<gold>Tägliche Belohnung! <green>+" + EconomyManager.fmt(money)
                + (crystals > 0 ? " <gray>und <yellow>+" + crystals + " Stars" : "") + " <dark_gray>(Streak: " + d.dailyStreak + ")");
    }

    private void stats(CommandSender s, String[] a) {
        PlayerData d;
        if (a.length > 0) d = plugin.data().find(a[0]);
        else if (s instanceof Player p) d = plugin.data().get(p.getUniqueId());
        else d = null;
        if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        double kd = d.deaths == 0 ? d.kills : Math.round((double) d.kills / d.deaths * 100) / 100.0;
        Msg.send(s, "<gold><bold>Stats von " + Msg.esc(d.name));
        s.sendMessage(Msg.c("<gray>Geld: <green>" + EconomyManager.fmt(d.money)));
        s.sendMessage(Msg.c("<gray>Gems: <light_purple>" + Num.format(d.gems) + " <gray>Stars: <yellow>" + Num.format(d.crystals)));
        s.sendMessage(Msg.c("<gray>Kills: <white>" + d.kills + " <gray>Tode: <white>" + d.deaths + " <gray>K/D: <white>" + kd));
        s.sendMessage(Msg.c("<gray>Spielzeit: <white>" + Num.duration(d.playtime * 1000)));
        s.sendMessage(Msg.c("<gray>Daily-Streak: <white>" + d.dailyStreak));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return c.getName().equalsIgnoreCase("stats") && a.length == 1 ? Players.names(a[0]) : List.of();
    }
}
