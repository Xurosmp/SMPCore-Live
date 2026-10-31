package de.smpcore.keyall;

import de.smpcore.SMPCore;
import de.smpcore.crate.CrateManager;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Automatischer Keyall: alle X Minuten bekommen alle Online-Spieler Keys. */
public final class KeyallManager {
    private final SMPCore plugin;
    private long next;

    public KeyallManager(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        reset();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("keyall.enabled", true); }

    public void reset() {
        next = System.currentTimeMillis() + plugin.getConfig().getLong("keyall.interval-minutes", 60) * 60_000L;
    }

    public long secondsLeft() { return Math.max(0, (next - System.currentTimeMillis()) / 1000); }

    private void tick() {
        if (enabled() && System.currentTimeMillis() >= next) run();
    }

    public void run() {
        reset();
        CrateManager.Crate c = plugin.crates().get(plugin.getConfig().getString("keyall.crate", "common"));
        int amount = Math.max(1, plugin.getConfig().getInt("keyall.amount", 1));
        if (c == null) {
            plugin.getLogger().warning("Keyall: Crate '" + plugin.getConfig().getString("keyall.crate") + "' existiert nicht.");
            return;
        }
        Msg.use("keyall");
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.crates().addKeys(p.getUniqueId(), c.id, amount);
                Msg.send(p, "<gold>Keyall! <gray>Du hast <white>" + amount + "x " + c.keyName + " <gray>erhalten.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }
        } finally {
            Msg.reset();
        }
    }
}
