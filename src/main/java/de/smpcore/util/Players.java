package de.smpcore.util;

import de.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class Players {
    private Players() {}

    /** Findet Online-Spieler, auch Bedrock-Namen mit/ohne Floodgate-Prefix. */
    public static Player online(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        String pre = SMPCore.get().getConfig().getString("bedrock-prefix", ".");
        if (!pre.isEmpty()) p = Bukkit.getPlayerExact(pre + name);
        return p;
    }

    public static List<String> names(String prefix) {
        return complete(prefix, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    public static List<String> complete(String prefix, Collection<String> options) {
        String l = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(l)).sorted().toList();
    }
}
