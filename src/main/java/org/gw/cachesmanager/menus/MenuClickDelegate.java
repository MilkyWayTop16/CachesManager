package org.gw.cachesmanager.menus;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface MenuClickDelegate {

    void delegateOpenMenu(Player player, String cacheName, String menuFile, int page);

    void delegateInitializeCachePageLoot(String cacheName);

    void delegateUpdateSingleItem(Player player, Inventory inventory, String menuFile, String cacheName, int page, int slot);

}