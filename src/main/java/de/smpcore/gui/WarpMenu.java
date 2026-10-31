package de.smpcore.gui;

import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.warp.WarpManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class WarpMenu extends PagedMenu {
    public WarpMenu(Player v) { super(v, "<aqua><bold>Warps"); }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (WarpManager.Warp w : plugin.warps().all()) {
            out.add(new Entry(Items.make(w.icon(), "<aqua>" + Msg.esc(w.name()), "", "<yellow>Klicken zum Teleportieren"), e -> {
                Location l = plugin.warps().location(w);
                closeLater();
                if (l == null) Msg.error(viewer, "Dieser Warp ist defekt.");
                else plugin.teleports().teleport(viewer, l, w.name());
            }));
        }
        return out;
    }

    @Override
    protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater()); }
}
