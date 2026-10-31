package de.smpcore.gui;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Texteingabe per Schild (Bedrock-tauglich) mit Chat als Fallback.
 * Ein kurzzeitiges Schild erscheint in der Luft neben dem Spieler (nur auf Luft-Blöcken) und wird danach wieder entfernt.
 */
public final class SignInput implements Listener {
    private record Session(Location loc, Consumer<String> callback, Runnable onCancel, boolean sign) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int[][] OFFSETS = {{0, 3, 0}, {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2}, {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}};

    /** Fragt einen Text ab. hints = bis zu 2 Hinweiszeilen auf dem Schild. */
    public static void ask(Player p, String[] hints, Consumer<String> callback, Runnable onCancel) {
        SMPCore pl = SMPCore.get();
        close(p);
        boolean useSign = pl.getConfig().getString("orders.input", "SIGN").equalsIgnoreCase("SIGN");
        Block spot = useSign ? findSpot(p) : null;
        if (spot != null) {
            spot.setType(Material.OAK_SIGN, false);
            if (spot.getState() instanceof Sign sign) {
                SignSide side = sign.getSide(Side.FRONT);
                side.line(0, Component.empty());
                side.line(1, Msg.c("<dark_gray>^^^^^^^^^^^^"));
                side.line(2, Msg.c(hints.length > 0 ? hints[0] : ""));
                side.line(3, Msg.c(hints.length > 1 ? hints[1] : ""));
                sign.setWaxed(false);
                sign.update(true, false);
                Session cur = new Session(spot.getLocation(), callback, onCancel, true);
                SESSIONS.put(p.getUniqueId(), cur);
                p.openSign(sign);
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (SESSIONS.get(p.getUniqueId()) == cur) {
                        SESSIONS.remove(p.getUniqueId());
                        clearSign(cur);
                        if (onCancel != null) onCancel.run();
                    }
                }, 20L * 60);
                return;
            }
            spot.setType(Material.AIR, false);
        }
        // Chat-Fallback
        Session cur = new Session(null, callback, onCancel, false);
        SESSIONS.put(p.getUniqueId(), cur);
        p.sendMessage(Msg.c("<yellow>» " + String.join(" ", hints) + " <gray>Schreibe den Wert in den Chat <dark_gray>(oder 'abbrechen')"));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (SESSIONS.get(p.getUniqueId()) == cur) {
                SESSIONS.remove(p.getUniqueId());
                if (onCancel != null) onCancel.run();
            }
        }, 20L * 60);
    }

    private static Block findSpot(Player p) {
        Block base = p.getLocation().getBlock();
        for (int[] o : OFFSETS) {
            Block b = base.getRelative(o[0], o[1], o[2]);
            if (b.getType().isAir() && b.getY() > b.getWorld().getMinHeight() && b.getY() < b.getWorld().getMaxHeight() - 1) return b;
        }
        return null;
    }

    private static void clearSign(Session s) {
        if (s.sign() && s.loc() != null && s.loc().getBlock().getType() == Material.OAK_SIGN) s.loc().getBlock().setType(Material.AIR, false);
    }

    private static void close(Player p) {
        Session s = SESSIONS.remove(p.getUniqueId());
        if (s != null) clearSign(s);
    }

    /** Beim Abschalten alle temporären Schilder entfernen. */
    public static void cleanup() {
        for (Session s : SESSIONS.values()) clearSign(s);
        SESSIONS.clear();
    }

    private static void finish(Player p, String text) {
        Session s = SESSIONS.remove(p.getUniqueId());
        if (s == null) return;
        Bukkit.getScheduler().runTask(SMPCore.get(), () -> {
            clearSign(s);
            if (text.isEmpty() || text.equalsIgnoreCase("abbrechen")) {
                if (s.onCancel() != null) s.onCancel().run();
            } else {
                s.callback().accept(text);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSign(SignChangeEvent e) {
        Session s = SESSIONS.get(e.getPlayer().getUniqueId());
        if (s == null || !s.sign() || s.loc() == null || !e.getBlock().getLocation().equals(s.loc())) return;
        e.setCancelled(true);
        Component c = e.line(0);
        String text = c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c).trim();
        finish(e.getPlayer(), text);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Session s = SESSIONS.get(e.getPlayer().getUniqueId());
        if (s == null || s.sign()) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(SMPCore.get(), () -> finish(p, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { close(e.getPlayer()); }
}
