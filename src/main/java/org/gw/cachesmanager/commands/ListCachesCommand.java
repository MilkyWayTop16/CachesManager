package org.gw.cachesmanager.commands;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListCachesCommand extends AbstractSubCommand {

    public ListCachesCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "listcaches";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.listcaches";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    protected boolean handle(CommandSender sender, String[] args) {
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
            Map<String, String> ph = createPlaceholders();
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