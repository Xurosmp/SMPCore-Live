package de.smpcore.live.util;

import de.smpcore.live.SMPCoreLive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Nachrichten mit Modul-Prefix (live / combat / holo / admin), Formate stehen in der config.yml. */
public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component c(String s) { return MM.deserialize(s); }

    public static String esc(String s) { return MM.escapeTags(s); }

    public static String prefix(String key) {
        return SMPCoreLive.get().getConfig().getString("prefixes." + key, "");
    }

    public static void send(CommandSender to, String module, String msg) {
        to.sendMessage(c(prefix(module) + msg));
    }

    public static void ok(CommandSender to, String module, String msg) {
        to.sendMessage(c(prefix(module) + "<green>" + msg));
    }

    public static void error(CommandSender to, String module, String msg) {
        to.sendMessage(c(prefix(module) + "<red>" + msg));
    }
}
