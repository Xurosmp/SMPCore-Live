package de.smpcore.teleport;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportManager {
    public record Request(UUID from, long expires) {}

    private final SMPCore plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>(); // Schlüssel = Ziel

    public TeleportManager(SMPCore plugin) { this.plugin = plugin; }

    private void doTeleport(Player p, Location dest) {
        de.smpcore.data.PlayerData d = plugin.data().get(p.getUniqueId());
        if (d != null) d.lastLocation = p.getLocation().clone();
        p.teleportAsync(dest);
    }

    public void cancel(Player p) {
        BukkitTask t = pending.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    public void teleport(Player p, Location dest, String label) {
        int delay = plugin.getConfig().getInt("teleport.delay-seconds", 3);
        cancel(p);
        if (delay <= 0 || p.hasPermission("smpcore.tp.bypass")) {
            doTeleport(p, dest);
            p.sendActionBar(Msg.c("<green>Teleportiert: <white>" + Msg.esc(label)));
            return;
        }
        Location start = p.getLocation().clone();
        BukkitTask task = new BukkitRunnable() {
            int left = delay;

            @Override
            public void run() {
                if (!p.isOnline()) { pending.remove(p.getUniqueId()); cancel(); return; }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.5) {
                    pending.remove(p.getUniqueId());
                    cancel();
                    p.sendActionBar(Msg.c("<red>Teleport abgebrochen – du hast dich bewegt!"));
                    return;
                }
                if (left <= 0) {
                    pending.remove(p.getUniqueId());
                    cancel();
                    doTeleport(p, dest);
                    p.sendActionBar(Msg.c("<green>Teleportiert: <white>" + Msg.esc(label)));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1f);
                    return;
                }
                p.sendActionBar(Msg.c("<yellow>Teleport in <white>" + left + "s <gray>– nicht bewegen!"));
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        pending.put(p.getUniqueId(), task);
    }

    // ---- TPA ----
    public void request(Player from, Player to) {
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), System.currentTimeMillis() + 60_000L));
    }

    public Request takeRequest(UUID target) {
        Request r = requests.remove(target);
        if (r == null || r.expires() < System.currentTimeMillis()) return null;
        return r;
    }

    // ---- RTP (Oberwelt / Nether / End) ----
    public boolean rtpEnabled(String dim) {
        return plugin.getConfig().getBoolean("rtp.worlds." + dim + ".enabled", dim.equals("overworld"));
    }

    public void setRtpEnabled(String dim, boolean on) {
        plugin.getConfig().set("rtp.worlds." + dim + ".enabled", on);
        plugin.saveConfig();
    }

    /** null = erlaubt, sonst der Grund für die Sperre. Admins dürfen immer. */
    public String rtpBlockReason(Player p, String dim) {
        if (p.hasPermission("smpcore.admin")) return null;
        if (!rtpEnabled(dim)) return "Noch nicht freigeschaltet";
        if (dim.equals("nether") && !de.smpcore.listeners.DimensionListener.enabled(plugin, "nether")) return "Der Nether ist geschlossen";
        if (dim.equals("end") && !de.smpcore.listeners.DimensionListener.enabled(plugin, "end")) return "Das End ist geschlossen";
        return null;
    }

    private World rtpWorld(String dim) {
        String def = dim.equals("nether") ? "world_nether" : dim.equals("end") ? "world_the_end" : plugin.getConfig().getString("rtp.world", "world");
        World w = Bukkit.getWorld(plugin.getConfig().getString("rtp.worlds." + dim + ".world", def));
        if (w != null) return w;
        World.Environment env = dim.equals("nether") ? World.Environment.NETHER : dim.equals("end") ? World.Environment.THE_END : World.Environment.NORMAL;
        for (World x : Bukkit.getWorlds()) if (x.getEnvironment() == env) return x;
        return null;
    }

    public int rtpMin(String dim) {
        int def = dim.equals("nether") ? 200 : dim.equals("end") ? 1000 : plugin.getConfig().getInt("rtp.min-radius", 500);
        return plugin.getConfig().getInt("rtp.worlds." + dim + ".min-radius", def);
    }

    public int rtpMax(String dim) {
        int def = dim.equals("nether") ? 2000 : dim.equals("end") ? 3000 : plugin.getConfig().getInt("rtp.max-radius", 5000);
        return Math.max(rtpMin(dim) + 1, plugin.getConfig().getInt("rtp.worlds." + dim + ".max-radius", def));
    }

    public void rtp(Player p) { rtp(p, "overworld"); }

    public void rtp(Player p, String dim) {
        long cd = plugin.getConfig().getLong("rtp.cooldown-seconds", 60) * 1000L;
        Long last = rtpCooldown.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cd && !p.hasPermission("smpcore.tp.bypass")) {
            Msg.error(p, "Bitte warte noch " + ((cd - (System.currentTimeMillis() - last)) / 1000 + 1) + "s.");
            return;
        }
        String reason = rtpBlockReason(p, dim);
        if (reason != null) { Msg.error(p, "RTP nicht möglich: " + reason + "."); return; }
        World w = rtpWorld(dim);
        if (w == null) { Msg.error(p, "Die Welt für diesen RTP-Ort wurde nicht gefunden (config.yml -> rtp.worlds)."); return; }
        Msg.send(p, "<yellow>Suche einen sicheren Ort ...");
        attempt(p, w, dim, rtpMin(dim), rtpMax(dim), 0);
    }

    private void attempt(Player p, World w, String dim, int min, int max, int tries) {
        if (!p.isOnline()) return;
        if (tries >= 25) { Msg.error(p, "Kein sicherer Ort gefunden, versuche es nochmal."); return; }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double ang = rnd.nextDouble() * Math.PI * 2;
        int r = min + rnd.nextInt(max - min + 1);
        final int x = (int) (Math.cos(ang) * r);
        final int z = (int) (Math.sin(ang) * r);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location dest = dim.equals("nether") ? netherSpot(w, x, z) : surfaceSpot(w, x, z);
            if (dest == null) {
                attempt(p, w, dim, min, max, tries + 1);
                return;
            }
            dest.setYaw(p.getLocation().getYaw());
            dest.setPitch(p.getLocation().getPitch());
            rtpCooldown.put(p.getUniqueId(), System.currentTimeMillis());
            String label = dim.equals("nether") ? "Zufälliger Ort (Nether)" : dim.equals("end") ? "Zufälliger Ort (End)" : "Zufälliger Ort";
            teleport(p, dest, label);
        });
    }

    private Location surfaceSpot(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight()) return null;
        Material m = w.getBlockAt(x, y, z).getType();
        if (!m.isSolid() || Tag.LEAVES.isTagged(m) || m == Material.MAGMA_BLOCK || m == Material.CACTUS) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    /** Nether: von oben nach unten einen festen Boden mit 2 Blöcken Luft darüber suchen (unter dem Dach). */
    private Location netherSpot(World w, int x, int z) {
        for (int y = 120; y > 32; y--) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (!m.isSolid() || m == Material.MAGMA_BLOCK || m == Material.BEDROCK) continue;
            if (w.getBlockAt(x, y + 1, z).getType().isAir() && w.getBlockAt(x, y + 2, z).getType().isAir()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
}
