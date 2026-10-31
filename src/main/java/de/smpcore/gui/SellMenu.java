package de.smpcore.gui;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /sell: Items in die obere Fläche legen, Fenster schließen = verkaufen.
 * Nicht verkaufbare Items bekommst du zurück. Untere Reihe = Buttons (nicht belegbar).
 */
public final class SellMenu implements Listener {
    private static final int ITEM_SLOTS = 45;

    public static final class Holder implements InventoryHolder {
        Inventory inv;
        boolean done;
        @Override public Inventory getInventory() { return inv; }
    }

    public static void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c("<gold><bold>Verkaufen"));
        h.inv = inv;
        for (int i = ITEM_SLOTS; i < 54; i++) inv.setItem(i, Items.filler());
        inv.setItem(45, Items.make(Material.PAPER, "<yellow>So geht's", "<gray>Lege Items in die obere Fläche.", "<gray>Fenster schließen = verkaufen.",
                "<gray>Nicht verkaufbare Items bekommst du zurück."));
        inv.setItem(51, Items.make(Material.HOPPER, "<aqua>Alles Verkaufbare einlegen", "<gray>Legt alle verkaufbaren Items aus deinem", "<gray>Inventar in dieses Fenster", "", "<yellow>Klicken"));
        inv.setItem(53, Items.make(Material.LIME_CONCRETE, "<green><bold>Jetzt verkaufen", "<gray>Verkauft und schließt das Fenster"));
        refreshInfo(inv);
        p.openInventory(inv);
    }

    private static double value(Inventory inv) {
        SMPCore pl = SMPCore.get();
        double v = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || it.hasItemMeta()) continue;
            v += pl.shop().sellPrice(it.getType()) * it.getAmount();
        }
        return v;
    }

    private static void refreshInfo(Inventory inv) {
        inv.setItem(49, Items.make(Material.GOLD_INGOT, "<gold>Verkaufswert: <green>" + EconomyManager.fmt(value(inv)),
                "<gray>Wird beim Schließen ausgezahlt"));
    }

    private static boolean sellable(ItemStack it) {
        return it != null && !it.getType().isAir() && !it.hasItemMeta() && SMPCore.get().shop().sellPrice(it.getType()) > 0;
    }

    private void settle(Player p, Inventory inv) {
        SMPCore pl = SMPCore.get();
        double total = 0;
        int count = 0;
        List<ItemStack> back = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(i, null);
            if (sellable(it)) { total += pl.shop().sellPrice(it.getType()) * it.getAmount(); count += it.getAmount(); }
            else back.add(it);
        }
        if (total > 0) pl.economy().add(p.getUniqueId(), total);
        for (ItemStack it : back) p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        if (total > 0) {
            String prev = Msg.current();
            Msg.use("sell");
            try { Msg.send(p, "Verkauft: <white>" + count + " Items <gray>für <gold>" + EconomyManager.fmt(total)); } finally { Msg.use(prev); }
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.9f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Holder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        if (e.getClickedInventory() == top && e.getSlot() >= ITEM_SLOTS) {
            e.setCancelled(true);
            if (e.getSlot() == 53) Bukkit.getScheduler().runTask(SMPCore.get(), () -> p.closeInventory());
            else if (e.getSlot() == 51) {
                ItemStack[] cont = p.getInventory().getStorageContents();
                for (int i = 0; i < cont.length; i++) {
                    if (!sellable(cont[i])) continue;
                    ItemStack left = top.addItem(cont[i]).values().stream().findFirst().orElse(null);
                    cont[i] = left;
                }
                p.getInventory().setStorageContents(cont);
                refreshInfo(top);
            }
            return;
        }
        Bukkit.getScheduler().runTask(SMPCore.get(), () -> refreshInfo(top));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Holder)) return;
        for (int slot : e.getRawSlots()) {
            if (slot >= ITEM_SLOTS && slot < top.getSize()) { e.setCancelled(true); return; }
        }
        Bukkit.getScheduler().runTask(SMPCore.get(), () -> refreshInfo(top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h) || h.done) return;
        h.done = true;
        if (e.getPlayer() instanceof Player p) settle(p, e.getInventory());
    }
}
