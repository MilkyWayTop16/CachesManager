package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.HashMap;
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
        int amountIndex = -1;
        for (int i = args.length - 1; i >= 1; i--) {
            if (args[i].matches("\\d+")) {
                amountIndex = i;
                break;
            }
        }
        String cacheName;
        int amount = 1;
        if (amountIndex != -1) {
            cacheName = String.join(" ", Arrays.copyOfRange(args, 1, amountIndex)).trim();
            try {
                amount = Integer.parseInt(args[amountIndex]);
                if (amount <= 0) amount = 1;
            } catch (NumberFormatException ignored) {}
        } else {
            cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        }
        if (cacheName.isEmpty()) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.givekey");
            return true;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        if (plugin.getCacheManager().getCache(cacheName) == null) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "cache.not-found", ph);
            return true;
        }
        Player target;
        String targetName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        if (amountIndex != -1 && amountIndex + 1 < args.length) {
            targetName = args[args.length - 1];
            target = plugin.getServer().getPlayerExact(targetName);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
            return true;
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