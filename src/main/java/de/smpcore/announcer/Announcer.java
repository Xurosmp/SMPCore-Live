package de.smpcore.announcer;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Automatische Chat-Nachrichten (announcer in der config.yml). */
public final class Announcer {
    private final SMPCore plugin;
    private int index = 0;

    public Announcer(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        long ticks = 20L * Math.max(10, plugin.getConfig().getLong("announcer.interval-seconds", 300));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), ticks, ticks);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("announcer.enabled", true)) return;
        List<String> msgs = plugin.getConfig().getStringList("announcer.messages");
        if (msgs.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String m = msgs.get(index++ % msgs.size());
        Msg.use("info");
        try {
            for (Player p : Bukkit.getOnlinePlayers()) Msg.send(p, m);
        } finally { Msg.reset(); }
    }
}
