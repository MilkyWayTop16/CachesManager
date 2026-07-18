package org.gw.cachesmanager.caches;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CacheRegistry {
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final Map<String, Cache> cachesByLocation = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> cachesByChunk = new ConcurrentHashMap<>();

    public static String toLocationKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static String toChunkKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private void addChunkMapping(Location loc, String cacheName) {
        String key = toChunkKey(loc);
        if (key == null) return;
        cachesByChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(cacheName);
    }

    private void removeChunkMapping(Location loc, String cacheName) {
        String key = toChunkKey(loc);
        if (key == null) return;
        Set<String> names = cachesByChunk.get(key);
        if (names != null) {
            names.remove(cacheName);
            if (names.isEmpty()) {
                cachesByChunk.remove(key, names);
            }
        }
    }

    public void register(Cache cache) {
        caches.put(cache.getName(), cache);
        String key = toLocationKey(cache.getLocation());
        if (key != null) {
            cachesByLocation.put(key, cache);
            addChunkMapping(cache.getLocation(), cache.getName());
        }
    }

    public void unregister(String name) {
        Cache cache = caches.remove(name);
        if (cache != null) {
            String key = toLocationKey(cache.getLocation());
            if (key != null) {
                cachesByLocation.remove(key);
                removeChunkMapping(cache.getLocation(), name);
            }
        }
    }

    public Cache getByName(String name) {
        return caches.get(name);
    }

    public Cache getByLocation(Location location) {
        String key = toLocationKey(location);
        return key == null ? null : cachesByLocation.get(key);
    }

    public void addLocationMapping(Location loc, Cache cache) {
        if (loc != null && cache != null) {
            String key = toLocationKey(loc);
            if (key != null) {
                cachesByLocation.put(key, cache);
                addChunkMapping(loc, cache.getName());
            }
        }
    }

    public void removeLocationMapping(Location loc) {
        String key = toLocationKey(loc);
        if (key != null) {
            Cache existing = cachesByLocation.remove(key);
            if (existing != null) {
                removeChunkMapping(loc, existing.getName());
            }
        }
    }

    public Set<String> getCacheNamesInChunk(String worldName, int chunkX, int chunkZ) {
        Set<String> names = cachesByChunk.get(worldName + ":" + chunkX + ":" + chunkZ);
        return names == null ? Collections.emptySet() : new HashSet<>(names);
    }

    public Map<String, Cache> getCachesMap() {
        return Collections.unmodifiableMap(caches);
    }

    public Map<String, Cache> snapshotCachesMap() {
        return new HashMap<>(caches);
    }

    public int size() {
        return caches.size();
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(caches.keySet());
    }

    public void clear() {
        caches.clear();
        cachesByLocation.clear();
        cachesByChunk.clear();
    }
}