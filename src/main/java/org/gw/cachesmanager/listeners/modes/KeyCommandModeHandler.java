package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class KeyCommandModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public KeyCommandModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.KEY_CMD;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        try {
            int cmd = Integer.parseInt(message);
            cacheManager.setCacheKeyCustomModelData(cache, cmd);

            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());
            ph.put("key-cmd", String.valueOf(cmd));
            configManager.executeActions(player, "key.cmd-changed", ph);

            return true;
        } catch (NumberFormatException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());
            configManager.executeActions(player, "key.change-cmd.invalid", ph);
            return false;
        }
    }

    @Override
    public String getCancelMessagePath() {
        return "key.change-cmd.cancelled";
    }
}
