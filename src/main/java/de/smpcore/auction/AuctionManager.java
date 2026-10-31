package de.smpcore.auction;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public final class AuctionManager {
    public enum Sort {
        NEWEST("Neueste zuerst"), CHEAPEST("Günstigste zuerst"), PRICIEST("Teuerste zuerst");
        public final String label;
        Sort(String l) { label = l; }
        public Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final class Listing {
        public final UUID id, seller;
        public final String sellerName;
        public final ItemStack item;
        public final double price;
        public final long created;
        public long expires;

        public Listing(UUID id, UUID seller, String sellerName, ItemStack item, double price, long created, long expires) {
            this.id = id; this.seller = seller; this.sellerName = sellerName;
            this.item = item; this.price = price; this.created = created; this.expires = expires;
        }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();

    public AuctionManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ah.yml");
    }

    public void load() {
        listings.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("listings");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection c = root.getConfigurationSection(key);
                if (c == null) continue;
                Listing l = new Listing(UUID.fromString(key), UUID.fromString(c.getString("seller", "")),
                        c.getString("sellerName", "?"), Items.decode(c.getString("item", "")),
                        c.getDouble("price"), c.getLong("created"), c.getLong("expires"));
                listings.put(l.id, l);
            } catch (Exception ex) {
                plugin.getLogger().warning("AH: Listing " + key + " konnte nicht geladen werden: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String b = "listings." + l.id + ".";
            y.set(b + "seller", l.seller.toString());
            y.set(b + "sellerName", l.sellerName);
            y.set(b + "price", l.price);
            y.set(b + "created", l.created);
            y.set(b + "expires", l.expires);
            y.set(b + "item", Items.encode(l.item));
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    public boolean isActive(Listing l) { return l.expires > System.currentTimeMillis(); }

    public Listing get(UUID id) { return listings.get(id); }

    public List<Listing> active(String filter, Sort sort) {
        String f = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (!isActive(l)) continue;
            if (f != null && !matches(l.item, f)) continue;
            out.add(l);
        }
        switch (sort) {
            case NEWEST -> out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
            case CHEAPEST -> out.sort(Comparator.comparingDouble((Listing l) -> l.price));
            case PRICIEST -> out.sort(Comparator.comparingDouble((Listing l) -> l.price).reversed());
        }
        return out;
    }

    private boolean matches(ItemStack it, String f) {
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(f)
                || Items.pretty(it.getType()).toLowerCase(Locale.ROOT).contains(f);
    }

    public List<Listing> mine(UUID id) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller.equals(id)) out.add(l);
        out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        return out;
    }

    public void create(Player p, double price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Msg.error(p, "Du musst ein Item in der Hand halten."); return; }
        double min = plugin.getConfig().getDouble("auction.min-price", 1);
        double max = plugin.getConfig().getDouble("auction.max-price", 1e12);
        if (price < min || price > max) {
            Msg.error(p, "Preis muss zwischen " + EconomyManager.fmt(min) + " und " + EconomyManager.fmt(max) + " liegen.");
            return;
        }
        long count = listings.values().stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
        int maxL = plugin.ranks().listings(p);
        if (count >= maxL) {
            Msg.error(p, "Du hast bereits " + maxL + " Angebote (inkl. abgelaufener – hole sie unter 'Meine Items' ab).");
            return;
        }
        ItemStack copy = hand.clone();
        p.getInventory().setItemInMainHand(null);
        long now = System.currentTimeMillis();
        long hours = plugin.getConfig().getLong("auction.duration-hours", 48);
        Listing l = new Listing(UUID.randomUUID(), p.getUniqueId(), p.getName(), copy, price, now, now + hours * 3_600_000L);
        listings.put(l.id, l);
        save(true);
        Msg.send(p, "<green>Angebot erstellt: <white>" + copy.getAmount() + "x " + Items.pretty(copy.getType())
                + " <gray>für <green>" + EconomyManager.fmt(price));
    }

    public void buy(Player p, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !isActive(l)) { Msg.error(p, "Dieses Angebot existiert nicht mehr."); return; }
        if (l.seller.equals(p.getUniqueId())) { Msg.error(p, "Du kannst dein eigenes Item nicht kaufen."); return; }
        if (p.getInventory().firstEmpty() == -1) { Msg.error(p, "Dein Inventar ist voll!"); return; }
        if (!plugin.economy().take(p.getUniqueId(), l.price)) {
            Msg.error(p, "Nicht genug Geld! Du brauchst " + EconomyManager.fmt(l.price));
            return;
        }
        listings.remove(id);
        double tax = l.price * plugin.getConfig().getDouble("auction.tax-percent", 5) / 100.0;
        double payout = l.price - tax;
        plugin.economy().add(l.seller, payout);
        p.getInventory().addItem(l.item);
        save(true);
        Msg.send(p, "<green>Gekauft: <white>" + l.item.getAmount() + "x " + Items.pretty(l.item.getType())
                + " <gray>für <green>" + EconomyManager.fmt(l.price));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        Player seller = plugin.getServer().getPlayer(l.seller);
        if (seller != null) {
            Msg.send(seller, "<gold>Dein Item <white>" + l.item.getAmount() + "x " + Items.pretty(l.item.getType())
                    + " <gold>wurde verkauft! <green>+" + EconomyManager.fmt(payout) + " <gray>(Steuer " + EconomyManager.fmt(tax) + ")");
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1f);
        }
    }

    /** Zurückziehen (aktiv) oder Einsammeln (abgelaufen). Nur für den Verkäufer. */
    public void retrieve(Player p, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller.equals(p.getUniqueId())) { Msg.error(p, "Angebot nicht gefunden."); return; }
        if (p.getInventory().firstEmpty() == -1) { Msg.error(p, "Dein Inventar ist voll!"); return; }
        listings.remove(id);
        p.getInventory().addItem(l.item);
        save(true);
        Msg.send(p, "<green>Item zurückerhalten: <white>" + l.item.getAmount() + "x " + Items.pretty(l.item.getType()));
    }
}
