package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListCachesCommand {
    private final CachesManager plugin;

    public ListCachesCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
            return true;
        }

        ConfigManager configManager = plugin.getConfigManager();
        List<String> cacheNames = configManager.getCacheNames();

        if (cacheNames.isEmpty()) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "list-caches.empty-caches");
            return true;
        }

        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "list-caches.header");

        for (int i = 0; i < cacheNames.size(); i++) {
            String cacheName = cacheNames.get(i);
            FileConfiguration cacheConfig = configManager.loadCacheConfig(cacheName);
            if (cacheConfig == null) continue;

            Location location = configManager.getCacheLocation(cacheConfig);
            Map<String, String> ph = new HashMap<>();
            ph.put("number", String.valueOf(i + 1));
            ph.put("name-cache", cacheName);
            ph.put("x", location != null ? String.valueOf(location.getBlockX()) : "—");
            ph.put("y", location != null ? String.valueOf(location.getBlockY()) : "—");
            ph.put("z", location != null ? String.valueOf(location.getBlockZ()) : "—");
            ph.put("world", location != null ? location.getWorld().getName() : "—");
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "list-caches.caches", ph);
        }

        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "list-caches.footer");

        return true;
    }
}