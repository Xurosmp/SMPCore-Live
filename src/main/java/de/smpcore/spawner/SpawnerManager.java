package de.smpcore.spawner;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** SmartSpawner: Spawner spawnen keine Mobs, sondern lagern Loot + XP virtuell (GUI), stapelbar. */
public final class SpawnerManager {
    public record Loot(Material material, int min, int max, double chance, double sell) {}
    public record Mob(EntityType type, int xp, List<Loot> loot) {}

    public static final class Spawner {
        public final String key, world;
        public final int x, y, z;
        public final UUID owner;
        public final EntityType type;
        public int stack = 1;
        public long xp;
        public final Map<Material, Long> storage = new LinkedHashMap<>();

        Spawner(String key, String world, int x, int y, int z, UUID owner, EntityType type) {
            this.key = key; this.world = world; this.x = x; this.y = y; this.z = z; this.owner = owner; this.type = type;
        }

        public long total() {
            long t = 0;
            for (long v : storage.values()) t += v;
            return t;
        }
    }

    private final SMPCore plugin;
    private final File dataFile;
    private final Map<String, Spawner> spawners = new HashMap<>();
    private final Map<EntityType, Mob> mobs = new LinkedHashMap<>();
    private final NamespacedKey typeKey;
    private int maxStack = 100;
    private long maxStorage = 50_000;
    private int intervalSeconds = 15;

