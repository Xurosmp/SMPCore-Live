package de.smpcore.kit;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/** Kits mit Cooldown und Permission (kits.yml). cooldown: -1 = einmalig. */
public final class KitManager {
    public static final class Kit {
        public final String id;
        public String name, permission;
        public Material icon = Material.CHEST;
        public long cooldown;
        public final List<ItemStack> items = new ArrayList<>();
        public final List<String> commands = new ArrayList<>();
        public Kit(String id) { this.id = id; }
    }

    private final SMPCore plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        kits.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "kits.yml"));
        ConfigurationSection root = y.getConfigurationSection("kits");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            Kit k = new Kit(id.toLowerCase(Locale.ROOT));
            k.name = c.getString("name", id);
            k.permission = c.getString("permission", "");
            k.cooldown = c.getLong("cooldown", 86400);
            Material ic = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (ic != null) k.icon = ic;
            for (Map<?, ?> m : c.getMapList("items")) {
                Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                if (mat == null) continue;
                int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
                ItemStack it = new ItemStack(mat, Math.max(1, amount));
                ItemMeta meta = it.getItemMeta();
                if (m.get("name") != null) meta.displayName(Msg.item(String.valueOf(m.get("name"))));
                if (m.get("enchants") instanceof Map<?, ?> em) {
                    for (Map.Entry<?, ?> en : em.entrySet()) {
                        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(String.valueOf(en.getKey()).toLowerCase(Locale.ROOT)));
                        if (ench != null && en.getValue() instanceof Number lvl) meta.addEnchant(ench, lvl.intValue(), true);
                    }
                }
                it.setItemMeta(meta);
                k.items.add(it);
            }
            for (String cmd : c.getStringList("commands")) k.commands.add(cmd);
            kits.put(k.id, k);
        }
    }

    public Collection<Kit> all() { return kits.values(); }
    public Kit get(String id) { return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT)); }

    public boolean hasAccess(Player p, Kit k) {
        return k.permission == null || k.permission.isEmpty() || p.hasPermission(k.permission) || p.hasPermission("smpcore.admin");
    }

    /** Wartezeit in Millisekunden, 0 = bereit, -1 = einmalig schon abgeholt. */
    public long waitMs(Player p, Kit k) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return 0;
        Long last = d.kitUsed.get(k.id);
        if (last == null) return 0;
        if (k.cooldown < 0) return -1;
        return Math.max(0, last + k.cooldown * 1000 - System.currentTimeMillis());
    }

    public String status(Player p, Kit k) {
        if (!hasAccess(p, k)) return "<red>Gesperrt (Rang erforderlich)";
        long w = waitMs(p, k);
        if (w < 0) return "<red>Bereits abgeholt (einmalig)";
        if (w > 0) return "<yellow>Wieder in " + Num.duration(w);
        return "<green>Bereit";
    }

    public void claim(Player p, Kit k) {
        if (!hasAccess(p, k)) { Msg.error(p, "Dafür brauchst du einen höheren Rang."); return; }
        long w = waitMs(p, k);
        if (w < 0) { Msg.error(p, "Dieses Kit gibt es nur einmal."); return; }
        if (w > 0) { Msg.error(p, "Du kannst dieses Kit in " + Num.duration(w) + " wieder abholen."); return; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        for (ItemStack it : k.items) {
            p.getInventory().addItem(it.clone()).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
        for (String cmd : k.commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        d.kitUsed.put(k.id, System.currentTimeMillis());
        Msg.send(p, "<green>Kit abgeholt: <white>" + k.name);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
    }
}
