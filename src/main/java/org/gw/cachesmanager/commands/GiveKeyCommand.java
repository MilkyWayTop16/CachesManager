package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ItemManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GiveKeyCommand extends AbstractSubCommand {

    public GiveKeyCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "givekey";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.givekey";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    protected boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        List<String> cacheNames = plugin.getCacheManager().getCacheNames();
        int nameEnd = CacheCommandArgs.findFullNameEnd(cacheNames, args, 1, args.length);
        String cacheName;
        int cursor;

        if (nameEnd != -1) {
            cacheName = CacheCommandArgs.resolveExistingName(cacheNames, CacheCommandArgs.join(args, 1, nameEnd));
            cursor = nameEnd;
        } else {
            int end = args.length;
            Player maybePlayer = plugin.getServer().getPlayerExact(args[end - 1]);
            if (maybePlayer != null && end - 1 > 1) {
                end--;
            }
            if (end - 1 > 1 && args[end - 1].matches("\\d+")) {
                end--;
            }
            cacheName = CacheCommandArgs.join(args, 1, end);
            nameEnd = end;
            cursor = end;
        }

        cacheName = plugin.getConfigManager().sanitizeCacheName(cacheName);
        if (cacheName.isEmpty()) {
            sendHelp(sender);
            return true;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "cache.not-found", ph);
            return true;
        }

        int amount = 1;
        Player target = null;

        if (cursor < args.length && args[cursor].matches("\\d+")) {
            try {
                amount = Integer.parseInt(args[cursor]);
            } catch (NumberFormatException e) {
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-amount", ph);
                return true;
            }
            if (amount <= 0) {
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-amount", ph);
                return true;
            }
            amount = Math.min(amount, ItemManager.MAX_KEYS_PER_GIVE);
            cursor++;
        }

        if (cursor < args.length) {
            target = plugin.getServer().getPlayerExact(args[cursor]);
            if (target == null) {
                ph.put("player", args[cursor]);
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-player", ph);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            ph.put("player", "Неизвестно");
            plugin.getConfigManager().executeActions(null, "errors.invalid-player", ph);
            return true;
        }

        plugin.getItemManager().giveKey(target, cacheName, amount);

        ph.put("amount", String.valueOf(amount));
        ph.put("player", target.getName());
        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "key.given", ph);
        return true;
    }
}
