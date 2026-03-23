package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DeleteCacheCommand {
    private final CachesManager plugin;

    public DeleteCacheCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
            return true;
        }

        if (args.length < 2) {
            plugin.getConfigManager().executeActions((Player) sender, "help.deletecache");
            return true;
        }

        String cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        cacheName = plugin.getConfigManager().sanitizeCacheName(cacheName);
        Player p = (Player) sender;

        if (cacheName.isEmpty()) {
            plugin.getConfigManager().executeActions(p, "help.deletecache");
            return true;
        }

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cacheName);
            plugin.getConfigManager().executeActions(p, "cache.not-found", ph);
            return true;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        plugin.getConfigManager().executeActions(p, "cache.delete.confirm", ph);

        plugin.getConfirmDeleteListener().addPending(p, cacheName);
        return true;
    }
}