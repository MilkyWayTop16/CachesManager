package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.HashMap;
import java.util.Map;

public class KeyNameModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public KeyNameModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.KEY_NAME;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        cacheManager.setCacheKeyName(cache, message);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        ph.put("key-name", message);

        configManager.executeActions(player, "key.name-changed", ph);
        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "key.change-name.cancelled";
    }
}
