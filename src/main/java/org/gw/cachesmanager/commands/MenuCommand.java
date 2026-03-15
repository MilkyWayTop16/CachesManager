package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

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
        String cacheName = args[1];
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            plugin.getConfigManager().executeActions(player, "cache.not-found", ph);
            return true;
        }

        String menuFile = "global-menu.yml";
        if (args.length >= 3) {
            menuFile = args[2];
            if (!menuFile.endsWith(".yml")) menuFile += ".yml";

            if (plugin.getConfigManager().loadMenuConfig(menuFile) == null) {
                ph.put("menu-file", menuFile);
                plugin.getConfigManager().executeActions(player, "errors.menu-not-found", ph);
                return true;
            }
        }

        plugin.getMenuManager().openMenu(player, cacheName, menuFile);
        return true;
    }
}