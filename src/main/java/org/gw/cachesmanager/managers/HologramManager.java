package org.gw.cachesmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.platform.DecentHologramsPlatform;
import org.gw.cachesmanager.animations.platform.FancyHologramsPlatform;
import org.gw.cachesmanager.animations.platform.HologramPlatform;
import org.gw.cachesmanager.animations.platform.ModernMinecraftPlatform;

import java.util.ArrayList;
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
        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            this.platform = new DecentHologramsPlatform();
        } else if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            this.platform = new FancyHologramsPlatform();
        } else {
            this.platform = new ModernMinecraftPlatform(plugin);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("DecentHolograms") || name.equals("FancyHolograms")) {
            selectPlatform();
            new BukkitRunnable() {
                @Override
                public void run() {
                    recreateAllHolograms();
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    private void recreateAllHolograms() {
        pendingHolograms.forEach((cacheName, pending) -> createHologramNow(cacheName, pending.location, pending.text));
        pendingHolograms.clear();
    }

    public void createHologram(String cacheName, Location location, String text) {
        if (location == null || location.getWorld() == null) return;

        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && !cache.isHologramEnabled()) {
            removeCacheHologram(cacheName);
            return;
        }

        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            pendingHolograms.put(cacheName, new PendingHologram(location, text));
            return;
        }

        createHologramNow(cacheName, location, text);
    }

    private void createHologramNow(String cacheName, Location location, String text) {
        CacheManager.Cache c = plugin.getCacheManager().getCache(cacheName);
        if (c != null && c.isInUse()) {
            return;
        }

        double offsetX = 0.0, offsetY = 0.5, offsetZ = 0.0;
        if (c != null) {
            offsetX = c.getHologramOffsetX();
            offsetY = c.getHologramOffsetY();
            offsetZ = c.getHologramOffsetZ();
        }

        Location holoLoc = new Location(
                location.getWorld(),
                location.getBlockX() + 0.5 + offsetX,
                location.getBlockY() + 1.0 + offsetY,
                location.getBlockZ() + 0.5 + offsetZ
        );

        String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");

        try {
            platform.createHologram(safeId, holoLoc, text);
        } catch (Exception e) {
            plugin.log("Ошибка создания голограммы " + cacheName + ": " + e.getMessage());
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
                createHologramNow(cacheName, loc, pending.text);
                return true;
            }
            return false;
        });
    }

    public void updateHologram(String cacheName, String newText) {
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && cache.getLocation() != null) {
            if (cache.isInUse()) {
                return;
            }
            String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
            platform.updateHologram(safeId, newText);
        }
    }

    public void removeCacheHologram(String cacheName) {
        pendingHolograms.remove(cacheName);
        String safeId = "cm_" + cacheName.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
        try {
            platform.deleteHologram(safeId);
        } catch (Exception ignored) {}
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
            new ArrayList<>(plugin.getCacheManager().getCaches().keySet())
                    .forEach(this::removeCacheHologram);
        }
    }

    private static class PendingHologram {
        final Location location;
        final String text;
        PendingHologram(Location location, String text) {
            this.location = location.clone();
            this.text = text;
        }
    }
}