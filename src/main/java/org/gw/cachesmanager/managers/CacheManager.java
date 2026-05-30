package org.gw.cachesmanager.managers;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.*;

import java.util.*;

public class CacheManager {
    private final CachesManager plugin;
    private final ConfigManager configManager;
    @Getter @Setter private HologramManager hologramManager;
    private final AnimationsManager animationsManager;
    private final StatsManager statsManager;
    private final LootHistoryManager lootHistoryManager;

    private final CacheRegistry registry = new CacheRegistry();
    private final CachePersistenceHandler persistenceHandler;
    @Getter
    private final CacheInteractionHandler interactionHandler;
    private volatile boolean isReloading = false;
    private boolean legacyHologramCleanupDone = false;

    public CacheManager(CachesManager plugin, ConfigManager configManager, HologramManager hologramManager,
                        ItemManager itemManager, AnimationsManager animationsManager, StatsManager statsManager,
                        LootHistoryManager lootHistoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.hologramManager = hologramManager;
        this.animationsManager = animationsManager;
        this.statsManager = statsManager;
        this.lootHistoryManager = lootHistoryManager;

        this.persistenceHandler = new CachePersistenceHandler(plugin, configManager);
        this.interactionHandler = new CacheInteractionHandler(plugin);
    }

    public void removeLocationMapping(Location loc) {
        registry.removeLocationMapping(loc);
    }

    public void addLocationMapping(Location loc, Cache cache) {
        registry.addLocationMapping(loc, cache);
    }

    private void removeHologram(String cacheName) {
        if (hologramManager != null) {
            hologramManager.removeHologram(cacheName);
        }
    }

    private void refreshHologram(Cache cache) {
        if (hologramManager == null || !cache.isHologramEnabled() || cache.getLocation() == null) {
            return;
        }
        removeHologram(cache.getName());
        hologramManager.createHologram(cache.getName(), cache.getLocation(), cache.getHologramText());
    }

    private void recreateHologramAfterLocationChange(Cache cache) {
        if (hologramManager == null) return;

        removeHologram(cache.getName());

        if (cache.getLocation() != null && cache.isHologramEnabled()) {
            hologramManager.createHologram(cache.getName(), cache.getLocation(), cache.getHologramText());
        }
    }

    public void setCacheLocation(Cache cache, Location loc) {
        if (cache.getLocation() != null) {
            removeLocationMapping(cache.getLocation());
        }
        cache.setLocation(loc);
        configManager.setCacheLocation(cache.getName(), loc);

        if (loc != null) {
            addLocationMapping(loc, cache);
            plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(cache.getBlockType()));
        }

