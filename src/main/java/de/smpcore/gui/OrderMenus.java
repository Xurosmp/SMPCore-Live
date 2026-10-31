package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.order.OrderManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class OrderMenus {
    static final Map<UUID, OrderManager.Sort> SORT = new HashMap<>();
    private OrderMenus() {}

    public static void open(Player p, String filter) { new Browse(p, filter).open(); }

    private static int count(Player p, Material m) {
        int c = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && !it.hasItemMeta()) c += it.getAmount();
        }
        return c;
    }

    // ------------------------------------------------------------ Alle offenen Orders
    public static final class Browse extends PagedMenu {
        private final String filter;

        public Browse(Player v, String filter) {
            super(v, "<gold><bold>Orders" + (filter != null ? " <gray>» " + Msg.esc(filter) : ""));
            this.filter = filter;
        }

        @Override
        protected List<Entry> entries() {
            OrderManager.Sort sort = SORT.getOrDefault(viewer.getUniqueId(), OrderManager.Sort.PRICE);
            List<Entry> out = new ArrayList<>();
            for (OrderManager.Order o : plugin.orders().open(filter, sort)) {
                boolean own = o.owner.equals(viewer.getUniqueId());
                ItemStack icon = Items.withLore(new ItemStack(o.material), List.of(
                        "",
                        "<gray>Besteller: <white>" + Msg.esc(o.ownerName),
                        "<gray>Offen: <white>" + o.remaining() + "<dark_gray>/" + o.amount,
                        "<gray>Preis pro Stück: <green>" + EconomyManager.fmt(o.price),
                        "<gray>Gesamt offen: <green>" + EconomyManager.fmt(o.price * o.remaining()),
                        "",
                        own ? "<yellow>Deine Order – Klick öffnet 'Meine Orders'" : "<yellow>Klicken zum Liefern"));
                out.add(new Entry(icon, e -> go(own ? new Mine(viewer) : new Fulfill(viewer, o.id, filter))));
            }
            return out;
        }

        @Override
        protected void controls() {
            OrderManager.Sort sort = SORT.getOrDefault(viewer.getUniqueId(), OrderManager.Sort.PRICE);
            set(46, Items.make(Material.HOPPER, "<yellow>Sortierung: <white>" + sort.label, "<gray>Klicken zum Wechseln"),
                    e -> { SORT.put(viewer.getUniqueId(), sort.next()); page = 0; refresh(); });
            if (filter != null) set(47, Items.make(Material.BARRIER, "<red>Filter zurücksetzen"), e -> go(new Browse(viewer, null)));
            set(49, Items.make(Material.CHEST, "<aqua>Meine Orders", "<gray>Eigene Orders & Lieferungen abholen"), e -> go(new Mine(viewer)));
            set(51, Items.make(Material.EMERALD, "<green>Neue Order erstellen", "<gray>Item auswählen, Menge und Preis per Schild eingeben", "",
                    "<yellow>Klicken", "<dark_gray>Oder: /order create <Item> <Menge> <Preis>"), e -> OrderCreateMenus.start(viewer));
            set(52, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
        }
    }

    // ------------------------------------------------------------ Liefern
    public static final class Fulfill extends Menu {
        private final UUID id;
        private final String filter;

        public Fulfill(Player v, UUID id, String filter) {
            super(v, 27, "<gold>Order beliefern");
            this.id = id;
            this.filter = filter;
        }

        @Override
        protected void build() {
            OrderManager.Order o = plugin.orders().get(id);
            if (o == null || !plugin.orders().isOpen(o)) { go(new Browse(viewer, filter)); return; }
            int have = count(viewer, o.material);
            set(13, Items.withLore(new ItemStack(o.material), List.of(
                    "",
                    "<gray>Besteller: <white>" + Msg.esc(o.ownerName),
                    "<gray>Offen: <white>" + o.remaining() + "<dark_gray>/" + o.amount,
                    "<gray>Preis pro Stück: <green>" + EconomyManager.fmt(o.price),
                    "<gray>Du hast: <white>" + Num.format(have))));
            int[] amts = {1, 16, 64};
            for (int i = 0; i < 3; i++) {
                final int a = amts[i];
                set(10 + i, Items.make(Material.LIME_STAINED_GLASS_PANE, a, "<green><bold>Liefern <white>" + a + "x",
                        List.of("<gray>Erlös: <green>" + EconomyManager.fmt(Math.min(a, o.remaining()) * o.price))),
                        e -> { plugin.orders().deliver(viewer, id, a); refresh(); });
            }
            set(15, Items.make(Material.GOLD_BLOCK, "<gold><bold>Alles liefern", "<gray>Maximal so viel, wie offen ist"),
                    e -> { plugin.orders().deliver(viewer, id, Integer.MAX_VALUE); refresh(); });
            set(22, Items.make(Material.RED_STAINED_GLASS_PANE, "<red>Zurück"), e -> go(new Browse(viewer, filter)));
        }
    }

    // ------------------------------------------------------------ Meine Orders
    public static final class Mine extends PagedMenu {
        public Mine(Player v) { super(v, "<gold>Meine Orders"); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (OrderManager.Order o : plugin.orders().mine(viewer.getUniqueId())) {
                boolean open = plugin.orders().isOpen(o);
                String action = o.stored > 0 ? "<yellow>Klicken: " + o.stored + " gelieferte Items abholen"
                        : open ? "<yellow>Klicken: Order schließen (Rest wird erstattet)" : "<gray>Abgeschlossen";
                ItemStack icon = Items.withLore(new ItemStack(o.material), List.of(
                        "",
                        "<gray>Fortschritt: <white>" + o.filled + "<dark_gray>/" + o.amount,
                        "<gray>Preis pro Stück: <green>" + EconomyManager.fmt(o.price),
                        "<gray>Zum Abholen: <white>" + o.stored,
                        "<gray>Status: " + (open ? "<green>offen" : "<red>geschlossen"),
                        "", action));
                out.add(new Entry(icon, e -> {
                    if (o.stored > 0) { plugin.orders().collect(viewer, o.id); refresh(); }
                    else if (open) go(new ConfirmMenu(viewer, "<red>Order schließen?", Items.make(o.material, "<white>" + Items.pretty(o.material)),
                            () -> plugin.orders().cancel(viewer, o.id), () -> new Mine(viewer)));
                }));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Browse(viewer, null)));
            set(51, Items.make(Material.EMERALD, "<green>Neue Order erstellen", "<yellow>Klicken"), e -> OrderCreateMenus.start(viewer));
        }
    }
}
