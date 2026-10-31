package de.smpcore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class Persist {
    private static final Object LOCK = new Object();
    private Persist() {}

    public static void write(Plugin pl, File f, String content, boolean async) {
        Runnable r = () -> {
            synchronized (LOCK) {
                try {
                    File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
                    Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    pl.getLogger().severe("Konnte " + f.getName() + " nicht speichern: " + e.getMessage());
                }
            }
        };
        if (async && pl.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(pl, r);
        else r.run();
    }
}