        recreateHologramAfterLocationChange(cache);
    }

    public void setCacheBlockType(Cache cache, Material blockType) {
        cache.setBlockType(blockType);
        if (cache.getLocation() != null && blockType != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> cache.getLocation().getBlock().setType(blockType));
        }
        configManager.setCacheBlock(cache.getName(), blockType);
    }

    public void setCacheHologramText(Cache cache, String text) {
        cache.setHologramText(text);
        configManager.setCacheHologramText(cache.getName(), text);

        if (cache.isHologramEnabled() && cache.getLocation() != null && hologramManager != null) {
            hologramManager.updateHologram(cache.getName(), text);
        }
    }

    public void setCacheHologramEnabled(Cache cache, boolean enabled) {
        cache.setHologramEnabled(enabled);
        configManager.setCacheHologramEnabled(cache.getName(), enabled);
        refreshHologram(cache);
    }

    public void setCacheHologramOffsetX(Cache cache, double x) {
        cache.setHologramOffsetX(x);
        configManager.setCacheHologramOffset(cache.getName(), x, cache.getHologramOffsetY(), cache.getHologramOffsetZ());
        refreshHologram(cache);
    }

    public void setCacheHologramOffsetY(Cache cache, double y) {
        cache.setHologramOffsetY(y);
        configManager.setCacheHologramOffset(cache.getName(), cache.getHologramOffsetX(), y, cache.getHologramOffsetZ());
        refreshHologram(cache);
    }

    public void setCacheHologramOffsetZ(Cache cache, double z) {
        cache.setHologramOffsetZ(z);
        configManager.setCacheHologramOffset(cache.getName(), cache.getHologramOffsetX(), cache.getHologramOffsetY(), z);
        refreshHologram(cache);
    }

    public void setCacheAnimation(Cache cache, String animation) {
        cache.setAnimation(animation);
        configManager.setCacheAnimation(cache.getName(), animation);
    }

    public void setCacheUnbreakable(Cache cache, boolean unbreakable) {
        cache.setUnbreakable(unbreakable);
        configManager.setCacheUnbreakable(cache.getName(), unbreakable);
    }

    public void setCacheKeyMaterial(Cache cache, String material) {
        cache.setKeyMaterial(material);
        configManager.setKeyMaterial(cache.getName(), material);
    }

    public void setCacheKeyName(Cache cache, String name) {
        cache.setKeyName(name);
        configManager.setKeyName(cache.getName(), name);
    }

    public void setCacheKeyLore(Cache cache, List<String> lore) {
        cache.setKeyLore(lore);
        configManager.setKeyLore(cache.getName(), lore);
    }

    public void setCacheKeyCustomModelData(Cache cache, int cmd) {
        cache.setKeyCustomModelData(cmd);
        configManager.setKeyCustomModelData(cache.getName(), cmd);
    }

    public void setCacheKeyGlow(Cache cache, boolean glow) {
        cache.setKeyGlow(glow);
        configManager.setKeyGlow(cache.getName(), glow);
    }

    public void setCacheKeyFlags(Cache cache, List<String> flags) {
        cache.setKeyFlags(flags);
        configManager.setKeyFlags(cache.getName(), flags);
    }

    public void setCacheLootWithChances(Cache cache, List<Map.Entry<ItemStack, Integer>> lootWithChances) {
        cache.setLootWithChances(lootWithChances);
        configManager.setCacheLoot(cache.getName(), lootWithChances);
    }

    public boolean openCache(Cache cache, Player player) {
        return interactionHandler.open(player, cache);
    }

    public void loadCache(Cache cache) {
        if (cache != null) {
            persistenceHandler.loadCache(cache);
        }
    }

    public void loadCaches() {
        if (isReloading) return;
        isReloading = true;

        if (animationsManager != null) {
            removeHologramsExceptActiveAnimations(animationsManager);
        } else {
            removeAllHolograms();
        }

        if (hologramManager != null && !legacyHologramCleanupDone) {
            hologramManager.cleanupLegacyHologramsFromV12();
            legacyHologramCleanupDone = true;
        }

        Map<String, org.gw.cachesmanager.caches.Cache> oldCaches = registry.getCachesMap();
        registry.clear();

        List<String> cacheNames = configManager.getCacheNames();

        if (plugin.getDatabaseManager() != null && !plugin.getDatabaseManager().hasMigratedFromV12()) {
            plugin.getDatabaseManager().performEfficientV12Migration(cacheNames);
        }

        for (String cacheName : cacheNames) {
            org.gw.cachesmanager.caches.Cache oldCache = oldCaches.get(cacheName);
            boolean stoodInUse = oldCache != null && oldCache.isInUse();

            Cache cache = new Cache(cacheName);
            if (stoodInUse) {
                cache.setInUse(true);
            }
            persistenceHandler.loadCache(cache);
            registry.register(cache);

            boolean hasActiveAnim = plugin.getAnimationsManager() != null && plugin.getAnimationsManager().hasActiveAnimation(cacheName);
            if (!stoodInUse && !hasActiveAnim && cache.getLocation() != null && cache.isHologramEnabled()) {
                refreshHologram(cache);
            }
            if (plugin.getMenuManager() != null) {
                plugin.getMenuManager().initializeCachePageLoot(cacheName);
            }
        }

        isReloading = false;

        sanitizeStuckAnimationStates();

        plugin.log("Успешно инициализировано и загружено файлов тайников: " + registry.getCachesMap().size());
    }

    private void sanitizeStuckAnimationStates() {
        if (plugin.getAnimationsManager() == null) return;

        for (Cache cache : registry.getCachesMap().values()) {
            if (!cache.isInUse()) continue;

            boolean hasActiveAnim = plugin.getAnimationsManager() != null && plugin.getAnimationsManager().hasActiveAnimation(cache.getName());
            if (hasActiveAnim) continue;

            cache.setInUse(false);

            if (hologramManager != null && cache.getLocation() != null && cache.isHologramEnabled()) {
                hologramManager.createHologram(cache.getName(), cache.getLocation(), cache.getHologramText());
            }
        }
    }

    public boolean createCache(String cacheName) {
        cacheName = configManager.sanitizeCacheName(cacheName);
        if (cacheName.isEmpty() || registry.getByName(cacheName) != null) {
            plugin.error("Не удалось создать тайник, так как имя некорректно или уже занято: " + cacheName);
            return false;
        }
        configManager.createCacheConfig(cacheName);

        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().initializeDefaultStats(cacheName);
        }

        Cache cache = new Cache(cacheName);
        persistenceHandler.loadCache(cache);
        registry.register(cache);

        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().initializeCachePageLoot(cacheName);
        }

        plugin.log("Создан новый тайник в общей базе данных: " + cacheName);
        return true;
    }

    public boolean deleteCache(String cacheName) {
        org.gw.cachesmanager.caches.Cache cache = registry.getByName(cacheName);
        if (cache == null) {
            return false;
        }
        if (cache.isInUse()) {
            plugin.error("Запрос на удаление отклонен, так как тайник " + cacheName + " в данный момент открывается игроком...");
            return false;
        }

        registry.unregister(cacheName);
        removeHologram(cacheName);

        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().clearCacheForCache(cacheName);
        }
        if (lootHistoryManager != null) {
            lootHistoryManager.deleteHistory(cacheName);
        }
        configManager.deleteCacheConfig(cacheName);
        plugin.log("Тайник полностью удален из памяти сервера и конфигураций: " + cacheName);
        return true;
    }

    public Cache getCache(String cacheName) {
        return (Cache) registry.getByName(cacheName);
    }

    public Cache getCacheByLocation(Location location) {
        return (Cache) registry.getByLocation(location);
    }

    public Map<String, org.gw.cachesmanager.caches.Cache> getCaches() {
        return registry.getCachesMap();
    }

    public List<String> getCacheNames() {
        return registry.getCacheNames();
    }

    public void saveAllStatsSynchronously() {
        if (plugin.getDatabaseManager() == null) return;
        for (org.gw.cachesmanager.caches.Cache cache : registry.getCachesMap().values()) {
            plugin.getDatabaseManager().saveCacheStatsSynchronously(cache);
        }
    }

    public void removeAllHolograms() {
        if (hologramManager != null) {
            hologramManager.clearAllHolograms();
        }
    }

    public void removeHologramsExceptActiveAnimations(AnimationsManager animationsManager) {
        if (hologramManager == null) return;

        for (Cache cache : registry.getCachesMap().values()) {
            if (animationsManager != null && animationsManager.hasActiveAnimation(cache.getName())) {
                continue;
            }
            hologramManager.removeHologram(cache.getName());
        }
    }
}