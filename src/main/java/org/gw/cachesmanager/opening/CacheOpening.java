package org.gw.cachesmanager.opening;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.caches.Cache;

public interface CacheOpening {

    boolean start(Player player, Cache cache);

    void finishVisual();

    void cancel();

    boolean isRunning();

    Cache getCache();

    Player getPlayer();

    ItemStack getChosenItem();
}