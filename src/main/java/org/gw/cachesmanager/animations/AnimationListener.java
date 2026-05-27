package org.gw.cachesmanager.animations;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;

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
                executor.cleanupGhostEntities(event.getPlayer());
            }
        }.runTaskLater(plugin, 10L);
    }
}