package de.smpcore.order;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * Orders (Kaufaufträge): Ein Spieler bestellt Items zu einem Preis pro Stück (Geld wird sofort reserviert).
 * Andere Spieler liefern die Items und bekommen sofort das Geld. Der Besteller holt die Items unter "Meine Orders" ab.
 */
public final class OrderManager {
    public enum Sort {
        PRICE("Höchster Preis zuerst"), NEWEST("Neueste zuerst");
        public final String label;
        Sort(String l) { label = l; }
        public Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final class Order {
        public final UUID id, owner;
        public final String ownerName;
        public final Material material;
        public int amount, filled, stored;
        public final double price;
        public final long created;
        public long expires;

        public Order(UUID id, UUID owner, String ownerName, Material material, int amount, double price, long created, long expires) {
            this.id = id; this.owner = owner; this.ownerName = ownerName; this.material = material;
            this.amount = amount; this.price = price; this.created = created; this.expires = expires;
        }

        public int remaining() { return amount - filled; }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();

    public OrderManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
    }

    // ---------------------------------------------------------- Laden / Speichern
    public void load() {
        orders.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("orders");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            if (c == null) continue;
            try {
                Material m = Material.matchMaterial(c.getString("material", ""));
                if (m == null) continue;
                Order o = new Order(UUID.fromString(key), UUID.fromString(c.getString("owner", "")), c.getString("ownerName", "?"),
                        m, c.getInt("amount"), c.getDouble("price"), c.getLong("created"), c.getLong("expires"));
                o.filled = c.getInt("filled");
                o.stored = c.getInt("stored");
                orders.put(o.id, o);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Order " + key + " defekt: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Order o : orders.values()) {
            String b = "orders." + o.id + ".";
            y.set(b + "owner", o.owner.toString());
            y.set(b + "ownerName", o.ownerName);
            y.set(b + "material", o.material.name());
            y.set(b + "amount", o.amount);
            y.set(b + "filled", o.filled);
            y.set(b + "stored", o.stored);
            y.set(b + "price", o.price);
            y.set(b + "created", o.created);
            y.set(b + "expires", o.expires);
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    // ---------------------------------------------------------- Abfragen
    public Order get(UUID id) { return orders.get(id); }

    public boolean isOpen(Order o) { return o.remaining() > 0 && o.expires > System.currentTimeMillis(); }

    public List<Order> open(String filter, Sort sort) {
        String f = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        List<Order> out = new ArrayList<>();
        for (Order o : orders.values()) {
            if (!isOpen(o)) continue;
            if (f != null && !o.material.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(f)
                    && !Items.pretty(o.material).toLowerCase(Locale.ROOT).contains(f)) continue;
            out.add(o);
        }
        if (sort == Sort.PRICE) out.sort(Comparator.comparingDouble((Order o) -> o.price).reversed());
        else out.sort(Comparator.comparingLong((Order o) -> o.created).reversed());
        return out;
    }

    public List<Order> mine(UUID id) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders.values()) if (o.owner.equals(id)) out.add(o);
        out.sort(Comparator.comparingLong((Order o) -> o.created).reversed());
        return out;
    }

    // ---------------------------------------------------------- Aktionen
    public void create(Player p, Material m, int amount, double price) {
        List<String> black = plugin.getConfig().getStringList("orders.blacklist");
        if (m == null || m.isAir() || !m.isItem() || black.contains(m.name())) { Msg.error(p, "Dieses Item kann nicht bestellt werden."); return; }
        int maxAmount = plugin.getConfig().getInt("orders.max-amount", 2304);
        if (amount < 1 || amount > maxAmount) { Msg.error(p, "Menge: 1 bis " + maxAmount); return; }
        double minPrice = plugin.getConfig().getDouble("orders.min-price", 0.01);
        if (price < minPrice) { Msg.error(p, "Mindestpreis pro Stück: " + EconomyManager.fmt(minPrice)); return; }
        int maxOrders = plugin.getConfig().getInt("orders.max-per-player", 5);
        long mine = orders.values().stream().filter(o -> o.owner.equals(p.getUniqueId()) && (o.remaining() > 0 || o.stored > 0)).count();
        if (mine >= maxOrders) { Msg.error(p, "Du hast schon " + maxOrders + " Orders. Schließe oder leere erst welche (/order mine)."); return; }
        double total = Math.round(price * amount * 100) / 100.0;
        if (!plugin.economy().take(p.getUniqueId(), total)) { Msg.error(p, "Nicht genug Geld! Du brauchst " + EconomyManager.fmt(total) + "."); return; }
        long now = System.currentTimeMillis();
        long hours = plugin.getConfig().getLong("orders.duration-hours", 72);
        Order o = new Order(UUID.randomUUID(), p.getUniqueId(), p.getName(), m, amount, price, now, now + hours * 3_600_000L);
        orders.put(o.id, o);
        save(true);
        Msg.send(p, "<green>Order erstellt: <white>" + amount + "x " + Items.pretty(m) + " <gray>für <green>" + EconomyManager.fmt(price)
                + " <gray>pro Stück <dark_gray>(" + EconomyManager.fmt(total) + " reserviert)");
    }

