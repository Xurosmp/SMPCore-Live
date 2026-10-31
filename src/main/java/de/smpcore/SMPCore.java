package de.smpcore;

import de.smpcore.afk.AfkManager;
import de.smpcore.announcer.Announcer;
import de.smpcore.auction.AuctionManager;
import de.smpcore.bounty.BountyManager;
import de.smpcore.clan.ClanManager;
import de.smpcore.commands.*;
import de.smpcore.crate.CrateManager;
import de.smpcore.data.DataManager;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.gui.MenuListener;
import de.smpcore.gui.SignInput;
import de.smpcore.kit.KitManager;
import de.smpcore.warp.WarpManager;
import de.smpcore.keyall.KeyallManager;
import de.smpcore.order.OrderManager;
import de.smpcore.pack.PackManager;
import de.smpcore.listeners.*;
import de.smpcore.rank.RankManager;
import de.smpcore.scoreboard.ScoreboardManager;
import de.smpcore.shop.CrystalShopManager;
import de.smpcore.shop.ShopManager;
import de.smpcore.spawner.SpawnerManager;
import de.smpcore.tab.TabManager;
import de.smpcore.teleport.TeleportManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class SMPCore extends JavaPlugin {
    private static SMPCore instance;
    private DataManager data;
    private EconomyManager economy;
    private ShopManager shop;
    private CrystalShopManager crystalShop;
    private CrystalShopManager gemShop;
    private ClanManager clans;
    private AfkManager afk;
    private TabManager tab;
    private OrderManager orders;
    private WarpManager warps;
    private KitManager kits;
    private Announcer announcer;
    private PackManager packs;
    private final java.util.Set<java.util.UUID> vanished = new java.util.HashSet<>();
    private AuctionManager auctions;
    private BountyManager bounties;
    private CrateManager crates;
    private ScoreboardManager scoreboards;
    private TeleportManager teleports;
    private RankManager ranks;
    private KeyallManager keyall;
    private SpawnerManager spawners;
    private ToolManager tools;

    public static SMPCore get() { return instance; }
    public DataManager data() { return data; }
    public EconomyManager economy() { return economy; }
    public ShopManager shop() { return shop; }
    public CrystalShopManager crystalShop() { return crystalShop; }
    public CrystalShopManager gemShop() { return gemShop; }
    public ClanManager clans() { return clans; }
    public AfkManager afk() { return afk; }
    public TabManager tab() { return tab; }
    public OrderManager orders() { return orders; }
    public WarpManager warps() { return warps; }
    public PackManager packs() { return packs; }
    public KitManager kits() { return kits; }
    public java.util.Set<java.util.UUID> vanished() { return vanished; }
    public AuctionManager auctions() { return auctions; }
    public BountyManager bounties() { return bounties; }
    public CrateManager crates() { return crates; }
    public ScoreboardManager scoreboards() { return scoreboards; }
    public TeleportManager teleports() { return teleports; }
    public RankManager ranks() { return ranks; }
    public KeyallManager keyall() { return keyall; }
    public SpawnerManager spawners() { return spawners; }
    public ToolManager tools() { return tools; }

    public boolean feature(String name) { return getConfig().getBoolean("features." + name, true); }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        for (String f : new String[]{"shop.yml", "crates.yml", "crystalshop.yml", "ranks.yml", "spawners.yml", "tools.yml", "gemshop.yml", "kits.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        Msg.configure(getConfig());
        Num.setComma(getConfig().getBoolean("number-comma", true));

        data = new DataManager(this);
        data.load();
        economy = new EconomyManager(data);
        shop = new ShopManager(this);
        shop.load();
        crystalShop = new CrystalShopManager(this, "crystalshop.yml", false);
        crystalShop.load();
        gemShop = new CrystalShopManager(this, "gemshop.yml", true);
        gemShop.load();
        clans = new ClanManager(this);
        clans.load();
        orders = new OrderManager(this);
        orders.load();
        orders.expire();
        warps = new WarpManager(this);
        warps.load();
        kits = new KitManager(this);
        kits.load();
        announcer = new Announcer(this);
        packs = new PackManager(this);
        afk = new AfkManager(this);
        tab = new TabManager(this);
        auctions = new AuctionManager(this);
        auctions.load();
        bounties = new BountyManager(this);
        bounties.load();
        crates = new CrateManager(this);
        crates.load();
        ranks = new RankManager(this);
        ranks.load();
        spawners = new SpawnerManager(this);
        spawners.loadConfig();
        spawners.loadData();
        tools = new ToolManager(this);
        tools.load();
        keyall = new KeyallManager(this);
        scoreboards = new ScoreboardManager(this);
        teleports = new TeleportManager(this);

        EconomyCommands eco = new EconomyCommands(this);
        for (String n : new String[]{"balance", "pay", "eco", "crystals", "gems", "top"}) reg(n, eco);
        ShopCommands sc = new ShopCommands(this);
        for (String n : new String[]{"shop", "sell", "crystalshop", "gemshop"}) reg(n, sc);
        reg("ah", new AuctionCommand(this));
        reg("bounty", new BountyCommand(this));
        reg("crate", new CrateCommand(this));
        AdminCommand ac = new AdminCommand(this);
        for (String n : new String[]{"adminpanel", "smpcore", "keyall"}) reg(n, ac);
        reg("rank", new RankCommand(this));
        reg("clan", new ClanCommand(this));
        reg("order", new OrderCommand(this));
        reg("dimension", new DimensionCommand(this));
        UtilityCommands uc = new UtilityCommands(this);
        for (String n : new String[]{"back", "warp", "setwarp", "delwarp", "kit", "msg", "r", "heal", "feed", "fly", "craft", "ec", "trash", "vanish", "sign"}) reg(n, uc);
        reg("afk", new AfkCommand(this));
        SpawnerCommand spc = new SpawnerCommand(this);
        reg("spawner", spc);
        reg("tool", spc);
        TeleportCommands tc = new TeleportCommands(this);
        for (String n : new String[]{"spawn", "setspawn", "sethome", "home", "delhome", "homes", "tpa", "tpaccept", "tpdeny", "rtp"}) reg(n, tc);
        MiscCommands mc = new MiscCommands(this);
        for (String n : new String[]{"daily", "stats", "scoreboard"}) reg(n, mc);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(new ClanListener(this), this);
        getServer().getPluginManager().registerEvents(new SignInput(), this);
        getServer().getPluginManager().registerEvents(new de.smpcore.gui.SellMenu(), this);
        getServer().getPluginManager().registerEvents(new PackListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionListener(this), this);

        scoreboards.start();
        crates.startParticles();
        keyall.start();
        afk.start();
        tab.start();
        announcer.start();
        long ticks = 20L * spawners.intervalSeconds();
        getServer().getScheduler().runTaskTimer(this, () -> spawners.tick(), ticks, ticks);
        getServer().getScheduler().runTaskTimer(this, () -> saveAll(true), 20L * 300, 20L * 300);
        getServer().getScheduler().runTaskTimer(this, () -> orders.expire(), 20L * 60, 20L * 60 * 5);
        getServer().getScheduler().runTaskTimer(this, () -> playtimeTick(), 20L * 60, 20L * 60);
        for (Player p : getServer().getOnlinePlayers()) {
            data.getOrCreate(p.getUniqueId(), p.getName());
            ranks.apply(p);
        }
        getLogger().info("SMPCore aktiviert – " + data.all().size() + " Spieler, " + crates.all().size() + " Crates, "
                + ranks.all().size() + " Ränge geladen.");
    }

    @Override
    public void onDisable() {
        SignInput.cleanup();
        if (scoreboards != null) scoreboards.shutdown();
        if (ranks != null) for (Player p : getServer().getOnlinePlayers()) ranks.remove(p);
        if (data != null) saveAll(false);
    }

    /** Registriert einen Befehl und setzt vor jedem Aufruf den passenden Chat-Prefix (AH ▸, HOME ▸ ...). */
    private void reg(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        TabExecutor wrapped = new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
                Msg.use(cmd.getName());
                try { return ex.onCommand(s, cmd, label, a); } finally { Msg.reset(); }
            }

            @Override
            public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
                return ex.onTabComplete(s, cmd, label, a);
            }
        };
        c.setExecutor(wrapped);
        c.setTabCompleter(wrapped);
    }

    public void saveAll(boolean async) {
        data.save(async);
        auctions.save(async);
        bounties.save(async);
        spawners.save(async);
        clans.save(async);
        orders.save(async);
    }

    public void reloadAll() {
        reloadConfig();
        Msg.configure(getConfig());
        Num.setComma(getConfig().getBoolean("number-comma", true));
        shop.load();
        crystalShop.load();
        gemShop.load();
        crates.load();
        ranks.load();
        spawners.loadConfig();
        tools.load();
        kits.load();
        warps.load();
        keyall.reset();
    }

    private void playtimeTick() {
        int every = getConfig().getInt("crystals.playtime-minutes", 30);
        long amount = getConfig().getLong("crystals.playtime-amount", 1);
        for (Player p : getServer().getOnlinePlayers()) {
            PlayerData d = data.get(p.getUniqueId());
            if (d == null) continue;
            d.playtime += 60;
            if (every > 0 && amount > 0 && (d.playtime / 60) % every == 0) {
                economy.addCrystals(p.getUniqueId(), amount);
                Msg.use("crystals");
                try { Msg.send(p, "<yellow>+" + amount + " Stars <gray>für deine Spielzeit!"); } finally { Msg.reset(); }
            }
        }
    }
}
