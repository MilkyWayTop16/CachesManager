package org.gw.cachesmanager.menus;

import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CacheMenuHolder implements InventoryHolder {
    private final String menuFile;
    private final String cacheName;
    private int currentPage;
    @Setter
    private Inventory inventory;

    public CacheMenuHolder(String menuFile, String cacheName, int currentPage) {
        this.menuFile = menuFile;
        this.cacheName = cacheName;
        this.currentPage = currentPage;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String getMenuFile() {
        return menuFile;
    }

    public String getCacheName() {
        return cacheName;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
}