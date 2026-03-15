package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GiveKeyCommand {
    private final CachesManager plugin;

    public GiveKeyCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.givekey");
            return true;
        }

        String cacheName = args[1];
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (plugin.getCacheManager().getCache(cacheName) == null) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "cache.not-found", ph);
            return true;
        }

        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-amount");
                    return true;
                }
            } catch (NumberFormatException e) {
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-amount");
                return true;
            }
        }

        Player target;
        String targetName;

        if (args.length > 3) {
            targetName = args[3];
            target = plugin.getServer().getPlayerExact(targetName);
        } else {
            if (sender instanceof Player) {
                target = (Player) sender;
                targetName = target.getName();
            } else {
                plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
                return true;
            }
        }

        if (target == null) {
            ph.put("player", targetName);
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-player", ph);
            return true;
        }

        plugin.getItemManager().giveKey(target, cacheName, amount);
        ph.put("amount", String.valueOf(amount));
        ph.put("player", target.getName());
        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "key.given", ph);
        return true;
    }
}