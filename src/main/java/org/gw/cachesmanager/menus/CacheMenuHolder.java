package org.gw.cachesmanager.menus;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class CacheMenuHolder implements InventoryHolder {
    @Getter
    private final String menuFile;
    @Getter
    private final String cacheName;
    @Getter
    private int currentPage;
    @Setter
    private Inventory inventory;

    public CacheMenuHolder(String menuFile, String cacheName, int currentPage) {
        this.menuFile = menuFile;
        this.cacheName = cacheName;
        this.currentPage = currentPage;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}