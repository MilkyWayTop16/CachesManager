package org.gw.cachesmanager.caches;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheRegistry {
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final Map<String, Cache> cachesByLocation = new ConcurrentHashMap<>();

    public static String toLocationKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void register(Cache cache) {
        caches.put(cache.getName(), cache);
        String key = toLocationKey(cache.getLocation());
        if (key != null) {
            cachesByLocation.put(key, cache);
        }
    }

    public void unregister(String name) {
        Cache cache = caches.remove(name);
        if (cache != null) {
            String key = toLocationKey(cache.getLocation());
            if (key != null) {
                cachesByLocation.remove(key);
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
            }
        }
    }

    public void removeLocationMapping(Location loc) {
        String key = toLocationKey(loc);
        if (key != null) {
            cachesByLocation.remove(key);
        }
    }

    public Map<String, Cache> getCachesMap() {
        return new ConcurrentHashMap<>(caches);
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(caches.keySet());
    }

    public void clear() {
        caches.clear();
        cachesByLocation.clear();
    }
}