package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Nether und End lassen sich manuell abschalten (config dimensions.nether / dimensions.end). Admins dürfen immer durch. */
public final class DimensionListener implements Listener {
    private final SMPCore plugin;
    private final Map<UUID, Long> notified = new HashMap<>();

    public DimensionListener(SMPCore plugin) { this.plugin = plugin; }

    public static boolean enabled(SMPCore pl, String dim) { return pl.getConfig().getBoolean("dimensions." + dim, true); }

    public static void setEnabled(SMPCore pl, String dim, boolean on) {
        pl.getConfig().set("dimensions." + dim, on);
        pl.saveConfig();
        if (!on) evacuate(pl, dim.equals("nether") ? World.Environment.NETHER : World.Environment.THE_END);
    }

    private static void evacuate(SMPCore pl, World.Environment env) {
        Location spawn = pl.data().spawn();
        if (spawn == null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.NORMAL) { spawn = w.getSpawnLocation(); break; }
            }
        }
        if (spawn == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == env && !p.hasPermission("smpcore.admin")) {
                p.teleportAsync(spawn);
                String prev = Msg.current();
                Msg.use("dim");
                try { Msg.send(p, "<red>Diese Dimension wurde geschlossen. Du wurdest zum Spawn gebracht."); } finally { Msg.use(prev); }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null || e.getFrom().getWorld() == to.getWorld()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("smpcore.admin")) return;
        World.Environment env = to.getWorld().getEnvironment();
        String name;
        if (env == World.Environment.NETHER && !enabled(plugin, "nether")) name = "Der Nether";
        else if (env == World.Environment.THE_END && !enabled(plugin, "end")) name = "Das End";
        else return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = notified.get(p.getUniqueId());
        if (last == null || now - last > 3000) {
            notified.put(p.getUniqueId(), now);
            Msg.use("dim");
            try { Msg.send(p, "<red>" + name + " ist derzeit geschlossen!"); } finally { Msg.reset(); }
        }
    }
}
