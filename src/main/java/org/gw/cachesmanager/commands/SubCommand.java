package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;

public interface SubCommand {

    String getName();

    String getPermission();

    boolean isPlayerOnly();

    boolean execute(CommandSender sender, String[] args);
}