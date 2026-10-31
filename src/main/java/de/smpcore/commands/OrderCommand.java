package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.gui.OrderMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /order | /order create <Item|hand> <Menge> <Preis pro Stück> | /order mine | /order search <Item> */
public final class OrderCommand implements TabExecutor {
    private final SMPCore plugin;

    public OrderCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        if (!plugin.feature("orders")) { Msg.error(p, "Orders sind derzeit deaktiviert."); return true; }
        if (a.length == 0) { OrderMenus.open(p, null); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "mine" -> new OrderMenus.Mine(p).open();
            case "search" -> {
                if (a.length < 2) { Msg.error(p, "Nutzung: /order search <Item>"); return true; }
                OrderMenus.open(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "create" -> {
                if (a.length < 4) { Msg.error(p, "Nutzung: /order create <Item|hand> <Menge> <Preis pro Stück>"); return true; }
                Material m = a[1].equalsIgnoreCase("hand") ? p.getInventory().getItemInMainHand().getType() : Material.matchMaterial(a[1]);
                if (m == null || m.isAir()) { Msg.error(p, "Unbekanntes Item. Beispiel: /order create diamond 64 350"); return true; }
                int amount;
                double price;
                try {
                    amount = Integer.parseInt(a[2]);
                    price = Num.parse(a[3]);
                } catch (NumberFormatException ex) { Msg.error(p, "Ungültige Menge oder Preis. Preis-Beispiele: 350, 1.5k"); return true; }
                plugin.orders().create(p, m, amount, price);
            }
            default -> OrderMenus.open(p, String.join(" ", a));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], List.of("create", "mine", "search"));
        if (a.length == 2 && a[0].equalsIgnoreCase("create")) {
            List<String> mats = Arrays.stream(Material.values()).filter(m -> m.isItem() && !m.isAir())
                    .map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
            List<String> r = Players.complete(a[1], mats);
            return r.size() > 40 ? r.subList(0, 40) : r;
        }
        return List.of();
    }
}
