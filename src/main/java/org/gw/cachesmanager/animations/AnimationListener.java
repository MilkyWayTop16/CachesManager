package org.gw.cachesmanager.animations;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.view.LegacyAnimationView;
import org.gw.cachesmanager.managers.CacheManager;

import java.util.HashMap;
import java.util.Map;

public class AnimationListener implements Listener {
    private final CachesManager plugin;
    private final AnimationExecutor executor;

    public AnimationListener(CachesManager plugin, AnimationExecutor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!(executor.getAnimationView() instanceof LegacyAnimationView legacyView)) return;

        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();

        for (Map.Entry<String, Item> entry : new HashMap<>(legacyView.getPhantomItems()).entrySet()) {
            String cacheName = entry.getKey();
            Item phantomItem = entry.getValue();

            if (phantomItem == null || phantomItem.isDead() || phantomItem.getWorld() == null) continue;
            if (!phantomItem.getWorld().equals(world)) continue;

            try {
                Location loc = phantomItem.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        legacyView.remove(cacheName);
                    }
                }
            } catch (Exception ignored) {
                legacyView.remove(cacheName);
            }
        }

        for (Map.Entry<String, ArmorStand> entry : new HashMap<>(legacyView.getItemHolograms()).entrySet()) {
            String cacheName = entry.getKey();
            ArmorStand hologram = entry.getValue();

            if (hologram == null || hologram.isDead() || hologram.getWorld() == null) continue;
            if (!hologram.getWorld().equals(world)) continue;

            try {
                Location loc = hologram.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        legacyView.remove(cacheName);
                    }
                }
            } catch (Exception ignored) {
                legacyView.remove(cacheName);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        executor.forceFinishAnimationForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        executor.forceFinishAnimationForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        executor.forceFinishAnimationForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        executor.givePendingLootToPlayer(event.getPlayer());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (executor.getAnimationView() instanceof LegacyAnimationView legacyView) {
                    Location center = event.getPlayer().getLocation();
                    for (Entity entity : center.getWorld().getNearbyEntities(center, 50, 50, 50)) {
                        if (entity.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "ghost"), org.bukkit.persistence.PersistentDataType.STRING)) {
                            entity.remove();
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 10L);
    }
}