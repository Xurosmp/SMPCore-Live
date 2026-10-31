package de.smpcore.live.combat;

import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Kampf-Modus: nach PvP-Treffern X Sekunden getaggt, Ausloggen = Tod. */
public final class CombatManager {
    private static final String M = "combat";

    private final SMPCoreLive plugin;
    private final Map<UUID, Long> expiry = new HashMap<>();
    private final Map<UUID, UUID> opponent = new HashMap<>();
    private final Set<UUID> flightRestore = new HashSet<>();
    /** Spieler, die gerade wegen Combat-Log getötet werden (für die Todesnachricht). */
    private final Set<UUID> loggedOut = new HashSet<>();
    /** Spieler, die beim nächsten Join eine Meldung bekommen. */
    private final Set<UUID> pendingNotice = new HashSet<>();
    private final Map<UUID, Integer> logCounts = new HashMap<>();
    private File file;
    private BukkitTask task;
    private boolean punishing;

    public CombatManager(SMPCoreLive plugin) { this.plugin = plugin; }

    // ---------------------------------------------------------------- Lifecycle

    public void load() {
        file = new File(plugin.getDataFolder(), "combatlogs.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("pending")) {
            try { pendingNotice.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
        }
        if (y.getConfigurationSection("counts") != null) {
            for (String k : y.getConfigurationSection("counts").getKeys(false)) {
                try { logCounts.put(UUID.fromString(k), y.getInt("counts." + k)); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void save() {
        if (file == null) return;
        YamlConfiguration y = new YamlConfiguration();
        List<String> pend = new ArrayList<>();
        for (UUID id : pendingNotice) pend.add(id.toString());
        y.set("pending", pend);
        for (Map.Entry<UUID, Integer> e : logCounts.entrySet()) y.set("counts." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("combatlogs.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID id : new ArrayList<>(flightRestore)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setAllowFlight(true);
        }
        flightRestore.clear();
        expiry.clear();
        opponent.clear();
        save();
    }

    // ---------------------------------------------------------------- API

    public boolean isTagged(UUID id) {
        Long t = expiry.get(id);
        return t != null && t > System.currentTimeMillis();
    }

    public long secondsLeft(UUID id) {
        Long t = expiry.get(id);
        if (t == null) return 0;
        long left = t - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public UUID opponentOf(UUID id) { return opponent.get(id); }

    public int logCount(UUID id) { return logCounts.getOrDefault(id, 0); }

    public boolean bypass(Player p) {
        return p.hasPermission("smpcorelive.combat.bypass")
                || plugin.getConfig().getStringList("combat.disabled-worlds").contains(p.getWorld().getName());
    }

    public boolean isPunishing() { return punishing; }

    // ---------------------------------------------------------------- Tagging

    public void tag(Player victim, Player attacker) {
        if (victim.equals(attacker)) return;
        tagOne(victim, attacker);
        tagOne(attacker, victim);
    }

    private void tagOne(Player p, Player other) {
        if (bypass(p)) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        long secs = plugin.getConfig().getLong("combat.duration-seconds", 15);
        if (secs <= 0) return;
        boolean was = isTagged(p.getUniqueId());
        expiry.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        opponent.put(p.getUniqueId(), other.getUniqueId());
        if (was) return;
        Msg.send(p, M, plugin.getConfig().getString("combat.message-start", "<red>Du bist jetzt im Kampf!")
                .replace("%time%", String.valueOf(secs)));
        if (plugin.getConfig().getBoolean("combat.disable-elytra", true) && p.isGliding()) p.setGliding(false);
        if (plugin.getConfig().getBoolean("combat.disable-flight", true)
                && (p.getAllowFlight() || p.isFlying())) {
            flightRestore.add(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    /** Beendet den Kampf-Modus eines Spielers. */
    public void untag(Player p, boolean message) {
        UUID id = p.getUniqueId();
        boolean was = expiry.remove(id) != null;
        opponent.remove(id);
        if (flightRestore.remove(id)) p.setAllowFlight(true);
        if (was && message) {
            Msg.send(p, M, plugin.getConfig().getString("combat.message-end", "<green>Du bist nicht mehr im Kampf."));
        }
        if (was) p.sendActionBar(Component.empty());
    }

    private void tick() {
        if (expiry.isEmpty()) return;
        String bar = plugin.getConfig().getString("combat.actionbar", "");
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(expiry.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Long t = expiry.get(id);
            if (p == null || !p.isOnline() || t == null) {
                expiry.remove(id);
                opponent.remove(id);
                flightRestore.remove(id);
                continue;
            }
            if (t <= now) {
                untag(p, true);
                continue;
            }
            if (!bar.isEmpty()) {
                p.sendActionBar(Msg.c(bar.replace("%time%", String.valueOf((t - now + 999) / 1000))));
            }
        }
    }

    // ---------------------------------------------------------------- Kill / Logout

    /** Gegner wurde getötet: Kampf des Killers endet (falls das sein letzter Gegner war). */
    public void onKill(Player killer, Player victim) {
        if (!plugin.getConfig().getBoolean("combat.end-on-kill", true)) return;
        if (victim.getUniqueId().equals(opponent.get(killer.getUniqueId()))) untag(killer, true);
    }

    /** Spieler hat die Verbindung im Kampf getrennt. */
    public void onDisconnect(Player p, boolean timeout) {
        UUID id = p.getUniqueId();
        boolean tagged = isTagged(id);
        UUID oppId = opponent.get(id);
        untag(p, false);
        if (!tagged || bypass(p) || p.isDead() || p.getHealth() <= 0) return;
        if (!plugin.getConfig().getBoolean("combat.punish-disconnect", true)) return;
        if (timeout && !plugin.getConfig().getBoolean("combat.punish-timeout", true)) return;

        loggedOut.add(id);
        pendingNotice.add(id);
        logCounts.merge(id, 1, Integer::sum);
        save();

        String msg = plugin.getConfig().getString("combat.broadcast-logout", "<red>%player% hat sich im Kampf ausgeloggt!")
                .replace("%player%", Msg.esc(p.getName()));
        Bukkit.broadcast(Msg.c(msg));

        // Gegner als Killer eintragen, damit SMPCore Kill/Bounty/Stars vergibt
        Player opp = oppId == null ? null : Bukkit.getPlayer(oppId);
        punishing = true;
        try {
            if (opp != null && opp.isOnline() && !opp.equals(p)) {
                p.setNoDamageTicks(0);
                p.damage(0.01, opp);
            }
            p.setHealth(0.0);
        } finally {
            punishing = false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> loggedOut.remove(id));
    }

    public boolean isLoggedOutDeath(UUID id) { return loggedOut.contains(id); }

    /** Nachricht beim nächsten Join, falls der Spieler im Kampf ausgeloggt wurde. */
    public void onJoin(Player p) {
        if (pendingNotice.remove(p.getUniqueId())) {
            save();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) Msg.send(p, M, plugin.getConfig().getString("combat.join-notice", "<red>Du hast dich im Kampf ausgeloggt."));
            }, 20L);
        }
    }

    // ---------------------------------------------------------------- Befehle

    /** Gibt true zurück, wenn der Befehl im Kampf gesperrt ist. */
    public boolean isCommandBlocked(String rawMessage) {
        String m = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int sp = m.indexOf(' ');
        String label = (sp < 0 ? m : m.substring(0, sp)).toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return false;
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        String normalized = label;
        org.bukkit.command.PluginCommand pc = Bukkit.getPluginCommand(label);
        if (pc != null) normalized = pc.getName().toLowerCase(Locale.ROOT);

        List<String> list = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("combat.commands.list")) list.add(s.toLowerCase(Locale.ROOT));
        boolean contained = list.contains(label) || list.contains(normalized);
        boolean whitelist = !plugin.getConfig().getString("combat.commands.mode", "whitelist").equalsIgnoreCase("blacklist");
        return whitelist ? !contained : contained;
    }
}
