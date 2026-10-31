package de.smpcore.gui;

import de.smpcore.auction.AuctionManager;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class AuctionMenus {
    static final Map<UUID, AuctionManager.Sort> SORT = new HashMap<>();
    private AuctionMenus() {}

    public static void open(Player p, String filter) { new Main(p, filter).open(); }

    public static final class Main extends PagedMenu {
        private final String filter;

        public Main(Player v, String filter) {
            super(v, "<gold><bold>Auktionshaus" + (filter != null ? " <gray>» " + Msg.esc(filter) : ""));
            this.filter = filter;
        }

        @Override
        protected List<Entry> entries() {
            AuctionManager.Sort sort = SORT.getOrDefault(viewer.getUniqueId(), AuctionManager.Sort.NEWEST);
            List<Entry> out = new ArrayList<>();
            for (AuctionManager.Listing l : plugin.auctions().active(filter, sort)) {
                boolean own = l.seller.equals(viewer.getUniqueId());
                ItemStack shown = Items.withLore(l.item, List.of(
                        "",
                        "<gray>Preis: <green>" + EconomyManager.fmt(l.price),
                        "<gray>Verkäufer: <white>" + Msg.esc(l.sellerName),
                        "<gray>Läuft ab in: <white>" + Num.duration(l.expires - System.currentTimeMillis()),
                        "",
                        own ? "<yellow>Dein Angebot – Klick öffnet 'Meine Items'" : "<yellow>Klicken zum Kaufen"));
                out.add(new Entry(shown, e -> {
                    if (own) { go(new Mine(viewer)); return; }
                    go(new ConfirmMenu(viewer, "<green>Kauf bestätigen", shown,
                            () -> plugin.auctions().buy(viewer, l.id), () -> new Main(viewer, filter)));
                }));
            }
            return out;
        }

        @Override
        protected void controls() {
            AuctionManager.Sort sort = SORT.getOrDefault(viewer.getUniqueId(), AuctionManager.Sort.NEWEST);
            set(46, Items.make(Material.HOPPER, "<yellow>Sortierung: <white>" + sort.label, "<gray>Klicken zum Wechseln"),
                    e -> { SORT.put(viewer.getUniqueId(), sort.next()); page = 0; refresh(); });
            set(49, Items.make(Material.CHEST, "<aqua>Meine Items", "<gray>Eigene & abgelaufene Angebote"), e -> go(new Mine(viewer)));
            set(51, Items.make(Material.COMPASS, "<green>Suche", "<gray>/ah search <Text>", "<gray>Verkaufen: <white>/ah sell <Preis>"));
            if (filter != null) set(47, Items.make(Material.BARRIER, "<red>Filter zurücksetzen"), e -> go(new Main(viewer, null)));
            set(52, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
        }
    }

    public static final class Mine extends PagedMenu {
        public Mine(Player v) { super(v, "<gold>Meine Items"); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (AuctionManager.Listing l : plugin.auctions().mine(viewer.getUniqueId())) {
                boolean active = plugin.auctions().isActive(l);
                ItemStack shown = Items.withLore(l.item, List.of(
                        "",
                        "<gray>Preis: <green>" + EconomyManager.fmt(l.price),
                        active ? "<gray>Läuft ab in: <white>" + Num.duration(l.expires - System.currentTimeMillis()) : "<red>Abgelaufen",
                        "",
                        active ? "<yellow>Klicken zum Zurückziehen" : "<yellow>Klicken zum Einsammeln"));
                out.add(new Entry(shown, e -> { plugin.auctions().retrieve(viewer, l.id); refresh(); }));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer, null)));
        }
    }
}
