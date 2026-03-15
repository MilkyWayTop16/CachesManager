package org.gw.cachesmanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

public class CommandsHandler implements CommandExecutor {
    private final CachesManager plugin;
    private final CreateCacheCommand createCacheCommand;
    private final MenuCommand menuCommand;
    private final GiveKeyCommand giveKeyCommand;
    private final DeleteCacheCommand deleteCacheCommand;
    private final ListCachesCommand listCachesCommand;
    private final ReloadCommand reloadCommand;
    private final CancelSelectionCommand cancelSelectionCommand;

    public CommandsHandler(CachesManager plugin) {
        this.plugin = plugin;
        this.createCacheCommand = new CreateCacheCommand(plugin);
        this.menuCommand = new MenuCommand(plugin);
        this.giveKeyCommand = new GiveKeyCommand(plugin);
        this.deleteCacheCommand = new DeleteCacheCommand(plugin);
        this.listCachesCommand = new ListCachesCommand(plugin);
        this.reloadCommand = new ReloadCommand(plugin);
        this.cancelSelectionCommand = new CancelSelectionCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("cachesmanager.reload")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                return reloadCommand.execute(sender, args);
            case "createcache":
                if (!sender.hasPermission("cachesmanager.createcache")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
                    return true;
                }
                return createCacheCommand.execute(sender, args);
            case "menu":
                if (!sender.hasPermission("cachesmanager.menu")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
                    return true;
                }
                return menuCommand.execute(sender, args);
            case "givekey":
                if (!sender.hasPermission("cachesmanager.givekey")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                return giveKeyCommand.execute(sender, args);
            case "deletecache":
                if (!sender.hasPermission("cachesmanager.deletecache")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                return deleteCacheCommand.execute(sender, args);
            case "listcaches":
                if (!sender.hasPermission("cachesmanager.listcaches")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                return listCachesCommand.execute(sender, args);
            case "cancel":
                if (!sender.hasPermission("cachesmanager.cancel")) {
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
                    return true;
                }
                return cancelSelectionCommand.execute(sender, args);
            default:
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
                return true;
        }
    }
}