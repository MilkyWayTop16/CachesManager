package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;
import java.util.HashMap;
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

        String targetName = null;
        Player target = null;

        String lastArg = args[args.length - 1];
        Player testPlayer = plugin.getServer().getPlayerExact(lastArg);

        int endOffset = 0;
        if (testPlayer != null) {
            target = testPlayer;
            targetName = testPlayer.getName();
            endOffset = 1;
        } else {
            if (sender instanceof Player) {
                target = (Player) sender;
                targetName = target.getName();
            } else {
                if (args.length > 2) {
                    String possiblePlayer = args[args.length - 1];
                    String possibleAmount = args[args.length - 2];
                    if (possibleAmount.matches("\\d+")) {
                        targetName = possiblePlayer;
                    }
                }
                if (targetName == null) {
                    targetName = args[args.length - 1];
                }
            }
        }

        int amount = 1;
        int amountIndex = args.length - 1 - endOffset;

        if (amountIndex >= 1) {
            String amountStr = args[amountIndex];
            if (amountStr.matches("\\d+")) {
                amount = Integer.parseInt(amountStr);
                if (amount <= 0) amount = 1;
            } else {
                if (endOffset == 1 || (endOffset == 0 && args.length > 2 && !plugin.getCacheManager().getCaches().containsKey(String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim()))) {
                    Map<String, String> ph = createPlaceholders();
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.invalid-amount", ph);
                    return true;
                }
                amountIndex = args.length - 1;
            }
        } else {
            amountIndex = args.length - 1;
        }

        String cacheName;
        if (amountIndex == args.length - 1 - endOffset && args[amountIndex].matches("\\d+")) {
            cacheName = String.join(" ", Arrays.copyOfRange(args, 1, amountIndex)).trim();
        } else {
            cacheName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - endOffset)).trim();
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

        if (target == null) {
            ph.put("player", targetName != null ? targetName : "Unknown");
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