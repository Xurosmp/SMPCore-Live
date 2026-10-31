package de.smpcore.live;

import de.smpcore.SMPCore;
import de.smpcore.live.combat.CombatListener;
import de.smpcore.live.combat.CombatManager;
import de.smpcore.live.holo.HoloCommand;
import de.smpcore.live.holo.HologramManager;
import de.smpcore.live.live.LiveCommand;
import de.smpcore.live.live.LiveManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/** Addon für SMPCore: /live, Combat-Log, Hologramm-Leaderboards. */
public final class SMPCoreLive extends JavaPlugin {
    private static SMPCoreLive instance;
    private LiveManager live;
    private CombatManager combat;
    private HologramManager holograms;

    public static SMPCoreLive get() { return instance; }
    public LiveManager live() { return live; }
    public CombatManager combat() { return combat; }
    public HologramManager holograms() { return holograms; }
    public SMPCore core() { return SMPCore.get(); }

    public boolean feature(String name) { return getConfig().getBoolean("features." + name, true); }

    @Override
    public void onEnable() {
        instance = this;
        if (SMPCore.get() == null) {
            getLogger().severe("SMPCore wurde nicht gefunden - SMPCoreLive wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();

        live = new LiveManager(this);
        combat = new CombatManager(this);
        holograms = new HologramManager(this);

        if (feature("live")) {
            getServer().getPluginManager().registerEvents(live, this);
            live.start();
            reg("live", new LiveCommand(this));
        }
        if (feature("combat")) {
            combat.load();
            getServer().getPluginManager().registerEvents(new CombatListener(this), this);
            combat.start();
        }
        if (feature("holograms")) {
            holograms.load();
            getServer().getPluginManager().registerEvents(holograms, this);
            holograms.start();
            reg("hololb", new HoloCommand(this));
        }
        reg("smpcorelive", new AdminCommand(this));
        getLogger().info("SMPCoreLive aktiviert (live=" + feature("live") + ", combat=" + feature("combat")
                + ", holograms=" + feature("holograms") + ").");
    }

    @Override
    public void onDisable() {
        if (combat != null) combat.shutdown();
        if (live != null) live.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        if (holograms != null && feature("holograms")) {
            holograms.reload();
        }
    }

    private void reg(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        c.setExecutor(ex);
        c.setTabCompleter(ex);
    }
}
