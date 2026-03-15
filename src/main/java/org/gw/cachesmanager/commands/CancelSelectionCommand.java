package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

public class CancelSelectionCommand {
    private final CachesManager plugin;

    public CancelSelectionCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getConfigManager().executeActions(null, "errors.console-not-allowed");
            return true;
        }

        if (args.length > 1) {
            plugin.getConfigManager().executeActions((Player) sender, "help.main");
            return true;
        }

        Player player = (Player) sender;
        boolean cancelled = plugin.getCacheModeListener().cancelSelectionMode(player);
        if (!cancelled) {
            plugin.getConfigManager().executeActions(player, "interaction.select-block.no-selection-mode");
        }
        return true;
    }
}