package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class HologramOffsetModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;
    private final String axis;

    public HologramOffsetModeHandler(CacheManager cacheManager, ConfigManager configManager, String axis) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
        this.axis = axis.toUpperCase();
    }

    @Override
    public PlayerMode getMode() {
        return switch (axis) {
            case "X" -> PlayerMode.HOLOGRAM_OFFSET_X;
            case "Y" -> PlayerMode.HOLOGRAM_OFFSET_Y;
            case "Z" -> PlayerMode.HOLOGRAM_OFFSET_Z;
            default -> throw new IllegalArgumentException("Invalid axis: " + axis);
        };
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        try {
            double newOffset = Double.parseDouble(message);

            switch (axis) {
                case "X" -> cacheManager.setCacheHologramOffsetX(cache, newOffset);
                case "Y" -> cacheManager.setCacheHologramOffsetY(cache, newOffset);
                case "Z" -> cacheManager.setCacheHologramOffsetZ(cache, newOffset);
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getName());
            ph.put("offset", String.valueOf(newOffset));

            String actionPath = switch (axis) {
                case "X" -> "interaction.hologram.offset-x-changed";
                case "Y" -> "interaction.hologram.offset-y-changed";
                case "Z" -> "interaction.hologram.offset-z-changed";
                default -> "interaction.hologram.offset-x-changed";
            };

            configManager.executeActions(player, actionPath, ph);

            if (cache.getLocation() != null && cache.isHologramEnabled() && cacheManager.getHologramManager() != null) {
                cacheManager.getHologramManager().updateHologram(cache.getName(), cache.getHologramLines());
            }

            return true;
        } catch (NumberFormatException e) {
            configManager.executeActions(player, "interaction.hologram.offset-invalid-number", null);
            return false;
        }
    }

    @Override
    public String getCancelMessagePath() {
        return switch (axis) {
            case "X" -> "interaction.hologram.offset-x.cancelled";
            case "Y" -> "interaction.hologram.offset-y.cancelled";
            case "Z" -> "interaction.hologram.offset-z.cancelled";
            default -> "interaction.hologram.offset-x.cancelled";
        };
    }
}
