package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.Map;

public class DeleteCacheCommand extends AbstractSubCommand {

    public DeleteCacheCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "deletecache";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.deletecache";
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

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            Map<String, String> ph = createPlaceholders();
            ph.put("name-cache", cacheName);
            plugin.getConfigManager().executeActions(player, "cache.not-found", ph);
            return true;
        }

        Map<String, String> ph = createPlaceholders();
        ph.put("name-cache", cacheName);
        plugin.getConfigManager().executeActions(player, "cache.delete.confirm", ph);

        plugin.getConfirmDeleteListener().addPending(player, cacheName);
        return true;
    }
}