package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.CrystalShopMenu;
import de.smpcore.gui.ShopMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** shop, sell, crystalshop */
public final class ShopCommands implements TabExecutor {
    private final SMPCore plugin;

    public ShopCommands(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.feature("shop")) { Msg.error(s, "Der Shop ist derzeit deaktiviert."); return true; }
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "shop" -> ShopMenus.openMain(p);
            case "crystalshop" -> {
                if (plugin.crystalShop().hasLayout()) new de.smpcore.gui.CrystalStoreMenu(p).open();
                else new CrystalShopMenu(p, false).open();
            }
            case "gemshop" -> new CrystalShopMenu(p, true).open();
            case "sell" -> {
                if (a.length == 0) { de.smpcore.gui.SellMenu.open(p); return true; }
                if (a[0].equalsIgnoreCase("hand")) plugin.shop().sellHand(p);
                else if (a[0].equalsIgnoreCase("all")) plugin.shop().sellAll(p);
                else Msg.send(p, "Nutzung: <white>/sell <gray>(GUI), <white>/sell hand <gray>oder <white>/sell all");
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("sell") && a.length == 1) return Players.complete(a[0], List.of("hand", "all"));
        return List.of();
    }
}
