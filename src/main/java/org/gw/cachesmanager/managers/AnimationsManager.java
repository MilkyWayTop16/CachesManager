package org.gw.cachesmanager.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.*;
import org.gw.cachesmanager.animations.platform.*;
import org.gw.cachesmanager.animations.view.*;
import org.gw.cachesmanager.opening.CacheOpening;

import java.util.Map;

public class AnimationsManager implements Listener {
    private final CachesManager plugin;
    private final AnimationRegistry registry;
    private final AnimationExecutor executor;
    private final AnimationListener listener;

    public AnimationsManager(CachesManager plugin, HologramManager hologramManager) {
        this.plugin = plugin;

        AnimationEngineConfigurator.PacketItemMetadataSender packetSender = AnimationEngineConfigurator.selectPacketPlatform();
        AnimationView animationView = AnimationEngineConfigurator.selectAnimationView(plugin, packetSender);

        this.registry = new AnimationRegistry(plugin);
        this.executor = new AnimationExecutor(plugin, registry, animationView);
        this.listener = new AnimationListener(plugin, executor);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public Map<String, Animation> getAnimations() {
        return registry.getAnimations();
    }

    public void playAnimation(Player player, String animationName, Location location, ItemStack item, String cacheName, CacheOpening opening) {
        Animation animation = registry.getAnimations().get(animationName);
        if (animation != null) {
            executor.startAnimation(cacheName, player, item, location, animation, opening);
        }
    }

    public String getNextAnimation(String currentAnimation) {
        return registry.getNext(currentAnimation);
    }

    public String getPreviousAnimation(String currentAnimation) {
        return registry.getPrevious(currentAnimation);
    }

    public void removeAnimationArtifacts(String cacheName) {
        executor.removeAnimationArtifacts(cacheName);
    }

    public void clearAllAnimations() {
        executor.clearAllAnimations();
    }

    public boolean hasActiveAnimation(String cacheName) {
        return executor.hasActiveAnimationForCache(cacheName);
    }

    public void reloadAnimations(boolean forceClearActiveAnimations) {
        if (forceClearActiveAnimations) {
            executor.clearAllAnimations();
        }
        registry.load();
    }

}
