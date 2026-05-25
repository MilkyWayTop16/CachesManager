package org.gw.cachesmanager.managers;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.configs.ActionManager;
import org.gw.cachesmanager.configs.CacheConfigHandler;
import org.gw.cachesmanager.configs.MainConfig;
import org.gw.cachesmanager.configs.MenuConfigHandler;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Getter
public class ConfigManager {
    private final CachesManager plugin;
    private final MainConfig mainConfig;
    private final MenuConfigHandler menuConfigHandler;
    private final CacheConfigHandler cacheConfigHandler;
    private final ActionManager actionManager;

    public ConfigManager(CachesManager plugin) {
        this.plugin = plugin;
        this.mainConfig = new MainConfig(plugin);
        this.menuConfigHandler = new MenuConfigHandler(plugin);
        this.cacheConfigHandler = new CacheConfigHandler(plugin);
        this.actionManager = new ActionManager(plugin, mainConfig);

        menuConfigHandler.createDefaultMenus();
        cacheConfigHandler.preloadConfigs();
    }

    public FileConfiguration getConfig() { return mainConfig.getConfig(); }
    public File getCachesFolder() { return cacheConfigHandler.getCachesFolder(); }
    public File getMenusFolder() { return menuConfigHandler.getMenusFolder(); }
    public boolean isLogsInConsoleEnabled() { return mainConfig.isLogsInConsole(); }
    public int getModeTimeoutSeconds() { return mainConfig.getModeTimeoutSeconds(); }
    public int getHistoryMaxEntries() { return mainConfig.getHistoryMaxEntries(); }
    public int getHistoryMaxDays() { return mainConfig.getHistoryMaxDays(); }
    public boolean isTrimHologramItemName() { return mainConfig.isTrimHologramItemName(); }

    public String reloadConfig() {
        try {
            cacheConfigHandler.stopBatchSaver();
            mainConfig.load();
            menuConfigHandler.clearCache();
            cacheConfigHandler.clearCache();
            menuConfigHandler.createDefaultMenus();
            cacheConfigHandler.preloadConfigs();
            cacheConfigHandler.startBatchSaver();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public FileConfiguration loadMenuConfig(String fileName) {
        return menuConfigHandler.loadMenuConfig(fileName);
    }

    public void executeActions(Player player, String path) {
        actionManager.executeActions(player, path);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        actionManager.executeActions(player, path, placeholders);
    }

    public FileConfiguration loadCacheConfig(String cacheName) {
        return cacheConfigHandler.loadCacheConfig(cacheName);
    }

    public void saveCacheConfig(String cacheName) {
        cacheConfigHandler.saveCacheConfig(cacheName);
    }

    public void forceSaveAllCacheConfigs() {
        cacheConfigHandler.forceSaveAllCacheConfigs();
    }

    public boolean renameCacheConfig(String oldName, String newName) {
        return cacheConfigHandler.renameCacheConfig(oldName, newName);
    }

    public void setCacheHologramText(String cacheName, String hologramText) {
        cacheConfigHandler.setCacheHologramText(cacheName, hologramText);
    }

    public void createCacheConfig(String cacheName) {
        cacheConfigHandler.createCacheConfig(cacheName);
    }

    public boolean isCacheUnbreakable(String cacheName) {
        return cacheConfigHandler.isCacheUnbreakable(cacheName);
    }

    public void setCacheUnbreakable(String cacheName, boolean unbreakable) {
        cacheConfigHandler.setCacheUnbreakable(cacheName, unbreakable);
    }

    public void deleteCacheConfig(String cacheName) {
        cacheConfigHandler.deleteCacheConfig(cacheName);
    }

    public ItemStack getKeyItem(String cacheName, FileConfiguration cacheConfig) {
        return cacheConfigHandler.getKeyItem(cacheName, cacheConfig);
    }

    public String getCacheDisplayName(String cacheName) {
        return cacheConfigHandler.getCacheDisplayName(cacheName);
    }

    public Location getCacheLocation(FileConfiguration cacheConfig) {
        return cacheConfigHandler.getCacheLocation(cacheConfig);
    }

    public void setCacheLocation(String cacheName, Location location) {
        cacheConfigHandler.setCacheLocation(cacheName, location);
    }

    public Material getCacheBlock(String cacheName) {
        return cacheConfigHandler.getCacheBlock(cacheName);
    }

    public void setCacheBlock(String cacheName, Material blockType) {
        cacheConfigHandler.setCacheBlock(cacheName, blockType);
    }

    public List<Entry<ItemStack, Integer>> getCacheLootWithChances(FileConfiguration cacheConfig) {
        return cacheConfigHandler.getCacheLootWithChances(cacheConfig);
    }

    public void setCacheHologramEnabled(String cacheName, boolean enabled) {
        cacheConfigHandler.setCacheHologramEnabled(cacheName, enabled);
    }

    public void setCacheHologramOffset(String cacheName, double x, double y, double z) {
        cacheConfigHandler.setCacheHologramOffset(cacheName, x, y, z);
    }

    public void setCacheLoot(String cacheName, List<Entry<ItemStack, Integer>> lootWithChances) {
        cacheConfigHandler.setCacheLoot(cacheName, lootWithChances);
    }

    public String getCacheAnimation(String cacheName) {
        return cacheConfigHandler.getCacheAnimation(cacheName);
    }

    public void setCacheAnimation(String cacheName, String animation) {
        cacheConfigHandler.setCacheAnimation(cacheName, animation);
    }

    public List<String> getCacheNames() {
        return cacheConfigHandler.getCacheNames();
    }

    public void setItemChance(String cacheName, int index, int chance) {
        cacheConfigHandler.setItemChance(cacheName, index, chance);
    }

    public void setKeyMaterial(String cacheName, String material) {
        cacheConfigHandler.setKeyMaterial(cacheName, material);
    }

    public void setKeyName(String cacheName, String name) {
        cacheConfigHandler.setKeyName(cacheName, name);
    }

    public void setKeyLore(String cacheName, List<String> lore) {
        cacheConfigHandler.setKeyLore(cacheName, lore);
    }

    public void setKeyCustomModelData(String cacheName, int cmd) {
        cacheConfigHandler.setKeyCustomModelData(cacheName, cmd);
    }

    public void setKeyGlow(String cacheName, boolean glow) {
        cacheConfigHandler.setKeyGlow(cacheName, glow);
    }

    public void setKeyFlags(String cacheName, List<String> flags) {
        cacheConfigHandler.setKeyFlags(cacheName, flags);
    }

    public String getKeyUuid(String cacheName) {
        return cacheConfigHandler.getKeyUuid(cacheName);
    }

    public List<String> getKeyFlags(String cacheName) {
        return cacheConfigHandler.getKeyFlags(cacheName);
    }

    public boolean isUpdateCheckerEnabled() { return getConfig().getBoolean("settings.update-checker.enabled", true); }
    public String getUpdateNotifyMode() { return getConfig().getString("settings.update-checker.notify-mode", "both").toLowerCase(); }
    public int getUpdatePeriodicIntervalHours() { return Math.max(1, getConfig().getInt("settings.update-checker.periodic-interval-hours", 6)); }
    public boolean isBStatsEnabled() { return getConfig().getBoolean("settings.bstats.enabled", true); }

    public String sanitizeCacheName(String name) {
        return cacheConfigHandler.sanitizeCacheName(name);
    }

    public String sanitizeMenuFile(String file) {
        return menuConfigHandler.sanitizeMenuFile(file);
    }
}