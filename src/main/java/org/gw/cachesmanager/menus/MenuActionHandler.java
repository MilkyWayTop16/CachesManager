package org.gw.cachesmanager.menus;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class MenuActionHandler {
    private final CachesManager plugin;

    public MenuActionHandler(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void handleToggleHologram(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newState = !cache.isHologramEnabled();
        cache.setHologramEnabled(newState);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        plugin.getConfigManager().executeActions(p, newState ? "hologram.toggle-enabled" : "hologram.toggle-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleToggleUnbreakable(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newState = !cache.isUnbreakable();
        cache.setUnbreakable(newState);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        plugin.getConfigManager().executeActions(p, newState ? "unbreakable-enabled" : "unbreakable-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleNextAnimation(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        String next = plugin.getAnimationsManager().getNextAnimation(cache.getAnimation());
        if (next == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        cache.setAnimation(next);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("animation", plugin.getAnimationsManager().getAnimations().get(next).getName());
        plugin.getConfigManager().executeActions(p, "cache.animation-changed", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handlePreviousAnimation(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        String prev = plugin.getAnimationsManager().getPreviousAnimation(cache.getAnimation());
        if (prev == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        cache.setAnimation(prev);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("animation", plugin.getAnimationsManager().getAnimations().get(prev).getName());
        plugin.getConfigManager().executeActions(p, "cache.animation-changed", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleChanceChange(Player p, int index, boolean increase, int delta, CacheMenuHolder holder,
                                   java.util.function.BiConsumer<String, Integer> initializer, java.util.function.Consumer<Integer> individualUpdater) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null || index < 0 || index >= cache.getLootWithChances().size()) return;
        int oldChance = cache.getLootWithChances().get(index).getValue();
        int newChance = Math.max(0, Math.min(100, oldChance + (increase ? delta : -delta)));
        plugin.getConfigManager().setItemChance(cacheName, index, newChance);
        cache.getLootWithChances().set(index, new AbstractMap.SimpleEntry<>(cache.getLootWithChances().get(index).getKey(), newChance));
        initializer.accept(cacheName, index);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("chance", String.valueOf(newChance));
        plugin.getConfigManager().executeActions(p, "cache.chance-updated", ph);
        individualUpdater.accept(index);
    }

    public void handleToggleKeyGlow(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        boolean newGlow = !cache.isKeyGlowEnabled();
        cache.setKeyGlow(newGlow);
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        plugin.getConfigManager().executeActions(p, newGlow ? "key.glow-enabled" : "key.glow-disabled", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }

    public void handleResetKeyToDefault(Player p, CacheMenuHolder holder, Runnable invalidator) {
        String cacheName = holder.getCacheName();
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        cache.resetKeyToDefault();
        plugin.getConfigManager().saveCacheConfig(cacheName);
        invalidator.run();
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        plugin.getConfigManager().executeActions(p, "key.reset-to-default", ph);
        plugin.getMenuManager().updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage());
    }
}