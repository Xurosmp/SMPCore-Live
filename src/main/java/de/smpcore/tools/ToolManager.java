package de.smpcore.tools;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Spezial-Tools (Hammer, Excavator ...), die NxN Blöcke auf einmal abbauen. */
public final class ToolManager {
    public record Tool(String id, Material material, String name, List<String> lore, int size, Map<String, Integer> enchants, int model) {}

    private final SMPCore plugin;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final NamespacedKey key;

    public ToolManager(SMPCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "tool_id");
    }

    public void load() {
        tools.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "tools.yml"));
        ConfigurationSection s = y.getConfigurationSection("tools");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            Material m = Material.matchMaterial(c.getString("material", "DIAMOND_PICKAXE"));
            if (m == null) { plugin.getLogger().warning("Tool " + id + ": unbekanntes Material"); continue; }
            int size = c.getInt("size", 3);
            if (size % 2 == 0) size++; // nur ungerade Größen (3, 5, 7)
            size = Math.max(1, Math.min(size, 9));
            Map<String, Integer> ench = new LinkedHashMap<>();
            ConfigurationSection es = c.getConfigurationSection("enchants");
            if (es != null) for (String k : es.getKeys(false)) ench.put(k, es.getInt(k));
            tools.put(id.toLowerCase(Locale.ROOT), new Tool(id.toLowerCase(Locale.ROOT), m, c.getString("name", id),
                    c.getStringList("lore"), size, ench, c.getInt("model", 0)));
        }
    }

    public Collection<Tool> all() { return tools.values(); }
    public Tool get(String id) { return id == null ? null : tools.get(id.toLowerCase(Locale.ROOT)); }

    public ItemStack create(Tool t, int amount) {
        ItemStack it = new ItemStack(t.material(), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(t.name()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : t.lore()) lore.add(Msg.item(l));
        if (t.size() > 1) lore.add(Msg.item("<gray>Abbaufläche: <white>" + t.size() + "x" + t.size()));
        meta.lore(lore);
        for (Map.Entry<String, Integer> e : t.enchants().entrySet()) {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase(Locale.ROOT)));
            if (en != null) meta.addEnchant(en, e.getValue(), true);
        }
        if (t.model() > 0) meta.setCustomModelData(t.model());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id());
        it.setItemMeta(meta);
        return it;
    }

    public Tool of(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : tools.get(id);
    }

    /** Alle Blöcke der NxN-Fläche senkrecht zur angeklickten Seite. */
    public List<Block> area(Block origin, BlockFace face, int size) {
        int r = size / 2;
        List<Block> out = new ArrayList<>();
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                switch (face) {
                    case UP, DOWN -> out.add(origin.getRelative(a, 0, b));
                    case NORTH, SOUTH -> out.add(origin.getRelative(a, b, 0));
                    default -> out.add(origin.getRelative(0, a, b));
                }
            }
        }
        return out;
    }

    public boolean canBreak(Block b, ItemStack tool) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m == Material.SPAWNER || m.getHardness() < 0) return false;
        if (plugin.data().crateAt(b.getLocation()) != null) return false;
        if (b.getState() instanceof Container) return false;
        String n = tool.getType().name();
        Tag<Material> tag = n.endsWith("_PICKAXE") ? Tag.MINEABLE_PICKAXE
                : n.endsWith("_SHOVEL") ? Tag.MINEABLE_SHOVEL
                : n.endsWith("_AXE") ? Tag.MINEABLE_AXE
                : n.endsWith("_HOE") ? Tag.MINEABLE_HOE : null;
        return tag != null && tag.isTagged(m);
    }
}
