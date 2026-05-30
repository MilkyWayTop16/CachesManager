package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.Map;

public class CreateCacheCommand extends AbstractSubCommand {

    public CreateCacheCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "createcache";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.createcache";
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
        String cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        cacheName = plugin.getConfigManager().sanitizeCacheName(cacheName);

        if (cacheName.isEmpty()) {
            sendHelp(sender);
            return true;
        }

        Map<String, String> ph = createPlaceholders();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().createCache(cacheName)) {
            plugin.getConfigManager().executeActions(player, "cache.created", ph);
            plugin.getMenuManager().openMenu(player, cacheName, "global-menu.yml", 1);
        } else {
            plugin.getConfigManager().executeActions(player, "cache.already-exists", ph);
        }
        return true;
    }
}