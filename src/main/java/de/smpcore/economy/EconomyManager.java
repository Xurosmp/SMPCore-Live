package de.smpcore.economy;

import de.smpcore.data.DataManager;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Num;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Eigenes Economy-System (Geld + Stars). Zugriff über SMPCore.get().economy() */
public final class EconomyManager {
    private final DataManager data;

    public EconomyManager(DataManager data) { this.data = data; }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    public double balance(UUID id) {
        PlayerData d = data.get(id);
        return d == null ? 0 : d.money;
    }
    public boolean has(UUID id, double a) { return balance(id) + 1e-9 >= a; }
    public void set(UUID id, double a) {
        PlayerData d = data.get(id);
        if (d != null) d.money = round(Math.max(0, a));
    }
    public void add(UUID id, double a) { set(id, balance(id) + a); }
    public boolean take(UUID id, double a) {
        if (!has(id, a)) return false;
        set(id, balance(id) - a);
        return true;
    }

    public long crystals(UUID id) {
        PlayerData d = data.get(id);
        return d == null ? 0 : d.crystals;
    }
    public void setCrystals(UUID id, long a) {
        PlayerData d = data.get(id);
        if (d != null) d.crystals = Math.max(0, a);
    }
    public void addCrystals(UUID id, long a) { setCrystals(id, crystals(id) + a); }
    public boolean takeCrystals(UUID id, long a) {
        if (crystals(id) < a) return false;
        setCrystals(id, crystals(id) - a);
        return true;
    }

    // ---- Gems (Store-Währung) ----
    public long gems(UUID id) {
        PlayerData d = data.get(id);
        return d == null ? 0 : d.gems;
    }
    public void setGems(UUID id, long a) {
        PlayerData d = data.get(id);
        if (d != null) d.gems = Math.max(0, a);
    }
    public void addGems(UUID id, long a) { setGems(id, gems(id) + a); }
    public boolean takeGems(UUID id, long a) {
        if (gems(id) < a) return false;
        setGems(id, gems(id) - a);
        return true;
    }

    public List<PlayerData> top(String type, int limit) {
        Comparator<PlayerData> cmp = switch (type.toLowerCase(Locale.ROOT)) {
            case "crystals" -> Comparator.comparingLong((PlayerData d) -> d.crystals);
            case "gems" -> Comparator.comparingLong((PlayerData d) -> d.gems);
            case "kills" -> Comparator.comparingInt((PlayerData d) -> d.kills);
            case "playtime" -> Comparator.comparingLong((PlayerData d) -> d.playtime);
            default -> Comparator.comparingDouble((PlayerData d) -> d.money);
        };
        return data.all().stream().sorted(cmp.reversed()).limit(limit).toList();
    }

    public static String fmt(double v) { return "$" + Num.format(v); }
    public static String fmtC(long v) { return Num.format(v) + " Stars"; }
    public static String fmtG(long v) { return Num.format(v) + " Gems"; }
}
