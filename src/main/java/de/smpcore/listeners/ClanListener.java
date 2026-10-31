package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Friendly-Fire-Schutz innerhalb eines Clans. */
public final class ClanListener implements Listener {
    private final SMPCore plugin;

    public ClanListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player shooter) attacker = shooter;
        if (attacker == null || attacker.equals(victim)) return;
        ClanManager.Clan a = plugin.clans().of(attacker.getUniqueId());
        ClanManager.Clan v = plugin.clans().of(victim.getUniqueId());
        if (a != null && a == v && !a.friendlyFire) {
            e.setCancelled(true);
            Msg.use("clan");
            try { attacker.sendActionBar(Msg.c("<red>Du kannst Clan-Mitglieder nicht angreifen!")); } finally { Msg.reset(); }
        }
    }
}
