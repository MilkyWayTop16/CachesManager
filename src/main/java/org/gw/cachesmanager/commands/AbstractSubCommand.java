package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSubCommand implements SubCommand {

    protected final CachesManager plugin;

    public AbstractSubCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sendNoPermission(sender);
            return true;
        }

        if (isPlayerOnly() && !(sender instanceof Player)) {
            sendConsoleNotAllowed(sender);
            return true;
        }

        return handle(sender, args);
    }

    protected abstract boolean handle(CommandSender sender, String[] args);

    protected void sendHelp(CommandSender sender) {
        String helpPath = "help." + getName().toLowerCase();
        plugin.getConfigManager().executeActions(
                sender instanceof Player ? (Player) sender : null,
                helpPath
        );
    }

    protected void sendNoPermission(CommandSender sender) {
        plugin.getConfigManager().executeActions(
                sender instanceof Player ? (Player) sender : null,
                "errors.no-permission"
        );
    }

    protected void sendConsoleNotAllowed(CommandSender sender) {
        plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
    }

    protected Map<String, String> createPlaceholders() {
        return new HashMap<>();
    }
}