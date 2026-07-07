package org.gw.cachesmanager.menus;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.utils.HexColors;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuActionHandler {
    private final CachesManager plugin;

    public MenuActionHandler(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void handleToggleHologram(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newState = !cache.isHologramEnabled();
        plugin.getCacheManager().setCacheHologramEnabled(cache, newState);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        plugin.getConfigManager().executeActions(p, newState ? "hologram.toggle-enabled" : "hologram.toggle-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleToggleUnbreakable(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newState = !cache.isUnbreakable();
        plugin.getCacheManager().setCacheUnbreakable(cache, newState);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        plugin.getConfigManager().executeActions(p, newState ? "unbreakable-enabled" : "unbreakable-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleNextAnimation(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        String next = plugin.getAnimationsManager().getNextAnimation(cache.getAnimation());
        if (next == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        plugin.getCacheManager().setCacheAnimation(cache, next);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        ph.put("animation", plugin.getAnimationsManager().getAnimations().get(next).getName());
        plugin.getConfigManager().executeActions(p, "cache.animation-changed", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handlePreviousAnimation(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        String prev = plugin.getAnimationsManager().getPreviousAnimation(cache.getAnimation());
        if (prev == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        plugin.getCacheManager().setCacheAnimation(cache, prev);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        ph.put("animation", plugin.getAnimationsManager().getAnimations().get(prev).getName());
        plugin.getConfigManager().executeActions(p, "cache.animation-changed", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleChanceChange(Player p, int index, boolean increase, int delta, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null || index < 0 || index >= cache.getLootWithChances().size()) return;
        List<Map.Entry<ItemStack, Integer>> loot = cache.getLootWithChances();
        int oldChance = loot.get(index).getValue();
        int newChance = Math.max(0, Math.min(100, oldChance + (increase ? delta : -delta)));
        plugin.getConfigManager().setItemChance(cacheName, index, newChance);
        loot.set(index, new AbstractMap.SimpleEntry<>(loot.get(index).getKey(), newChance));
        plugin.getCacheManager().setCacheLootWithChances(cache, loot);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        ph.put("chance", String.valueOf(newChance));
        plugin.getConfigManager().executeActions(p, "cache.chance-updated", ph);
    }

    public void handleToggleKeyGlow(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newGlow = !cache.isKeyGlowEnabled();
        plugin.getCacheManager().setCacheKeyGlow(cache, newGlow);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        plugin.getConfigManager().executeActions(p, newGlow ? "key.glow-enabled" : "key.glow-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleResetKeyToDefault(Player p, CacheMenuHolder holder) {
        String cacheName = holder.getCacheName();
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        plugin.getCacheManager().setCacheKeyMaterial(cache, "TRIPWIRE_HOOK");
        plugin.getCacheManager().setCacheKeyName(cache, "&eКлюч от тайника " + cacheName);
        plugin.getCacheManager().setCacheKeyLore(cache, Arrays.asList("&7Для тайника: " + cacheName, "&7Одноразовый предмет"));
        plugin.getCacheManager().setCacheKeyCustomModelData(cache, 0);
        plugin.getCacheManager().setCacheKeyGlow(cache, false);
        plugin.getCacheManager().setCacheKeyFlags(cache, Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES"));
        plugin.getConfigManager().saveCacheConfig(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getName());
        plugin.getConfigManager().executeActions(p, "key.reset-to-default", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }
}