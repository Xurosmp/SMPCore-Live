package de.smpcore.rank;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.util.*;

/** Eigenes Rang-System: Prefix, Permissions (mit Vererbung), Home-/AH-Limits. */
public final class RankManager {
    public static final class Rank {
        public final String id;
        public String display = "", prefix = "";
        public int weight, homes, listings, afkBonus, signLimit;
        public final List<String> inherits = new ArrayList<>();
        public final List<String> permissions = new ArrayList<>();
        Rank(String id) { this.id = id; }
    }

    private final SMPCore plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private String defaultRank = "spieler";

    public RankManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        ranks.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "ranks.yml"));
        defaultRank = y.getString("default-rank", "spieler").toLowerCase(Locale.ROOT);
        ConfigurationSection s = y.getConfigurationSection("ranks");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c == null) continue;
                Rank r = new Rank(id.toLowerCase(Locale.ROOT));
                r.display = c.getString("display", id);
                r.prefix = c.getString("prefix", "");
                r.weight = c.getInt("weight");
                r.homes = c.getInt("homes");
                r.listings = c.getInt("listings");
                r.afkBonus = c.getInt("afk-bonus");
                r.signLimit = c.getInt("sign-limit");
                r.inherits.addAll(c.getStringList("inherits"));
                r.permissions.addAll(c.getStringList("permissions"));
                ranks.put(r.id, r);
            }
        }
        if (!ranks.containsKey(defaultRank)) {
            Rank r = new Rank(defaultRank);
            r.display = "<gray>Spieler";
            r.prefix = "<gray>";
            ranks.put(defaultRank, r);
        }
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    public List<Rank> all() {
        List<Rank> l = new ArrayList<>(ranks.values());
        l.sort(Comparator.comparingInt((Rank r) -> r.weight).reversed());
        return l;
    }

    public Rank get(String id) { return id == null ? null : ranks.get(id.toLowerCase(Locale.ROOT)); }

    public Rank rankOf(UUID id) {
        PlayerData d = plugin.data().get(id);
        Rank r = d == null || d.rank == null ? null : ranks.get(d.rank.toLowerCase(Locale.ROOT));
        return r != null ? r : ranks.get(defaultRank);
    }

    public void setRank(UUID id, String rankId) {
        PlayerData d = plugin.data().get(id);
        if (d == null) return;
        String key = rankId.toLowerCase(Locale.ROOT);
        d.rank = key.equals(defaultRank) ? null : key;
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            apply(p);
            String prev = Msg.current();
            Msg.use("rank");
            try { Msg.send(p, "Dein Rang ist jetzt: " + rankOf(id).display); } finally { Msg.use(prev); }
        }
    }

    public void apply(Player p) {
        remove(p);
        Rank r = rankOf(p.getUniqueId());
        if (r == null) return;
        PermissionAttachment att = p.addAttachment(plugin);
        for (String node : effective(r, new HashSet<>())) {
            if (node.startsWith("-")) att.setPermission(node.substring(1), false);
            else att.setPermission(node, true);
        }
        attachments.put(p.getUniqueId(), att);
        p.recalculatePermissions();
        p.playerListName(Msg.c(r.prefix + Msg.esc(p.getName())));
    }

    public void remove(Player p) {
        PermissionAttachment a = attachments.remove(p.getUniqueId());
        if (a != null) {
            try { p.removeAttachment(a); } catch (IllegalArgumentException ignored) { }
        }
    }

    private Set<String> effective(Rank r, Set<String> seen) {
        Set<String> out = new LinkedHashSet<>();
        if (!seen.add(r.id)) return out;
        for (String in : r.inherits) {
            Rank x = ranks.get(in.toLowerCase(Locale.ROOT));
            if (x != null) out.addAll(effective(x, seen));
        }
        out.addAll(r.permissions);
        return out;
    }

    private int maxOf(Rank r, java.util.function.ToIntFunction<Rank> f, Set<String> seen) {
        if (r == null || !seen.add(r.id)) return 0;
        int best = f.applyAsInt(r);
        for (String in : r.inherits) best = Math.max(best, maxOf(ranks.get(in.toLowerCase(Locale.ROOT)), f, seen));
        return best;
    }

    public int homes(Player p) {
        int v = maxOf(rankOf(p.getUniqueId()), r -> r.homes, new HashSet<>());
        return v > 0 ? v : plugin.getConfig().getInt("homes.max", 3);
    }

    public int listings(Player p) {
        int v = maxOf(rankOf(p.getUniqueId()), r -> r.listings, new HashSet<>());
        return v > 0 ? v : plugin.getConfig().getInt("auction.max-listings", 10);
    }

    /** Erlaubte /sign-Nutzungen pro Tag. */
    public int signLimit(Player p) {
        int v = maxOf(rankOf(p.getUniqueId()), r -> r.signLimit, new HashSet<>());
        return v > 0 ? v : plugin.getConfig().getInt("sign.daily-limit", 1);
    }

    /** Bonus in Prozent auf AFK-Stars. */
    public int afkBonus(Player p) {
        return maxOf(rankOf(p.getUniqueId()), r -> r.afkBonus, new HashSet<>());
    }
}
