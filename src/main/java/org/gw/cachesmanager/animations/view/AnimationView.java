package org.gw.cachesmanager.animations.view;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.animations.Animation;

public interface AnimationView {
    void spawn(String cacheName, Location location, ItemStack item, Animation animation);
    void update(String cacheName, int ticks, Animation animation);
    void remove(String cacheName);

    default boolean isManagedEntity(Entity entity) {
        return false;
    }
}