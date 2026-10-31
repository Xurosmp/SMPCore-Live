package de.smpcore.tab;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.rank.RankManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Eigene TAB-Liste: Header/Footer (config.yml -> tab), Name mit [CLAN] + Rang-Prefix,
 * Sortierung nach Rang (über Teams "xr###_rang"), optional Rang-Prefix über dem Kopf.
 */
public final class TabManager {
    private final SMPCore plugin;

    public TabManager(SMPCore plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        String header = String.join("\n", plugin.getConfig().getStringList("tab.header"));
        String footer = String.join("\n", plugin.getConfig().getStringList("tab.footer"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(Msg.c(Placeholders.apply(header, p)), Msg.c(Placeholders.apply(footer, p)));
            p.playerListName(listName(p));
        }
        syncTeams();
    }

    private Component listName(Player p) {
        ClanManager.Clan clan = plugin.clans().of(p.getUniqueId());
        RankManager.Rank r = plugin.ranks().rankOf(p.getUniqueId());
        String clanTag = clan == null ? "" : "<dark_gray>[<yellow>" + clan.tag + "<dark_gray>] ";
        String nc = plugin.getConfig().getString("tab.name-color", "<white>");
        return Msg.c(clanTag + (r == null ? "" : r.prefix) + nc + Msg.esc(p.getName()));
    }

    private static String teamName(RankManager.Rank r) {
        String id = r.id.length() > 10 ? r.id.substring(0, 10) : r.id;
        return String.format("xr%03d_%s", Math.max(0, Math.min(999, 999 - r.weight)), id);
    }

    /** Sortiert die TAB-Liste nach Rang (auf der Haupt- und allen Spieler-Scoreboards). */
    private void syncTeams() {
        List<Scoreboard> all = new ArrayList<>(plugin.scoreboards().boards());
        all.add(Bukkit.getScoreboardManager().getMainScoreboard());
        boolean nametag = plugin.getConfig().getBoolean("tab.nametag-prefix", true);
        for (Scoreboard sb : all) {
            for (Player q : Bukkit.getOnlinePlayers()) {
                RankManager.Rank r = plugin.ranks().rankOf(q.getUniqueId());
                if (r == null) continue;
                String tn = teamName(r);
                Team t = sb.getTeam(tn);
                if (t == null) t = sb.registerNewTeam(tn);
                t.prefix(nametag ? Msg.c(r.prefix + plugin.getConfig().getString("tab.name-color", "<white>")) : Component.empty());
                Team cur = sb.getEntryTeam(q.getName());
                if (cur != t) {
                    if (cur != null && cur.getName().startsWith("xr")) cur.removeEntry(q.getName());
                    t.addEntry(q.getName());
                }
            }
            for (Team t : sb.getTeams()) {
                if (!t.getName().startsWith("xr")) continue;
                for (String entry : new HashSet<>(t.getEntries())) {
                    if (Bukkit.getPlayerExact(entry) == null) t.removeEntry(entry);
                }
            }
        }
    }
}
