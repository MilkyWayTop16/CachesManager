package org.gw.cachesmanager.utils;

import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;

public class BStats {

    public BStats(JavaPlugin plugin) {
        if (plugin == null) return;

        try {
            Class<?> metricsClass = Class.forName("org.gw.cachesmanager.bstats.bukkit.Metrics");
            Constructor<?> constructor = metricsClass.getConstructor(org.bukkit.plugin.Plugin.class, int.class);
            constructor.newInstance(plugin, 30121);
        } catch (Exception ignored) {
        }
    }
}