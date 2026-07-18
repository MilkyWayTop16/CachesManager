package org.gw.cachesmanager.animations;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.CacheKeys;

public class AnimationListener implements Listener {
    private final CachesManager plugin;
    private final AnimationExecutor executor;

    public AnimationListener(CachesManager plugin, AnimationExecutor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        executor.handleChunkUnload(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeftAnimation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handlePlayerLeftAnimation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        handlePlayerLeftAnimation(event.getPlayer());
    }

    private void handlePlayerLeftAnimation(org.bukkit.entity.Player player) {
        if (executor.isPlayerInAnimation(player)) {
            executor.handlePlayerLeftAnimationEvent(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Firework firework)) {
            return;
        }
        if (firework.getPersistentDataContainer().has(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        executor.givePendingLootToPlayer(event.getPlayer());
        executor.tryReattachOrphanedAnimation(event.getPlayer());

        new BukkitRunnable() {
            @Override
            public void run() {
                executor.cleanupGhostEntities(event.getPlayer());
            }
        }.runTaskLater(plugin, 10L);
    }
}