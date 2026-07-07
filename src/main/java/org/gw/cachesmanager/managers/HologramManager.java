package org.gw.cachesmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.animations.platform.DecentHologramsPlatform;
import org.gw.cachesmanager.animations.platform.FancyHologramsPlatform;
import org.gw.cachesmanager.animations.platform.HologramPlatform;
import org.gw.cachesmanager.animations.platform.LegacyMinecraftPlatform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager implements Listener {

    private final CachesManager plugin;
    private HologramPlatform platform;
    private final Map<String, PendingHologram> pendingHolograms = new ConcurrentHashMap<>();

    public HologramManager(CachesManager plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        selectPlatform();
    }

    private void selectPlatform() {
        selectPlatform(null);
    }

    private void selectPlatform(String disablingPlugin) {
        HologramPlatform oldPlatform = this.platform;
        String oldName = getCurrentPlatformName();

        HologramPlatform newPlatform;

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms") && !"DecentHolograms".equals(disablingPlugin)) {
            newPlatform = new DecentHologramsPlatform(plugin);
        } else if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms") && !"FancyHolograms".equals(disablingPlugin)) {
            newPlatform = new FancyHologramsPlatform(plugin);
        } else {
            // Only try modern platform if the server supports TextDisplay (1.19.4+)
            boolean supportsModern = false;
            try {
                Class.forName("org.bukkit.entity.TextDisplay");
                supportsModern = true;
            } catch (ClassNotFoundException ignored) {}

            if (supportsModern) {
                try {
                    Class<?> modernClass = Class.forName("org.gw.cachesmanager.animations.platform.ModernMinecraftPlatform");
                    newPlatform = (HologramPlatform) modernClass.getConstructor(CachesManager.class).newInstance(plugin);
                } catch (Throwable t) {
                    plugin.log("Встроенная платформа голограмм недоступна, голограммы могут не работать.");
                    newPlatform = null;
                }
            } else {
                newPlatform = new LegacyMinecraftPlatform(plugin);
            }
        }

        this.platform = newPlatform;

        String newName = getCurrentPlatformName();
        if (oldPlatform != null && !newName.equals(oldName)) {
            plugin.log("Смена платформы голограмм: " + oldName + " → " + newName);
        }
    }

    private String getCurrentPlatformName() {
        if (platform == null) return "None";
        String className = platform.getClass().getName();
        if (className.endsWith("DecentHologramsPlatform")) return "DecentHolograms";
        if (className.endsWith("FancyHologramsPlatform")) return "FancyHolograms";
        if (className.endsWith("ModernMinecraftPlatform")) return "Built-in";
        if (className.endsWith("LegacyMinecraftPlatform")) return "Built-in";
        return platform.getClass().getSimpleName();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DecentHolograms") || name.equals("FancyHolograms")) {
            clearAllHolograms();

            selectPlatform();

            new BukkitRunnable() {
                @Override
                public void run() {
                    recreateAllActiveHolograms();
                }
            }.runTaskLater(plugin, 25L);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DecentHolograms") || name.equals("FancyHolograms")) {
            clearAllHolograms();

            selectPlatform(name);

            new BukkitRunnable() {
                @Override
                public void run() {
                    recreateAllActiveHolograms();
                }
            }.runTaskLater(plugin, 10L);
        }
    }

    private void recreateAllActiveHolograms() {
        if (plugin.getCacheManager() == null) {
            pendingHolograms.clear();
            return;
        }

        pendingHolograms.forEach((cacheName, pending) -> createHologramNow(cacheName, pending.location, pending.lines));
        pendingHolograms.clear();

        for (String cacheName : plugin.getCacheManager().getCacheNames()) {
            Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null && cache.getLocation() != null && cache.isHologramEnabled() && !cache.isInUse()) {
                createHologram(cacheName, cache.getLocation(), cache.getHologramLines());
            }
        }
    }

    public void createHologram(String cacheName, Location location, String text) {
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : Arrays.asList(text.split("\n"));
        createHologram(cacheName, location, lines);
    }

    public void createHologram(String cacheName, Location location, List<String> lines) {
        if (location == null || location.getWorld() == null) return;

        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && !cache.isHologramEnabled()) {
            removeCacheHologram(cacheName);
            return;
        }

        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            pendingHolograms.put(cacheName, new PendingHologram(location, lines));
            return;
        }

        createHologramNow(cacheName, location, lines);
    }

    private void createHologramNow(String cacheName, Location location, List<String> lines) {
        Cache c = plugin.getCacheManager().getCache(cacheName);
        if (c != null && c.isInUse()) {
            return;
        }

        double offsetX = 0.0, offsetY = 0.5, offsetZ = 0.0;
        if (c != null) {
            offsetX = c.getHologramOffsetX();
            offsetY = c.getHologramOffsetY();
            offsetZ = c.getHologramOffsetZ();
        }

        int lineCount = (lines == null) ? 0 : lines.size();
        double extraY = lineCount > 0 ? (lineCount - 1) * 0.28 : 0.0;

        Location holoLoc = new Location(
                location.getWorld(),
                location.getBlockX() + 0.5 + offsetX,
                location.getBlockY() + 1.0 + offsetY + extraY,
                location.getBlockZ() + 0.5 + offsetZ
        );

        String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");

        try {
            platform.createHologram(safeId, holoLoc, lines);
        } catch (Throwable t) {
            plugin.error("Критический сбой создания голограммы для тайника &#FB8808" + cacheName + " &f(Ошибка: &#FB8808" + t.getMessage() + "&f)...");
            t.printStackTrace();
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        pendingHolograms.entrySet().removeIf(entry -> {
            String cacheName = entry.getKey();
            PendingHologram pending = entry.getValue();
            Location loc = pending.location;
            if (loc.getWorld().equals(event.getWorld()) &&
                    loc.getBlockX() >> 4 == event.getChunk().getX() &&
                    loc.getBlockZ() >> 4 == event.getChunk().getZ()) {
                createHologramNow(cacheName, loc, pending.lines);
                return true;
            }
            return false;
        });
    }

    public void updateHologram(String cacheName, String newText) {
        List<String> lines = (newText == null || newText.isEmpty()) ? new ArrayList<>() : Arrays.asList(newText.split("\n"));
        updateHologram(cacheName, lines);
    }

    public void updateHologram(String cacheName, List<String> lines) {
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && cache.getLocation() != null) {
            if (cache.isInUse()) {
                return;
            }
            String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
            try {
                platform.updateHologram(safeId, lines);
            } catch (Throwable t) {
                plugin.error("Ошибка обновления голограммы для тайника &#FB8808" + cacheName + " &f(Ошибка: &#FB8808" + t.getMessage() + "&f)...");
                t.printStackTrace();
            }
        }
    }

    public void removeCacheHologram(String cacheName) {
        pendingHolograms.remove(cacheName);
        String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
        try {
            platform.deleteHologram(safeId);
        } catch (Throwable t) {
            plugin.error("Ошибка при удалении голограммы тайника &#FB8808" + cacheName + " &f(Ошибка: &#FB8808" + t.getMessage() + "&f)");
        }
    }

    public void removeHologram(String cacheName) {
        removeCacheHologram(cacheName);
        if (plugin.getAnimationsManager() != null) {
            plugin.getAnimationsManager().removeAnimationArtifacts(cacheName);
        }
    }

    public void clearAllHolograms() {
        pendingHolograms.clear();

        if (plugin.getCacheManager() != null) {
            for (String cacheName : new ArrayList<>(plugin.getCacheManager().getCaches().keySet())) {
                removeCacheHologram(cacheName);
            }
        }

        if (platform != null && 
            (platform.getClass().getName().endsWith("ModernMinecraftPlatform") || 
             platform.getClass().getName().endsWith("LegacyMinecraftPlatform"))) {
            forceCleanupModernHologramEntities();
        }
    }
    
    private void forceCleanupModernHologramEntities() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int removed = 0;
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        try {
                            boolean isArmorStand = entity instanceof org.bukkit.entity.ArmorStand;
                            boolean isTextDisplay = entity.getClass().getName().contains("TextDisplay");
                            if (isArmorStand || isTextDisplay) {
                                var pdc = entity.getPersistentDataContainer();
                                if (pdc.has(org.gw.cachesmanager.utils.CacheKeys.HOLOGRAM_ID.getNamespacedKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                                    String id = pdc.get(org.gw.cachesmanager.utils.CacheKeys.HOLOGRAM_ID.getNamespacedKey(), org.bukkit.persistence.PersistentDataType.STRING);
                                    if (id != null && id.startsWith("cm_")) {
                                        entity.remove();
                                        removed++;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (removed > 0) {
                    plugin.log("Принудительно удалено " + removed + " голограмм встроенной платформы");
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    public void cleanupLegacyHologramsFromV12() {
        if (plugin.getCacheManager() == null) return;

        List<String> names = plugin.getCacheManager().getCacheNames();
        if (names.isEmpty()) return;

        plugin.console("&#ffff00◆ CachesManager &f| Очистка старых голограмм от версии 1.2...");

        for (String name : names) {
            String sanitized = name.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");

            safeDeleteLegacyId("caches_" + name.hashCode());
            safeDeleteLegacyId("caches_" + sanitized);
            safeDeleteLegacyId("caches_" + name);
            safeDeleteLegacyId(name);
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int removed = 0;
                for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        if (entity instanceof org.bukkit.entity.ArmorStand || entity instanceof org.bukkit.entity.TextDisplay) {
                            var pdc = entity.getPersistentDataContainer();
                            if (pdc.has(org.gw.cachesmanager.utils.CacheKeys.HOLOGRAM_ID.getNamespacedKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                                String id = pdc.get(org.gw.cachesmanager.utils.CacheKeys.HOLOGRAM_ID.getNamespacedKey(), org.bukkit.persistence.PersistentDataType.STRING);
                                if (id != null && id.startsWith("caches_")) {
                                    entity.remove();
                                    removed++;
                                }
                            }
                        }
                    }
                }
                if (removed > 0) {
                    plugin.console("&#ffff00◆ CachesManager &f| Удалено " + removed + " устаревших голограмм от предыдущей версии");
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    private void safeDeleteLegacyId(String id) {
        try {
            platform.deleteHologram(id);
        } catch (Throwable ignored) {
        }
    }

    private static class PendingHologram {
        final Location location;
        final List<String> lines;
        PendingHologram(Location location, List<String> lines) {
            this.location = location.clone();
            this.lines = (lines != null) ? new ArrayList<>(lines) : new ArrayList<>();
        }
    }
}