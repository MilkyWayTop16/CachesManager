package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CreateCacheCommand {
    private final CachesManager plugin;

    public CreateCacheCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
            return true;
        }
        if (args.length < 2) {
            plugin.getConfigManager().executeActions((Player) sender, "help.createcache");
            return true;
        }

        Player player = (Player) sender;
        String cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        cacheName = plugin.getConfigManager().sanitizeCacheName(cacheName);

        if (cacheName.isEmpty()) {
            plugin.getConfigManager().executeActions(player, "help.createcache");
            return true;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().createCache(cacheName)) {
            plugin.getConfigManager().executeActions(player, "cache.created", ph);
            plugin.getMenuManager().openMenu(player, cacheName, "global-menu.yml");
        } else {
            plugin.getConfigManager().executeActions(player, "cache.already-exists", ph);
        }
        return true;
    }
}