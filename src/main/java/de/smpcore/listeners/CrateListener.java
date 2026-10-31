package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.crate.CrateManager;
import de.smpcore.gui.CrateMenus;
import de.smpcore.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CrateListener implements Listener {
    private final SMPCore plugin;

    public CrateListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        String id = plugin.data().crateAt(e.getClickedBlock().getLocation());
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        CrateManager.Crate c = plugin.crates().get(id);
        if (c == null) return;
        if (!plugin.feature("crates")) { Msg.error(p, "Crates sind derzeit deaktiviert."); return; }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) plugin.crates().open(p, c);
        else if (e.getAction() == Action.LEFT_CLICK_BLOCK) new CrateMenus.Preview(p, c).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.data().crateAt(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
            if (e.getPlayer().hasPermission("smpcore.admin")) Msg.error(e.getPlayer(), "Nutze /crate remove, um eine Crate zu entfernen.");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.data().crateAt(b.getLocation()) != null);
    }
}
