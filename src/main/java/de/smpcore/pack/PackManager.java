package de.smpcore.pack;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resource-Pack: wird Java-Spielern automatisch geschickt. Wer es geladen hat, sieht Icons/Logo als Pack-Glyphen
 * (Scoreboard, TAB-Header), alle anderen (z.B. Bedrock) sehen die Unicode-Fallbacks.
 */
public final class PackManager {
    private final SMPCore plugin;
    private final Set<UUID> loaded = new HashSet<>();

    public PackManager(SMPCore plugin) { this.plugin = plugin; }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("resourcepack.enabled", false)
                && !plugin.getConfig().getString("resourcepack.url", "").isBlank();
    }

    public boolean isBedrock(Player p) {
        String pre = plugin.getConfig().getString("bedrock-prefix", ".");
        return !pre.isEmpty() && p.getName().startsWith(pre);
    }

    public boolean has(Player p) { return loaded.contains(p.getUniqueId()); }
    public void mark(Player p, boolean ok) { if (ok) loaded.add(p.getUniqueId()); else loaded.remove(p.getUniqueId()); }
    public void forget(Player p) { loaded.remove(p.getUniqueId()); }

    public void send(Player p) {
        if (!enabled() || isBedrock(p)) return;
        try {
            p.setResourcePack(plugin.getConfig().getString("resourcepack.url", ""),
                    hex(plugin.getConfig().getString("resourcepack.sha1", "")),
                    Msg.c(plugin.getConfig().getString("resourcepack.prompt", "")),
                    plugin.getConfig().getBoolean("resourcepack.force", false));
        } catch (Exception ex) {
            plugin.getLogger().warning("Resource-Pack konnte nicht gesendet werden: " + ex.getMessage());
        }
    }

    private static byte[] hex(String s) {
        if (s == null || s.length() != 40) return null;
        try {
            byte[] b = new byte[20];
            for (int i = 0; i < 20; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
            return b;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String pick(Player p, String path) {
        String glyph = plugin.getConfig().getString(path + ".glyph", "");
        String fallback = plugin.getConfig().getString(path + ".fallback", "");
        return has(p) && !glyph.isEmpty() ? "<white>" + glyph + "</white>" : fallback;
    }

    public String logo(Player p) { return pick(p, "resourcepack.logo"); }
    public String icon(Player p, String key) { return pick(p, "resourcepack.icons." + key); }

    /** Ersetzt %logo% und %i_<name>% je nachdem, ob der Spieler das Pack geladen hat. */
    public String apply(String line, Player p) {
        if (line.indexOf('%') < 0) return line;
        String out = line.replace("%logo%", logo(p));
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("resourcepack.icons");
        if (s != null) for (String k : s.getKeys(false)) out = out.replace("%i_" + k + "%", icon(p, k));
        return out;
    }
}
