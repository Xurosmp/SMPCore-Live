package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.shop.CrystalShopManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Stars-Shop (AFK-Währung) und Gem-Shop (Store-Währung). */
public final class CrystalShopMenu extends PagedMenu {
    private final boolean gems;

    public CrystalShopMenu(Player p, boolean gems) {
        super(p, gems ? "<light_purple><bold>Gem-Shop" : "<yellow><bold>Stars-Shop");
        this.gems = gems;
    }

    private CrystalShopManager mgr() { return gems ? plugin.gemShop() : plugin.crystalShop(); }

    @Override protected String tag() { return gems ? "gemshop" : "crystalshop"; }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (CrystalShopManager.Entry en : mgr().entries()) {
            List<String> lore = new ArrayList<>(en.lore());
            lore.add("");
            lore.add("<gray>Preis: <light_purple>" + (gems ? EconomyManager.fmtG(en.cost()) : EconomyManager.fmtC(en.cost())));
            lore.add("<yellow>Klicken zum Kaufen");
            ItemStack shown = Items.make(en.icon(), en.amount() > 1 && en.amount() <= 64 && en.type().equals("ITEM") ? en.amount() : 1, en.name(), lore);
            out.add(new Entry(shown, e -> go(new ConfirmMenu(viewer, "<light_purple>Kauf bestätigen", shown,
                    () -> mgr().purchase(viewer, en), () -> new CrystalShopMenu(viewer, gems)))));
        }
        return out;
    }

    @Override
    protected void controls() {
        if (gems) {
            set(49, Items.make(Material.AMETHYST_CLUSTER, "<light_purple>Deine Gems",
                    "<white>" + EconomyManager.fmtG(plugin.economy().gems(viewer.getUniqueId()))));
        } else {
            set(49, Items.make(Material.NETHER_STAR, "<yellow>Deine Stars",
                    "<white>" + EconomyManager.fmtC(plugin.economy().crystals(viewer.getUniqueId())), "<gray>Hol dir Stars in der AFK-Zone: /afk"));
        }
    }
}
