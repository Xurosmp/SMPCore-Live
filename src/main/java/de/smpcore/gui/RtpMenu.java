package de.smpcore.gui;

import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** /rtp: Oberwelt, Nether und End. Nether/End sind manuell freischaltbar (config rtp.worlds.*.enabled bzw. Admin Panel). */
public final class RtpMenu extends Menu {
    public RtpMenu(Player v) { super(v, 27, "<aqua><bold>Random Teleport"); }

    @Override protected String tag() { return "rtp"; }

    private void entry(int slot, String dim, Material icon, String name) {
        String reason = plugin.teleports().rtpBlockReason(viewer, dim);
        if (reason != null) {
            set(slot, Items.make(Material.BARRIER, name + " <dark_gray>(gesperrt)", "<red>" + reason));
            return;
        }
        boolean open = plugin.teleports().rtpEnabled(dim);
        set(slot, Items.make(icon, name, "<gray>Radius: <white>" + plugin.teleports().rtpMin(dim) + " <gray>bis <white>" + plugin.teleports().rtpMax(dim),
                        open ? "" : "<dark_gray>(für Spieler noch gesperrt – nur du als Admin)", "<yellow>Klicken zum Teleportieren"),
                e -> { closeLater(); plugin.teleports().rtp(viewer, dim); });
    }

    @Override
    protected void build() {
        entry(11, "overworld", Material.GRASS_BLOCK, "<green><bold>Oberwelt");
        entry(13, "nether", Material.NETHERRACK, "<red><bold>Nether");
        entry(15, "end", Material.END_STONE, "<light_purple><bold>End");
    }
}
