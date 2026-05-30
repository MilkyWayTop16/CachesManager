package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.Map;

public class MenuCommand extends AbstractSubCommand {

    public MenuCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.menu";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    protected boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
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
            sendHelp(sender);
            return true;
        }

        Map<String, String> ph = createPlaceholders();
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