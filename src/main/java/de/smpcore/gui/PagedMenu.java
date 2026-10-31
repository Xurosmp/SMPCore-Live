package de.smpcore.gui;

import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public abstract class PagedMenu extends Menu {
    public record Entry(ItemStack item, Consumer<InventoryClickEvent> click) {}

    protected int page = 0;

    protected PagedMenu(Player v, String title) { super(v, 54, title); }

    protected abstract List<Entry> entries();

    /** false = untere Zeile bleibt leer (Shop-Look). */
    protected boolean filler() { return true; }

    /** Eigene Buttons in der unteren Zeile (Slots 46-52). */
    protected void controls() {}

    @Override
    protected void build() {
        List<Entry> list = entries();
        int pages = Math.max(1, (list.size() + 44) / 45);
        if (page >= pages) page = pages - 1;
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            set(i, list.get(idx).item(), list.get(idx).click());
        }
        if (filler()) for (int i = 45; i < 54; i++) set(i, Items.filler());
        if (page > 0) set(45, Items.make(Material.ARROW, "<yellow>« Zurück", "<gray>Seite " + page + "/" + pages),
                e -> { page--; refresh(); });
        if (page < pages - 1) set(53, Items.make(Material.ARROW, "<yellow>Weiter »", "<gray>Seite " + (page + 2) + "/" + pages),
                e -> { page++; refresh(); });
        controls();
    }
}
