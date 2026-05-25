package org.gw.cachesmanager.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConfirmDeleteListener implements Listener {

    private final CachesManager plugin;
    private final Map<UUID, String> pendingDelete = new HashMap<>();

    public ConfirmDeleteListener(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void addPending(Player player, String cacheName) {
        pendingDelete.put(player.getUniqueId(), cacheName);
    }

    public void removePending(Player player) {
        pendingDelete.remove(player.getUniqueId());
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String rawMessage = e.getMessage().trim();
        String lowerMessage = rawMessage.toLowerCase();

        if (!lowerMessage.contains(" deletecache ")) return;

        boolean isConfirm = lowerMessage.endsWith(" confirm");
        boolean isCancel = lowerMessage.endsWith(" cancel");

        if (!isConfirm && !isCancel) return;

        if (!p.hasPermission("cachesmanager.deletecache")) {
            plugin.getConfigManager().executeActions(p, "errors.no-permission");
            e.setCancelled(true);
            return;
        }

        int deleteCacheIdx = lowerMessage.indexOf(" deletecache ");
        String cachePart = rawMessage.substring(deleteCacheIdx + 13, rawMessage.length() - (isConfirm ? 8 : 7)).trim();

        if (cachePart.startsWith("\"") && cachePart.endsWith("\"") && cachePart.length() >= 2) {
            cachePart = cachePart.substring(1, cachePart.length() - 1).trim();
        }

        String cacheName = plugin.getConfigManager().sanitizeCacheName(cachePart);
        UUID uuid = p.getUniqueId();

        if (!pendingDelete.containsKey(uuid) || !pendingDelete.get(uuid).equals(cacheName)) {
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (isConfirm) {
            if (plugin.getCacheManager().deleteCache(cacheName)) {
                plugin.getConfigManager().executeActions(p, "cache.delete.success", ph);
            } else {
                plugin.getConfigManager().executeActions(p, "cache.not-found", ph);
            }
        } else {
            plugin.getConfigManager().executeActions(p, "cache.delete.cancelled", ph);
        }

        removePending(p);
        e.setCancelled(true);
    }
}