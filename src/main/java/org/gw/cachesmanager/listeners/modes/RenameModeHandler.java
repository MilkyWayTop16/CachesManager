package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.HashMap;
import java.util.Map;

public class RenameModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public RenameModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.RENAME;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        String oldDisplayName = cache.getDisplayName();
        String newDisplayName = message;

        configManager.setCacheDisplayName(cache.getName(), newDisplayName);
        cacheManager.loadCache(cache);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("old-name", oldDisplayName);
        ph.put("new-name", newDisplayName);

        configManager.executeActions(player, "interaction.rename.completed", ph);

        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "interaction.rename.cancelled";
    }
}
