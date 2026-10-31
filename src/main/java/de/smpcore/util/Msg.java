package de.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nachrichten mit Kurz-Prefix pro Befehl, z.B. "AH ▸", "HOME ▸" (max. 4 Zeichen).
 * Der Prefix richtet sich nach dem gerade ausgeführten Befehl/Menü (Msg.use / Msg.reset).
 */
public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static String format = "<green><bold>%tag%</bold> <dark_gray>▸ <gray>";
    private static final Map<String, String> TAGS = new HashMap<>();
    private static String current = "default";

    private Msg() {}

    public static void configure(ConfigurationSection cfg) {
        format = cfg.getString("prefix-format", format);
        TAGS.clear();
        ConfigurationSection s = cfg.getConfigurationSection("prefixes");
        if (s != null) for (String k : s.getKeys(false)) TAGS.put(k.toLowerCase(Locale.ROOT), limit(s.getString(k, k)));
        TAGS.putIfAbsent("default", "SMP");
    }

    private static String limit(String raw) {
        String plain = raw.replaceAll("<[^>]*>", "");
        return plain.length() <= 5 ? raw : plain.substring(0, 5);
    }

    public static void use(String key) { current = key == null ? "default" : key.toLowerCase(Locale.ROOT); }
    public static void reset() { current = "default"; }
    public static String current() { return current; }

    private static String prefix() {
        String t = TAGS.getOrDefault(current, TAGS.getOrDefault("default", "SMP"));
        return format.replace("%tag%", t);
    }

    public static Component c(String s) { return MM.deserialize(s); }
    public static Component item(String s) { return MM.deserialize("<!italic>" + s); }
    public static String esc(String s) { return MM.escapeTags(s); }

    public static void send(CommandSender to, String msg) { to.sendMessage(c(prefix() + msg)); }
    public static void ok(CommandSender to, String msg) { to.sendMessage(c(prefix() + "<green>" + msg)); }
    public static void error(CommandSender to, String msg) { to.sendMessage(c(prefix() + "<red>" + msg)); }
}
