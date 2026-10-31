package de.smpcore.data;

import de.smpcore.SMPCore;
import de.smpcore.util.Locs;
import de.smpcore.util.Persist;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class DataManager {
    private final SMPCore plugin;
    private final File playersFile, miscFile;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final Map<String, String> crates = new HashMap<>(); // "world;x;y;z" -> crateId
    private String spawn;
    private String afkPos1, afkPos2, afkSpawn;

    public DataManager(SMPCore plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        dir.mkdirs();
        playersFile = new File(dir, "players.yml");
        miscFile = new File(dir, "data.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                UUID id;
                try { id = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
                ConfigurationSection c = root.getConfigurationSection(key);
                if (c == null) continue;
                PlayerData d = new PlayerData(id, c.getString("name", "?"));
                d.money = c.getDouble("money");
                d.crystals = c.getLong("crystals");
                d.gems = c.getLong("gems");
                d.kills = c.getInt("kills");
                d.deaths = c.getInt("deaths");
                d.playtime = c.getLong("playtime");
                d.lastDaily = c.getLong("lastDaily");
                d.dailyStreak = c.getInt("dailyStreak");
                d.scoreboardOff = c.getBoolean("scoreboardOff");
                d.rank = c.getString("rank");
                d.signDay = c.getLong("signDay");
                d.signCount = c.getInt("signCount");
                ConfigurationSection k = c.getConfigurationSection("keys");
                if (k != null) for (String crate : k.getKeys(false)) d.keys.put(crate, k.getInt(crate));
                ConfigurationSection kt = c.getConfigurationSection("kits");
                if (kt != null) for (String kid : kt.getKeys(false)) d.kitUsed.put(kid, kt.getLong(kid));
                ConfigurationSection h = c.getConfigurationSection("homes");
                if (h != null) for (String n : h.getKeys(false)) d.homes.put(n, h.getString(n, ""));
                players.put(id, d);
                names.put(d.name.toLowerCase(Locale.ROOT), id);
            }
        }
        YamlConfiguration m = YamlConfiguration.loadConfiguration(miscFile);
        spawn = m.getString("spawn");
        afkPos1 = m.getString("afk.pos1");
        afkPos2 = m.getString("afk.pos2");
        afkSpawn = m.getString("afk.spawn");
        for (String s : m.getStringList("crates")) {
            int i = s.lastIndexOf(';');
            if (i > 0) crates.put(s.substring(0, i), s.substring(i + 1));
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (PlayerData d : players.values()) {
            String b = "players." + d.uuid + ".";
            y.set(b + "name", d.name);
            y.set(b + "money", d.money);
            y.set(b + "crystals", d.crystals);
            y.set(b + "gems", d.gems);
            y.set(b + "kills", d.kills);
            y.set(b + "deaths", d.deaths);
            y.set(b + "playtime", d.playtime);
            y.set(b + "lastDaily", d.lastDaily);
            y.set(b + "dailyStreak", d.dailyStreak);
            y.set(b + "scoreboardOff", d.scoreboardOff);
            if (d.rank != null) y.set(b + "rank", d.rank);
            y.set(b + "signDay", d.signDay);
            y.set(b + "signCount", d.signCount);
            d.keys.forEach((k, v) -> { if (v > 0) y.set(b + "keys." + k, v); });
            d.homes.forEach((k, v) -> y.set(b + "homes." + k, v));
            d.kitUsed.forEach((k, v) -> y.set(b + "kits." + k, v));
        }
        Persist.write(plugin, playersFile, y.saveToString(), async);

        YamlConfiguration m = new YamlConfiguration();
        m.set("spawn", spawn);
        m.set("afk.pos1", afkPos1);
        m.set("afk.pos2", afkPos2);
        m.set("afk.spawn", afkSpawn);
        List<String> list = new ArrayList<>();
        crates.forEach((k, v) -> list.add(k + ";" + v));
        m.set("crates", list);
        Persist.write(plugin, miscFile, m.saveToString(), async);
    }

    public PlayerData get(UUID id) { return players.get(id); }

    public PlayerData getOrCreate(UUID id, String name) {
        PlayerData d = players.get(id);
        if (d == null) {
            d = new PlayerData(id, name);
            d.money = plugin.getConfig().getDouble("economy.starting-money", 1000);
            d.crystals = plugin.getConfig().getLong("economy.starting-crystals", 0);
            d.gems = plugin.getConfig().getLong("economy.starting-gems", 0);
            players.put(id, d);
        } else if (!name.equals(d.name)) {
            names.remove(d.name.toLowerCase(Locale.ROOT));
            d.name = name;
        }
        names.put(name.toLowerCase(Locale.ROOT), id);
        return d;
    }

    public PlayerData find(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        UUID id = names.get(n);
        if (id == null) {
            String pre = plugin.getConfig().getString("bedrock-prefix", ".");
            if (!pre.isEmpty()) id = names.get(pre.toLowerCase(Locale.ROOT) + n);
        }
        return id == null ? null : players.get(id);
    }

    public Collection<PlayerData> all() { return players.values(); }

    public Location spawn() { return spawn == null ? null : Locs.parse(spawn); }
    public void setSpawn(Location l) { spawn = Locs.str(l); }

    // ---- AFK-Zone ----
    public void setAfk(int which, Location l) {
        String v = Locs.str(l);
        if (which == 1) afkPos1 = v; else if (which == 2) afkPos2 = v; else afkSpawn = v;
    }
    public Location afk(int which) {
        String v = which == 1 ? afkPos1 : which == 2 ? afkPos2 : afkSpawn;
        return v == null ? null : Locs.parse(v);
    }

    // ---- Crate-Blöcke ----
    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }
    public String crateAt(Location l) { return crates.get(key(l)); }
    public void setCrate(Location l, String id) { crates.put(key(l), id); }
    public boolean removeCrate(Location l) { return crates.remove(key(l)) != null; }
    public void removeCratesOf(String id) { crates.values().removeIf(v -> v.equalsIgnoreCase(id)); }
    public Map<String, String> crateBlocks() { return Collections.unmodifiableMap(crates); }
}
