package org.gw.cachesmanager.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;

public class ItemManager {
    private final CachesManager plugin;
    private final NamespacedKey cacheNameKey;
    private final NamespacedKey keyUuidKey;

    public ItemManager(CachesManager plugin) {
        this.plugin = plugin;
        this.cacheNameKey = new NamespacedKey(plugin, "cache-name");
        this.keyUuidKey = new NamespacedKey(plugin, "key-uuid");
    }

    public boolean isKey(ItemStack item, String cacheName) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String name = pdc.get(cacheNameKey, PersistentDataType.STRING);
        String uuid = pdc.get(keyUuidKey, PersistentDataType.STRING);

        if (name == null || !name.equals(cacheName) || uuid == null) return false;

        String correctUuid = plugin.getConfigManager().getKeyUuid(cacheName);
        return uuid.equals(correctUuid);
    }

    public void giveKey(Player player, String cacheName, int amount) {
        FileConfiguration cacheConfig = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (cacheConfig == null) return;

        ItemStack key = plugin.getConfigManager().getKeyItem(cacheName, cacheConfig);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(cacheNameKey, PersistentDataType.STRING, cacheName);

            String uuid = plugin.getConfigManager().getKeyUuid(cacheName);
            pdc.set(keyUuidKey, PersistentDataType.STRING, uuid);

            key.setItemMeta(meta);
        }
        key.setAmount(amount);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), key);
        } else {
            player.getInventory().addItem(key);
        }
    }

    public boolean isAnyKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(cacheNameKey, PersistentDataType.STRING);
    }
}