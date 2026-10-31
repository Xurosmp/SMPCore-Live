package de.smpcore.gui;

import de.smpcore.kit.KitManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class KitMenu extends PagedMenu {
    public KitMenu(Player v) { super(v, "<green><bold>Kits"); }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (KitManager.Kit k : plugin.kits().all()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            for (ItemStack it : k.items) lore.add("<gray>• <white>" + it.getAmount() + "x " + Items.pretty(it.getType()));
            lore.add("");
            lore.add("<gray>Status: " + plugin.kits().status(viewer, k));
            lore.add("<yellow>Klicken zum Abholen");
            out.add(new Entry(Items.make(k.icon, 1, k.name, lore), e -> { plugin.kits().claim(viewer, k); refresh(); }));
        }
        return out;
    }

    @Override
    protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater()); }
}
