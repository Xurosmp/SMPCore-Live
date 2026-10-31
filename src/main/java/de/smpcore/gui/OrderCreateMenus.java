package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Order-Erstellung per GUI: Item wählen -> Menge und Preis pro Stück per Schild -> bestätigen. */
public final class OrderCreateMenus {
    public static final class Draft {
        public Material material;
        public int amount = 64;
        public double price = 1;
    }

    static final Map<UUID, Draft> DRAFTS = new HashMap<>();
    private static List<Material> allItems;

    private OrderCreateMenus() {}

    static Draft draft(Player p) { return DRAFTS.computeIfAbsent(p.getUniqueId(), k -> new Draft()); }

    private static List<Material> all() {
        if (allItems == null) {
            List<String> black = de.smpcore.SMPCore.get().getConfig().getStringList("orders.blacklist");
            List<Material> l = new ArrayList<>();
            for (Material m : Material.values()) {
                if (m.name().startsWith("LEGACY_") || !m.isItem() || m.isAir() || black.contains(m.name())) continue;
                l.add(m);
            }
            allItems = l;
        }
        return allItems;
    }

    public static void start(Player p) {
        DRAFTS.remove(p.getUniqueId());
        new ItemPicker(p, "ALL", null).open();
    }

    // ------------------------------------------------------------ Item wählen
    public static final class ItemPicker extends PagedMenu {
        private final String mode;   // ALL | INV | SEARCH
        private final String filter;

        public ItemPicker(Player v, String mode, String filter) {
            super(v, "<gold>Order » Item wählen" + (filter != null ? " <gray>» " + Msg.esc(filter) : ""));
            this.mode = mode;
            this.filter = filter;
        }

        @Override
        protected List<Entry> entries() {
            List<Material> list;
            if (mode.equals("INV")) {
                Set<Material> set = new LinkedHashSet<>();
                for (ItemStack it : viewer.getInventory().getStorageContents()) {
                    if (it != null && !it.getType().isAir() && !it.hasItemMeta()) set.add(it.getType());
                }
                list = new ArrayList<>(set);
            } else if (mode.equals("SEARCH") && filter != null) {
                String f = filter.toLowerCase(Locale.ROOT);
                list = new ArrayList<>();
                for (Material m : all()) {
                    if (m.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(f)) list.add(m);
                }
            } else {
                list = all();
            }
            List<Entry> out = new ArrayList<>();
            for (Material m : list) {
                out.add(new Entry(Items.withLore(new ItemStack(m), List.of("", "<yellow>Klicken zum Auswählen")), e -> {
                    draft(viewer).material = m;
                    go(new Setup(viewer));
                }));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(46, Items.make(Material.CHEST, mode.equals("INV") ? "<green>Aus meinem Inventar <dark_gray>(aktiv)" : "<aqua>Aus meinem Inventar",
                    "<gray>Zeigt nur Items, die du dabei hast"), e -> go(new ItemPicker(viewer, "INV", null)));
            set(47, Items.make(Material.BOOKSHELF, mode.equals("ALL") ? "<green>Alle Items <dark_gray>(aktiv)" : "<aqua>Alle Items",
                    "<gray>Alle bestellbaren Items"), e -> go(new ItemPicker(viewer, "ALL", null)));
            set(48, Items.make(Material.OAK_SIGN, "<yellow>Suche", "<gray>Item-Name auf einem Schild eingeben"), e ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        viewer.closeInventory();
                        SignInput.ask(viewer, new String[]{"Item suchen", "(z.B. diamond)"},
                                text -> new ItemPicker(viewer, "SEARCH", text).open(),
                                () -> new ItemPicker(viewer, "ALL", null).open());
                    }));
            set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new OrderMenus.Browse(viewer, null)));
        }
    }

    // ------------------------------------------------------------ Menge / Preis / Bestätigen
    public static final class Setup extends Menu {
        public Setup(Player v) { super(v, 27, "<gold>Order erstellen"); }

        private void askAmount() {
            Bukkit.getScheduler().runTask(plugin, () -> {
                viewer.closeInventory();
                SignInput.ask(viewer, new String[]{"Menge eingeben", "(z.B. 64 oder 1k)"}, text -> {
                    try {
                        int n = (int) Num.parse(text);
                        int max = plugin.getConfig().getInt("orders.max-amount", 2304);
                        if (n < 1 || n > max) { Msg.error(viewer, "Menge: 1 bis " + max); }
                        else draft(viewer).amount = n;
                    } catch (NumberFormatException ex) { Msg.error(viewer, "Das ist keine gültige Zahl."); }
                    new Setup(viewer).open();
                }, () -> new Setup(viewer).open());
            });
        }

        private void askPrice() {
            Bukkit.getScheduler().runTask(plugin, () -> {
                viewer.closeInventory();
                SignInput.ask(viewer, new String[]{"Preis pro Stück", "(z.B. 350 oder 1.5k)"}, text -> {
                    try {
                        double v = Num.parse(text);
                        double min = plugin.getConfig().getDouble("orders.min-price", 0.01);
                        if (v < min) Msg.error(viewer, "Mindestpreis: " + EconomyManager.fmt(min));
                        else draft(viewer).price = v;
                    } catch (NumberFormatException ex) { Msg.error(viewer, "Das ist keine gültige Zahl."); }
                    new Setup(viewer).open();
                }, () -> new Setup(viewer).open());
            });
        }

        @Override
        protected void build() {
            Draft d = draft(viewer);
            if (d.material == null) { go(new ItemPicker(viewer, "ALL", null)); return; }
            double total = Math.round(d.amount * d.price * 100) / 100.0;
            set(13, Items.withLore(new ItemStack(d.material), List.of("",
                    "<gray>Menge: <white>" + d.amount,
                    "<gray>Preis pro Stück: <green>" + EconomyManager.fmt(d.price),
                    "<gray>Gesamt (reserviert): <green>" + EconomyManager.fmt(total),
                    "<gray>Dein Geld: <white>" + EconomyManager.fmt(plugin.economy().balance(viewer.getUniqueId())))));
            set(11, Items.make(Material.PAPER, "<aqua>Menge ändern", "<gray>Aktuell: <white>" + d.amount, "", "<yellow>Klicken: per Schild eingeben"), e -> askAmount());
            set(15, Items.make(Material.GOLD_NUGGET, "<gold>Preis pro Stück ändern", "<gray>Aktuell: <green>" + EconomyManager.fmt(d.price), "", "<yellow>Klicken: per Schild eingeben"), e -> askPrice());
            set(18, Items.make(Material.ARROW, "<yellow>Anderes Item wählen"), e -> go(new ItemPicker(viewer, "ALL", null)));
            set(20, Items.make(Material.LIME_CONCRETE, "<green><bold>Order erstellen", "<gray>Reserviert <green>" + EconomyManager.fmt(total)), e -> {
                plugin.orders().create(viewer, d.material, d.amount, d.price);
                DRAFTS.remove(viewer.getUniqueId());
                go(new OrderMenus.Mine(viewer));
            });
            set(24, Items.make(Material.RED_CONCRETE, "<red><bold>Abbrechen"), e -> {
                DRAFTS.remove(viewer.getUniqueId());
                go(new OrderMenus.Browse(viewer, null));
            });
        }
    }
}
