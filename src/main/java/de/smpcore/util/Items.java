package de.smpcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class Items {
    private Items() {}

    public static ItemStack make(Material m, String name, String... lore) {
        return make(m, 1, name, Arrays.asList(lore));
    }

    public static ItemStack make(Material m, int amount, String name, List<String> lore) {
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(Msg.item(name));
            if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(Msg::item).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack filler() {
        return make(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack named(ItemStack base, String name) {
        ItemStack c = base.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta != null) { meta.displayName(Msg.item(name)); c.setItemMeta(meta); }
        return c;
    }

    public static ItemStack withLore(ItemStack base, List<String> extra) {
        ItemStack c = base.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta == null) return c;
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String s : extra) lore.add(Msg.item(s));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        c.setItemMeta(meta);
        return c;
    }

    public static ItemStack head(UUID id, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        m.displayName(Msg.item(name));
        m.lore(lore.stream().map(Msg::item).toList());
        it.setItemMeta(m);
        return it;
    }

    public static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    public static ItemStack decode(String s) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }
}
