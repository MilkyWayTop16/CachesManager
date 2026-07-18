package org.gw.cachesmanager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public class ReloadCommand extends AbstractSubCommand {

    public ReloadCommand(CachesManager plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "cachesmanager.reload";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    protected boolean handle(CommandSender sender, String[] args) {
        String mode = (args.length > 1) ? args[1].toLowerCase() : "configs";
        long startTime = System.currentTimeMillis();

        String path;
        switch (mode) {
            case "all":
                boolean force = args.length > 2 && args[2].equalsIgnoreCase("-force");
                if (force) {
                    plugin.forceReloadPlugin();
                    path = "reload.all";
                } else {
                    plugin.reloadPlugin();
                    path = "reload.all";
                }
                break;
            case "configs":
                plugin.log("Перезагрузка всех конфигураций...");
                String error = plugin.getConfigManager().reloadConfig();
                if (error != null) {
                    Map<String, String> ph = createPlaceholders();
                    ph.put("reason", error);
                    plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "reload.failed", ph);
                    return true;
                }
                plugin.getAnimationsManager().reloadAnimations(false);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getCacheManager().loadCaches(() -> {
                            if (plugin.getMenuManager() != null) plugin.getMenuManager().reload();
                        });
                    }
                }.runTask(plugin);
                path = "reload.configs";
                break;
            case "holograms":
                plugin.log("Перезагрузка голограмм над тайниками...");
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
                plugin.log("Перезагрузка системы анимаций...");
                plugin.getAnimationsManager().reloadAnimations(false);
                path = "reload.animations";
                break;
            default:
                plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, "help.main");
                return true;
        }

        long ms = System.currentTimeMillis() - startTime;
        Map<String, String> ph = createPlaceholders();
        ph.put("ms", String.valueOf(ms));

        plugin.getConfigManager().executeActions(sender instanceof Player ? (Player) sender : null, path, ph);

        if ("animations".equals(mode) && plugin.getMenuManager() != null) {
            plugin.getMenuManager().refreshOpenMenus();
        }

        return true;
    }
}