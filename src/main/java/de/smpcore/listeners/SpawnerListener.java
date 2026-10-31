package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.gui.SpawnerMenus;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SpawnerListener implements Listener {
    private final SMPCore plugin;

    public SpawnerListener(SMPCore plugin) { this.plugin = plugin; }

    private boolean allowed(Player p, SpawnerManager.Spawner s) {
        return s.owner.equals(p.getUniqueId()) || p.hasPermission("smpcore.admin");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        EntityType t = plugin.spawners().typeOf(e.getItemInHand());
        if (t == null) return;
        Msg.use("spawner");
        try {
            if (plugin.spawners().mob(t) == null) {
                e.setCancelled(true);
                Msg.error(e.getPlayer(), "Dieser Spawner-Typ ist nicht konfiguriert.");
                return;
            }
            plugin.spawners().register(e.getBlockPlaced(), e.getPlayer().getUniqueId(), t);
            Msg.send(e.getPlayer(), "Smart-Spawner platziert! Rechtsklick zum Öffnen.");
        } finally { Msg.reset(); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerManager.Spawner s = plugin.spawners().at(b);
        if (s == null) return;
        Player p = e.getPlayer();
        Msg.use("spawner");
        try {
            if (!allowed(p, s)) { e.setCancelled(true); Msg.error(p, "Das ist nicht dein Spawner!"); return; }
            if (s.total() > 0) { e.setCancelled(true); Msg.error(p, "Leere zuerst das Lager (Rechtsklick auf den Spawner)."); return; }
            e.setDropItems(false);
            e.setExpToDrop(0);
            if (s.xp > 0) p.giveExp((int) Math.min(s.xp, Integer.MAX_VALUE));
            plugin.spawners().give(p, s.type, s.stack);
            plugin.spawners().unregister(s);
            Msg.send(p, "Spawner abgebaut: <white>" + s.stack + "x " + SpawnerManager.prettyType(s.type));
        } finally { Msg.reset(); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        SpawnerManager.Spawner s = plugin.spawners().at(b);
        if (s == null) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return; // Bauen erlauben
        e.setCancelled(true);
        Msg.use("spawner");
        try {
            if (!allowed(p, s)) { Msg.error(p, "Das ist nicht dein Spawner!"); return; }
            new SpawnerMenus.Main(p, s).open();
        } finally { Msg.reset(); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(SpawnerSpawnEvent e) {
        if (e.getSpawner() != null && plugin.spawners().at(e.getSpawner().getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && plugin.spawners().at(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && plugin.spawners().at(b) != null);
    }
}
