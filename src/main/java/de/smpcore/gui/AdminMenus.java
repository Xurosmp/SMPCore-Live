package de.smpcore.gui;

import de.smpcore.SMPCore;
import de.smpcore.crate.CrateManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.rank.RankManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class AdminMenus {
    private AdminMenus() {}

    private static boolean admin(Player p) { return p.hasPermission("smpcore.admin"); }

    private static void log(Player admin, String msg) {
        SMPCore.get().getLogger().info("[Admin] " + admin.getName() + ": " + msg);
    }

    // ------------------------------------------------------------ Hauptmenü
    public static final class Main extends Menu {
        public Main(Player v) { super(v, 36, "<red><bold>Admin Panel"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected void build() {
            set(10, Items.make(Material.PLAYER_HEAD, "<aqua>Spieler verwalten", "<gray>Geld, Stars, Keys, Rang, Teleport ..."), e -> go(new Players(viewer)));
            set(11, Items.make(Material.SKELETON_SKULL, "<red>Kopfgelder", "<gray>Anzeigen & entfernen"), e -> go(new Bounties(viewer)));
            set(12, Items.make(Material.CHEST, "<gold>Crate-Editor", "<gray>Crates erstellen & Rewards bearbeiten"), e -> go(new CrateList(viewer)));
            set(13, Items.make(Material.SPAWNER, "<green>Spawner geben", "<gray>Smart-Spawner an dich geben"), e -> go(new GiveSpawner(viewer)));
            set(14, Items.make(Material.NETHERITE_PICKAXE, "<yellow>Tools geben", "<gray>Hammer, Excavator ..."), e -> go(new GiveTool(viewer)));
            set(15, Items.make(Material.TRIPWIRE_HOOK, "<gold>Keyall", "<gray>Intervall, Crate, jetzt auslösen"), e -> go(new KeyallMenu(viewer)));
            set(16, Items.make(Material.COMMAND_BLOCK, "<light_purple>Server-Tools", "<gray>Features, Wartung, Reload"), e -> go(new Tools(viewer)));
            set(19, Items.make(Material.WHITE_BANNER, "<yellow>Clans", "<gray>Alle Clans anzeigen & auflösen"), e -> go(new Clans(viewer)));
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Spieler
    public static final class Players extends PagedMenu {
        public Players(Player v) { super(v, "<red>Admin » Spieler"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = plugin.data().get(p.getUniqueId());
                if (d == null) continue;
                out.add(new Entry(Items.head(p.getUniqueId(), "<aqua>" + Msg.esc(p.getName()), List.of(
                        "<gray>Geld: <green>" + EconomyManager.fmt(d.money),
                        "<gray>Stars: <light_purple>" + d.crystals,
                        "<gray>Rang: " + plugin.ranks().rankOf(p.getUniqueId()).display,
                        "", "<yellow>Klicken zum Verwalten")), e -> go(new Target(viewer, p.getUniqueId()))));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer)));
            set(51, Items.make(Material.PAPER, "<gray>Offline-Spieler", "<white>/ap <Name>"));
        }
    }

    public static final class Target extends Menu {
        private final UUID target;

        private static String title(UUID id) {
            PlayerData d = SMPCore.get().data().get(id);
            return "<red>Admin » <white>" + Msg.esc(d == null ? "?" : d.name);
        }

        public Target(Player v, UUID target) {
            super(v, 54, title(target));
            this.target = target;
        }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected void build() {
            PlayerData d = plugin.data().get(target);
            if (d == null) { closeLater(); return; }
            Player online = Bukkit.getPlayer(target);
            set(4, Items.head(target, "<aqua>" + Msg.esc(d.name), List.of(
                    "<gray>Geld: <green>" + EconomyManager.fmt(d.money),
                    "<gray>Gems: <light_purple>" + d.gems + " <gray>Stars: <yellow>" + d.crystals,
                    "<gray>Rang: " + plugin.ranks().rankOf(target).display,
                    "<gray>Kills/Tode: <white>" + d.kills + "/" + d.deaths,
                    "<gray>Kopfgeld: <red>" + EconomyManager.fmt(plugin.bounties().get(target)),
                    "<gray>Online: " + (online != null ? "<green>ja" : "<red>nein"))));

            double[] money = {1_000, 10_000, 100_000, 1_000_000};
            String[] ml = {"1K", "10K", "100K", "1M"};
            for (int i = 0; i < 4; i++) {
                final double a = money[i];
                final String l = ml[i];
                set(9 + i, Items.make(Material.LIME_STAINED_GLASS_PANE, "<green>+$" + l), e -> {
                    plugin.economy().add(target, a); log(viewer, "+" + a + " Geld für " + d.name); refresh();
                });
                set(14 + i, Items.make(Material.RED_STAINED_GLASS_PANE, "<red>-$" + l), e -> {
                    plugin.economy().set(target, plugin.economy().balance(target) - a); log(viewer, "-" + a + " Geld von " + d.name); refresh();
                });
            }
            long[] cr = {1, 10, 100};
            for (int i = 0; i < 3; i++) {
                final long a = cr[i];
                set(18 + i, Items.make(Material.AMETHYST_SHARD, "<light_purple>+" + a + " Stars"), e -> {
                    plugin.economy().addCrystals(target, a); log(viewer, "+" + a + " Stars für " + d.name); refresh();
                });
                set(24 + i, Items.make(Material.REDSTONE, "<red>-" + a + " Stars"), e -> {
                    plugin.economy().setCrystals(target, plugin.economy().crystals(target) - a); log(viewer, "-" + a + " Stars von " + d.name); refresh();
                });
            }
            long[] gm = {10, 100};
            for (int i = 0; i < 2; i++) {
                final long a = gm[i];
                set(21 + i, Items.make(Material.AMETHYST_CLUSTER, "<light_purple>+" + a + " Gems"), e -> {
                    plugin.economy().addGems(target, a); log(viewer, "+" + a + " Gems für " + d.name); refresh();
                });
            }
            set(23, Items.make(Material.BARRIER, "<red>-10 Gems"), e -> {
                plugin.economy().setGems(target, plugin.economy().gems(target) - 10); log(viewer, "-10 Gems von " + d.name); refresh();
            });
            int slot = 27;
            for (CrateManager.Crate c : plugin.crates().all()) {
                if (slot > 35) break;
                set(slot++, Items.make(Material.TRIPWIRE_HOOK, c.keyName,
                        "<gray>Keys: <white>" + plugin.crates().keys(target, c.id), "<yellow>Klick: +1 Key"), e -> {
                    plugin.crates().addKeys(target, c.id, 1); log(viewer, "+1 Key " + c.id + " für " + d.name); refresh();
                });
            }
            set(45, Items.make(Material.ENDER_PEARL, "<aqua>Zu Spieler teleportieren"), e -> {
                Player t = Bukkit.getPlayer(target);
                if (t == null) { Msg.error(viewer, "Spieler ist offline."); return; }
                closeLater(); viewer.teleportAsync(t.getLocation());
            });
            set(46, Items.make(Material.ENDER_EYE, "<aqua>Spieler zu mir holen"), e -> {
                Player t = Bukkit.getPlayer(target);
                if (t == null) { Msg.error(viewer, "Spieler ist offline."); return; }
                t.teleportAsync(viewer.getLocation()); log(viewer, "holte " + d.name);
            });
            set(47, Items.make(Material.GOLDEN_APPLE, "<green>Heilen & Sättigen"), e -> {
                Player t = Bukkit.getPlayer(target);
                if (t == null) { Msg.error(viewer, "Spieler ist offline."); return; }
                healFeed(t); log(viewer, "heilte " + d.name);
            });
            set(48, Items.make(Material.SKELETON_SKULL, "<red>Kopfgeld löschen", "<gray>Aktuell: " + EconomyManager.fmt(plugin.bounties().get(target))), e -> {
                plugin.bounties().remove(target); log(viewer, "löschte Kopfgeld von " + d.name); refresh();
            });
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Players(viewer)));
            set(51, Items.make(Material.NAME_TAG, "<gold>Rang ändern", "<gray>Aktuell: " + plugin.ranks().rankOf(target).display), e -> go(new RankPick(viewer, target)));
            set(53, Items.make(Material.TNT, "<dark_red>Kicken"), e -> {
                Player t = Bukkit.getPlayer(target);
                if (t == null) { Msg.error(viewer, "Spieler ist offline."); return; }
                go(new ConfirmMenu(viewer, "<dark_red>Wirklich kicken?", Items.make(Material.TNT, "<red>" + Msg.esc(d.name) + " kicken"),
                        () -> { t.kick(Msg.c("<red>Vom Admin gekickt.")); log(viewer, "kickte " + d.name); },
                        () -> new Players(viewer)));
            });
            fill(Items.filler());
        }

        @SuppressWarnings("deprecation")
        private static void healFeed(Player t) {
            t.setHealth(t.getMaxHealth());
            t.setFoodLevel(20);
            t.setSaturation(20f);
        }
    }

    public static final class RankPick extends PagedMenu {
        private final UUID target;

        public RankPick(Player v, UUID target) {
            super(v, "<red>Admin » Rang wählen");
            this.target = target;
        }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (RankManager.Rank r : plugin.ranks().all()) {
                out.add(new Entry(Items.make(Material.NAME_TAG, r.display, "<gray>ID: <white>" + r.id,
                        "<gray>Prefix: " + r.prefix + "Name", "<gray>Homes: <white>" + (r.homes > 0 ? r.homes : "geerbt"),
                        "", "<yellow>Klicken zum Setzen"), e -> {
                    plugin.ranks().setRank(target, r.id);
                    log(viewer, "Rang von " + target + " -> " + r.id);
                    go(new Target(viewer, target));
                }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Target(viewer, target))); }
    }

    // ------------------------------------------------------------ Kopfgelder
    public static final class Bounties extends PagedMenu {
        public Bounties(Player v) { super(v, "<red>Admin » Kopfgelder"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (Map.Entry<UUID, Double> en : plugin.bounties().all().entrySet()) {
                PlayerData d = plugin.data().get(en.getKey());
                out.add(new Entry(Items.head(en.getKey(), "<red>" + Msg.esc(d == null ? "?" : d.name), List.of(
                        "<gray>Kopfgeld: <green>" + EconomyManager.fmt(en.getValue()), "", "<yellow>Klicken zum Entfernen")),
                        e -> { plugin.bounties().remove(en.getKey()); log(viewer, "entfernte Kopfgeld"); refresh(); }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    // ------------------------------------------------------------ Crate-Editor
    public static final class CrateList extends PagedMenu {
        public CrateList(Player v) { super(v, "<red>Admin » Crate-Editor"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (CrateManager.Crate c : plugin.crates().all()) {
                out.add(new Entry(Items.make(c.icon, c.name, "<gray>ID: <white>" + c.id, "<gray>Rewards: <white>" + c.rewards.size(),
                        "<gray>Modus: <white>" + c.mode, "", "<yellow>Klicken zum Bearbeiten"), e -> go(new CrateEdit(viewer, c))));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer)));
            set(51, Items.make(Material.EMERALD, "<green>Neue Crate erstellen", "<gray>Name/Icon danach per Befehl:",
                    "<white>/crate setname <id> <Name>", "<white>/crate seticon <id>"), e -> {
                int n = 1;
                while (plugin.crates().get("crate" + n) != null) n++;
                CrateManager.Crate c = plugin.crates().create("crate" + n);
                log(viewer, "erstellte Crate crate" + n);
                if (c != null) go(new CrateEdit(viewer, c)); else refresh();
            });
        }
    }

    public static final class CrateEdit extends PagedMenu {
        private final CrateManager.Crate crate;

        public CrateEdit(Player v, CrateManager.Crate crate) {
            super(v, "<red>Crate » " + crate.name);
            this.crate = crate;
        }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < crate.rewards.size(); i++) {
                final int idx = i;
                CrateManager.Reward r = crate.rewards.get(i);
                out.add(new Entry(Items.withLore(r.icon(), List.of("",
                        "<gray>Gewicht: <white>" + Num.format(r.weight()),
                        String.format(Locale.US, "<gray>Chance: <yellow>%.2f%%", crate.chance(r)),
                        "", "<yellow>Klicken zum Bearbeiten")), e -> go(new RewardEdit(viewer, crate, idx))));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(46, Items.make(Material.HOPPER, "<green>Item aus Hand hinzufügen", "<gray>Nimmt das Item in deiner Hand",
                    "<gray>(inkl. Name/Verzauberungen) als Reward"), e -> {
                ItemStack hand = viewer.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { Msg.error(viewer, "Du hältst kein Item in der Hand."); return; }
                crate.rewards.add(CrateManager.item(hand, 10));
                crate.recalc(); plugin.crates().save(); log(viewer, "Reward (Item) zu " + crate.id); refresh();
            });
            set(47, Items.make(Material.GOLD_INGOT, "<gold>+ Geld-Reward", "<gray>$10K (danach anpassbar)"), e -> {
                crate.rewards.add(CrateManager.money(10_000, 10));
                crate.recalc(); plugin.crates().save(); log(viewer, "Reward (Geld) zu " + crate.id); refresh();
            });
            set(48, Items.make(Material.AMETHYST_SHARD, "<light_purple>+ Stars-Reward", "<gray>5 Stars (danach anpassbar)"), e -> {
                crate.rewards.add(CrateManager.crystals(5, 10));
                crate.recalc(); plugin.crates().save(); log(viewer, "Reward (Stars) zu " + crate.id); refresh();
            });
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new CrateList(viewer)));
            set(50, Items.make(Material.COMPARATOR, "<aqua>Modus: <white>" + crate.mode,
                    "<gray>RANDOM = Zufall", "<gray>CHOOSE = Spieler wählt selbst", "", "<yellow>Klicken zum Umschalten"), e -> {
                crate.mode = crate.mode.equals("CHOOSE") ? "RANDOM" : "CHOOSE";
                plugin.crates().save(); log(viewer, "Modus von " + crate.id + " -> " + crate.mode); refresh();
            });
            set(51, Items.make(Material.TRIPWIRE_HOOK, crate.keyName, "<gray>Deine Keys: <white>" + plugin.crates().keys(viewer.getUniqueId(), crate.id),
                    "<yellow>Klick: +1 Key für dich"), e -> { plugin.crates().addKeys(viewer.getUniqueId(), crate.id, 1); refresh(); });
            set(52, Items.make(Material.TNT, "<dark_red>Crate löschen"), e ->
                    go(new ConfirmMenu(viewer, "<dark_red>Crate wirklich löschen?", Items.make(crate.icon, crate.name),
                            () -> { plugin.crates().delete(crate.id); log(viewer, "löschte Crate " + crate.id); },
                            () -> new CrateList(viewer))));
        }
    }

    public static final class RewardEdit extends Menu {
        private final CrateManager.Crate crate;
        private final int index;

        public RewardEdit(Player v, CrateManager.Crate crate, int index) {
            super(v, 27, "<red>Reward bearbeiten");
            this.crate = crate;
            this.index = index;
        }
        @Override protected boolean allowed() { return admin(viewer); }

        private void replace(CrateManager.Reward nr) {
            crate.rewards.set(index, nr);
            crate.recalc();
            plugin.crates().save();
            log(viewer, "Reward " + index + " in " + crate.id + " geändert");
            refresh();
        }

        @Override
        protected void build() {
            if (index >= crate.rewards.size()) { go(new CrateEdit(viewer, crate)); return; }
            CrateManager.Reward r = crate.rewards.get(index);
            set(13, Items.withLore(r.icon(), List.of("",
                    "<gray>Gewicht: <white>" + Num.format(r.weight()),
                    String.format(Locale.US, "<gray>Chance: <yellow>%.2f%%", crate.chance(r)))));
            double[] d = {-10, -1, 1, 10};
            int[] slots = {10, 11, 15, 16};
            for (int i = 0; i < 4; i++) {
                final double delta = d[i];
                set(slots[i], Items.make(delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                        (delta < 0 ? "<red>" : "<green>+") + (int) delta + " Gewicht"), e -> replace(r.withWeight(r.weight() + delta)));
            }
            if (r.type().equals("MONEY")) {
                set(19, Items.make(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Betrag halbieren"), e -> replace(CrateManager.money(Math.max(1, r.amount() / 2), r.weight())));
                set(25, Items.make(Material.YELLOW_STAINED_GLASS_PANE, "<gold>Betrag verdoppeln"), e -> replace(CrateManager.money(r.amount() * 2, r.weight())));
            } else if (r.type().equals("CRYSTALS")) {
                set(19, Items.make(Material.ORANGE_STAINED_GLASS_PANE, "<light_purple>Menge halbieren"), e -> replace(CrateManager.crystals(Math.max(1, (long) r.amount() / 2), r.weight())));
                set(25, Items.make(Material.YELLOW_STAINED_GLASS_PANE, "<light_purple>Menge verdoppeln"), e -> replace(CrateManager.crystals((long) r.amount() * 2, r.weight())));
            }
            set(22, Items.make(Material.RED_CONCRETE, "<red><bold>Reward löschen"), e -> {
                if (crate.rewards.size() <= 1) { Msg.error(viewer, "Eine Crate braucht mindestens einen Reward."); return; }
                crate.rewards.remove(index);
                crate.recalc();
                plugin.crates().save();
                log(viewer, "Reward gelöscht in " + crate.id);
                go(new CrateEdit(viewer, crate));
            });
            set(18, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new CrateEdit(viewer, crate)));
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Spawner / Tools geben
    public static final class GiveSpawner extends PagedMenu {
        private static final int[] AMOUNTS = {1, 8, 16, 64};
        private int ai = 0;

        public GiveSpawner(Player v) { super(v, "<red>Admin » Spawner geben"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (SpawnerManager.Mob m : plugin.spawners().mobs()) {
                ItemStack it = Items.withLore(plugin.spawners().createItem(m.type(), 1), List.of("", "<yellow>Klick: " + AMOUNTS[ai] + "x erhalten"));
                out.add(new Entry(it, e -> {
                    plugin.spawners().give(viewer, m.type(), AMOUNTS[ai]);
                    log(viewer, AMOUNTS[ai] + "x Spawner " + m.type());
                }));
            }
            return out;
        }

        @Override
        protected void controls() {
            set(46, Items.make(Material.HOPPER, "<aqua>Menge: <white>" + AMOUNTS[ai], "<gray>Klicken zum Wechseln"), e -> { ai = (ai + 1) % AMOUNTS.length; refresh(); });
            set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer)));
        }
    }

    public static final class GiveTool extends PagedMenu {
        public GiveTool(Player v) { super(v, "<red>Admin » Tools geben"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (ToolManager.Tool t : plugin.tools().all()) {
                ItemStack it = Items.withLore(plugin.tools().create(t, 1), List.of("", "<yellow>Klick: 1x erhalten"));
                out.add(new Entry(it, e -> {
                    viewer.getInventory().addItem(plugin.tools().create(t, 1)).values()
                            .forEach(i -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), i));
                    log(viewer, "Tool " + t.id());
                }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    // ------------------------------------------------------------ Clans
    public static final class Clans extends PagedMenu {
        public Clans(Player v) { super(v, "<red>Admin » Clans"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (de.smpcore.clan.ClanManager.Clan c : plugin.clans().all()) {
                out.add(new Entry(Items.make(Material.WHITE_BANNER, "<yellow>" + Msg.esc(c.name) + " <dark_gray>[<yellow>" + c.tag + "<dark_gray>]",
                        "<gray>Mitglieder: <white>" + c.members.size(), "<gray>Kills: <red>" + c.kills, "<gray>Bank: <green>" + EconomyManager.fmt(c.bank),
                        "", "<yellow>Klicken zum Auflösen"),
                        e -> go(new ConfirmMenu(viewer, "<dark_red>Clan auflösen?", Items.make(Material.TNT, "<red>" + Msg.esc(c.name)),
                                () -> { plugin.clans().disbandClan(c); log(viewer, "löste Clan " + c.name + " auf"); }, () -> new Clans(viewer)))));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    // ------------------------------------------------------------ Keyall
    public static final class KeyallMenu extends Menu {
        public KeyallMenu(Player v) { super(v, 27, "<red>Admin » Keyall"); }
        @Override protected boolean allowed() { return admin(viewer); }

        private void setInterval(long minutes) {
            plugin.getConfig().set("keyall.interval-minutes", Math.max(1, minutes));
            plugin.saveConfig();
            plugin.keyall().reset();
            refresh();
        }

        @Override
        protected void build() {
            long interval = plugin.getConfig().getLong("keyall.interval-minutes", 60);
            boolean on = plugin.keyall().enabled();
            String crate = plugin.getConfig().getString("keyall.crate", "common");
            set(13, Items.make(Material.CLOCK, "<gold>Nächster Keyall in <white>" + Num.mmss(plugin.keyall().secondsLeft()),
                    "<gray>Status: " + (on ? "<green>an" : "<red>aus"), "<gray>Intervall: <white>" + interval + " Min",
                    "<gray>Crate: <white>" + crate + " <gray>x" + plugin.getConfig().getInt("keyall.amount", 1)), e -> refresh());
            set(10, Items.make(Material.RED_STAINED_GLASS_PANE, "<red>-10 Minuten"), e -> setInterval(interval - 10));
            set(16, Items.make(Material.LIME_STAINED_GLASS_PANE, "<green>+10 Minuten"), e -> setInterval(interval + 10));
            set(12, Items.make(on ? Material.LIME_DYE : Material.GRAY_DYE, on ? "<green>Keyall AN" : "<red>Keyall AUS", "<gray>Klicken zum Umschalten"), e -> {
                plugin.getConfig().set("keyall.enabled", !on);
                plugin.saveConfig();
                refresh();
            });
            set(14, Items.make(Material.FIREWORK_ROCKET, "<yellow>Jetzt auslösen", "<gray>Gibt sofort allen Online-Spielern Keys"), e -> { plugin.keyall().run(); log(viewer, "Keyall ausgelöst"); refresh(); });
            set(22, Items.make(Material.CHEST, "<aqua>Crate: <white>" + crate, "<gray>Klicken zum Wechseln"), e -> {
                List<String> ids = plugin.crates().all().stream().map(c -> c.id).toList();
                if (ids.isEmpty()) return;
                int i = ids.indexOf(crate.toLowerCase(Locale.ROOT));
                plugin.getConfig().set("keyall.crate", ids.get((i + 1) % ids.size()));
                plugin.saveConfig();
                refresh();
            });
            set(18, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer)));
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Server-Tools
    public static final class Tools extends Menu {
        private static final String[][] FEATURES = {
                {"shop", "Shop"}, {"auction", "Auktionshaus"}, {"bounty", "Kopfgelder"}, {"crates", "Crates"}, {"scoreboard", "Scoreboard"}, {"orders", "Orders"}};

        public Tools(Player v) { super(v, 36, "<red>Admin » Server-Tools"); }
        @Override protected boolean allowed() { return admin(viewer); }

        @Override
        protected void build() {
            int slot = 10;
            for (String[] f : FEATURES) {
                boolean on = plugin.feature(f[0]);
                set(slot++, Items.make(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "<green>" : "<red>") + f[1] + (on ? " AN" : " AUS"),
                        "<gray>Klicken zum Umschalten"), e -> {
                    plugin.getConfig().set("features." + f[0], !plugin.feature(f[0]));
                    plugin.saveConfig();
                    log(viewer, "Feature " + f[0] + " umgeschaltet");
                    refresh();
                });
            }
            boolean maint = plugin.getConfig().getBoolean("maintenance", false);
            set(16, Items.make(maint ? Material.BARRIER : Material.OAK_DOOR, maint ? "<red>Wartungsmodus AN" : "<green>Wartungsmodus AUS",
                    "<gray>AN = nur Admins dürfen joinen", "<gray>Klicken zum Umschalten"), e -> {
                plugin.getConfig().set("maintenance", !maint);
                plugin.saveConfig();
                log(viewer, "Wartungsmodus " + !maint);
                refresh();
            });
            boolean nether = de.smpcore.listeners.DimensionListener.enabled(plugin, "nether");
            boolean end = de.smpcore.listeners.DimensionListener.enabled(plugin, "end");
            set(21, Items.make(nether ? Material.NETHERRACK : Material.BARRIER, nether ? "<green>Nether OFFEN" : "<red>Nether GESCHLOSSEN",
                    "<gray>Spieler im Nether werden zum Spawn gebracht", "<gray>Klicken zum Umschalten"), e -> {
                de.smpcore.listeners.DimensionListener.setEnabled(plugin, "nether", !nether);
                log(viewer, "Nether " + (!nether ? "geöffnet" : "geschlossen"));
                refresh();
            });
            set(22, Items.make(end ? Material.END_STONE : Material.BARRIER, end ? "<green>End OFFEN" : "<red>End GESCHLOSSEN",
                    "<gray>Spieler im End werden zum Spawn gebracht", "<gray>Klicken zum Umschalten"), e -> {
                de.smpcore.listeners.DimensionListener.setEnabled(plugin, "end", !end);
                log(viewer, "End " + (!end ? "geöffnet" : "geschlossen"));
                refresh();
            });
            double money = 0;
            long crystals = 0, gemsTotal = 0;
            for (PlayerData d : plugin.data().all()) { money += d.money; crystals += d.crystals; gemsTotal += d.gems; }
            set(20, Items.make(Material.GOLD_BLOCK, "<gold>Economy-Übersicht",
                    "<gray>Spieler: <white>" + plugin.data().all().size(),
                    "<gray>Gesamt-Geld: <green>" + EconomyManager.fmt(money),
                    "<gray>Gesamt-Gems: <light_purple>" + gemsTotal,
                    "<gray>Gesamt-Stars: <yellow>" + crystals,
                    "<gray>Offene Kopfgelder: <red>" + plugin.bounties().all().size()));
            set(24, Items.make(Material.REDSTONE, "<yellow>Konfiguration neu laden", "<gray>config, shop, crates, ränge, tools ..."), e -> {
                plugin.reloadAll();
                Msg.ok(viewer, "Neu geladen!");
            });
            boolean rtpN = plugin.teleports().rtpEnabled("nether");
            boolean rtpE = plugin.teleports().rtpEnabled("end");
            set(29, Items.make(rtpN ? Material.NETHERRACK : Material.BARRIER, rtpN ? "<green>RTP Nether FREIGESCHALTET" : "<red>RTP Nether GESPERRT",
                    "<gray>Klicken zum Umschalten"), e -> {
                plugin.teleports().setRtpEnabled("nether", !rtpN);
                log(viewer, "RTP Nether " + (!rtpN ? "freigeschaltet" : "gesperrt"));
                refresh();
            });
            set(30, Items.make(rtpE ? Material.END_STONE : Material.BARRIER, rtpE ? "<green>RTP End FREIGESCHALTET" : "<red>RTP End GESPERRT",
                    "<gray>Klicken zum Umschalten"), e -> {
                plugin.teleports().setRtpEnabled("end", !rtpE);
                log(viewer, "RTP End " + (!rtpE ? "freigeschaltet" : "gesperrt"));
                refresh();
            });
            set(25, Items.make(Material.WRITABLE_BOOK, "<aqua>Jetzt speichern"), e -> { plugin.saveAll(true); Msg.ok(viewer, "Gespeichert!"); });
            set(31, Items.make(Material.BARRIER, "<red>Zurück"), e -> go(new Main(viewer)));
            fill(Items.filler());
        }
    }
}
