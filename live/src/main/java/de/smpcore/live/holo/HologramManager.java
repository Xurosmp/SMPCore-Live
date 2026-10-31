package de.smpcore.live.holo;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.live.SMPCoreLive;
import de.smpcore.live.util.Msg;
import de.smpcore.util.Num;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Hologramm-Leaderboards aus den SMPCore-Spielerdaten.
 * Die Entities sind persistent und tragen ein PDC-Tag mit der Hologramm-ID. Gefunden werden sie über den Chunk,
 * doppelte/alte Entities werden dabei automatisch entfernt.
 */
public final class HologramManager implements Listener {
    public static final List<String> TYPES = List.of("money", "crystals", "gems", "kills", "deaths", "kd", "playtime");

    public static final class Holo {
        public final String id;
        public String type;
        public String world;
        public double x, y, z;
        public String title; // null = Standard-Titel aus der config.yml
        public int limit;    // 0 = default-limit

        Holo(String id, String type) {
            this.id = id;
            this.type = type;
        }

        int chunkX() { return (int) Math.floor(x) >> 4; }
        int chunkZ() { return (int) Math.floor(z) >> 4; }
    }

    private record Row(String name, String value) {}

    private final SMPCoreLive plugin;
    private final Map<String, Holo> holos = new LinkedHashMap<>();
    private final NamespacedKey keyId;
    private final NamespacedKey keyLine;
    private File file;
    private BukkitTask task;

