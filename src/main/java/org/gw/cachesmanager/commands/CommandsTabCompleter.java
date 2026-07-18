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
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandsTabCompleter implements TabCompleter {

    private static final List<String> MENU_FILES = Arrays.asList(
            "global-menu.yml",
            "hologram-menu.yml",
            "loot-menu.yml",
            "chance-menu.yml",
            "stats-menu.yml",
            "key-menu.yml",
            "history-menu.yml"
    );

    private static final List<String> GIVE_AMOUNTS = Arrays.asList("1", "5", "10", "32", "64");

    private final CachesManager plugin;

    public CommandsTabCompleter(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(Arrays.asList(
                    "reload", "createcache", "menu", "givekey", "deletecache", "listcaches", "cancel"
            ), args[0], s -> sender.hasPermission("cachesmanager." + s));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> cacheNames = plugin.getCacheManager() != null
                ? plugin.getCacheManager().getCacheNames()
                : List.of();

        return switch (sub) {
            case "reload" -> completeReload(args);
            case "createcache" -> completeCreateCache(args);
            case "deletecache" -> completeDeleteCache(args, cacheNames);
            case "menu" -> completeMenu(args, cacheNames);
            case "givekey" -> completeGiveKey(args, cacheNames);
            default -> List.of();
        };
    }

    private List<String> completeReload(String[] args) {
        if (args.length == 2) {
            return filter(Arrays.asList("all", "configs", "holograms", "animations"), args[1], null);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("all")) {
            return filter(List.of("-force"), args[2], null);
        }
        return List.of();
    }

    private List<String> completeCreateCache(String[] args) {
        if (args.length >= 2 && args[args.length - 1].isEmpty()) {
            return List.of("<Название тайника>");
        }
        if (args.length == 2 && args[1].isEmpty()) {
            return List.of("<Название тайника>");
        }
        return List.of();
    }

    private List<String> completeDeleteCache(String[] args, List<String> cacheNames) {
        if (args.length < 2) return List.of();
        if (CacheCommandArgs.isPastCacheName(cacheNames, args, 1)) {
            return List.of();
        }
        return CacheCommandArgs.completeNameToken(cacheNames, args, 1);
    }

    private List<String> completeMenu(String[] args, List<String> cacheNames) {
        if (args.length < 2) return List.of();

        if (!CacheCommandArgs.isPastCacheName(cacheNames, args, 1)) {
            return CacheCommandArgs.completeNameToken(cacheNames, args, 1);
        }

        int nameEnd = CacheCommandArgs.nameEndIndex(cacheNames, args, 1);
        if (nameEnd < 0) {
            return CacheCommandArgs.completeNameToken(cacheNames, args, 1);
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args[args.length - 1].isEmpty()) {
            return new ArrayList<>(MENU_FILES);
        }
        return MENU_FILES.stream()
                .filter(m -> m.toLowerCase(Locale.ROOT).startsWith(last)
                        || m.toLowerCase(Locale.ROOT).replace(".yml", "").startsWith(last))
                .collect(Collectors.toList());
    }

    private List<String> completeGiveKey(String[] args, List<String> cacheNames) {
        if (args.length < 2) return List.of();

        if (!CacheCommandArgs.isPastCacheName(cacheNames, args, 1)) {
            return CacheCommandArgs.completeNameToken(cacheNames, args, 1);
        }

        int nameEnd = CacheCommandArgs.nameEndIndex(cacheNames, args, 1);
        if (nameEnd < 0) {
            return CacheCommandArgs.completeNameToken(cacheNames, args, 1);
        }

        int afterName = args.length - nameEnd;
        boolean trailingEmpty = args[args.length - 1].isEmpty();
        String last = args[args.length - 1];

        if (afterName == 1 && trailingEmpty) {
            List<String> out = new ArrayList<>();
            out.add("<Количество>");
            out.addAll(GIVE_AMOUNTS);
            return out;
        }

        if (afterName == 1) {
            if (last.matches("\\d*")) {
                List<String> out = new ArrayList<>();
                if (last.isEmpty()) out.add("<Количество>");
                for (String amount : GIVE_AMOUNTS) {
                    if (amount.startsWith(last)) out.add(amount);
                }
                return out;
            }
            return onlinePlayers(last);
        }

        if (afterName == 2 && trailingEmpty) {
            List<String> out = new ArrayList<>();
            out.add("<Никнейм>");
            out.addAll(onlinePlayers(""));
            return out;
        }

        if (afterName >= 2) {
            return onlinePlayers(last);
        }

        return List.of();
    }

    private List<String> onlinePlayers(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String prefix, java.util.function.Predicate<String> permission) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> permission == null || permission.test(s))
                .filter(s -> {
                    if (s.startsWith("<")) return p.isEmpty();
                    return s.toLowerCase(Locale.ROOT).startsWith(p);
                })
                .collect(Collectors.toList());
    }
}
