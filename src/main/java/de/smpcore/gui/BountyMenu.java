package de.smpcore.gui;

import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

public final class BountyMenu extends PagedMenu {
    public BountyMenu(Player v) { super(v, "<red><bold>Kopfgelder"); }

    @Override
    protected List<Entry> entries() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(plugin.bounties().all().entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> en : list) {
            PlayerData d = plugin.data().get(en.getKey());
            String name = d == null ? "?" : d.name;
            out.add(new Entry(Items.head(en.getKey(), "<red>" + Msg.esc(name), List.of(
                    "",
                    "<gray>Kopfgeld: <green>" + EconomyManager.fmt(en.getValue()),
                    "<gray>Töte den Spieler, um es zu kassieren!")), null));
        }
        return out;
    }

    @Override
    protected void controls() {
        set(49, Items.make(Material.PAPER, "<yellow>Kopfgeld setzen", "<gray>/bounty <Spieler> <Betrag>",
                "<gray>Mindestbetrag: <green>" + EconomyManager.fmt(plugin.getConfig().getDouble("bounty.min-amount", 1000))));
    }
}