    public HologramManager(SMPCoreLive plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "holo_id");
        this.keyLine = new NamespacedKey(plugin, "holo_line");
    }

    // ---------------------------------------------------------------- Laden / Speichern

    public void load() {
        holos.clear();
        file = new File(plugin.getDataFolder(), "holograms.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("holograms");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            Holo h = new Holo(id, c.getString("type", "money"));
            h.world = c.getString("world", "world");
            h.x = c.getDouble("x");
            h.y = c.getDouble("y");
            h.z = c.getDouble("z");
            h.title = c.getString("title");
            h.limit = c.getInt("limit", 0);
            holos.put(id, h);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Holo h : holos.values()) {
            String b = "holograms." + h.id + ".";
            y.set(b + "type", h.type);
            y.set(b + "world", h.world);
            y.set(b + "x", h.x);
            y.set(b + "y", h.y);
            y.set(b + "z", h.z);
            y.set(b + "title", h.title);
            y.set(b + "limit", h.limit);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("holograms.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public void start() {
        long every = Math.max(5L, plugin.getConfig().getLong("holograms.refresh-seconds", 60)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, every);
    }

    public void reload() {
        if (task != null) task.cancel();
        load();
        start();
        refreshAll();
    }

    // ---------------------------------------------------------------- API

    public Holo get(String id) { return holos.get(id.toLowerCase(Locale.ROOT)); }

    public List<Holo> all() { return new ArrayList<>(holos.values()); }

    public Holo create(String id, String type, Location loc, int limit) {
        String key = id.toLowerCase(Locale.ROOT);
        if (holos.containsKey(key)) return null;
        Holo h = new Holo(key, type);
        h.limit = limit;
        place(h, loc);
        holos.put(key, h);
        save();
        update(h);
        return h;
    }

    private void place(Holo h, Location loc) {
        h.world = loc.getWorld().getName();
        h.x = loc.getX();
        h.y = loc.getY();
        h.z = loc.getZ();
    }

    public boolean remove(String id) {
        Holo h = holos.remove(id.toLowerCase(Locale.ROOT));
        if (h == null) return false;
        save();
        removeEntities(h.id, h.world, h.chunkX(), h.chunkZ(), 0);
        return true;
    }

    public void move(Holo h, Location loc) {
        removeEntities(h.id, h.world, h.chunkX(), h.chunkZ(), 0);
        place(h, loc);
        save();
        update(h);
    }

    public void changed(Holo h) {
        save();
        update(h);
    }

    public void refreshAll() {
        for (Holo h : new ArrayList<>(holos.values())) update(h);
    }

    // ---------------------------------------------------------------- Entities

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        String wn = e.getWorld().getName();
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        for (Holo h : holos.values()) {
            if (h.world.equals(wn) && h.chunkX() == cx && h.chunkZ() == cz) {
                Bukkit.getScheduler().runTask(plugin, () -> update(h));
            }
        }
    }

    private List<Entity> findEntities(Chunk chunk, String id) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : chunk.getEntities()) {
            if (!(e instanceof TextDisplay) && !(e instanceof ArmorStand)) continue;
            String tag = e.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
            if (id.equals(tag)) out.add(e);
        }
        return out;
    }

    private void removeEntities(String id, String world, int cx, int cz, int attempt) {
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        if (w.isChunkLoaded(cx, cz)) {
            Chunk chunk = w.getChunkAt(cx, cz);
            if (chunk.isEntitiesLoaded()) {
                for (Entity e : findEntities(chunk, id)) e.remove();
                return;
            }
        } else if (attempt == 0) {
            w.getChunkAtAsync(cx, cz);
        }
        if (attempt < 10) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeEntities(id, world, cx, cz, attempt + 1), 20L);
        }
    }

    /** Erzeugt/aktualisiert die Entities eines Hologramms (nur wenn der Chunk samt Entities geladen ist). */
    public void update(Holo h) {
        World w = Bukkit.getWorld(h.world);
        if (w == null) return;
        int cx = h.chunkX();
        int cz = h.chunkZ();
        if (!w.isChunkLoaded(cx, cz)) return;
        Chunk chunk = w.getChunkAt(cx, cz);
        if (!chunk.isEntitiesLoaded()) return;

        List<Component> lines = buildLines(h);
        List<Entity> found = findEntities(chunk, h.id);
        boolean displayMode = !plugin.getConfig().getString("holograms.mode", "display").equalsIgnoreCase("armorstand");
        if (displayMode) updateDisplay(w, h, lines, found);
        else updateArmorStands(w, h, lines, found);
    }

    private void updateDisplay(World w, Holo h, List<Component> lines, List<Entity> found) {
        Component text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
        TextDisplay td = null;
        for (Entity e : found) {
            if (td == null && e instanceof TextDisplay t) td = t;
            else e.remove();
        }
        Location loc = new Location(w, h.x, h.y, h.z);
        if (td == null) {
            FileConfiguration c = plugin.getConfig();
            int alpha = Math.max(0, Math.min(255, c.getInt("holograms.background-alpha", 110)));
            float range = (float) (c.getDouble("holograms.view-range", 48) / 64.0);
            final Component fText = text;
            td = w.spawn(loc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(alpha, 0, 0, 0));
                d.setLineWidth(400);
                d.setViewRange(range);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setInvulnerable(true);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, h.id);
                d.text(fText);
            });
        } else {
            td.text(text);
            if (td.getLocation().distanceSquared(loc) > 0.0004) td.teleport(loc);
        }
    }

    private void updateArmorStands(World w, Holo h, List<Component> lines, List<Entity> found) {
        int n = lines.size();
        double spacing = plugin.getConfig().getDouble("holograms.line-spacing", 0.27);
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity e : found) {
            if (e instanceof ArmorStand a) stands.add(a);
            else e.remove();
        }
        if (stands.size() != n) {
            for (ArmorStand a : stands) a.remove();
            stands.clear();
            for (int i = 0; i < n; i++) {
                Location loc = lineLocation(w, h, i, n, spacing);
                final int index = i;
                final Component name = lines.get(i);
                w.spawn(loc, ArmorStand.class, a -> {
                    a.setInvisible(true);
                    a.setMarker(true);
                    a.setGravity(false);
                    a.setBasePlate(false);
                    a.setArms(false);
                    a.setSilent(true);
                    a.setInvulnerable(true);
                    a.setCollidable(false);
                    a.setCanPickupItems(false);
                    a.setPersistent(true);
                    a.setCustomNameVisible(true);
                    a.customName(name);
                    a.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, h.id);
                    a.getPersistentDataContainer().set(keyLine, PersistentDataType.INTEGER, index);
                });
            }
            return;
        }
        stands.sort(Comparator.comparingInt(a -> a.getPersistentDataContainer().getOrDefault(keyLine, PersistentDataType.INTEGER, 0)));
        for (int i = 0; i < n; i++) {
            ArmorStand a = stands.get(i);
            a.customName(lines.get(i));
            Location want = lineLocation(w, h, i, n, spacing);
            if (a.getLocation().distanceSquared(want) > 0.0004) a.teleport(want);
        }
    }

    private Location lineLocation(World w, Holo h, int i, int n, double spacing) {
        double top = h.y + (n - 1) * spacing / 2.0;
        return new Location(w, h.x, top - i * spacing, h.z);
    }

    // ---------------------------------------------------------------- Inhalt

    public String defaultTitle(String type) {
        return plugin.getConfig().getString("holograms.titles." + type, "<gold><bold>TOP " + type.toUpperCase(Locale.ROOT));
    }

    private List<Component> buildLines(Holo h) {
        FileConfiguration c = plugin.getConfig();
        int limit = h.limit > 0 ? h.limit : c.getInt("holograms.default-limit", 10);
        limit = Math.max(1, Math.min(20, limit));
        List<Row> rows = top(h.type, limit);

        List<Component> out = new ArrayList<>();
        out.add(Msg.c(h.title != null && !h.title.isBlank() ? h.title : defaultTitle(h.type)));
        String sep = c.getString("holograms.separator", "");
        if (!sep.isEmpty()) out.add(Msg.c(sep));
        List<String> colors = c.getStringList("holograms.pos-colors");
        String defColor = c.getString("holograms.default-pos-color", "<gray>");
        String rowFmt = c.getString("holograms.row-format", "%color%#%pos% <white>%name% <dark_gray>» <yellow>%value%");
        String emptyFmt = c.getString("holograms.empty-row", "<dark_gray>#%pos% ---");
        for (int i = 0; i < limit; i++) {
            String pos = String.valueOf(i + 1);
            if (i < rows.size()) {
                Row r = rows.get(i);
                out.add(Msg.c(rowFmt
                        .replace("%color%", i < colors.size() ? colors.get(i) : defColor)
                        .replace("%pos%", pos)
                        .replace("%name%", Msg.esc(r.name()))
                        .replace("%value%", Msg.esc(r.value()))));
            } else {
                out.add(Msg.c(emptyFmt.replace("%pos%", pos)));
            }
        }
        if (!sep.isEmpty()) out.add(Msg.c(sep));
        String footer = c.getString("holograms.footer", "");
        if (!footer.isEmpty()) out.add(Msg.c(footer.replace("%seconds%", String.valueOf(c.getLong("holograms.refresh-seconds", 60)))));
        return out;
    }

    private List<Row> top(String type, int limit) {
        SMPCore core = SMPCore.get();
        FileConfiguration c = plugin.getConfig();
        ToDoubleFunction<PlayerData> score;
        Function<PlayerData, String> fmt;
        int minKills = c.getInt("holograms.min-kills-for-kd", 10);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "crystals" -> { score = d -> d.crystals; fmt = d -> EconomyManager.fmtC(d.crystals); }
            case "gems" -> { score = d -> d.gems; fmt = d -> EconomyManager.fmtG(d.gems); }
            case "kills" -> { score = d -> d.kills; fmt = d -> String.valueOf(d.kills); }
            case "deaths" -> { score = d -> d.deaths; fmt = d -> String.valueOf(d.deaths); }
            case "playtime" -> { score = d -> d.playtime; fmt = d -> Num.duration(d.playtime * 1000L); }
            case "kd" -> {
                score = d -> d.kills < minKills ? -1 : (d.deaths == 0 ? d.kills : (double) d.kills / d.deaths);
                fmt = d -> String.format(Locale.GERMANY, "%.2f", d.deaths == 0 ? (double) d.kills : (double) d.kills / d.deaths);
            }
            default -> { score = d -> d.money; fmt = d -> EconomyManager.fmt(d.money); }
        }
        List<String> excluded = new ArrayList<>();
        for (String s : c.getStringList("holograms.excluded-ranks")) excluded.add(s.toLowerCase(Locale.ROOT));

        List<PlayerData> list = new ArrayList<>();
        for (PlayerData d : core.data().all()) {
            if (score.applyAsDouble(d) <= 0) continue;
            if (!excluded.isEmpty() && core.ranks().rankOf(d.uuid) != null
                    && excluded.contains(core.ranks().rankOf(d.uuid).id)) continue;
            list.add(d);
        }
        list.sort(Comparator.comparingDouble(score).reversed());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            PlayerData d = list.get(i);
            rows.add(new Row(d.name == null ? "?" : d.name, fmt.apply(d)));
        }
        return rows;
    }
}
