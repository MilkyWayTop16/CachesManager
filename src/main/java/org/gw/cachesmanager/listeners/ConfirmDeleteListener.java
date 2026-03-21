package org.gw.cachesmanager.listeners;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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
        String message = e.getMessage().trim().toLowerCase();

        if (!message.startsWith("/cm deletecache ")) return;

        boolean isConfirm = message.endsWith(" confirm");
        boolean isCancel = message.endsWith(" cancel");

        if (!isConfirm && !isCancel) return;

        String base = e.getMessage().substring(0, e.getMessage().length() - (isConfirm ? 8 : 7)).trim();
        String cacheName = base.substring(16).trim();

        if (cacheName.startsWith("\"") && cacheName.endsWith("\"")) {
            cacheName = cacheName.substring(1, cacheName.length() - 1);
        }

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