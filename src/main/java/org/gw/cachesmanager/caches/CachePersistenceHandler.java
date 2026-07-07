package org.gw.cachesmanager.caches;

import org.bukkit.configuration.file.FileConfiguration;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CachePersistenceHandler {
    private final CachesManager plugin;
    private final ConfigManager configManager;

    public CachePersistenceHandler(CachesManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadCache(Cache cache) {
        FileConfiguration config = configManager.loadCacheConfig(cache.getName());
        if (config == null) {
            plugin.error("Не удалось загрузить конфигурацию для тайника: " + cache.getName());
            return;
        }
        cache.setLocation(configManager.getCacheLocation(config));
        cache.setBlockType(configManager.getCacheBlock(cache.getName()));
        cache.setLootWithChances(configManager.getCacheLootWithChances(config));
        cache.setUnbreakable(configManager.isCacheUnbreakable(cache.getName()));
        cache.setDisplayName(configManager.getCacheDisplayName(cache.getName()));
        Object holoTextObj = config.get("hologram.text");
        List<String> holoLines = new ArrayList<>();
        if (holoTextObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) holoTextObj;
            holoLines = new ArrayList<>(lines);
        } else {
            String text = config.getString("hologram.text");
            if (text != null) {
                text = text.replace("\\n", "\n");
                holoLines = new ArrayList<>(Arrays.asList(text.split("\n")));
            }
        }
        cache.setHologramLines(holoLines);
        cache.setAnimation(configManager.getCacheAnimation(cache.getName()));
        cache.setHologramEnabled(config.getBoolean("hologram.enabled", true));
        cache.setHologramOffsetX(config.getDouble("hologram.offset.x", 0.0));
        cache.setHologramOffsetY(config.getDouble("hologram.offset.y", 0.5));
        cache.setHologramOffsetZ(config.getDouble("hologram.offset.z", 0.0));
        cache.setKeyMaterial(config.getString("key.material", "TRIPWIRE_HOOK"));
        cache.setKeyName(config.getString("key.name"));
        cache.setKeyLore(config.getStringList("key.lore"));
        cache.setKeyCustomModelData(config.getInt("key.custom-model-data", 0));
        cache.setKeyGlow(config.getBoolean("key.glow", false));
        cache.setKeyFlags(configManager.getKeyFlags(cache.getName()));

        if (plugin.getDatabaseManager() != null) {
            boolean hasDbStats = plugin.getDatabaseManager().loadCacheStats(cache.getName(), cache);

            if (!hasDbStats) {
                long now = System.currentTimeMillis();
                cache.setCreatedTime(now);
                cache.setFirstOpenedTime(0);
                cache.setLastOpenedTime(0);
            }
        }

        if (plugin.getAnimationsManager() != null && !plugin.getAnimationsManager().getAnimations().containsKey(cache.getAnimation())) {
            cache.setAnimation("default");
            configManager.setCacheAnimation(cache.getName(), "default");
        }
    }

}