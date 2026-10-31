package de.smpcore.live.combat;

import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class CombatListener implements Listener {
    private static final String M = "combat";
    private final SMPCoreLive plugin;
    private final CombatManager combat;

    public CombatListener(SMPCoreLive plugin) {
        this.plugin = plugin;
        this.combat = plugin.combat();
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean active(Player p) {
        return combat.isTagged(p.getUniqueId()) && !combat.bypass(p);
    }

    private void blocked(Player p) {
        Msg.send(p, M, plugin.getConfig().getString("combat.message-blocked", "<red>Das geht im Kampf nicht!"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (combat.isPunishing()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (e.getFinalDamage() <= 0) return;
        combat.tag(victim, attacker);
    }

    /** Todesnachricht von Combat-Log-Toden unterdrücken (wir senden eine eigene). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathMessage(PlayerDeathEvent e) {
        if (combat.isLoggedOutDeath(e.getEntity().getUniqueId())) e.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        combat.untag(victim, false);
        if (killer != null && !killer.equals(victim)) combat.onKill(killer, victim);
    }

    /** LOWEST: läuft vor den Aufräumarbeiten von SMPCore, die Spielerdaten sind noch vollständig. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        switch (e.getReason()) {
            case DISCONNECTED -> combat.onDisconnect(p, false);
            case TIMED_OUT -> combat.onDisconnect(p, true);
            default -> combat.untag(p, false); // Kick / Server-Stop: keine Strafe
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        combat.onJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        if (combat.isCommandBlocked(e.getMessage())) {
            e.setCancelled(true);
            blocked(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        boolean block = switch (e.getCause()) {
            case COMMAND, PLUGIN, UNKNOWN -> plugin.getConfig().getBoolean("combat.block-teleport", true);
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> plugin.getConfig().getBoolean("combat.block-portals", true);
            default -> false;
        };
        if (block) {
            e.setCancelled(true);
            blocked(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p) || !e.isGliding()) return;
        if (!plugin.getConfig().getBoolean("combat.disable-elytra", true) || !active(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!e.isFlying() || !plugin.getConfig().getBoolean("combat.disable-flight", true) || !active(p)) return;
        e.setCancelled(true);
        blocked(p);
    }
}
