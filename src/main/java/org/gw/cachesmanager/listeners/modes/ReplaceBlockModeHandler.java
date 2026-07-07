package org.gw.cachesmanager.listeners.modes;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class ReplaceBlockModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public ReplaceBlockModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.REPLACE_BLOCK;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        Material newBlockType;
        try {
            newBlockType = Material.valueOf(message.toUpperCase());
            if (!newBlockType.isBlock()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("block-id", message);
            configManager.executeActions(player, "interaction.replace-block.invalid", ph);
            return false;
        }

        if (cache.getBlockType() == newBlockType) {
            Map<String, String> ph = new HashMap<>();
            ph.put("block-id", message);
            configManager.executeActions(player, "interaction.replace-block.same", ph);
            return false;
        }

        cacheManager.setCacheBlockType(cache, newBlockType);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        ph.put("block-id", newBlockType.name().toLowerCase());

        configManager.executeActions(player, "interaction.replace-block.completed", ph);
        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "interaction.replace-block.cancelled";
    }
}
