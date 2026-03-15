package org.gw.cachesmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandsTabCompleter implements TabCompleter {
    private final CachesManager plugin;

    public CommandsTabCompleter(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "createcache", "menu", "givekey", "deletecache", "listcaches", "cancel"));
            completions = completions.stream()
                    .filter(s -> sender.hasPermission("cachesmanager." + s))
                    .collect(Collectors.toList());
        }
        else if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("reload")) {
                completions.addAll(Arrays.asList("all", "configs", "holograms", "animations"));
            }
            else if (sub.equals("createcache")) {
                if (args[1].isEmpty()) completions.add("<Название тайника>");
            }
            else if (sub.equals("menu") || sub.equals("givekey") || sub.equals("deletecache")) {
                if (args[1].isEmpty()) completions.add("<Название тайника>");
                completions.addAll(plugin.getCacheManager().getCacheNames());
            }
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("menu")) {
            String last = args[2].toLowerCase();
            List<String> menus = Arrays.asList(
                    "global-menu.yml",
                    "hologram-menu.yml",
                    "loot-menu.yml",
                    "chance-menu.yml",
                    "stats-menu.yml",
                    "key-menu.yml",
                    "history-menu.yml"
            );
            completions.addAll(menus.stream()
                    .filter(m -> m.toLowerCase().startsWith(last))
                    .collect(Collectors.toList()));
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("givekey")) {
            if (args[2].isEmpty()) {
                completions.add("<Количество>");
            } else {
                completions.addAll(Arrays.asList("1", "5", "10", "32", "64"));
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("givekey")) {
            if (args[3].isEmpty()) {
                completions.add("<Никнейм>");
            }
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
        }

        String lastArg = args[args.length - 1].toLowerCase();

        return completions.stream()
                .filter(s -> {
                    if (s.startsWith("<")) return lastArg.isEmpty();
                    return s.toLowerCase().startsWith(lastArg);
                })
                .collect(Collectors.toList());
    }
}