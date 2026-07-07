package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.HashMap;
import java.util.Map;

public class HologramTextModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public HologramTextModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.HOLOGRAM_TEXT;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        String newText = message.replace("\\n", "\n");
        cacheManager.setCacheHologramText(cache, newText);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());

        configManager.executeActions(player, "interaction.hologram.change-text.completed", ph);
        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "interaction.hologram.change-text.cancelled";
    }
}
