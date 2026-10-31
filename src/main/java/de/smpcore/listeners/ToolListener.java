package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.tools.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ToolListener implements Listener {
    private final SMPCore plugin;
    private final Set<UUID> processing = new HashSet<>();

    public ToolListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (processing.contains(p.getUniqueId()) || p.isSneaking()) return; // Sneaken = nur 1 Block
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolManager.Tool tool = plugin.tools().of(hand);
        if (tool == null || tool.size() <= 1) return;

        Block origin = e.getBlock();
        BlockFace face = BlockFace.UP;
        RayTraceResult rt = p.rayTraceBlocks(6.0);
        if (rt != null && rt.getHitBlockFace() != null) face = rt.getHitBlockFace();

        int extra = 0;
        processing.add(p.getUniqueId());
        try {
            for (Block b : plugin.tools().area(origin, face, tool.size())) {
                if (b.equals(origin) || !plugin.tools().canBreak(b, hand)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p); // damit Schutz-Plugins mitreden können
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) b.breakNaturally(hand);
                else b.setType(Material.AIR);
                extra++;
            }
        } finally {
            processing.remove(p.getUniqueId());
        }
        if (extra > 0) damage(p, hand, extra);
    }

    private void damage(Player p, ItemStack hand, int blocks) {
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable d)) return;
        int unb = hand.getEnchantmentLevel(Enchantment.UNBREAKING);
        int dmg = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < blocks; i++) if (unb == 0 || rnd.nextInt(unb + 1) == 0) dmg++;
        d.setDamage(d.getDamage() + dmg);
        if (d.getDamage() >= hand.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            hand.setItemMeta(d);
        }
    }
}
