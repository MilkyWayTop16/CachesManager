package org.gw.cachesmanager.listeners.modes;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.HashMap;
import java.util.Map;

public class KeyMaterialModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public KeyMaterialModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.KEY_MATERIAL;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        Material mat = Material.matchMaterial(message.toUpperCase());
        if (mat == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("material", message);
            configManager.executeActions(player, "key.change-material.invalid", ph);
            return false;
        }

        cacheManager.setCacheKeyMaterial(cache, mat.name());

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("key-material", mat.name());

        configManager.executeActions(player, "key.material-changed", ph);
        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "key.change-material.cancelled";
    }
}
