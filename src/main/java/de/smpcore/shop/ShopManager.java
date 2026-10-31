package de.smpcore.shop;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public final class ShopManager {
    public record Item(Material material, double buy, double sell) {}
    public record Category(String id, String name, Material icon, int slot, boolean locked, String action, List<Item> items) {}

    private final SMPCore plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Double> sellPrices = new HashMap<>();

    public ShopManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        categories.clear();
        sellPrices.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "shop.yml"));
        List<String> black = plugin.getConfig().getStringList("shop-blacklist");
        ConfigurationSection cs = y.getConfigurationSection("categories");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            List<Item> items = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("items")) {
                Object mat = m.get("material");
                if (mat == null) continue;
                Material mt = Material.matchMaterial(String.valueOf(mat));
                if (mt == null || !mt.isItem()) {
                    plugin.getLogger().warning("Shop: unbekanntes Item " + mat);
                    continue;
                }
                if (black.contains(mt.name())) { plugin.getLogger().warning("Shop: " + mt + " steht auf der Blacklist und wird übersprungen."); continue; }
                double buy = num(m.get("buy"));
                double sell = num(m.get("sell"));
                items.add(new Item(mt, buy, sell));
                if (sell > 0) sellPrices.merge(mt, sell, Math::max);
            }
            categories.put(id, new Category(id, c.getString("name", id), icon, c.getInt("slot", -1), c.getBoolean("locked", false), c.getString("action", ""), items));
        }
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    public Collection<Category> categories() { return categories.values(); }

    /** Verkaufspreis pro Stück (0 = nicht verkaufbar). */
    public double sellPrice(Material m) { return sellPrices.getOrDefault(m, 0.0); }

    private int space(Player p, Material m) {
        int max = m.getMaxStackSize();
        int space = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) space += max;
            else if (it.getType() == m && !it.hasItemMeta()) space += Math.max(0, max - it.getAmount());
        }
        return space;
    }

    public void buy(Player p, Item item, int amount) {
        EconomyManager eco = plugin.economy();
        int can = Math.min(amount, space(p, item.material()));
        if (can <= 0) { Msg.error(p, "Dein Inventar ist voll!"); return; }
        double cost = item.buy() * can;
        if (!eco.take(p.getUniqueId(), cost)) {
            Msg.error(p, "Nicht genug Geld! Du brauchst " + EconomyManager.fmt(cost));
            return;
        }
        p.getInventory().addItem(new ItemStack(item.material(), can));
        Msg.send(p, "Gekauft: <white>" + can + "x " + Items.pretty(item.material()) + " <gray>für <green>" + EconomyManager.fmt(cost));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    public int removeItems(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack s = cont[i];
            if (s == null || s.getType() != m || s.hasItemMeta()) continue;
            int take = Math.min(left, s.getAmount());
            if (take == s.getAmount()) cont[i] = null;
            else s.setAmount(s.getAmount() - take);
            left -= take;
        }
        p.getInventory().setStorageContents(cont);
        return amount - left;
    }

    public void sellItem(Player p, Material m, double price, int amount) {
        int removed = removeItems(p, m, amount);
        if (removed <= 0) { Msg.error(p, "Du hast kein " + Items.pretty(m) + " zum Verkaufen."); return; }
        double total = removed * price;
        plugin.economy().add(p.getUniqueId(), total);
        Msg.send(p, "Verkauft: <white>" + removed + "x " + Items.pretty(m) + " <gray>für <gold>" + EconomyManager.fmt(total));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }

    public void sellAll(Player p) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && !s.hasItemMeta() && sellPrices.containsKey(s.getType()))
                counts.merge(s.getType(), s.getAmount(), Integer::sum);
        }
        double total = 0;
        int items = 0;
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            int r = removeItems(p, e.getKey(), e.getValue());
            total += r * sellPrices.get(e.getKey());
            items += r;
        }
        if (items == 0) { Msg.error(p, "Du hast nichts Verkaufbares im Inventar."); return; }
        plugin.economy().add(p.getUniqueId(), total);
        Msg.send(p, "Verkauft: <white>" + items + " Items <gray>für <gold>" + EconomyManager.fmt(total));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }

    public void sellHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Double price = sellPrices.get(hand.getType());
        if (hand.getType().isAir() || price == null || hand.hasItemMeta()) {
            Msg.error(p, "Dieses Item kann nicht verkauft werden.");
            return;
        }
        sellItem(p, hand.getType(), price, hand.getAmount());
    }
}
