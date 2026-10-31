package de.smpcore.util;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Platzhalter für Scoreboard und TAB-Liste. */
public final class Placeholders {
    private Placeholders() {}

    public static String apply(String line, Player p) {
        SMPCore pl = SMPCore.get();
        line = pl.packs().apply(line, p);
        PlayerData d = pl.data().get(p.getUniqueId());
        if (d == null) return line;
        double bounty = pl.bounties().get(p.getUniqueId());
        ClanManager.Clan clan = pl.clans().of(p.getUniqueId());
        String ip = pl.getConfig().getString("server-ip", "localhost");
        return line
                .replace("%player%", Msg.esc(p.getName()))
                .replace("%money%", EconomyManager.fmt(d.money))
                .replace("%gems%", Num.format(d.gems))
                .replace("%crystals%", Num.format(d.crystals))
                .replace("%kills%", String.valueOf(d.kills))
                .replace("%deaths%", String.valueOf(d.deaths))
                .replace("%playtime%", Num.duration(d.playtime * 1000))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%bounty%", bounty > 0 ? EconomyManager.fmt(bounty) : "-")
                .replace("%ping%", String.valueOf(p.getPing()))
                .replace("%rank%", pl.ranks().rankOf(p.getUniqueId()).display)
                .replace("%keyall%", pl.keyall().enabled() ? Num.mmss(pl.keyall().secondsLeft()) : "aus")
                .replace("%clan%", clan == null ? "???" : Msg.esc(clan.name))
                .replace("%server%", pl.getConfig().getString("server-name", "SERVER"))
                .replace("%IP%", ip.toUpperCase(Locale.ROOT))
                .replace("%ip%", ip);
    }
}
