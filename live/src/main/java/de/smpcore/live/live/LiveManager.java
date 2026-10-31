package de.smpcore.live.live;

import de.smpcore.SMPCore;
import de.smpcore.clan.ClanManager;
import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import de.smpcore.rank.RankManager;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streamer-Modus: /live <Link> setzt einen roten Punkt hinter den Namen (Chat + TAB-Liste)
 * und kündigt den Stream im Chat an.
 */
public final class LiveManager implements Listener {
    private static final String M = "live";

    public static final class Stream {
        public final UUID id;
        public final String name;
        public final String url;
        public final long since;

        Stream(UUID id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.since = System.currentTimeMillis();
        }
    }

    private final SMPCoreLive plugin;
    private final Map<UUID, Stream> live = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStart = new HashMap<>();
    private BukkitTask task;

    public LiveManager(SMPCoreLive plugin) { this.plugin = plugin; }

    public void start() {
        // delay 20 / period 20 wie die TAB-Liste von SMPCore, damit unser Punkt nach deren Update gesetzt wird
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID id : new ArrayList<>(live.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playerListName(null);
        }
        live.clear();
    }

    // ---------------------------------------------------------------- API

    public boolean isLive(UUID id) { return live.containsKey(id); }

    public Stream get(UUID id) { return live.get(id); }

    public List<Stream> all() {
        List<Stream> l = new ArrayList<>(live.values());
        l.sort((x, y) -> Long.compare(x.since, y.since));
        return l;
    }

    public boolean canGoLive(Player p) {
        if (p.hasPermission("smpcorelive.live")) return true;
        RankManager.Rank r = SMPCore.get().ranks().rankOf(p.getUniqueId());
        if (r == null) return false;
        for (String allowed : plugin.getConfig().getStringList("live.allowed-ranks")) {
            if (allowed.equalsIgnoreCase(r.id)) return true;
        }
        return false;
    }

    /** Sekunden bis /live wieder erlaubt ist (0 = jetzt). */
    public long cooldownLeft(Player p) {
        Long last = lastStart.get(p.getUniqueId());
        if (last == null) return 0;
        long cd = plugin.getConfig().getLong("live.cooldown-seconds", 30) * 1000L;
        long left = last + cd - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** Prüft den Link gegen die erlaubten Seiten. Gibt den bereinigten https-Link zurück oder null. */
    public String normalizeUrl(String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 200) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) s = "https://" + s.substring(7);
        else if (!lower.startsWith("https://")) s = "https://" + s;
        URI u;
        try {
            u = new URI(s);
        } catch (URISyntaxException e) {
            return null;
        }
        String host = u.getHost();
        if (host == null || u.getUserInfo() != null) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (!host.contains(".")) return null;
        List<String> domains = plugin.getConfig().getStringList("live.allowed-domains");
        if (domains.isEmpty()) return s;
        for (String d : domains) {
            String dom = d.toLowerCase(Locale.ROOT).trim();
            if (!dom.isEmpty() && (host.equals(dom) || host.endsWith("." + dom))) return s;
        }
        return null;
    }

    public void goLive(Player p, String url) {
        boolean already = live.containsKey(p.getUniqueId());
        Stream st = new Stream(p.getUniqueId(), p.getName(), url);
        live.put(p.getUniqueId(), st);
        lastStart.put(p.getUniqueId(), System.currentTimeMillis());
        applyListName(p);
        if (already) return;
        Component msg = Msg.c(plugin.getConfig().getString("live.broadcast-start", "<red>● <white>%player% <gray>ist LIVE! ")
                .replace("%player%", Msg.esc(p.getName())))
                .append(linkComponent(url));
        Bukkit.broadcast(Msg.c(Msg.prefix(M)).append(msg));
    }

    /** Beendet den Live-Status. Gibt false zurück, wenn der Spieler gar nicht live war. */
    public boolean stop(Player p, boolean announce) {
        Stream st = live.remove(p.getUniqueId());
        if (st == null) return false;
        p.playerListName(null);
        if (announce) {
            String t = plugin.getConfig().getString("live.broadcast-stop", "<gray>%player% ist nicht mehr live.");
            Bukkit.broadcast(Msg.c(Msg.prefix(M) + t.replace("%player%", Msg.esc(p.getName()))));
        }
        return true;
    }

    public Component linkComponent(String url) {
        String label = plugin.getConfig().getString("live.broadcast-text-link", "<aqua><underlined>[Zuschauen]");
        return Msg.c(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Msg.c("<gray>" + Msg.esc(url) + "\n<yellow>Klicken zum Öffnen")));
    }

    // ---------------------------------------------------------------- intern

    private String dot() { return plugin.getConfig().getString("live.dot", "<red>●"); }

    private void tick() {
        long maxMin = plugin.getConfig().getLong("live.max-minutes", 360);
        long now = System.currentTimeMillis();
        for (Stream st : new ArrayList<>(live.values())) {
            Player p = Bukkit.getPlayer(st.id);
            if (p == null || !p.isOnline()) {
                live.remove(st.id);
                continue;
            }
            if (maxMin > 0 && now - st.since > maxMin * 60_000L) {
                stop(p, true);
                Msg.send(p, M, "<gray>Dein Live-Status wurde nach <white>" + maxMin + " Minuten <gray>automatisch beendet.");
                continue;
            }
            applyListName(p);
        }
    }

    /** Hängt den roten Punkt an den TAB-Namen (SMPCore setzt den Namen jede Sekunde neu, deshalb prüfen wir vorher). */
    private void applyListName(Player p) {
        Component cur = p.playerListName();
        if (cur == null) cur = p.name();
        String plainDot = PlainTextComponentSerializer.plainText().serialize(Msg.c(dot())).trim();
        String plainCur = PlainTextComponentSerializer.plainText().serialize(cur);
        if (!plainDot.isEmpty() && plainCur.contains(plainDot)) return;
        p.playerListName(cur.append(Msg.c(" " + dot())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.getConfig().getBoolean("live.end-on-quit", true)) {
            live.remove(e.getPlayer().getUniqueId());
        }
    }

    /** Chat-Format wie SMPCore ([CLAN] [Rang] Name » Nachricht), aber mit rotem Punkt hinter dem Namen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Stream st = live.get(p.getUniqueId());
        if (st == null) return;
        SMPCore core = SMPCore.get();
        String prefix = "";
        if (core.getConfig().getBoolean("chat.rank-format", true)) {
            ClanManager.Clan clan = core.clans().of(p.getUniqueId());
            RankManager.Rank r = core.ranks().rankOf(p.getUniqueId());
            prefix = (clan == null ? "" : "<dark_gray>[<yellow>" + clan.tag + "<dark_gray>] ") + (r == null ? "" : r.prefix);
        }
        Component head = Msg.c(prefix + Msg.esc(p.getName()) + " " + dot())
                .hoverEvent(HoverEvent.showText(Msg.c("<red>● LIVE <gray>- Klicken, um den Stream zu öffnen\n<dark_gray>" + Msg.esc(st.url))))
                .clickEvent(ClickEvent.openUrl(st.url));
        Component line = Component.empty().append(head).append(Msg.c("<dark_gray> » <white>"));
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) -> line.append(message)));
    }
}