    public SpawnerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spawners-data.yml");
        this.typeKey = new NamespacedKey(plugin, "spawner_type");
    }

    // ---------- Laden / Speichern ----------
    public void loadConfig() {
        mobs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "spawners.yml"));
        maxStack = Math.max(1, y.getInt("settings.max-stack", 100));
        maxStorage = Math.max(64, y.getLong("settings.max-storage", 50_000));
        intervalSeconds = Math.max(1, y.getInt("settings.interval-seconds", 15));
        ConfigurationSection ms = y.getConfigurationSection("mobs");
        if (ms == null) return;
        for (String key : ms.getKeys(false)) {
            EntityType type;
            try { type = EntityType.valueOf(key.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Spawner: unbekannter Mob " + key); continue; }
            ConfigurationSection c = ms.getConfigurationSection(key);
            if (c == null) continue;
            List<Loot> loot = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("loot")) {
                Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                if (mat == null) continue;
                int min = m.get("min") instanceof Number n ? n.intValue() : 1;
                int max = m.get("max") instanceof Number n ? n.intValue() : min;
                double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 100;
                double sell = m.get("sell") instanceof Number n ? n.doubleValue() : 0;
                loot.add(new Loot(mat, Math.max(0, min), Math.max(min, max), chance, sell));
            }
            mobs.put(type, new Mob(type, c.getInt("xp", 1), loot));
        }
    }

    public void loadData() {
        spawners.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = y.getConfigurationSection("spawners");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Spawner s = new Spawner(c.getString("key", ""), c.getString("world", ""), c.getInt("x"), c.getInt("y"), c.getInt("z"),
                        UUID.fromString(c.getString("owner", "")), EntityType.valueOf(c.getString("type", "ZOMBIE")));
                s.stack = Math.max(1, c.getInt("stack", 1));
                s.xp = c.getLong("xp");
                ConfigurationSection st = c.getConfigurationSection("storage");
                if (st != null) {
                    for (String mk : st.getKeys(false)) {
                        Material m = Material.matchMaterial(mk);
                        if (m != null && st.getLong(mk) > 0) s.storage.put(m, st.getLong(mk));
                    }
                }
                spawners.put(s.key, s);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Spawner-Daten defekt (" + k + "): " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Spawner s : spawners.values()) {
            String b = "spawners." + (i++) + ".";
            y.set(b + "key", s.key);
            y.set(b + "world", s.world);
            y.set(b + "x", s.x);
            y.set(b + "y", s.y);
            y.set(b + "z", s.z);
            y.set(b + "owner", s.owner.toString());
            y.set(b + "type", s.type.name());
            y.set(b + "stack", s.stack);
            y.set(b + "xp", s.xp);
            for (Map.Entry<Material, Long> e : s.storage.entrySet()) y.set(b + "storage." + e.getKey().name(), e.getValue());
        }
        Persist.write(plugin, dataFile, y.saveToString(), async);
    }

    // ---------- Zugriff ----------
    public int maxStack() { return maxStack; }
    public long maxStorage() { return maxStorage; }
    public int intervalSeconds() { return intervalSeconds; }
    public Collection<Mob> mobs() { return mobs.values(); }
    public Mob mob(EntityType t) { return mobs.get(t); }

    private static String key(Block b) { return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ(); }
    public Spawner at(Block b) { return spawners.get(key(b)); }

    public static String prettyType(EntityType t) {
        StringBuilder sb = new StringBuilder();
        for (String part : t.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------- Items ----------
    public ItemStack createItem(EntityType t, int amount) {
        ItemStack it = new ItemStack(Material.SPAWNER, Math.max(1, amount));
        ItemMeta im = it.getItemMeta();
        if (im instanceof BlockStateMeta bm) {
            BlockState st = bm.getBlockState();
            if (st instanceof CreatureSpawner cs) {
                cs.setSpawnedType(t);
                bm.setBlockState(cs);
            }
            bm.displayName(Msg.item("<yellow>" + prettyType(t) + " Spawner"));
            bm.lore(List.of(Msg.item("<gray>Smart-Spawner: lagert Loot & XP"), Msg.item("<gray>Rechtsklick zum Öffnen")));
            bm.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, t.name());
            it.setItemMeta(bm);
        }
        return it;
    }

    public EntityType typeOf(ItemStack it) {
        if (it == null || it.getType() != Material.SPAWNER || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (s == null) return null;
        try { return EntityType.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    public void give(Player p, EntityType t, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            p.getInventory().addItem(createItem(t, n)).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            left -= n;
        }
    }

    // ---------- Platzieren / Entfernen ----------
    public void register(Block b, UUID owner, EntityType t) {
        Spawner s = new Spawner(key(b), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), owner, t);
        spawners.put(s.key, s);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (b.getState() instanceof CreatureSpawner cs) {
                cs.setSpawnedType(t);
                cs.setRequiredPlayerRange(0); // vanilla spawnt nichts
                cs.update();
            }
        });
        save(true);
    }

    public void unregister(Spawner s) {
        spawners.remove(s.key);
        save(true);
    }

    // ---------- Loot-Erzeugung ----------
    public void tick() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Spawner s : spawners.values()) {
            World w = Bukkit.getWorld(s.world);
            if (w == null || !w.isChunkLoaded(s.x >> 4, s.z >> 4)) continue;
            Mob m = mobs.get(s.type);
            if (m == null || s.total() >= maxStorage) continue;
            for (int i = 0; i < s.stack; i++) {
                for (Loot l : m.loot()) {
                    if (rnd.nextDouble() * 100.0 < l.chance()) {
                        int n = l.min() + rnd.nextInt(l.max() - l.min() + 1);
                        if (n > 0) s.storage.merge(l.material(), (long) n, Long::sum);
                    }
                }
            }
            s.xp += (long) m.xp() * s.stack;
        }
    }

    // ---------- Aktionen ----------
    public double sellPrice(Spawner s, Material mat) {
        Mob m = mobs.get(s.type);
        if (m == null) return 0;
        for (Loot l : m.loot()) if (l.material() == mat) return l.sell();
        return 0;
    }

    public double sellValue(Spawner s) {
        double v = 0;
        for (Map.Entry<Material, Long> e : s.storage.entrySet()) v += e.getValue() * sellPrice(s, e.getKey());
        return v;
    }

    public void sellAll(Player p, Spawner s) {
        double v = sellValue(s);
        if (v <= 0) { Msg.error(p, "Es gibt nichts zu verkaufen."); return; }
        s.storage.clear();
        plugin.economy().add(p.getUniqueId(), v);
        Msg.send(p, "Loot verkauft für <green>" + EconomyManager.fmt(v));
    }

    public void collectXp(Player p, Spawner s) {
        if (s.xp <= 0) { Msg.error(p, "Keine XP im Spawner."); return; }
        int give = (int) Math.min(s.xp, Integer.MAX_VALUE);
        p.giveExp(give);
        s.xp -= give;
        Msg.send(p, "Du hast <green>" + give + " XP <gray>eingesammelt.");
    }

    /** Spawner-Items aus dem Inventar in den Stapel einzahlen. */
    public int addStack(Player p, Spawner s) {
        int added = 0;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && s.stack < maxStack; i++) {
            ItemStack it = cont[i];
            if (it == null || typeOf(it) != s.type) continue;
            int take = Math.min(it.getAmount(), maxStack - s.stack);
            s.stack += take;
            added += take;
            if (take == it.getAmount()) cont[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(cont);
        return added;
    }
}
