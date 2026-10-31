package de.smpcore.bounty;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class BountyManager {
    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Double> bounties = new LinkedHashMap<>();

    public BountyManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
    }

    public void load() {
        bounties.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("bounties");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { bounties.put(UUID.fromString(k), s.getDouble(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        bounties.forEach((k, v) -> y.set("bounties." + k, v));
        Persist.write(plugin, file, y.saveToString(), async);
    }

    public Map<UUID, Double> all() { return Collections.unmodifiableMap(bounties); }
    public double get(UUID id) { return bounties.getOrDefault(id, 0.0); }

    public void remove(UUID id) {
        bounties.remove(id);
        save(true);
    }

    public void place(Player p, PlayerData target, double amount) {
        double min = plugin.getConfig().getDouble("bounty.min-amount", 1000);
        if (target.uuid.equals(p.getUniqueId())) { Msg.error(p, "Du kannst kein Kopfgeld auf dich selbst setzen."); return; }
        if (amount < min) { Msg.error(p, "Mindestbetrag: " + EconomyManager.fmt(min)); return; }
        if (!plugin.economy().take(p.getUniqueId(), amount)) {
            Msg.error(p, "Nicht genug Geld! Du brauchst " + EconomyManager.fmt(amount));
            return;
        }
        double total = bounties.merge(target.uuid, amount, Double::sum);
        save(true);
        Bukkit.broadcast(Msg.c("<dark_gray>[<red>Kopfgeld<dark_gray>] <white>" + Msg.esc(p.getName())
                + " <gray>hat <green>" + EconomyManager.fmt(amount) + " <gray>auf <red>" + Msg.esc(target.name)
                + " <gray>gesetzt! <dark_gray>(Gesamt: <green>" + EconomyManager.fmt(total) + "<dark_gray>)"));
    }

    public void onKill(Player killer, Player victim) {
        Double b = bounties.remove(victim.getUniqueId());
        if (b == null) return;
        plugin.economy().add(killer.getUniqueId(), b);
        save(true);
        Bukkit.broadcast(Msg.c("<dark_gray>[<red>Kopfgeld<dark_gray>] <white>" + Msg.esc(killer.getName())
                + " <gray>hat das Kopfgeld auf <red>" + Msg.esc(victim.getName())
                + " <gray>kassiert: <green>" + EconomyManager.fmt(b)));
    }
}
