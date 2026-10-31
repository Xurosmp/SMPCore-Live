package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;

public final class PlayerListener implements Listener {
    private final SMPCore plugin;
    private final Map<String, Long> lastKill = new HashMap<>();

    public PlayerListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        if (plugin.getConfig().getBoolean("maintenance", false) && !e.getPlayer().hasPermission("smpcore.admin")) {
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, Msg.c("<red><bold>Wartungsarbeiten!\n<gray>Wir sind gleich wieder da."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = plugin.data().get(p.getUniqueId()) == null;
        plugin.data().getOrCreate(p.getUniqueId(), p.getName());
        plugin.ranks().apply(p);
        if (!p.hasPermission("smpcore.admin")) {
            for (java.util.UUID v : plugin.vanished()) {
                Player vp = org.bukkit.Bukkit.getPlayer(v);
                if (vp != null) p.hidePlayer(plugin, vp);
            }
        }
        e.joinMessage(Msg.c("<dark_gray>[<green>+<dark_gray>] <gray>" + Msg.esc(p.getName())));
        if (first) {
            Location sp = plugin.data().spawn();
            if (sp != null) p.teleportAsync(sp);
            Bukkit.broadcast(Msg.c("<aqua>Willkommen <white>" + Msg.esc(p.getName()) + " <aqua>auf dem Server!"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(Msg.c("<dark_gray>[<red>-<dark_gray>] <gray>" + Msg.esc(p.getName())));
        plugin.scoreboards().remove(p);
        plugin.ranks().remove(p);
        plugin.teleports().cancel(p);
        plugin.data().save(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        PlayerData vd = plugin.data().get(victim.getUniqueId());
        if (vd != null) vd.deaths++;
        plugin.clans().addDeath(victim.getUniqueId());
        if (vd != null) vd.lastLocation = victim.getLocation().clone();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        PlayerData kd = plugin.data().get(killer.getUniqueId());
        plugin.clans().addKill(killer.getUniqueId());
        if (kd != null) {
            kd.kills++;
            long perKill = plugin.getConfig().getLong("crystals.per-kill", 1);
            long cooldown = plugin.getConfig().getLong("crystals.anti-farm-minutes", 10) * 60_000L;
            String key = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastKill.get(key);
            if (perKill > 0 && (last == null || now - last >= cooldown)) {
                plugin.economy().addCrystals(killer.getUniqueId(), perKill);
                Msg.send(killer, "<light_purple>+" + perKill + " Stars <gray>für den Kill!");
                lastKill.put(key, now);
            }
        }
        if (plugin.feature("bounty")) plugin.bounties().onKill(killer, victim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        Location sp = plugin.data().spawn();
        if (sp != null) e.setRespawnLocation(sp);
    }
}
