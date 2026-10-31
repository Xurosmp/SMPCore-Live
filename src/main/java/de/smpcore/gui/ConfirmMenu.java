package de.smpcore.gui;

import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/** Bestätigungsmenü – schützt vor versehentlichen Taps (wichtig auf Handy/Bedrock). */
public final class ConfirmMenu extends Menu {
    private final ItemStack display;
    private final Runnable onConfirm;
    private final Supplier<Menu> back;

    public ConfirmMenu(Player v, String title, ItemStack display, Runnable onConfirm, Supplier<Menu> back) {
        super(v, 27, title);
        this.display = display;
        this.onConfirm = onConfirm;
        this.back = back;
    }

    @Override
    protected void build() {
        set(13, display);
        set(11, Items.make(Material.LIME_CONCRETE, "<green><bold>Bestätigen", "<gray>Klicke zum Bestätigen"), e -> {
            onConfirm.run();
            leave();
        });
        set(15, Items.make(Material.RED_CONCRETE, "<red><bold>Abbrechen"), e -> leave());
        fill(Items.filler());
    }

    private void leave() {
        if (back != null) go(back.get());
        else closeLater();
    }
}
