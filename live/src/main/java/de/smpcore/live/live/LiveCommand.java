package de.smpcore.live.live;

import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import de.smpcore.live.util.Tab;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /live <Link> | /live off | /live list */
public final class LiveCommand implements TabExecutor {
    private static final String M = "live";
    private final SMPCoreLive plugin;

    public LiveCommand(SMPCoreLive plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        LiveManager lm = plugin.live();
        if (a.length >= 1 && a[0].equalsIgnoreCase("list")) {
            List<LiveManager.Stream> all = lm.all();
            if (all.isEmpty()) {
                Msg.send(s, M, "Gerade ist niemand live.");
                return true;
            }
            Msg.send(s, M, "<white>Aktuell live <gray>(" + all.size() + ")<white>:");
            for (LiveManager.Stream st : all) {
                Component line = Msg.c("<red>● <white>" + Msg.esc(st.name) + " <dark_gray>▸ ").append(lm.linkComponent(st.url));
                s.sendMessage(line);
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            Msg.error(s, M, "Nur Spieler können /live nutzen. Mit /live list siehst du alle Streams.");
            return true;
        }
        if (!lm.canGoLive(p)) {
            Msg.error(p, M, "Dafür brauchst du den <#ff3fa4>MEDIA<red>-Rang.");
            return true;
        }
        if (a.length == 0) {
            if (lm.isLive(p.getUniqueId())) {
                Msg.send(p, M, "Du bist live: <white>" + Msg.esc(lm.get(p.getUniqueId()).url));
                Msg.send(p, M, "Beenden mit <white>/" + label + " off");
            } else {
                Msg.send(p, M, "Benutzung: <white>/" + label + " [Stream-Link] <gray>- startet deinen Live-Status");
                Msg.send(p, M, "<white>/" + label + " off <gray>- beendet ihn, <white>/" + label + " list <gray>- zeigt alle Streams");
            }
            return true;
        }
        String first = a[0].toLowerCase(Locale.ROOT);
        if (first.equals("off") || first.equals("stop") || first.equals("aus")) {
            if (lm.stop(p, true)) Msg.ok(p, M, "Du bist nicht mehr live.");
            else Msg.error(p, M, "Du bist gerade nicht live.");
            return true;
        }
        if (a.length > 1) {
            Msg.error(p, M, "Bitte nur den Link angeben: /" + label + " [Stream-Link]");
            return true;
        }
        long cd = lm.cooldownLeft(p);
        if (cd > 0 && !lm.isLive(p.getUniqueId())) {
            Msg.error(p, M, "Bitte warte noch " + cd + " Sekunden.");
            return true;
        }
        String url = lm.normalizeUrl(a[0]);
        if (url == null) {
            String allowed = String.join(", ", plugin.getConfig().getStringList("live.allowed-domains"));
            Msg.error(p, M, "Ungültiger Link." + (allowed.isEmpty() ? "" : " Erlaubt sind: " + Msg.esc(allowed)));
            return true;
        }
        boolean already = lm.isLive(p.getUniqueId());
        lm.goLive(p, url);
        Msg.ok(p, M, already ? "Dein Stream-Link wurde aktualisiert." : "Du bist jetzt live! Beenden mit /" + label + " off");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return Tab.filter(a[0], List.of("off", "list"));
        return List.of();
    }
}
