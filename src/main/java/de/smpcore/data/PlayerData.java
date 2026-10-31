package de.smpcore.data;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public final UUID uuid;
    public String name;
    public double money;
    public long crystals;   // AFK-Währung
    public long gems;       // Store-Währung
    public int kills, deaths;
    public long playtime; // Sekunden
    public long lastDaily;
    public int dailyStreak;
    public boolean scoreboardOff;
    public long signDay;    // Tag (epoch day) der letzten /sign-Nutzung
    public int signCount;   // Signaturen heute
    public String rank; // null = Standard-Rang
    public final Map<String, Long> kitUsed = new HashMap<>();
    public Location lastLocation; // für /back (nicht gespeichert)
    public final Map<String, Integer> keys = new HashMap<>();
    public final Map<String, String> homes = new LinkedHashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
