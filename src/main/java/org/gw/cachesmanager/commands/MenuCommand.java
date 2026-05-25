package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MenuCommand {
    private final CachesManager plugin;

    public MenuCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
            return true;
        }
        if (args.length < 2) {
            plugin.getConfigManager().executeActions((Player) sender, "help.menu");
            return true;
        }

        Player player = (Player) sender;
        String cacheName;
        String menuFile = "global-menu.yml";

        String lastArg = args[args.length - 1];
        if (lastArg.toLowerCase().endsWith(".yml")) {
            cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1)).trim();
            menuFile = lastArg;
        } else {
            String fullJoined = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (plugin.getCacheManager().getCache(plugin.getConfigManager().sanitizeCacheName(fullJoined)) != null) {
                cacheName = fullJoined;
            } else if (args.length >= 3) {
                cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1)).trim();
                menuFile = lastArg + ".yml";
            } else {
                cacheName = fullJoined;
            }
        }

        cacheName = plugin.getConfigManager().sanitizeCacheName(cacheName);
        menuFile = plugin.getConfigManager().sanitizeMenuFile(menuFile);

        if (cacheName.isEmpty()) {
            plugin.getConfigManager().executeActions(player, "help.menu");
            return true;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            plugin.getConfigManager().executeActions(player, "cache.not-found", ph);
            return true;
        }

        if (plugin.getConfigManager().loadMenuConfig(menuFile) == null) {
            ph.put("menu-file", menuFile);
            plugin.getConfigManager().executeActions(player, "errors.menu-not-found", ph);
            return true;
        }

        plugin.getMenuManager().openMenu(player, cacheName, menuFile);
        return true;
    }
}