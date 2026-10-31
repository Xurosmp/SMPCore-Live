package de.smpcore.listeners;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.rank.RankManager;
import de.smpcore.util.Msg;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Chat-Format: [CLAN] [Rang] Name » Nachricht, plus Clan-Chat-Modus. */
public final class ChatListener implements Listener {
    private final SMPCore plugin;

    public ChatListener(SMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        ClanManager.Clan clan = plugin.clans().of(p.getUniqueId());

        if (clan != null && plugin.clans().isChatOn(p.getUniqueId())) {
            String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.clans().sendChat(p, msg));
            return;
        }
        if (!plugin.getConfig().getBoolean("chat.rank-format", true)) return;
        RankManager.Rank r = plugin.ranks().rankOf(p.getUniqueId());
        String prefix = (clan == null ? "" : "<dark_gray>[<yellow>" + clan.tag + "<dark_gray>] ") + (r == null ? "" : r.prefix);
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                Msg.c(prefix + Msg.esc(source.getName()) + "<dark_gray> » <white>").append(message)));
    }
}
