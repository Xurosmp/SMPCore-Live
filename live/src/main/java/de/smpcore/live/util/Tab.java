package de.smpcore.live.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class Tab {
    private Tab() {}

    public static List<String> filter(String prefix, Collection<String> options) {
        String l = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(l)).sorted().toList();
    }
}
