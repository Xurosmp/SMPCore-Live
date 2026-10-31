package de.smpcore.warp;

import de.smpcore.SMPCore;
import de.smpcore.util.Locs;
import de.smpcore.util.Persist;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class WarpManager {
    public record Warp(String name, String loc, Material icon) {}

    private final SMPCore plugin;
    private final File file;
    private final Map<String, Warp> warps = new LinkedHashMap<>();

    public WarpManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
    }

    public void load() {
        warps.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("warps");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            Material m = Material.matchMaterial(c.getString("icon", "ENDER_PEARL"));
            warps.put(k, new Warp(k, c.getString("loc", ""), m == null ? Material.ENDER_PEARL : m));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Warp w : warps.values()) {
            y.set("warps." + w.name() + ".loc", w.loc());
            y.set("warps." + w.name() + ".icon", w.icon().name());
        }
        Persist.write(plugin, file, y.saveToString(), true);
    }

    public Collection<Warp> all() { return warps.values(); }
    public Warp get(String name) { return name == null ? null : warps.get(name.toLowerCase(Locale.ROOT)); }
    public Location location(Warp w) { return Locs.parse(w.loc()); }

    public void set(String name, Location l, Material icon) {
        String k = name.toLowerCase(Locale.ROOT);
        warps.put(k, new Warp(k, Locs.str(l), icon));
        save();
    }

    public boolean delete(String name) {
        boolean r = warps.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (r) save();
        return r;
    }
}
