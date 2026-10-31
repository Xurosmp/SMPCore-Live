package de.smpcore.gui;

import de.smpcore.crate.CrateManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrateMenus {
    private CrateMenus() {}

    /** Übersicht: alle Crates + eigene Keys */
    public static final class Keys extends Menu {
        public Keys(Player v) { super(v, 27, "<gold><bold>Crates & Keys"); }

        @Override
        protected void build() {
            int slot = 10;
            for (CrateManager.Crate c : plugin.crates().all()) {
                if (slot > 16) break;
                int keys = plugin.crates().keys(viewer.getUniqueId(), c.id);
                set(slot++, Items.make(c.icon, c.name, "", "<gray>Deine Keys: <white>" + keys, "",
                        "<yellow>Klicken für Vorschau"), e -> go(new Preview(viewer, c)));
            }
            set(22, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
            fill(Items.filler());
        }
    }

    /** Vorschau aller Rewards mit Chancen */
    public static final class Preview extends PagedMenu {
        private final CrateManager.Crate crate;

        public Preview(Player v, CrateManager.Crate crate) {
            super(v, crate.name);
            this.crate = crate;
        }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (CrateManager.Reward r : crate.rewards) {
                out.add(new Entry(Items.withLore(r.icon(), List.of("", String.format(Locale.US, "<gray>Chance: <yellow>%.2f%%", crate.chance(r)))), null));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Keys(viewer)));
            set(47, Items.make(Material.TRIPWIRE_HOOK, crate.keyName, "<gray>Deine Keys: <white>" + plugin.crates().keys(viewer.getUniqueId(), crate.id),
                    "<gray>Modus: <white>" + (crate.mode.equals("CHOOSE") ? "Du wählst deinen Reward" : "Zufall"),
                    "<gray>Rechtsklick auf die Crate zum Öffnen"));
        }
    }

    /** Wahl-Crate: Spieler sucht sich einen Reward selbst aus. */
    public static final class Choose extends PagedMenu {
        private final CrateManager.Crate crate;

        public Choose(Player v, CrateManager.Crate crate) {
            super(v, crate.name);
            this.crate = crate;
        }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (CrateManager.Reward r : crate.rewards) {
                out.add(new Entry(Items.withLore(r.icon(), List.of("", "<yellow>Klicken zum Auswählen")),
                        e -> go(new ConfirmMenu(viewer, "<green>Reward wählen?", r.icon(),
                                () -> plugin.crates().claim(viewer, crate, r), null))));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
            set(47, Items.make(Material.TRIPWIRE_HOOK, crate.keyName, "<gray>Wähle deinen Reward!",
                    "<gray>Keys: <white>" + plugin.crates().keys(viewer.getUniqueId(), crate.id)));
        }
    }

    /** Öffnungs-Animation. Der Gewinn ist beim Start schon vergeben – die Animation ist nur die Show. */
    public static final class Roll extends Menu {
        private static final int TOTAL = 28;
        private final CrateManager.Crate crate;
        private final CrateManager.Reward winner;
        private final List<CrateManager.Reward> strip = new ArrayList<>();
        private int step = 0;

        public Roll(Player v, CrateManager.Crate crate, CrateManager.Reward winner) {
            super(v, 27, crate.name);
            this.crate = crate;
            this.winner = winner;
            for (int i = 0; i < TOTAL + 9; i++) strip.add(crate.pick());
            strip.set(TOTAL + 4, winner);
        }

        @Override
        protected void build() {
            for (int i = 0; i < 9; i++) set(9 + i, strip.get(step + i).icon());
            set(4, Items.make(Material.HOPPER, "<yellow>▼"));
            set(22, Items.make(Material.END_ROD, "<yellow>▲"));
            fill(Items.filler());
        }

        public void start() {
            open();
            Bukkit.getScheduler().runTaskLater(plugin, () -> run(1), 2L);
        }

        private int delay(int s) {
            if (s < 14) return 2;
            if (s < 21) return 3;
            if (s < 26) return 5;
            return 8;
        }

        private void run(int s) {
            step = s;
            if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                refresh();
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
            }
            if (s >= TOTAL) { finish(); return; }
            Bukkit.getScheduler().runTaskLater(plugin, () -> run(s + 1), delay(s));
        }

        private void finish() {
            if (viewer.isOnline()) {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                Msg.send(viewer, "<green>Du hast gewonnen: <white>" + winner.label());
            }
            double limit = plugin.getConfig().getDouble("crates.broadcast-chance-below", 5.0);
            if (crate.chance(winner) <= limit) {
                Bukkit.broadcast(Msg.c("<dark_gray>[<gold>Crate<dark_gray>] <white>" + Msg.esc(viewer.getName())
                        + " <gray>hat <yellow>" + winner.label() + " <gray>aus " + crate.name + " <gray>gezogen!"));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder(false) == this) viewer.closeInventory();
            }, 40L);
        }
    }
}
