package de.smpcore.gui;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Basis für alle Menüs. Bedrock-kompatibel: reine Chest-GUIs, jede Aktion
 * ist mit einem einzelnen Tap (Linksklick) erreichbar.
 */
public abstract class Menu implements InventoryHolder {
    protected final SMPCore plugin = SMPCore.get();
    protected final Player viewer;
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(Player viewer, int size, String title) {
        this.viewer = viewer;
        this.inv = Bukkit.createInventory(this, size, Msg.c(title));
    }

    protected abstract void build();

    /** Zusätzliche Rechteprüfung pro Klick (z.B. Admin-Menüs). */
    protected boolean allowed() { return true; }

    public void open() {
        refresh();
        viewer.openInventory(inv);
    }

    public void refresh() {
        inv.clear();
        actions.clear();
        build();
    }

    protected void set(int slot, ItemStack item) { set(slot, item, null); }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected void fill(ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    /** Öffnet ein anderes Menü einen Tick später (stabil mit Geyser/Bedrock). */
    protected void go(Menu next) {
        Bukkit.getScheduler().runTask(plugin, () -> next.open());
    }

    protected void closeLater() {
        Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
    }

    /** Bestimmt den Chat-Prefix (z.B. "AH", "SHOP") für Nachrichten aus diesem Menü. */
    protected String tag() {
        String n = getClass().getName();
        if (n.contains("CrystalShop") || n.contains("CrystalStore")) return "crystalshop";
        if (n.contains("ShopMenus")) return "shop";
        if (n.contains("AuctionMenus")) return "ah";
        if (n.contains("BountyMenu")) return "bounty";
        if (n.contains("HomesMenu")) return "home";
        if (n.contains("CrateMenus")) return "crate";
        if (n.contains("SpawnerMenus")) return "spawner";
        if (n.contains("ClanMenus")) return "clan";
        if (n.contains("WarpMenu")) return "warp";
        if (n.contains("KitMenu")) return "kit";
        if (n.contains("OrderMenus") || n.contains("OrderCreateMenus")) return "order";
        if (n.contains("AdminMenus")) return "adminpanel";
        return "default";
    }

    public void handleClick(InventoryClickEvent e) {
        if (!allowed()) { closeLater(); return; }
        Consumer<InventoryClickEvent> a = actions.get(e.getSlot());
        if (a == null) return;
        Msg.use(tag());
        try { a.accept(e); } finally { Msg.reset(); }
    }

    public void onClose() {}

    @Override
    public Inventory getInventory() { return inv; }
}
