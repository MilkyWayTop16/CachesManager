package org.gw.cachesmanager.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager implements Listener {

    private final CachesManager plugin;
    private final boolean useDecentHolograms;
    private final Map<String, Hologram> decentHolograms = new ConcurrentHashMap<>();
    private final Map<String, PendingHologram> pendingHolograms = new ConcurrentHashMap<>();

    public HologramManager(CachesManager plugin) {
        this.plugin = plugin;
        this.useDecentHolograms = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean isDecentHologramsReady() {
        if (!useDecentHolograms || !Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            return false;
        }
        try {
            return DecentHologramsAPI.get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void createHologram(String cacheName, Location location, String text) {
        if (location == null || location.getWorld() == null) return;

        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && !cache.isHologramEnabled()) {
            removeCacheHologram(cacheName);
            return;
        }

        if (!isDecentHologramsReady() ||
                !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            pendingHolograms.put(cacheName, new PendingHologram(location, text));
            return;
        }

        createHologramNow(cacheName, location, text);
    }

    private void createHologramNow(String cacheName, Location location, String text) {
        double offsetX = 0.0, offsetY = 0.7, offsetZ = 0.0;
        CacheManager.Cache c = plugin.getCacheManager().getCache(cacheName);
        if (c != null) {
            offsetX = c.getHologramOffsetX();
            offsetY = c.getHologramOffsetY();
            offsetZ = c.getHologramOffsetZ();
        }

        Location holoLoc = location.clone().add(0.5 + offsetX, 1.25 + offsetY, 0.5 + offsetZ);
        String safeId = "caches_" + cacheName.toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_-]", "_");
        List<String> lines = Arrays.asList(HexColors.translate(text).split("\n"));

        try {
            Hologram existing = DHAPI.getHologram(safeId);
            if (existing != null) {
                DHAPI.setHologramLines(existing, lines);
                DHAPI.moveHologram(existing, holoLoc);
                decentHolograms.put(cacheName, existing);
                plugin.log("Голограмма обновлена для тайника: &#ffff00" + cacheName);
                return;
            }

            Hologram hologram = DHAPI.createHologram(safeId, holoLoc, lines);
            if (hologram != null) {
                decentHolograms.put(cacheName, hologram);
                plugin.log("Голограмма создана для тайника: &#ffff00" + cacheName);
            }
        } catch (Exception e) {
            plugin.log("&#FF5D00Ошибка создания голограммы " + cacheName + ": " + e.getMessage());
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

                if (isDecentHologramsReady()) {
                    createHologramNow(cacheName, loc, pending.text);
                }
                return true;
            }
            return false;
        });
    }

    public void updateHologram(String cacheName, String newText) {
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache != null && cache.getLocation() != null) {
            createHologram(cacheName, cache.getLocation(), newText);
        }
    }

    public void removeCacheHologram(String cacheName) {
        pendingHolograms.remove(cacheName);

        if (!isDecentHologramsReady()) {
            decentHolograms.remove(cacheName);
            return;
        }

        String safeId = "caches_" + cacheName.toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_-]", "_");
        try {
            DHAPI.removeHologram(safeId);
        } catch (Exception ignored) {}
        decentHolograms.remove(cacheName);
    }

    public void removeHologram(String cacheName) {
        removeCacheHologram(cacheName);
        plugin.getAnimationsManager().removeAnimationArtifacts(cacheName);
    }

    public void clearAllHolograms() {
        pendingHolograms.clear();
        if (plugin.getCacheManager() != null) {
            new ArrayList<>(plugin.getCacheManager().getCaches().keySet())
                    .forEach(this::removeCacheHologram);
        }
        decentHolograms.clear();
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