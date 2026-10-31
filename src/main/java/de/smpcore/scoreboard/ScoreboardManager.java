package de.smpcore.scoreboard;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import de.smpcore.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardManager {
    private record Board(Scoreboard sb, int lines) {}

    private final SMPCore plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private BukkitTask task;

    public ScoreboardManager(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) remove(p);
    }

    /** Alle aktiven Spieler-Scoreboards (für die TAB-Sortierung). */
    public Collection<Scoreboard> boards() {
        List<Scoreboard> l = new ArrayList<>();
        for (Board b : boards.values()) l.add(b.sb());
        return l;
    }

    public void remove(Player p) {
        if (boards.remove(p.getUniqueId()) != null && p.isOnline()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private Board create(Player p, int lines) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("smp", Criteria.DUMMY, Msg.c("SMP"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lines; i++) {
            String entry = "\u00a7" + "0123456789abcdef".charAt(i) + "\u00a7r";
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            o.getScore(entry).setScore(lines - i);
        }
        p.setScoreboard(sb);
        return new Board(sb, lines);
    }

    private void tick() {
        boolean on = plugin.feature("scoreboard");
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int n = Math.min(lines.size(), 15);
        String title = plugin.getConfig().getString("scoreboard.title", "SMP");
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p.getUniqueId());
            if (!on || d == null || d.scoreboardOff || n == 0) { remove(p); continue; }
            Board b = boards.get(p.getUniqueId());
            if (b == null || b.lines() != n) {
                b = create(p, n);
                boards.put(p.getUniqueId(), b);
            } else if (p.getScoreboard() != b.sb()) {
                p.setScoreboard(b.sb());
            }
            Objective o = b.sb().getObjective("smp");
            if (o != null) o.displayName(Msg.c(Placeholders.apply(title, p)));
            for (int i = 0; i < n; i++) {
                Team t = b.sb().getTeam("l" + i);
                if (t != null) t.prefix(Msg.c(Placeholders.apply(lines.get(i), p)));
            }
        }
    }
}
