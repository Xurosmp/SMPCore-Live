package de.smpcore.gui;

import de.smpcore.clan.ClanManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

public final class ClanMenus {
    private ClanMenus() {}

    private static String nameOf(UUID id) {
        PlayerData d = de.smpcore.SMPCore.get().data().get(id);
        return d == null ? "?" : d.name;
    }

    // ------------------------------------------------------------ Hauptmenü
    public static final class Main extends Menu {
        public Main(Player v) { super(v, 36, "<yellow><bold>Clan"); }

        @Override
        protected void build() {
            ClanManager cm = plugin.clans();
            UUID me = viewer.getUniqueId();
            ClanManager.Clan c = cm.of(me);
            if (c == null) {
                set(11, Items.make(Material.WHITE_BANNER, "<green>Clan erstellen", "<gray>/clan create <Name> <Tag>",
                        "<gray>Kosten: <green>" + EconomyManager.fmt(plugin.getConfig().getDouble("clans.create-cost", 50000))));
                set(13, Items.make(Material.GOLD_BLOCK, "<gold>Top Clans", "<gray>Bestenliste", "", "<yellow>Klicken"), e -> go(new Top(viewer)));
                set(15, Items.make(Material.PAPER, "<yellow>Einladungen <white>(" + cm.invitesFor(me).size() + ")", "<gray>Einladungen annehmen", "", "<yellow>Klicken"),
                        e -> go(new Invites(viewer)));
                fill(Items.filler());
                return;
            }
            ClanManager.Role role = cm.role(c, me);
            set(4, Items.make(Material.WHITE_BANNER, "<yellow>" + Msg.esc(c.name) + " <dark_gray>[<yellow>" + c.tag + "<dark_gray>]",
                    "<gray>Leader: <white>" + Msg.esc(nameOf(c.leader)),
                    "<gray>Mitglieder: <white>" + c.members.size() + "<dark_gray>/" + cm.maxMembers(),
                    "<gray>Kills: <red>" + c.kills + " <gray>Tode: <white>" + c.deaths,
                    "<gray>Bank: <green>" + EconomyManager.fmt(c.bank),
                    "<gray>Deine Rolle: <white>" + role.label));
            set(10, Items.make(Material.PLAYER_HEAD, "<aqua>Mitglieder", "<gray>Anzeigen & verwalten", "", "<yellow>Klicken"), e -> go(new Members(viewer, c)));
            set(11, Items.make(Material.GOLD_INGOT, "<gold>Clan-Bank", "<gray>Kontostand: <green>" + EconomyManager.fmt(c.bank), "", "<yellow>Klicken"), e -> go(new Bank(viewer, c)));
            set(12, Items.make(Material.RED_BED, "<green>Clan-Home", c.home == null ? "<red>Noch nicht gesetzt" : "<gray>Teleportieren", "",
                    "<yellow>Klicken: hin teleportieren", role.atLeast(ClanManager.Role.OFFICER) ? "<dark_gray>Setzen: /clan sethome" : ""),
                    e -> { closeLater(); cm.home(viewer); });
            set(13, Items.make(c.friendlyFire ? Material.RED_DYE : Material.LIME_DYE, "<white>Friendly Fire: " + (c.friendlyFire ? "<red>AN" : "<green>AUS"),
                    "<gray>Offiziere & Leader können umschalten", "", "<yellow>Klicken"), e -> { cm.toggleFriendlyFire(viewer); refresh(); });
            set(14, Items.make(Material.GOLD_BLOCK, "<gold>Top Clans", "<yellow>Klicken"), e -> go(new Top(viewer)));
            boolean chat = cm.isChatOn(me);
            set(15, Items.make(Material.WRITABLE_BOOK, "<white>Clan-Chat: " + (chat ? "<green>AN" : "<red>AUS"),
                    "<gray>AN = alles, was du schreibst, sieht nur dein Clan", "", "<yellow>Klicken zum Umschalten"),
                    e -> { cm.toggleChat(me); refresh(); });
            if (role.atLeast(ClanManager.Role.OFFICER)) {
                set(16, Items.make(Material.PAPER, "<green>Spieler einladen", "<gray>Online-Spieler ohne Clan", "", "<yellow>Klicken"), e -> go(new InvitePick(viewer)));
            }
            if (role == ClanManager.Role.LEADER) {
                set(22, Items.make(Material.TNT, "<dark_red>Clan auflösen"), e -> go(new ConfirmMenu(viewer, "<dark_red>Clan wirklich auflösen?",
                        Items.make(Material.TNT, "<red>" + Msg.esc(c.name) + " auflösen"), () -> cm.disband(viewer), null)));
            } else {
                set(22, Items.make(Material.BARRIER, "<red>Clan verlassen"), e -> go(new ConfirmMenu(viewer, "<red>Clan wirklich verlassen?",
                        Items.make(Material.BARRIER, "<red>" + Msg.esc(c.name) + " verlassen"), () -> cm.leave(viewer), null)));
            }
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Mitglieder
    public static final class Members extends PagedMenu {
        private final ClanManager.Clan clan;

        public Members(Player v, ClanManager.Clan clan) {
            super(v, "<yellow>Clan-Mitglieder");
            this.clan = clan;
        }

        @Override
        protected List<Entry> entries() {
            ClanManager cm = plugin.clans();
            ClanManager.Role mine = cm.role(clan, viewer.getUniqueId());
            List<Map.Entry<UUID, ClanManager.Role>> list = new ArrayList<>(clan.members.entrySet());
            list.sort((a, b) -> b.getValue().ordinal() - a.getValue().ordinal());
            List<Entry> out = new ArrayList<>();
            for (Map.Entry<UUID, ClanManager.Role> en : list) {
                UUID id = en.getKey();
                boolean manage = !id.equals(viewer.getUniqueId()) && cm.canManage(mine, en.getValue());
                out.add(new Entry(Items.head(id, "<white>" + Msg.esc(nameOf(id)), List.of(
                        "<gray>Rolle: <yellow>" + en.getValue().label,
                        "<gray>Online: " + (Bukkit.getPlayer(id) != null ? "<green>ja" : "<red>nein"),
                        "", manage ? "<yellow>Klicken zum Verwalten" : "")),
                        manage ? e -> go(new MemberAction(viewer, clan, id)) : null));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    public static final class MemberAction extends Menu {
        private final ClanManager.Clan clan;
        private final UUID target;

        public MemberAction(Player v, ClanManager.Clan clan, UUID target) {
            super(v, 27, "<yellow>" + Msg.esc(nameOf(target)));
            this.clan = clan;
            this.target = target;
        }

        @Override
        protected void build() {
            ClanManager cm = plugin.clans();
            ClanManager.Role mine = cm.role(clan, viewer.getUniqueId());
            ClanManager.Role theirs = cm.role(clan, target);
            set(13, Items.head(target, "<white>" + Msg.esc(nameOf(target)), List.of("<gray>Rolle: <yellow>" + theirs.label)));
            if (mine == ClanManager.Role.LEADER && theirs == ClanManager.Role.MEMBER)
                set(11, Items.make(Material.LIME_CONCRETE, "<green>Zum Offizier befördern"), e -> { cm.promote(viewer, target); go(new Members(viewer, clan)); });
            if (mine == ClanManager.Role.LEADER && theirs == ClanManager.Role.OFFICER)
                set(11, Items.make(Material.ORANGE_CONCRETE, "<gold>Zum Mitglied degradieren"), e -> { cm.demote(viewer, target); go(new Members(viewer, clan)); });
            if (mine == ClanManager.Role.LEADER)
                set(15, Items.make(Material.GOLDEN_HELMET, "<gold>Leader übertragen"), e -> go(new ConfirmMenu(viewer, "<gold>Leader wirklich übertragen?",
                        Items.make(Material.GOLDEN_HELMET, "<gold>An " + Msg.esc(nameOf(target))), () -> cm.transfer(viewer, target), () -> new Main(viewer))));
            set(22, Items.make(Material.RED_CONCRETE, "<red><bold>Aus dem Clan werfen"), e -> go(new ConfirmMenu(viewer, "<red>Wirklich kicken?",
                    Items.make(Material.RED_CONCRETE, "<red>" + Msg.esc(nameOf(target)) + " kicken"), () -> cm.kick(viewer, target), () -> new Members(viewer, clan))));
            set(18, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Members(viewer, clan)));
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Bank
    public static final class Bank extends Menu {
        private final ClanManager.Clan clan;

        public Bank(Player v, ClanManager.Clan clan) {
            super(v, 27, "<gold>Clan-Bank");
            this.clan = clan;
        }

        @Override
        protected void build() {
            ClanManager cm = plugin.clans();
            set(13, Items.make(Material.GOLD_BLOCK, "<gold>Clan-Bank", "<gray>Kontostand: <green>" + EconomyManager.fmt(clan.bank),
                    "<gray>Dein Geld: <white>" + EconomyManager.fmt(plugin.economy().balance(viewer.getUniqueId()))));
            double[] amts = {1_000, 10_000, 100_000};
            String[] lbl = {"1K", "10K", "100K"};
            for (int i = 0; i < 3; i++) {
                final double a = amts[i];
                set(10 + i, Items.make(Material.LIME_STAINED_GLASS_PANE, "<green>Einzahlen $" + lbl[i]), e -> { cm.deposit(viewer, a); refresh(); });
                set(14 + i, Items.make(Material.RED_STAINED_GLASS_PANE, "<red>Abheben $" + lbl[i], "<gray>Nur Offiziere & Leader"), e -> { cm.withdraw(viewer, a); refresh(); });
            }
            set(18, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer)));
            fill(Items.filler());
        }
    }

    // ------------------------------------------------------------ Top
    public static final class Top extends PagedMenu {
        public Top(Player v) { super(v, "<gold>Top Clans"); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            int rank = 1;
            for (ClanManager.Clan c : plugin.clans().top()) {
                out.add(new Entry(Items.make(Material.WHITE_BANNER, 1, "<gold>#" + rank++ + " <yellow>" + Msg.esc(c.name) + " <dark_gray>[<yellow>" + c.tag + "<dark_gray>]",
                        List.of("<gray>Leader: <white>" + Msg.esc(nameOf(c.leader)), "<gray>Mitglieder: <white>" + c.members.size(),
                                "<gray>Kills: <red>" + c.kills + " <gray>Tode: <white>" + c.deaths, "<gray>Bank: <green>" + EconomyManager.fmt(c.bank))), null));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    // ------------------------------------------------------------ Einladen / Einladungen
    public static final class InvitePick extends PagedMenu {
        public InvitePick(Player v) { super(v, "<green>Spieler einladen"); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(viewer) || plugin.clans().of(p.getUniqueId()) != null) continue;
                out.add(new Entry(Items.head(p.getUniqueId(), "<white>" + Msg.esc(p.getName()), List.of("", "<yellow>Klicken zum Einladen")),
                        e -> { plugin.clans().invite(viewer, p); refresh(); }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }

    public static final class Invites extends PagedMenu {
        public Invites(Player v) { super(v, "<yellow>Einladungen"); }

        @Override
        protected List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            for (ClanManager.Clan c : plugin.clans().invitesFor(viewer.getUniqueId())) {
                out.add(new Entry(Items.make(Material.WHITE_BANNER, "<yellow>" + Msg.esc(c.name) + " <dark_gray>[<yellow>" + c.tag + "<dark_gray>]",
                        "<gray>Mitglieder: <white>" + c.members.size(), "", "<yellow>Klicken zum Beitreten"),
                        e -> { plugin.clans().accept(viewer, c.name); go(new Main(viewer)); }));
            }
            return out;
        }

        @Override
        protected void controls() { set(49, Items.make(Material.ARROW, "<red>Zurück"), e -> go(new Main(viewer))); }
    }
}
