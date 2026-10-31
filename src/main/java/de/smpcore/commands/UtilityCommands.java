package de.smpcore.commands;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.KitMenu;
import de.smpcore.gui.WarpMenu;
import de.smpcore.kit.KitManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Players;
import de.smpcore.warp.WarpManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** back, warp, setwarp, delwarp, kit, msg, r, heal, feed, fly, craft, ec, trash, vanish, sign */
public final class UtilityCommands implements TabExecutor {
    private final SMPCore plugin;
    private final Map<UUID, UUID> lastMsg = new HashMap<>();

    public UtilityCommands(SMPCore plugin) { this.plugin = plugin; }

    private boolean can(CommandSender s, String node) {
        return s.hasPermission("smpcore.cmd." + node) || s.hasPermission("smpcore.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { Msg.error(s, "Nur für Spieler."); return true; }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return true;
        String n = c.getName().toLowerCase(Locale.ROOT);
        switch (n) {
            case "back" -> {
                if (d.lastLocation == null) { Msg.error(p, "Es gibt keinen Ort, zu dem du zurück kannst."); return true; }
                plugin.teleports().teleport(p, d.lastLocation, "Zurück");
            }
            case "warp" -> {
                if (a.length == 0) { new WarpMenu(p).open(); return true; }
                WarpManager.Warp w = plugin.warps().get(a[0]);
                Location l = w == null ? null : plugin.warps().location(w);
                if (l == null) { Msg.error(p, "Diesen Warp gibt es nicht. Liste: /warp"); return true; }
                plugin.teleports().teleport(p, l, w.name());
            }
            case "setwarp" -> {
                if (!p.hasPermission("smpcore.admin")) { Msg.error(p, "Keine Rechte."); return true; }
                if (a.length < 1 || !a[0].matches("[A-Za-z0-9_-]{1,16}")) { Msg.error(p, "Nutzung: /setwarp <Name> (Item in der Hand = Icon)"); return true; }
                Material icon = p.getInventory().getItemInMainHand().getType();
                plugin.warps().set(a[0], p.getLocation(), icon.isAir() ? Material.ENDER_PEARL : icon);
                Msg.ok(p, "Warp '" + a[0].toLowerCase(Locale.ROOT) + "' gesetzt.");
            }
            case "delwarp" -> {
                if (!p.hasPermission("smpcore.admin")) { Msg.error(p, "Keine Rechte."); return true; }
                if (a.length < 1 || !plugin.warps().delete(a[0])) Msg.error(p, "Warp nicht gefunden.");
                else Msg.ok(p, "Warp gelöscht.");
            }
            case "kit" -> {
                if (a.length == 0) { new KitMenu(p).open(); return true; }
                KitManager.Kit k = plugin.kits().get(a[0]);
                if (k == null) { Msg.error(p, "Dieses Kit gibt es nicht. Liste: /kit"); return true; }
                plugin.kits().claim(p, k);
            }
            case "msg" -> {
                if (a.length < 2) { Msg.error(p, "Nutzung: /msg <Spieler> <Nachricht>"); return true; }
                Player t = Players.online(a[0]);
                if (t == null || t.equals(p)) { Msg.error(p, "Spieler nicht gefunden."); return true; }
                dm(p, t, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "r" -> {
                UUID u = lastMsg.get(p.getUniqueId());
                Player t = u == null ? null : Bukkit.getPlayer(u);
                if (t == null) { Msg.error(p, "Niemand, dem du antworten kannst."); return true; }
                if (a.length < 1) { Msg.error(p, "Nutzung: /r <Nachricht>"); return true; }
                dm(p, t, String.join(" ", a));
            }
            case "heal" -> {
                if (!can(p, "heal")) { Msg.error(p, "Keine Rechte."); return true; }
                Player t = a.length > 0 && p.hasPermission("smpcore.cmd.others") ? Players.online(a[0]) : p;
                if (t == null) { Msg.error(p, "Spieler nicht gefunden."); return true; }
                healFeed(t, true);
                Msg.send(p, "<green>Geheilt" + (t.equals(p) ? "." : ": <white>" + Msg.esc(t.getName())));
            }
            case "feed" -> {
                if (!can(p, "feed")) { Msg.error(p, "Keine Rechte."); return true; }
                Player t = a.length > 0 && p.hasPermission("smpcore.cmd.others") ? Players.online(a[0]) : p;
                if (t == null) { Msg.error(p, "Spieler nicht gefunden."); return true; }
                healFeed(t, false);
                Msg.send(p, "<green>Gesättigt" + (t.equals(p) ? "." : ": <white>" + Msg.esc(t.getName())));
            }
            case "fly" -> {
                if (!can(p, "fly")) { Msg.error(p, "Keine Rechte."); return true; }
                boolean on = !p.getAllowFlight();
                p.setAllowFlight(on);
                if (!on) p.setFlying(false);
                Msg.send(p, "Fliegen: " + (on ? "<green>an" : "<red>aus"));
            }
            case "craft" -> {
                if (!can(p, "craft")) { Msg.error(p, "Keine Rechte."); return true; }
                p.openWorkbench(null, true);
            }
            case "ec" -> {
                if (!can(p, "ec")) { Msg.error(p, "Keine Rechte."); return true; }
                p.openInventory(p.getEnderChest());
            }
            case "trash" -> {
                if (!can(p, "trash")) { Msg.error(p, "Keine Rechte."); return true; }
                p.openInventory(Bukkit.createInventory(null, 27, Msg.c("<red><bold>Mülleimer")));
            }
            case "vanish" -> {
                if (!can(p, "vanish")) { Msg.error(p, "Keine Rechte."); return true; }
                boolean now = plugin.vanished().add(p.getUniqueId());
                if (!now) plugin.vanished().remove(p.getUniqueId());
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o.equals(p) || o.hasPermission("smpcore.admin")) continue;
                    if (now) o.hidePlayer(plugin, p); else o.showPlayer(plugin, p);
                }
                Msg.send(p, "Vanish: " + (now ? "<green>an" : "<red>aus"));
            }
            case "sign" -> sign(p, d, a);
            default -> { }
        }
        return true;
    }

    /** /sign [Text]: signiert das Item in der Hand. Tageslimit je nach Rang (Spieler: 1x pro Tag). */
    private void sign(Player p, PlayerData d, String[] a) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Msg.error(p, "Halte das Item, das du signieren willst, in der Hand."); return; }
        ItemMeta meta = hand.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "signed_by");
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) { Msg.error(p, "Dieses Item ist schon signiert."); return; }
        String text = a.length > 0 ? String.join(" ", a) : "";
        if (text.length() > 40) { Msg.error(p, "Dein Text darf maximal 40 Zeichen lang sein."); return; }
        long today = LocalDate.now().toEpochDay();
        if (d.signDay != today) { d.signDay = today; d.signCount = 0; }
        int limit = plugin.ranks().signLimit(p);
        if (d.signCount >= limit) {
            Msg.error(p, "Du hast heute schon " + d.signCount + "/" + limit + " Signaturen benutzt. Morgen wieder!");
            return;
        }
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Msg.item("<gray>Signiert von <white>" + Msg.esc(p.getName()) + " <dark_gray>" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
        if (!text.isEmpty()) lore.add(Msg.item("<gray>\"<white>" + Msg.esc(text) + "<gray>\""));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, p.getName());
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
        d.signCount++;
        Msg.send(p, "<green>Item signiert! <dark_gray>(heute " + d.signCount + "/" + limit + ")");
    }

    private void dm(Player from, Player to, String text) {
        String t = Msg.esc(text);
        from.sendMessage(Msg.c("<dark_gray>[<gray>Du <dark_gray>→ <white>" + Msg.esc(to.getName()) + "<dark_gray>] <gray>" + t));
        to.sendMessage(Msg.c("<dark_gray>[<white>" + Msg.esc(from.getName()) + " <dark_gray>→ <gray>Du<dark_gray>] <gray>" + t));
        lastMsg.put(from.getUniqueId(), to.getUniqueId());
        lastMsg.put(to.getUniqueId(), from.getUniqueId());
    }

    @SuppressWarnings("deprecation")
    private static void healFeed(Player t, boolean health) {
        if (health) t.setHealth(t.getMaxHealth());
        t.setFoodLevel(20);
        t.setSaturation(20f);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (a.length != 1) return List.of();
        if (n.equals("msg") || n.equals("heal") || n.equals("feed")) return Players.names(a[0]);
        if (n.equals("warp") || n.equals("delwarp")) return Players.complete(a[0], plugin.warps().all().stream().map(WarpManager.Warp::name).toList());
        if (n.equals("kit")) return Players.complete(a[0], plugin.kits().all().stream().map(k -> k.id).toList());
        return List.of();
    }
}
