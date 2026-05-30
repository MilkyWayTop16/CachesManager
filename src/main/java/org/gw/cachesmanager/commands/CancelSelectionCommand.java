package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;

public class CancelSelectionCommand extends AbstractSubCommand {

    public CancelSelectionCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "cancel";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.cancel";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    protected boolean handle(CommandSender sender, String[] args) {
        if (args.length > 1) {
            plugin.getConfigManager().executeActions((Player) sender, "help.main");
            return true;
        }

        Player player = (Player) sender;
        boolean cancelled = plugin.getCacheModeListener().cancelSession(player);
        if (!cancelled) {
            plugin.getConfigManager().executeActions(player, "interaction.select-block.no-selection-mode");
        }
        return true;
    }
}