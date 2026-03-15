package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public class ReloadCommand {
    private final CachesManager plugin;

    public ReloadCommand(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cachesmanager.reload")) {
            plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "errors.no-permission");
            return true;
        }

        String mode = (args.length > 1) ? args[1].toLowerCase() : "configs";
        long startTime = System.currentTimeMillis();

        String path;
        switch (mode) {
            case "all":
                plugin.reloadPlugin();
                path = "reload.all";
                break;
            case "configs":
                plugin.console("&#ffff00◆ CachesManager &f| Перезагрузка всех конфигураций...");
                String error = plugin.getConfigManager().reloadConfig();
                if (error != null) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("reason", error);
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "reload.failed", ph);
                    return true;
                }
                plugin.getAnimationsManager().reloadAnimations();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getCacheManager().loadCaches();
                        if (plugin.getMenuManager() != null) plugin.getMenuManager().reload();
                    }
                }.runTask(plugin);
                path = "reload.configs";
                break;
            case "holograms":
                plugin.console("&#ffff00◆ CachesManager &f| Перезагрузка голограмм над тайниками...");
                plugin.getCacheManager().removeAllHolograms();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getCacheManager().loadCaches();
                    }
                }.runTask(plugin);
                path = "reload.holograms";
                break;
            case "animations":
                plugin.console("&#ffff00◆ CachesManager &f| Перезагрузка системы анимаций...");
                plugin.getAnimationsManager().reloadAnimations();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getCacheManager().loadCaches();
                    }
                }.runTask(plugin);
                path = "reload.animations";
                break;
            default:
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
                return true;
        }

        long ms = System.currentTimeMillis() - startTime;
        Map<String, String> ph = new HashMap<>();
        ph.put("ms", String.valueOf(ms));

        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, path, ph);
        return true;
    }
}