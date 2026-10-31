package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.gui.ClanMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Players;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ClanCommand implements TabExecutor {
    private final SMPCore plugin;

    public ClanCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        ClanManager cm = plugin.clans();
        if (a.length == 0) { new ClanMenus.Main(p).open(); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> new ClanMenus.Main(p).open();
            case "create" -> {
                if (a.length < 3) { Msg.error(p, "Nutzung: /clan create <Name> <Tag>"); return true; }
                cm.create(p, a[1], a[2]);
            }
            case "invite" -> {
                Player t = a.length > 1 ? Players.online(a[1]) : null;
                if (t == null) { Msg.error(p, "Nutzung: /clan invite <Spieler> (muss online sein)"); return true; }
                cm.invite(p, t);
            }
            case "accept", "join" -> cm.accept(p, a.length > 1 ? a[1] : null);
            case "deny" -> cm.deny(p);
            case "leave" -> cm.leave(p);
            case "kick", "promote", "demote", "transfer" -> {
                PlayerData d = a.length > 1 ? plugin.data().find(a[1]) : null;
                if (d == null) { Msg.error(p, "Nutzung: /clan " + a[0].toLowerCase(Locale.ROOT) + " <Spieler>"); return true; }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "kick" -> cm.kick(p, d.uuid);
                    case "promote" -> cm.promote(p, d.uuid);
                    case "demote" -> cm.demote(p, d.uuid);
                    default -> cm.transfer(p, d.uuid);
                }
            }
            case "disband" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) { Msg.send(p, "Zum Auflösen: <white>/clan disband confirm"); return true; }
                cm.disband(p);
            }
            case "info" -> {
                ClanManager.Clan cl = a.length > 1 ? cm.get(a[1]) : cm.of(p.getUniqueId());
                if (cl == null) { Msg.error(p, "Clan nicht gefunden."); return true; }
                Msg.send(p, "<yellow><bold>" + Msg.esc(cl.name) + " <dark_gray>[<yellow>" + cl.tag + "<dark_gray>]");
                StringBuilder sb = new StringBuilder();
                for (UUID u : cl.members.keySet()) {
                    PlayerData d = plugin.data().get(u);
                    if (sb.length() > 0) sb.append("<gray>, ");
                    sb.append(cl.members.get(u) == ClanManager.Role.LEADER ? "<gold>" : "<white>").append(Msg.esc(d == null ? "?" : d.name));
                }
                p.sendMessage(Msg.c("<gray>Mitglieder (" + cl.members.size() + "): " + sb));
                p.sendMessage(Msg.c("<gray>Kills: <red>" + cl.kills + " <gray>Tode: <white>" + cl.deaths + " <gray>Bank: <green>" + EconomyManager.fmt(cl.bank)));
            }
            case "top" -> new ClanMenus.Top(p).open();
            case "chat" -> {
                if (cm.of(p.getUniqueId()) == null) { Msg.error(p, "Du bist in keinem Clan."); return true; }
                if (a.length > 1) cm.sendChat(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
                else Msg.send(p, "Clan-Chat: " + (cm.toggleChat(p.getUniqueId()) ? "<green>AN" : "<red>AUS"));
            }
            case "bank" -> {
                if (a.length < 3) { Msg.error(p, "Nutzung: /clan bank <deposit|withdraw> <Betrag>"); return true; }
                double amt;
                try { amt = Num.parse(a[2]); } catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Betrag."); return true; }
                if (a[1].equalsIgnoreCase("deposit")) cm.deposit(p, amt);
                else if (a[1].equalsIgnoreCase("withdraw")) cm.withdraw(p, amt);
                else Msg.error(p, "Nutze deposit oder withdraw.");
            }
            case "sethome" -> cm.setHome(p);
            case "home" -> cm.home(p);
            case "ff" -> cm.toggleFriendlyFire(p);
            default -> Msg.error(p, "Unbekannter Unterbefehl. Öffne das Menü mit /clan");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return Players.complete(a[0], List.of("menu", "create", "invite", "accept", "deny", "leave", "kick", "promote", "demote",
                "transfer", "disband", "info", "top", "chat", "bank", "sethome", "home", "ff"));
        if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "invite", "kick", "promote", "demote", "transfer" -> { return Players.names(a[1]); }
                case "accept", "info" -> { return Players.complete(a[1], plugin.clans().all().stream().map(x -> x.name).toList()); }
                case "bank" -> { return Players.complete(a[1], List.of("deposit", "withdraw")); }
                default -> { return List.of(); }
            }
        }
        return List.of();
    }
}
