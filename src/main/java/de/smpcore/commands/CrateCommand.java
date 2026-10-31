package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.crate.CrateManager;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.CrateMenus;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrateCommand implements TabExecutor {
    private final SMPCore plugin;

    public CrateCommand(SMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "keys" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("smpcore.admin");
        switch (sub) {
            case "keys", "menu" -> {
                if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
                new CrateMenus.Keys(p).open();
            }
            case "preview" -> {
                if (!(s instanceof Player p) || a.length < 2) { Msg.error(s, "Nutzung: /crate preview <id>"); return true; }
                CrateManager.Crate cr = plugin.crates().get(a[1]);
                if (cr == null) { Msg.error(s, "Crate nicht gefunden."); return true; }
                new CrateMenus.Preview(p, cr).open();
            }
            case "list" -> {
                if (!admin) { Msg.error(s, "Keine Rechte."); return true; }
                Msg.send(s, "Crates: <white>" + String.join(", ", plugin.crates().all().stream().map(x -> x.id).toList()));
            }
            case "set" -> {
                if (!admin) { Msg.error(s, "Keine Rechte."); return true; }
                if (!(s instanceof Player p) || a.length < 2) { Msg.error(s, "Nutzung: /crate set <id> (Block anschauen)"); return true; }
                CrateManager.Crate cr = plugin.crates().get(a[1]);
                Block b = p.getTargetBlockExact(6);
                if (cr == null) { Msg.error(s, "Crate nicht gefunden."); return true; }
                if (b == null) { Msg.error(s, "Schau einen Block an."); return true; }
                plugin.data().setCrate(b.getLocation(), cr.id);
                plugin.data().save(true);
                Msg.ok(s, "Block ist jetzt eine Crate: " + cr.id);
            }
            case "remove" -> {
                if (!admin) { Msg.error(s, "Keine Rechte."); return true; }
                if (!(s instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                if (b != null && plugin.data().removeCrate(b.getLocation())) {
                    plugin.data().save(true);
                    Msg.ok(s, "Crate entfernt.");
                } else Msg.error(s, "Das ist keine Crate.");
            }
            case "give", "giveall" -> {
                if (!admin) { Msg.error(s, "Keine Rechte."); return true; }
                boolean all = sub.equals("giveall");
                int idx = all ? 1 : 2;
                if (a.length < idx + 1) { Msg.error(s, "Nutzung: /crate give <Spieler> <id> [Anzahl] | /crate giveall <id> [Anzahl]"); return true; }
                CrateManager.Crate cr = plugin.crates().get(a[idx]);
                if (cr == null) { Msg.error(s, "Crate nicht gefunden."); return true; }
                int amount = 1;
                if (a.length > idx + 1) {
                    try { amount = Math.max(1, Integer.parseInt(a[idx + 1])); } catch (NumberFormatException ex) { Msg.error(s, "Ungültige Anzahl."); return true; }
                }
                if (all) {
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        plugin.crates().addKeys(p.getUniqueId(), cr.id, amount);
                        Msg.send(p, "<gold>+" + amount + " Key(s) <gray>für " + cr.name);
                    }
                    Msg.ok(s, "Keys an alle Online-Spieler vergeben.");
                } else {
                    PlayerData d = plugin.data().find(a[1]);
                    if (d == null) { Msg.error(s, "Spieler nicht gefunden."); return true; }
                    plugin.crates().addKeys(d.uuid, cr.id, amount);
                    Msg.ok(s, amount + " Key(s) an " + Msg.esc(d.name) + " vergeben.");
                    Player t = plugin.getServer().getPlayer(d.uuid);
                    if (t != null) Msg.send(t, "<gold>+" + amount + " Key(s) <gray>für " + cr.name);
                }
            }
            case "create", "delete", "setname", "seticon", "mode", "edit" -> {
                if (!admin) { Msg.error(s, "Keine Rechte."); return true; }
                if (a.length < 2) { Msg.error(s, "Nutzung: /crate " + sub + " <id>" + (sub.equals("setname") ? " <Name>" : "")); return true; }
                String id = a[1].toLowerCase(Locale.ROOT);
                if (sub.equals("create")) {
                    if (!id.matches("[a-z0-9_-]{1,16}")) { Msg.error(s, "Ungültige ID (a-z, 0-9, _ -, max. 16)."); return true; }
                    CrateManager.Crate nc = plugin.crates().create(id);
                    if (nc == null) Msg.error(s, "Diese Crate gibt es schon.");
                    else Msg.ok(s, "Crate '" + id + "' erstellt. Bearbeiten: /crate edit " + id);
                    return true;
                }
                CrateManager.Crate cr = plugin.crates().get(id);
                if (cr == null) { Msg.error(s, "Crate nicht gefunden."); return true; }
                switch (sub) {
                    case "delete" -> { plugin.crates().delete(id); Msg.ok(s, "Crate gelöscht."); }
                    case "setname" -> {
                        if (a.length < 3) { Msg.error(s, "Nutzung: /crate setname <id> <Name mit <farben>>"); return true; }
                        cr.name = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
                        plugin.crates().save();
                        Msg.ok(s, "Name gesetzt: " + cr.name);
                    }
                    case "seticon" -> {
                        if (!(s instanceof Player p)) return true;
                        org.bukkit.Material m = p.getInventory().getItemInMainHand().getType();
                        if (m.isAir()) { Msg.error(s, "Halte ein Item in der Hand."); return true; }
                        cr.icon = m;
                        plugin.crates().save();
                        Msg.ok(s, "Icon gesetzt.");
                    }
                    case "mode" -> {
                        cr.mode = cr.mode.equals("CHOOSE") ? "RANDOM" : "CHOOSE";
                        plugin.crates().save();
                        Msg.ok(s, "Modus: " + cr.mode);
                    }
                    default -> {
                        if (!(s instanceof Player p)) return true;
                        new de.smpcore.gui.AdminMenus.CrateEdit(p, cr).open();
                    }
                }
            }
            default -> Msg.error(s, "Unbekannter Unterbefehl.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> ids = plugin.crates().all().stream().map(x -> x.id).toList();
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("keys", "preview"));
            if (s.hasPermission("smpcore.admin")) subs.addAll(List.of("list", "set", "remove", "give", "giveall", "create", "delete", "setname", "seticon", "mode", "edit"));
            return Players.complete(a[0], subs);
        }
        if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "give" -> { return Players.names(a[1]); }
                case "preview", "set", "giveall", "delete", "setname", "seticon", "mode", "edit" -> { return Players.complete(a[1], ids); }
                default -> { return List.of(); }
            }
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("give")) return Players.complete(a[2], ids);
        return List.of();
    }
}
