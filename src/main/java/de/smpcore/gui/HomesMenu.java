package de.smpcore.gui;

import de.smpcore.data.PlayerData;
import de.smpcore.util.Items;
import de.smpcore.util.Locs;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HomesMenu extends PagedMenu {
    public HomesMenu(Player v) { super(v, "<green><bold>Deine Homes"); }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        PlayerData d = plugin.data().get(viewer.getUniqueId());
        if (d == null) return out;
        for (Map.Entry<String, String> h : d.homes.entrySet()) {
            Location loc = Locs.parse(h.getValue());
            if (loc == null) continue;
            out.add(new Entry(Items.make(Material.RED_BED, "<green>" + h.getKey(),
                    "<gray>Welt: <white>" + loc.getWorld().getName(),
                    "<gray>XYZ: <white>" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                    "", "<yellow>Klicken zum Teleportieren", "<dark_gray>Löschen: /delhome " + h.getKey()),
                    e -> { closeLater(); plugin.teleports().teleport(viewer, loc, h.getKey()); }));
        }
        return out;
    }

    @Override
    protected void controls() {
        set(49, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
    }
}
