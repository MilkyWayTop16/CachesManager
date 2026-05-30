package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyLoreModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public KeyLoreModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.KEY_LORE;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        List<String> lines = Arrays.asList(message.split("\\\\n"));
        cacheManager.setCacheKeyLore(cache, lines);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(player, "key.lore-changed", ph);

        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "key.change-lore.cancelled";
    }
}
