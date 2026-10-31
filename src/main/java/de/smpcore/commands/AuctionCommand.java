package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.AuctionMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class AuctionCommand implements TabExecutor {
    private final SMPCore plugin;

    public AuctionCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.feature("auction")) { Msg.error(s, "Das Auktionshaus ist derzeit deaktiviert."); return true; }
        if (a.length == 0) { AuctionMenus.open(p, null); return true; }
        switch (a[0].toLowerCase()) {
            case "sell" -> {
                if (a.length < 2) { Msg.error(p, "Nutzung: /ah sell <Preis>"); return true; }
                try { plugin.auctions().create(p, Num.parse(a[1])); }
                catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Preis. Beispiele: 500, 2.5k, 1m"); }
            }
            case "search" -> {
                if (a.length < 2) { Msg.error(p, "Nutzung: /ah search <Text>"); return true; }
                AuctionMenus.open(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "mine" -> new AuctionMenus.Mine(p).open();
            default -> AuctionMenus.open(p, String.join(" ", a));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? Players.complete(a[0], List.of("sell", "search", "mine")) : List.of();
    }
}