    /** Liefert Items aus dem Inventar an eine Order und zahlt sofort aus. */
    public void deliver(Player p, UUID id, int amount) {
        Order o = orders.get(id);
        if (o == null || !isOpen(o)) { Msg.error(p, "Diese Order ist nicht mehr offen."); return; }
        if (o.owner.equals(p.getUniqueId())) { Msg.error(p, "Du kannst nicht an deine eigene Order liefern."); return; }
        int want = Math.min(amount, o.remaining());
        int removed = plugin.shop().removeItems(p, o.material, want);
        if (removed <= 0) { Msg.error(p, "Du hast kein (unverändertes) " + Items.pretty(o.material) + " im Inventar."); return; }
        double pay = Math.round(removed * o.price * 100) / 100.0;
        plugin.economy().add(p.getUniqueId(), pay);
        o.filled += removed;
        o.stored += removed;
        save(true);
        Msg.send(p, "Geliefert: <white>" + removed + "x " + Items.pretty(o.material) + " <gray>→ <green>+" + EconomyManager.fmt(pay));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Player owner = plugin.getServer().getPlayer(o.owner);
        if (owner != null) {
            String prev = Msg.current();
            Msg.use("order");
            try {
                Msg.send(owner, "<white>" + Msg.esc(p.getName()) + " <gray>hat <white>" + removed + "x " + Items.pretty(o.material)
                        + " <gray>geliefert! Abholen: <white>/order mine");
            } finally { Msg.use(prev); }
        }
    }

    /** Schließt die Order und erstattet den offenen Rest. Bereits gelieferte Items bleiben abholbar. */
    public void cancel(Player p, UUID id) {
        Order o = orders.get(id);
        if (o == null || !o.owner.equals(p.getUniqueId())) { Msg.error(p, "Order nicht gefunden."); return; }
        refundRest(o);
        save(true);
        Msg.send(p, "<green>Order geschlossen.");
    }

    private void refundRest(Order o) {
        int rest = o.remaining();
        if (rest > 0) {
            double refund = Math.round(rest * o.price * 100) / 100.0;
            plugin.economy().add(o.owner, refund);
            o.amount = o.filled;
            Player owner = plugin.getServer().getPlayer(o.owner);
            if (owner != null) {
                String prev = Msg.current();
                Msg.use("order");
                try { Msg.send(owner, "<green>" + EconomyManager.fmt(refund) + " <gray>für nicht gelieferte " + Items.pretty(o.material) + " erstattet."); }
                finally { Msg.use(prev); }
            }
        }
        if (o.stored <= 0) orders.remove(o.id);
    }

    /** Gelieferte Items ins Inventar holen. */
    public void collect(Player p, UUID id) {
        Order o = orders.get(id);
        if (o == null || !o.owner.equals(p.getUniqueId())) { Msg.error(p, "Order nicht gefunden."); return; }
        if (o.stored <= 0) { Msg.error(p, "Es sind keine gelieferten Items zum Abholen da."); return; }
        int given = 0;
        while (o.stored > 0) {
            int n = Math.min(o.stored, o.material.getMaxStackSize());
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(o.material, n));
            int notGiven = 0;
            for (ItemStack l : left.values()) notGiven += l.getAmount();
            int got = n - notGiven;
            o.stored -= got;
            given += got;
            if (notGiven > 0) break;
        }
        if (o.stored <= 0 && o.remaining() <= 0) orders.remove(o.id);
        save(true);
        if (given > 0) Msg.send(p, "<green>Abgeholt: <white>" + given + "x " + Items.pretty(o.material));
        else Msg.error(p, "Dein Inventar ist voll!");
    }

    /** Abgelaufene Orders schließen (Rest wird erstattet). */
    public void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Order o : new ArrayList<>(orders.values())) {
            if (o.expires <= now && o.remaining() > 0) { refundRest(o); changed = true; }
        }
        if (changed) save(true);
    }
}
