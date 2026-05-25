package org.gw.cachesmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlaceholderAPIHook {

    private static boolean enabled = false;

    private PlaceholderAPIHook() {}

    public static void init() {
        enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String parse(Player player, String text) {
        if (text == null || text.isEmpty()) return "";
        if (!enabled) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }
}