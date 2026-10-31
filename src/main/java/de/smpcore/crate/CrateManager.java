package de.smpcore.crate;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.gui.CrateMenus;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateManager {
    public record Reward(String type, ItemStack icon, double weight, double amount, String command, ItemStack give, String label) {
        public Reward withWeight(double w) {
            return new Reward(type, icon, Math.max(0.01, w), amount, command, give, label);
        }
    }

    public static final class Crate {
        public final String id;
        public String name, keyName;
        public String mode = "RANDOM"; // RANDOM = Zufall, CHOOSE = Spieler wählt selbst
        public Material icon;
        public final List<Reward> rewards = new ArrayList<>();
        public double total;

        public Crate(String id, String name, String keyName, Material icon) {
            this.id = id; this.name = name; this.keyName = keyName; this.icon = icon;
        }

        public void recalc() { total = rewards.stream().mapToDouble(Reward::weight).sum(); }

        public double chance(Reward r) { return total <= 0 ? 0 : r.weight() / total * 100.0; }

        public Reward pick() {
            double r = ThreadLocalRandom.current().nextDouble() * total;
            for (Reward x : rewards) {
                r -= x.weight();
                if (r < 0) return x;
            }
            return rewards.get(rewards.size() - 1);
        }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
    }

    // ---------- Reward-Fabriken (auch für den Editor) ----------
    public static Reward money(double amount, double chance) {
        String label = "$" + Num.format(amount);
        return new Reward("MONEY", Items.make(Material.GOLD_INGOT, "<gold>" + label), chance, amount, null, null, label);
    }

    public static Reward crystals(long amount, double chance) {
        String label = Num.format(amount) + " Stars";
        return new Reward("CRYSTALS", Items.make(Material.AMETHYST_SHARD, "<light_purple>" + label), chance, amount, null, null, label);
    }

    public static Reward item(ItemStack give, double chance) {
        String label = give.getAmount() + "x " + Items.pretty(give.getType());
        return new Reward("ITEM", give.clone(), chance, give.getAmount(), null, give.clone(), label);
    }

    // ---------- Laden / Speichern ----------
    public void load() {
        crates.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cs = y.getConfigurationSection("crates");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
            Crate crate = new Crate(id.toLowerCase(Locale.ROOT), c.getString("name", id),
                    c.getString("key-name", "<white>" + id + " Key"), icon == null ? Material.CHEST : icon);
            crate.mode = c.getString("mode", "RANDOM").toUpperCase(Locale.ROOT);
            for (Map<?, ?> m : c.getMapList("rewards")) {
                Reward r = parseReward(m);
                if (r != null) crate.rewards.add(r);
            }
            if (crate.rewards.isEmpty()) {
                plugin.getLogger().warning("Crate '" + id + "' hat keine gültigen Rewards.");
                continue;
            }
            crate.recalc();
            crates.put(crate.id, crate);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Crate c : crates.values()) {
            String b = "crates." + c.id + ".";
            y.set(b + "name", c.name);
            y.set(b + "key-name", c.keyName);
            y.set(b + "icon", c.icon.name());
            y.set(b + "mode", c.mode);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Reward r : c.rewards) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", r.type());
                m.put("chance", r.weight());
                switch (r.type()) {
                    case "ITEM" -> m.put("item", Items.encode(r.give()));
                    case "COMMAND" -> {
                        m.put("command", r.command());
                        m.put("icon", r.icon().getType().name());
                        m.put("name", r.label());
                    }
                    default -> m.put("amount", r.amount());
                }
                list.add(m);
            }
            y.set(b + "rewards", list);
        }
        Persist.write(plugin, file, y.saveToString(), true);
    }

    private static String str(Map<?, ?> m, String k, String def) {
        Object o = m.get(k);
        return o == null ? def : String.valueOf(o);
    }
    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    private Reward parseReward(Map<?, ?> m) {
        String type = str(m, "type", "ITEM").toUpperCase(Locale.ROOT);
        double chance = num(m.get("chance"));
        if (chance <= 0) return null;
        double amount = num(m.get("amount"));
        if (amount <= 0) amount = 1;
        String custom = m.get("name") == null ? null : String.valueOf(m.get("name"));
        if (type.equals("ITEM")) {
            ItemStack give;
            String enc = m.get("item") == null ? null : String.valueOf(m.get("item"));
            if (enc != null) {
                try { give = Items.decode(enc); } catch (Exception ex) { return null; }
            } else {
                Material mt = Material.matchMaterial(str(m, "material", "STONE"));
                if (mt == null || !mt.isItem()) return null;
                give = new ItemStack(mt, (int) amount);
                if (custom != null) give = Items.named(give, custom);
            }
            if (plugin.getConfig().getStringList("shop-blacklist").contains(give.getType().name())) {
                plugin.getLogger().warning("Crate: " + give.getType() + " steht auf der Blacklist und wird übersprungen.");
                return null;
            }
            String label = custom != null ? custom : give.getAmount() + "x " + Items.pretty(give.getType());
            return new Reward(type, give.clone(), chance, give.getAmount(), null, give, label);
        }
        if (type.equals("MONEY")) return money(amount, chance);
        if (type.equals("CRYSTALS")) return crystals((long) amount, chance);
        if (type.equals("COMMAND")) {
            String cmd = str(m, "command", null);
            if (cmd == null) return null;
            Material mt = Material.matchMaterial(str(m, "icon", "PAPER"));
            String label = custom != null ? custom : cmd;
            return new Reward(type, Items.make(mt == null ? Material.PAPER : mt, label), chance, amount, cmd, null, label);
        }
        return null;
    }

    // ---------- Crate-Verwaltung ----------
    public Collection<Crate> all() { return crates.values(); }
    public Crate get(String id) { return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT)); }

    public Crate create(String id) {
        String k = id.toLowerCase(Locale.ROOT);
        if (crates.containsKey(k)) return null;
        Crate c = new Crate(k, "<white><bold>" + id + " Crate", "<white>" + id + " Key", Material.CHEST);
        c.rewards.add(money(1000, 10));
        c.recalc();
        crates.put(k, c);
        save();
        return c;
    }

    public boolean delete(String id) {
        Crate c = crates.remove(id.toLowerCase(Locale.ROOT));
        if (c == null) return false;
        plugin.data().removeCratesOf(c.id);
        plugin.data().save(true);
        save();
        return true;
    }

    // ---------- Keys ----------
    public int keys(UUID id, String crate) {
        PlayerData d = plugin.data().get(id);
        return d == null ? 0 : d.keys.getOrDefault(crate.toLowerCase(Locale.ROOT), 0);
    }

    public void addKeys(UUID id, String crate, int amount) {
        PlayerData d = plugin.data().get(id);
        if (d == null) return;
        String k = crate.toLowerCase(Locale.ROOT);
        int n = Math.max(0, d.keys.getOrDefault(k, 0) + amount);
        if (n == 0) d.keys.remove(k);
        else d.keys.put(k, n);
    }

    // ---------- Öffnen ----------
    public void open(Player p, Crate c) {
        if (keys(p.getUniqueId(), c.id) <= 0) {
            Msg.error(p, "Du hast keinen Key für diese Crate! Keys gibt es im Stars-Shop (/crystalshop).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (c.mode.equals("CHOOSE")) {
            new CrateMenus.Choose(p, c).open();
            return;
        }
        addKeys(p.getUniqueId(), c.id, -1);
        Reward r = c.pick();
        give(p, r);
        new CrateMenus.Roll(p, c, r).start();
    }

    /** CHOOSE-Modus: Spieler hat sich einen Reward ausgesucht. */
    public void claim(Player p, Crate c, Reward r) {
        if (keys(p.getUniqueId(), c.id) <= 0) { Msg.error(p, "Du hast keinen Key mehr."); return; }
        addKeys(p.getUniqueId(), c.id, -1);
        give(p, r);
        Msg.send(p, "<green>Du hast gewählt: <white>" + r.label());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    public void give(Player p, Reward r) {
        switch (r.type()) {
            case "ITEM" -> p.getInventory().addItem(r.give().clone())
                    .values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            case "MONEY" -> plugin.economy().add(p.getUniqueId(), r.amount());
            case "CRYSTALS" -> plugin.economy().addCrystals(p.getUniqueId(), (long) r.amount());
            case "COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), r.command().replace("%player%", p.getName()));
            default -> { }
        }
    }

    // ---------- Partikel an Crate-Blöcken ----------
    public void startParticles() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String key : plugin.data().crateBlocks().keySet()) {
                String[] s = key.split(";");
                if (s.length < 4) continue;
                World w = Bukkit.getWorld(s[0]);
                if (w == null) continue;
                try {
                    int x = Integer.parseInt(s[1]), y = Integer.parseInt(s[2]), z = Integer.parseInt(s[3]);
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    w.spawnParticle(Particle.END_ROD, x + 0.5, y + 1.2, z + 0.5, 2, 0.25, 0.25, 0.25, 0.01);
                } catch (NumberFormatException ignored) { }
            }
        }, 40L, 20L);
    }
}
