package de.smpcore.afk;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** AFK-Zone: Wer in der Zone steht, bekommt regelmäßig Stars (AFK-Währung). */
public final class AfkManager {
    private final SMPCore plugin;
    private final Map<UUID, Integer> seconds = new HashMap<>();

    public AfkManager(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    public boolean inZone(Location l) {
        Location a = plugin.data().afk(1), b = plugin.data().afk(2);
        if (a == null || b == null || a.getWorld() == null || l.getWorld() != a.getWorld() || a.getWorld() != b.getWorld()) return false;
        double minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY()) + 2;
        double minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        return l.getX() >= minX && l.getX() <= maxX && l.getY() >= minY && l.getY() <= maxY && l.getZ() >= minZ && l.getZ() <= maxZ;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;
        int interval = Math.max(5, plugin.getConfig().getInt("afk.interval-seconds", 60));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inZone(p.getLocation())) { seconds.remove(p.getUniqueId()); continue; }
            int s = seconds.merge(p.getUniqueId(), 1, Integer::sum);
            if (s >= interval) {
                seconds.put(p.getUniqueId(), 0);
                reward(p);
            } else {
                p.sendActionBar(Msg.c("<yellow>AFK-Zone <dark_gray>» <gray>nächste Stars in <white>" + (interval - s) + "s"));
            }
        }
    }

    private void reward(Player p) {
        long base = Math.max(1, plugin.getConfig().getLong("afk.crystals", 1));
        int bonus = plugin.ranks().afkBonus(p);
        long amount = Math.max(1, Math.round(base * (1 + bonus / 100.0)));
        plugin.economy().addCrystals(p.getUniqueId(), amount);
        String prev = Msg.current();
        Msg.use("afk");
        try {
            Msg.send(p, "<yellow>+" + amount + " Stars" + (bonus > 0 ? " <dark_gray>(+" + bonus + "% Rang-Bonus)" : "")
                    + " <gray>– Gesamt: <white>" + EconomyManager.fmtC(plugin.economy().crystals(p.getUniqueId())));
        } finally { Msg.use(prev); }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }
}
