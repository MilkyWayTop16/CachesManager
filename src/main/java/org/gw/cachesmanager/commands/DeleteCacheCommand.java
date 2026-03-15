package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public class DeleteCacheCommand {
    private final CachesManager plugin;

    public DeleteCacheCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.deletecache");
            return true;
        }

        String cacheName = args[1];
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        if (plugin.getCacheManager().deleteCache(cacheName)) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "cache.deleted", ph);
        } else {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "cache.not-found", ph);
        }
        return true;
    }
}