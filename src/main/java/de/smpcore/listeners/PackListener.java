package de.smpcore.listeners;

import de.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class PackListener implements Listener {
    private final SMPCore plugin;

    public PackListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) plugin.packs().send(p); }, 30L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        PlayerResourcePackStatusEvent.Status st = e.getStatus();
        if (st == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) plugin.packs().mark(e.getPlayer(), true);
        else if (st == PlayerResourcePackStatusEvent.Status.DECLINED || st == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || st == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD) plugin.packs().mark(e.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { plugin.packs().forget(e.getPlayer()); }
}
