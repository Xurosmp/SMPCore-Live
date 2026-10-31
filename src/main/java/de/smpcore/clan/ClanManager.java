package de.smpcore.clan;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Locs;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/** Eigenes Clan-System: Rollen, Einladungen, Bank, Home, Clan-Chat, Friendly-Fire, Stats. */
public final class ClanManager {
    public enum Role {
        MEMBER("Mitglied"), OFFICER("Offizier"), LEADER("Leader");
        public final String label;
        Role(String l) { label = l; }
        public boolean atLeast(Role r) { return ordinal() >= r.ordinal(); }
    }

    public static final class Clan {
        public final String id;
        public String name, tag, home;
        public UUID leader;
        public long created;
        public double bank;
        public int kills, deaths;
        public boolean friendlyFire;
        public final Map<UUID, Role> members = new LinkedHashMap<>();

        Clan(String name, String tag, UUID leader) {
            this.id = name.toLowerCase(Locale.ROOT);
            this.name = name;
            this.tag = tag;
            this.leader = leader;
            this.created = System.currentTimeMillis();
        }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    private final Set<UUID> clanChat = new HashSet<>();

    public ClanManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    // ---------------------------------------------------------- Laden / Speichern
    public void load() {
        clans.clear();
        byPlayer.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("clans");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            if (c == null) continue;
            try {
                Clan cl = new Clan(c.getString("name", key), c.getString("tag", "???"), UUID.fromString(c.getString("leader", "")));
                cl.created = c.getLong("created", cl.created);
                cl.bank = c.getDouble("bank");
                cl.kills = c.getInt("kills");
                cl.deaths = c.getInt("deaths");
                cl.friendlyFire = c.getBoolean("ff");
                cl.home = c.getString("home");
                ConfigurationSection ms = c.getConfigurationSection("members");
                if (ms != null) {
                    for (String u : ms.getKeys(false)) {
                        try {
                            cl.members.put(UUID.fromString(u), Role.valueOf(ms.getString(u, "MEMBER")));
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
                cl.members.put(cl.leader, Role.LEADER);
                clans.put(cl.id, cl);
                for (UUID u : cl.members.keySet()) byPlayer.put(u, cl.id);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Clan " + key + " defekt: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String b = "clans." + c.id + ".";
            y.set(b + "name", c.name);
            y.set(b + "tag", c.tag);
            y.set(b + "leader", c.leader.toString());
            y.set(b + "created", c.created);
            y.set(b + "bank", c.bank);
            y.set(b + "kills", c.kills);
            y.set(b + "deaths", c.deaths);
            y.set(b + "ff", c.friendlyFire);
            y.set(b + "home", c.home);
            for (Map.Entry<UUID, Role> e : c.members.entrySet()) y.set(b + "members." + e.getKey(), e.getValue().name());
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    // ---------------------------------------------------------- Abfragen
    public Collection<Clan> all() { return clans.values(); }
    public Clan get(String name) { return name == null ? null : clans.get(name.toLowerCase(Locale.ROOT)); }
    public Clan of(UUID id) { String c = byPlayer.get(id); return c == null ? null : clans.get(c); }
    public Role role(Clan c, UUID id) { return c.members.getOrDefault(id, Role.MEMBER); }
    public int maxMembers() { return plugin.getConfig().getInt("clans.max-members", 10); }
    public boolean canManage(Role actor, Role target) { return actor.atLeast(Role.OFFICER) && actor.ordinal() > target.ordinal(); }

    private boolean tagTaken(String tag) {
        for (Clan c : clans.values()) if (c.tag.equalsIgnoreCase(tag)) return true;
        return false;
    }

    private String nameOf(UUID id) {
        PlayerData d = plugin.data().get(id);
        return d == null ? "?" : d.name;
    }

    public List<Clan> invitesFor(UUID id) {
        List<Clan> out = new ArrayList<>();
        Map<String, Long> m = invites.get(id);
        if (m == null) return out;
        long now = System.currentTimeMillis();
        m.values().removeIf(t -> t < now);
        for (String cid : m.keySet()) {
            Clan c = clans.get(cid);
            if (c != null) out.add(c);
        }
        return out;
    }

    // ---------------------------------------------------------- Nachrichten
    public void broadcast(Clan c, String msg) {
        String prev = Msg.current();
        Msg.use("clan");
        try {
            for (UUID u : c.members.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) Msg.send(p, msg);
            }
        } finally { Msg.use(prev); }
    }

    public boolean isChatOn(UUID id) { return clanChat.contains(id); }

    public boolean toggleChat(UUID id) {
        if (!clanChat.remove(id)) { clanChat.add(id); return true; }
        return false;
    }

    public void sendChat(Player p, String message) {
        Clan c = of(p.getUniqueId());
        if (c == null) return;
        net.kyori.adventure.text.Component line = Msg.c("<dark_gray>[<yellow>Clan<dark_gray>] <gray>" + Msg.esc(p.getName())
                + "<dark_gray>: <white>" + Msg.esc(message));
        for (UUID u : c.members.keySet()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null) t.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    // ---------------------------------------------------------- Aktionen
    public void create(Player p, String name, String tag) {
        if (of(p.getUniqueId()) != null) { Msg.error(p, "Du bist schon in einem Clan."); return; }
        if (!name.matches("[A-Za-z0-9_]{3,16}")) { Msg.error(p, "Name: 3-16 Zeichen (A-Z, 0-9, _)."); return; }
        if (!tag.matches("[A-Za-z0-9]{2,4}")) { Msg.error(p, "Tag: 2-4 Zeichen (A-Z, 0-9)."); return; }
        if (get(name) != null) { Msg.error(p, "Diesen Clan-Namen gibt es schon."); return; }
        if (tagTaken(tag)) { Msg.error(p, "Dieses Tag ist schon vergeben."); return; }
        double cost = plugin.getConfig().getDouble("clans.create-cost", 50000);
        if (!plugin.economy().take(p.getUniqueId(), cost)) { Msg.error(p, "Ein Clan kostet " + EconomyManager.fmt(cost) + "."); return; }
        Clan c = new Clan(name, tag.toUpperCase(Locale.ROOT), p.getUniqueId());
        c.members.put(p.getUniqueId(), Role.LEADER);
        clans.put(c.id, c);
        byPlayer.put(p.getUniqueId(), c.id);
        save(true);
        Bukkit.broadcast(Msg.c("<dark_gray>[<yellow>Clan<dark_gray>] <white>" + Msg.esc(p.getName()) + " <gray>hat den Clan <yellow>"
                + Msg.esc(c.name) + " <dark_gray>[<yellow>" + c.tag + "<dark_gray>] <gray>gegründet!"));
    }

    public void invite(Player actor, Player target) {
        Clan c = of(actor.getUniqueId());
        if (c == null) { Msg.error(actor, "Du bist in keinem Clan."); return; }
        if (!role(c, actor.getUniqueId()).atLeast(Role.OFFICER)) { Msg.error(actor, "Nur Offiziere und der Leader dürfen einladen."); return; }
        if (target.equals(actor)) { Msg.error(actor, "Du kannst dich nicht selbst einladen."); return; }
        if (of(target.getUniqueId()) != null) { Msg.error(actor, Msg.esc(target.getName()) + " ist schon in einem Clan."); return; }
        if (c.members.size() >= maxMembers()) { Msg.error(actor, "Euer Clan ist voll (" + maxMembers() + ")."); return; }
        invites.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>()).put(c.id, System.currentTimeMillis() + 120_000L);
        Msg.send(actor, "Einladung an <white>" + Msg.esc(target.getName()) + " <gray>gesendet (2 Min gültig).");
        Msg.send(target, "Du wurdest in den Clan <yellow>" + Msg.esc(c.name) + " <gray>eingeladen! "
                + "<click:run_command:/clan accept " + c.name + "><green><underlined>[Annehmen]</underlined></green></click> <dark_gray>(/clan accept " + c.name + ")");
    }

    public void accept(Player p, String clanName) {
        UUID id = p.getUniqueId();
        if (of(id) != null) { Msg.error(p, "Du bist schon in einem Clan."); return; }
        List<Clan> inv = invitesFor(id);
        Clan c = null;
        if (clanName == null) {
            if (inv.size() == 1) c = inv.get(0);
            else if (inv.isEmpty()) { Msg.error(p, "Du hast keine Einladungen."); return; }
            else { Msg.error(p, "Mehrere Einladungen – nutze /clan accept <Name>."); return; }
        } else {
            for (Clan x : inv) if (x.id.equals(clanName.toLowerCase(Locale.ROOT))) c = x;
        }
        if (c == null) { Msg.error(p, "Keine Einladung von diesem Clan."); return; }
        if (c.members.size() >= maxMembers()) { Msg.error(p, "Dieser Clan ist voll."); return; }
        c.members.put(id, Role.MEMBER);
        byPlayer.put(id, c.id);
        invites.remove(id);
        save(true);
        broadcast(c, "<white>" + Msg.esc(p.getName()) + " <gray>ist dem Clan beigetreten!");
    }

    public void deny(Player p) {
        invites.remove(p.getUniqueId());
        Msg.send(p, "Alle Einladungen abgelehnt.");
    }

    public void leave(Player p) {
        Clan c = of(p.getUniqueId());
        if (c == null) { Msg.error(p, "Du bist in keinem Clan."); return; }
        if (role(c, p.getUniqueId()) == Role.LEADER) { Msg.error(p, "Der Leader kann nicht gehen: /clan transfer <Spieler> oder /clan disband."); return; }
        c.members.remove(p.getUniqueId());
        byPlayer.remove(p.getUniqueId());
        clanChat.remove(p.getUniqueId());
        save(true);
        Msg.send(p, "Du hast den Clan verlassen.");
        broadcast(c, "<white>" + Msg.esc(p.getName()) + " <gray>hat den Clan verlassen.");
    }

    public void kick(Player actor, UUID target) {
        Clan c = of(actor.getUniqueId());
        if (c == null || !c.members.containsKey(target)) { Msg.error(actor, "Dieser Spieler ist nicht in deinem Clan."); return; }
        if (!canManage(role(c, actor.getUniqueId()), role(c, target))) { Msg.error(actor, "Dazu hast du keine Rechte."); return; }
        c.members.remove(target);
        byPlayer.remove(target);
        clanChat.remove(target);
        save(true);
        broadcast(c, "<white>" + Msg.esc(nameOf(target)) + " <gray>wurde aus dem Clan geworfen.");
        Player t = Bukkit.getPlayer(target);
        if (t != null) Msg.send(t, "<red>Du wurdest aus dem Clan geworfen.");
    }

    public void promote(Player actor, UUID target) {
        Clan c = of(actor.getUniqueId());
        if (c == null || role(c, actor.getUniqueId()) != Role.LEADER) { Msg.error(actor, "Nur der Leader darf befördern."); return; }
        if (!c.members.containsKey(target) || role(c, target) != Role.MEMBER) { Msg.error(actor, "Nur Mitglieder können befördert werden."); return; }
        c.members.put(target, Role.OFFICER);
        save(true);
        broadcast(c, "<white>" + Msg.esc(nameOf(target)) + " <gray>ist jetzt <yellow>Offizier<gray>.");
    }

    public void demote(Player actor, UUID target) {
        Clan c = of(actor.getUniqueId());
        if (c == null || role(c, actor.getUniqueId()) != Role.LEADER) { Msg.error(actor, "Nur der Leader darf degradieren."); return; }
        if (!c.members.containsKey(target) || role(c, target) != Role.OFFICER) { Msg.error(actor, "Nur Offiziere können degradiert werden."); return; }
        c.members.put(target, Role.MEMBER);
        save(true);
        broadcast(c, "<white>" + Msg.esc(nameOf(target)) + " <gray>ist jetzt wieder Mitglied.");
    }

    public void transfer(Player actor, UUID target) {
        Clan c = of(actor.getUniqueId());
        if (c == null || role(c, actor.getUniqueId()) != Role.LEADER) { Msg.error(actor, "Nur der Leader darf das."); return; }
        if (!c.members.containsKey(target) || target.equals(actor.getUniqueId())) { Msg.error(actor, "Ungültiger Spieler."); return; }
        c.members.put(actor.getUniqueId(), Role.OFFICER);
        c.members.put(target, Role.LEADER);
        c.leader = target;
        save(true);
        broadcast(c, "<white>" + Msg.esc(nameOf(target)) + " <gray>ist jetzt der neue <gold>Leader<gray>!");
    }

    public void disband(Player actor) {
        Clan c = of(actor.getUniqueId());
        if (c == null || role(c, actor.getUniqueId()) != Role.LEADER) { Msg.error(actor, "Nur der Leader darf den Clan auflösen."); return; }
        disbandClan(c);
    }

    public void disbandClan(Clan c) {
        broadcast(c, "<red>Der Clan wurde aufgelöst.");
        for (UUID u : c.members.keySet()) { byPlayer.remove(u); clanChat.remove(u); }
        clans.remove(c.id);
        save(true);
        Bukkit.broadcast(Msg.c("<dark_gray>[<yellow>Clan<dark_gray>] <gray>Der Clan <yellow>" + Msg.esc(c.name) + " <gray>wurde aufgelöst."));
    }

    public void deposit(Player p, double amount) {
        Clan c = of(p.getUniqueId());
        if (c == null) { Msg.error(p, "Du bist in keinem Clan."); return; }
        if (amount <= 0 || !plugin.economy().take(p.getUniqueId(), amount)) { Msg.error(p, "Nicht genug Geld!"); return; }
        c.bank += amount;
        save(true);
        Msg.send(p, "<green>" + EconomyManager.fmt(amount) + " <gray>eingezahlt. Clan-Bank: <green>" + EconomyManager.fmt(c.bank));
    }

    public void withdraw(Player p, double amount) {
        Clan c = of(p.getUniqueId());
        if (c == null) { Msg.error(p, "Du bist in keinem Clan."); return; }
        if (!role(c, p.getUniqueId()).atLeast(Role.OFFICER)) { Msg.error(p, "Nur Offiziere und der Leader dürfen abheben."); return; }
        if (amount <= 0 || c.bank + 1e-9 < amount) { Msg.error(p, "So viel ist nicht in der Clan-Bank!"); return; }
        c.bank -= amount;
        plugin.economy().add(p.getUniqueId(), amount);
        save(true);
        Msg.send(p, "<green>" + EconomyManager.fmt(amount) + " <gray>abgehoben. Clan-Bank: <green>" + EconomyManager.fmt(c.bank));
    }

    public void setHome(Player p) {
        Clan c = of(p.getUniqueId());
        if (c == null) { Msg.error(p, "Du bist in keinem Clan."); return; }
        if (!role(c, p.getUniqueId()).atLeast(Role.OFFICER)) { Msg.error(p, "Nur Offiziere und der Leader dürfen das."); return; }
        c.home = Locs.str(p.getLocation());
        save(true);
        Msg.send(p, "<green>Clan-Home gesetzt.");
    }

    public void home(Player p) {
        Clan c = of(p.getUniqueId());
        if (c == null) { Msg.error(p, "Du bist in keinem Clan."); return; }
        Location l = c.home == null ? null : Locs.parse(c.home);
        if (l == null) { Msg.error(p, "Euer Clan hat noch kein Home."); return; }
        plugin.teleports().teleport(p, l, "Clan-Home");
    }

    public void toggleFriendlyFire(Player p) {
        Clan c = of(p.getUniqueId());
        if (c == null || !role(c, p.getUniqueId()).atLeast(Role.OFFICER)) { Msg.error(p, "Nur Offiziere und der Leader dürfen das."); return; }
        c.friendlyFire = !c.friendlyFire;
        save(true);
        broadcast(c, "Friendly Fire ist jetzt " + (c.friendlyFire ? "<red>AN" : "<green>AUS") + "<gray>.");
    }

    // ---------------------------------------------------------- Stats
    public void addKill(UUID killer) {
        Clan c = of(killer);
        if (c != null) c.kills++;
    }

    public void addDeath(UUID victim) {
        Clan c = of(victim);
        if (c != null) c.deaths++;
    }

    public List<Clan> top() {
        List<Clan> l = new ArrayList<>(clans.values());
        l.sort(Comparator.comparingInt((Clan c) -> c.kills).reversed().thenComparingInt(c -> -c.members.size()));
        return l;
    }
}
