package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** balance, pay, eco, crystals, top */
public final class EconomyCommands implements TabExecutor {
    private final SMPCore plugin;

    public EconomyCommands(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(s, a);
            case "pay" -> pay(s, a);
            case "eco" -> admin(s, a, false);
            case "crystals" -> crystals(s, a);
            case "gems" -> gems(s, a);
            case "top" -> top(s, a, label);
            default -> { }
        }
        return true;
    }

    private void balance(CommandSender s, String[] a) {
        PlayerData d;
        if (a.length > 0) {
            d = plugin.data().find(a[0]);
            if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) {
            d = plugin.data().get(p.getUniqueId());
            if (d == null) return;
        } else { Msg.error(s, "Nutzung: /balance <Spieler>"); return; }
        Msg.send(s, "<white>" + Msg.esc(d.name) + "<gray>: <green>" + EconomyManager.fmt(d.money)
                + " <dark_gray>(" + Num.full(d.money) + ")");
    }

    private void pay(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return; }
        if (a.length < 2) { Msg.error(s, "Nutzung: /pay <Spieler> <Betrag>"); return; }
        PlayerData t = plugin.data().find(a[0]);
        if (t == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        if (t.uuid.equals(p.getUniqueId())) { Msg.error(s, "Du kannst dir nicht selbst Geld senden."); return; }
        double amt;
        try { amt = Num.parse(a[1]); } catch (NumberFormatException ex) { Msg.error(s, "Ungültiger Betrag."); return; }
        if (amt < 1) { Msg.error(s, "Mindestbetrag: $1"); return; }
        if (!plugin.economy().take(p.getUniqueId(), amt)) { Msg.error(s, "Nicht genug Geld!"); return; }
        plugin.economy().add(t.uuid, amt);
        Msg.send(s, "Du hast <green>" + EconomyManager.fmt(amt) + " <gray>an <white>" + Msg.esc(t.name) + " <gray>gesendet.");
        Player tp = plugin.getServer().getPlayer(t.uuid);
        if (tp != null) Msg.send(tp, "<white>" + Msg.esc(p.getName()) + " <gray>hat dir <green>" + EconomyManager.fmt(amt) + " <gray>gesendet.");
    }

    private void admin(CommandSender s, String[] a, boolean cr) {
        if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return; }
        if (a.length < 3) { Msg.error(s, "Nutzung: /" + (cr ? "crystals" : "eco") + " <give|take|set> <Spieler> <Betrag>"); return; }
        PlayerData d = plugin.data().find(a[1]);
        if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        double amt;
        try { amt = Num.parse(a[2]); } catch (NumberFormatException ex) { Msg.error(s, "Ungültiger Betrag."); return; }
        EconomyManager eco = plugin.economy();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> { if (cr) eco.addCrystals(d.uuid, (long) amt); else eco.add(d.uuid, amt); }
            case "take" -> { if (cr) eco.setCrystals(d.uuid, eco.crystals(d.uuid) - (long) amt); else eco.set(d.uuid, eco.balance(d.uuid) - amt); }
            case "set" -> { if (cr) eco.setCrystals(d.uuid, (long) amt); else eco.set(d.uuid, amt); }
            default -> { Msg.error(s, "Nutze give, take oder set."); return; }
        }
        Msg.ok(s, Msg.esc(d.name) + " hat jetzt " + (cr ? EconomyManager.fmtC(eco.crystals(d.uuid)) : EconomyManager.fmt(eco.balance(d.uuid))));
    }

    private void crystals(CommandSender s, String[] a) {
        if (a.length > 0 && List.of("give", "take", "set").contains(a[0].toLowerCase(Locale.ROOT))) { admin(s, a, true); return; }
        if (a.length >= 3 && a[0].equalsIgnoreCase("pay")) { payCrystals(s, a); return; }
        PlayerData d;
        if (a.length > 0) {
            d = plugin.data().find(a[0]);
            if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) {
            d = plugin.data().get(p.getUniqueId());
            if (d == null) return;
        } else { Msg.error(s, "Nutzung: /crystals <Spieler>"); return; }
        Msg.send(s, "<white>" + Msg.esc(d.name) + "<gray>: <light_purple>" + EconomyManager.fmtC(d.crystals));
    }

    private void gems(CommandSender s, String[] a) {
        if (a.length > 0 && List.of("give", "take", "set").contains(a[0].toLowerCase(Locale.ROOT))) {
            if (!s.hasPermission("smpcore.admin")) { Msg.error(s, "Keine Rechte."); return; }
            if (a.length < 3) { Msg.error(s, "Nutzung: /gems <give|take|set> <Spieler> <Menge>"); return; }
            PlayerData d = plugin.data().find(a[1]);
            if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
            long amt;
            try { amt = (long) Num.parse(a[2]); } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Menge."); return; }
            EconomyManager eco = plugin.economy();
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "give" -> eco.addGems(d.uuid, amt);
                case "take" -> eco.setGems(d.uuid, eco.gems(d.uuid) - amt);
                default -> eco.setGems(d.uuid, amt);
            }
            Msg.ok(s, Msg.esc(d.name) + " hat jetzt " + EconomyManager.fmtG(eco.gems(d.uuid)));
            Player tp = plugin.getServer().getPlayer(d.uuid);
            if (tp != null && a[0].equalsIgnoreCase("give")) Msg.send(tp, "<light_purple>+" + EconomyManager.fmtG(amt) + " <gray>erhalten!");
            return;
        }
        PlayerData d;
        if (a.length > 0) {
            d = plugin.data().find(a[0]);
            if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return; }
        } else if (s instanceof Player p) {
            d = plugin.data().get(p.getUniqueId());
            if (d == null) return;
        } else { Msg.error(s, "Nutzung: /gems <Spieler>"); return; }
        Msg.send(s, "<white>" + Msg.esc(d.name) + "<gray>: <light_purple>" + EconomyManager.fmtG(d.gems));
    }

    private void payCrystals(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        PlayerData t = plugin.data().find(a[1]);
        if (t == null || t.uuid.equals(p.getUniqueId())) { Msg.error(s, "Ungültiger Spieler."); return; }
        long amt;
        try { amt = (long) Num.parse(a[2]); } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Menge."); return; }
        if (amt < 1) { Msg.error(s, "Mindestens 1 Kristall."); return; }
        if (!plugin.economy().takeCrystals(p.getUniqueId(), amt)) { Msg.error(s, "Nicht genug Stars!"); return; }
        plugin.economy().addCrystals(t.uuid, amt);
        Msg.send(s, "Du hast <light_purple>" + EconomyManager.fmtC(amt) + " <gray>an <white>" + Msg.esc(t.name) + " <gray>gesendet.");
        Player tp = plugin.getServer().getPlayer(t.uuid);
        if (tp != null) Msg.send(tp, "<white>" + Msg.esc(p.getName()) + " <gray>hat dir <light_purple>" + EconomyManager.fmtC(amt) + " <gray>gesendet.");
    }

    private void top(CommandSender s, String[] a, String label) {
        String type = label.equalsIgnoreCase("baltop") ? "money" : (a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "money");
        if (!List.of("money", "crystals", "kills", "playtime").contains(type)) type = "money";
        String title = switch (type) {
            case "crystals" -> "Stars";
            case "kills" -> "Kills";
            case "playtime" -> "Spielzeit";
            default -> "Geld";
        };
        Msg.send(s, "<gold><bold>Top 10 – " + title);
        int i = 1;
        for (PlayerData d : plugin.economy().top(type, 10)) {
            String val = switch (type) {
                case "crystals" -> Num.format(d.crystals);
                case "kills" -> String.valueOf(d.kills);
                case "playtime" -> Num.duration(d.playtime * 1000);
                default -> EconomyManager.fmt(d.money);
            };
            s.sendMessage(Msg.c("<gray>" + i++ + ". <white>" + Msg.esc(d.name) + " <dark_gray>- <green>" + val));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (n.equals("top")) return a.length == 1 ? Players.complete(a[0], List.of("money", "crystals", "kills", "playtime")) : List.of();
        if (n.equals("eco")) {
            if (a.length == 1) return Players.complete(a[0], List.of("give", "take", "set"));
            if (a.length == 2) return Players.names(a[1]);
        }
        if (n.equals("gems")) {
            if (a.length == 1) {
                List<String> all = new java.util.ArrayList<>(s.hasPermission("smpcore.admin") ? List.of("give", "take", "set") : List.<String>of());
                all.addAll(Players.names(""));
                return Players.complete(a[0], all);
            }
            if (a.length == 2) return Players.names(a[1]);
        }
        if (n.equals("crystals")) {
            if (a.length == 1) {
                List<String> subs = s.hasPermission("smpcore.admin") ? List.of("pay", "give", "take", "set") : List.of("pay");
                List<String> all = new java.util.ArrayList<>(subs);
                all.addAll(Players.names(""));
                return Players.complete(a[0], all);
            }
            if (a.length == 2) return Players.names(a[1]);
        }
        if (a.length == 1) return Players.names(a[0]);
        if (a.length == 2 && n.equals("pay")) return List.of();
        return List.of();
    }
}
