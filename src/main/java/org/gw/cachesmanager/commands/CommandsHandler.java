package org.gw.cachesmanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public class CommandsHandler implements CommandExecutor {

    private final CachesManager plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandsHandler(CachesManager plugin) {
        this.plugin = plugin;

        register(new ReloadCommand(plugin));
        register(new CreateCacheCommand(plugin));
        register(new MenuCommand(plugin));
        register(new GiveKeyCommand(plugin));
        register(new DeleteCacheCommand(plugin));
        register(new ListCachesCommand(plugin));
        register(new CancelSelectionCommand(plugin));
    }

    private void register(SubCommand command) {
        subCommands.put(command.getName().toLowerCase(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subName);

        if (subCommand == null) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
            return true;
        }

        return subCommand.execute(sender, args);
    }
}