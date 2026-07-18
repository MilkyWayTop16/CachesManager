package org.gw.cachesmanager.listeners.modes;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class SelectionModeHandler implements ChatModeHandler {

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public SelectionModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.SELECTION;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        String[] parts = message.split(" ");
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());

        if (parts.length != 5) {
            configManager.executeActions(player, "cache.invalid-coordinates-format", ph);
            return false;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(parts[0]);
            y = Double.parseDouble(parts[1]);
            z = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            configManager.executeActions(player, "cache.invalid-coordinates", ph);
            return false;
        }

        String worldName = parts[3];
        World world = player.getServer().getWorld(worldName);
        if (world == null) {
            ph.put("world", worldName);
            configManager.executeActions(player, "cache.invalid-world", ph);
            return false;
        }

        String blockId = parts[4];
        Material blockType = org.gw.cachesmanager.utils.MaterialCompat.match(blockId, null);
        if (blockType == null || !blockType.isBlock()) {
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.select-block.invalid-block", ph);
            return false;
        }

        Location location = new Location(world, x, y, z);
        Cache existing = cacheManager.getCacheByLocation(location);
        if (existing != null && !existing.getName().equals(cache.getName())) {
            ph.put("name-cache", existing.getName());
            configManager.executeActions(player, "cache.already-exists", ph);
            return false;
        }

        cacheManager.setCacheLocation(cache, location);
        cacheManager.setCacheBlockType(cache, blockType);

        ph.put("x", String.valueOf(location.getBlockX()));
        ph.put("y", String.valueOf(location.getBlockY()));
        ph.put("z", String.valueOf(location.getBlockZ()));
        ph.put("world", location.getWorld().getName());

        configManager.executeActions(player, "interaction.select-block.set-location", ph);
        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "interaction.select-block.cancelled";
    }
}
