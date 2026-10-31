package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class SpawnerMenus {
    private SpawnerMenus() {}

    public static final class Main extends Menu {
        private final SpawnerManager.Spawner s;

        public Main(Player v, SpawnerManager.Spawner s) {
            super(v, 27, "<yellow>" + SpawnerManager.prettyType(s.type) + " Spawner");
            this.s = s;
        }

        @Override
        protected void build() {
            SpawnerManager sm = plugin.spawners();
            set(4, Items.make(Material.SPAWNER, "<yellow>" + SpawnerManager.prettyType(s.type) + " Spawner",
                    "<gray>Stapel: <white>" + s.stack + "<dark_gray>/" + sm.maxStack(),
                    "<gray>Intervall: <white>" + sm.intervalSeconds() + "s",
                    "<gray>Lager: <white>" + Num.format(s.total()) + "<dark_gray>/" + Num.format(sm.maxStorage())));
            set(11, Items.make(Material.CHEST, "<aqua>Lager öffnen", "<gray>Items entnehmen", "", "<yellow>Klicken"), e -> go(new Storage(viewer, s)));
            set(13, Items.make(Material.EXPERIENCE_BOTTLE, "<green>XP einsammeln", "<gray>Gespeichert: <white>" + Num.format(s.xp), "", "<yellow>Klicken"),
                    e -> { sm.collectXp(viewer, s); refresh(); });
            set(15, Items.make(Material.GOLD_INGOT, "<gold>Alles verkaufen", "<gray>Wert: <green>" + EconomyManager.fmt(sm.sellValue(s)), "", "<yellow>Klicken"),
                    e -> { sm.sellAll(viewer, s); refresh(); });
            set(22, Items.make(Material.HOPPER, "<light_purple>Spawner stapeln", "<gray>Legt Spawner aus deinem Inventar dazu",
                            "<gray>Max: <white>" + sm.maxStack(), "", "<yellow>Klicken"),
                    e -> {
                        int n = sm.addStack(viewer, s);
                        if (n > 0) Msg.send(viewer, "<green>+" + n + " Spawner gestapelt."); else Msg.error(viewer, "Keine passenden Spawner im Inventar (oder Maximum erreicht).");
                        refresh();
                    });
            set(18, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
        }
    }

    public static final class Storage extends PagedMenu {
        private final SpawnerManager.Spawner s;

        public Storage(Player v, SpawnerManager.Spawner s) {
            super(v, "<aqua>Spawner-Lager");
            this.s = s;
        }

        @Override
        protected List<Entry> entries() {
            List<Map.Entry<Material, Long>> list = new ArrayList<>(s.storage.entrySet());
            list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            List<Entry> out = new ArrayList<>();
            for (Map.Entry<Material, Long> en : list) {
                Material mat = en.getKey();
                long count = en.getValue();
                ItemStack icon = Items.make(mat, (int) Math.min(count, 64), "<white>" + Items.pretty(mat),
                        List.of("<gray>Gelagert: <white>" + Num.format(count), "", "<yellow>Klicken: bis zu 1 Stack entnehmen"));
                out.add(new Entry(icon, e -> {
                    long cur = s.storage.getOrDefault(mat, 0L);
                    int take = (int) Math.min(cur, mat.getMaxStackSize());
                    if (take <= 0) { refresh(); return; }
                    Map<Integer, ItemStack> left = viewer.getInventory().addItem(new ItemStack(mat, take));
                    long back = 0;
                    for (ItemStack l : left.values()) back += l.getAmount();
                    long rest = cur - take + back;
                    if (rest <= 0) s.storage.remove(mat); else s.storage.put(mat, rest);
                    if (back == take) Msg.error(viewer, "Dein Inventar ist voll!");
                    refresh();
                }));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer, s)));
            set(47, Items.make(Material.CHEST, "<aqua>Gesamt gelagert", "<white>" + Num.format(s.total()) + "<gray>/" + Num.format(plugin.spawners().maxStorage())));
        }
    }
}
