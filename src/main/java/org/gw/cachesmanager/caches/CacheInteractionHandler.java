package org.gw.cachesmanager.caches;

import com.google.common.cache.CacheBuilder;
import org.bukkit.entity.Player;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.opening.CacheOpening;
import org.gw.cachesmanager.opening.StandardCacheOpening;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheInteractionHandler {
    private final CachesManager plugin;
    private final com.google.common.cache.Cache<UUID, Boolean> openCooldown = CacheBuilder.newBuilder()
            .expireAfterWrite(500, TimeUnit.MILLISECONDS)
            .build();

    public CacheInteractionHandler(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean open(Player player, Cache cache) {
        if (openCooldown.getIfPresent(player.getUniqueId()) != null) {
            return false;
        }
        openCooldown.put(player.getUniqueId(), true);

        CacheOpening opening = new StandardCacheOpening(plugin, cache);
        return opening.start(player, cache);
    }
}