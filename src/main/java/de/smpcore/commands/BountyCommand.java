package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.BountyMenu;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public final class BountyCommand implements TabExecutor {
    private final SMPCore plugin;

    public BountyCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.feature("bounty")) { Msg.error(s, "Kopfgelder sind derzeit deaktiviert."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) { new BountyMenu(p).open(); return true; }
        if (a.length < 2) { Msg.error(p, "Nutzung: /bounty <Spieler> <Betrag>"); return true; }
        PlayerData t = plugin.data().find(a[0]);
        if (t == null) { Msg.error(p, "Spieler nicht gefunden (war er schon mal online?)."); return true; }
        double amt;
        try { amt = Num.parse(a[1]); } catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Betrag."); return true; }
        plugin.bounties().place(p, t, amt);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? Players.names(a[0]) : List.of();
    }
}
