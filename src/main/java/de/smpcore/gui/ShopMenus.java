package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.shop.ShopManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shop im HugoSMP-Look: 3 Reihen, kein Füll-Glas.
 * Hauptmenü: Kategorien mittig. Unterseite: Titel in Cyan, Items in der mittleren Reihe,
 * rote Glasscheibe unten mittig = Zurück.
 */
public final class ShopMenus {
    private ShopMenus() {}

    public static void openMain(Player p) { new Main(p).open(); }

    private static ItemStack backPane() {
        return Items.make(Material.RED_STAINED_GLASS_PANE, "<red>Zurück");
    }

    public static final class Main extends Menu {
        public Main(Player p) { super(p, 27, "Shop"); }

        @Override
        protected void build() {
            int next = 11;
            Set<Integer> used = new HashSet<>();
            for (ShopManager.Category c : plugin.shop().categories()) {
                int slot = c.slot();
                if (slot < 0 || slot >= 27 || used.contains(slot)) {
                    while (used.contains(next)) next++;
                    slot = next;
                }
                used.add(slot);
                if (c.action().equalsIgnoreCase("crystalstore")) {
                    set(slot, Items.make(c.icon(), c.name(), "<gray>Gib deine Stars für Ausrüstung & Spawner aus",
                                    "<gray>Du hast: <yellow>" + EconomyManager.fmtC(plugin.economy().crystals(viewer.getUniqueId())),
                                    "<dark_gray>Stars verdienst du in der AFK-Zone (/afk)", "", "<yellow>Klicken zum Öffnen"),
                            e -> go(plugin.crystalShop().hasLayout() ? new CrystalStoreMenu(viewer) : new CrystalShopMenu(viewer, false)));
                } else if (c.locked()) {
                    set(slot, Items.make(c.icon(), c.name()));
                } else {
                    set(slot, Items.make(c.icon(), c.name(), "<gray>" + c.items().size() + " Items", "", "<yellow>Klicken zum Öffnen"),
                            e -> go(new Cat(viewer, c, 0)));
                }
            }
        }
    }

    /** Unterseite einer Kategorie: 9 Items pro Seite in der mittleren Reihe. */
    public static final class Cat extends Menu {
        private final ShopManager.Category cat;
        private final int page;

        public Cat(Player p, ShopManager.Category cat, int page) {
            super(p, 27, "<aqua>" + cat.name().replaceAll("<[^>]*>", ""));
            this.cat = cat;
            this.page = page;
        }

        @Override
        protected void build() {
            List<ShopManager.Item> items = cat.items();
            int pages = Math.max(1, (items.size() + 8) / 9);
            int pg = Math.min(Math.max(0, page), pages - 1);
            for (int i = 0; i < 9; i++) {
                int idx = pg * 9 + i;
                if (idx >= items.size()) break;
                ShopManager.Item it = items.get(idx);
                List<String> lore = new ArrayList<>();
                lore.add("");
                if (it.buy() > 0) lore.add("<gray>Kaufen: <green>" + EconomyManager.fmt(it.buy()));
                if (it.sell() > 0) lore.add("<gray>Verkaufen: <gold>" + EconomyManager.fmt(it.sell()));
                lore.add("");
                lore.add("<yellow>Klicken zum Auswählen");
                set(9 + i, Items.withLore(new ItemStack(it.material()), lore), e -> go(new Detail(viewer, cat, it, pg)));
            }
            set(22, backPane(), e -> go(new Main(viewer)));
            if (pg > 0) set(18, Items.make(Material.ARROW, "<yellow>« Vorherige Seite"), e -> go(new Cat(viewer, cat, pg - 1)));
            if (pg < pages - 1) set(26, Items.make(Material.ARROW, "<yellow>Nächste Seite »"), e -> go(new Cat(viewer, cat, pg + 1)));
        }
    }

    /** Kauf-/Verkaufsmenü eines Items. */
    public static final class Detail extends Menu {
        private final ShopManager.Category cat;
        private final ShopManager.Item item;
        private final int page;

        public Detail(Player p, ShopManager.Category cat, ShopManager.Item item, int page) {
            super(p, 27, "<aqua>" + Items.pretty(item.material()));
            this.cat = cat;
            this.item = item;
            this.page = page;
        }

        @Override
        protected void build() {
            Material m = item.material();
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (item.buy() > 0) lore.add("<gray>Kaufpreis: <green>" + EconomyManager.fmt(item.buy()) + " <dark_gray>/ Stück");
            if (item.sell() > 0) lore.add("<gray>Verkaufspreis: <gold>" + EconomyManager.fmt(item.sell()) + " <dark_gray>/ Stück");
            lore.add("");
            lore.add("<gray>Dein Geld: <white>" + EconomyManager.fmt(plugin.economy().balance(viewer.getUniqueId())));
            set(13, Items.withLore(new ItemStack(m), lore));

            int[] amounts = {1, 16, 64};
            for (int i = 0; i < 3; i++) {
                final int amt = amounts[i];
                if (item.buy() > 0) {
                    set(10 + i, Items.make(Material.LIME_STAINED_GLASS_PANE, amt, "<green><bold>Kaufen <white>" + amt + "x",
                                    List.of("<gray>Preis: <green>" + EconomyManager.fmt(item.buy() * amt))),
                            e -> { plugin.shop().buy(viewer, item, amt); refresh(); });
                }
                if (item.sell() > 0) {
                    set(14 + i, Items.make(Material.RED_STAINED_GLASS_PANE, amt, "<red><bold>Verkaufen <white>" + amt + "x",
                                    List.of("<gray>Erlös: <gold>" + EconomyManager.fmt(item.sell() * amt))),
                            e -> { plugin.shop().sellItem(viewer, m, item.sell(), amt); refresh(); });
                }
            }
            if (item.sell() > 0) {
                set(4, Items.make(Material.GOLD_BLOCK, "<gold><bold>Alles verkaufen", "<gray>Verkauft alle " + Items.pretty(m) + " aus deinem Inventar"),
                        e -> { plugin.shop().sellItem(viewer, m, item.sell(), Integer.MAX_VALUE); refresh(); });
            }
            set(22, backPane(), e -> go(new Cat(viewer, cat, page)));
        }
    }
}
