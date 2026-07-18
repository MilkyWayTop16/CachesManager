package org.gw.cachesmanager.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.MaterialCompat;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ItemManager {
    public static final int MAX_KEYS_PER_GIVE = 2304;

    private final CachesManager plugin;

    public ItemManager(CachesManager plugin) {
        this.plugin = plugin;
    }

    public boolean isKey(ItemStack item, String cacheName) {
        if (!isValidItem(item) || cacheName == null || cacheName.isEmpty()) {
            return false;
        }

        String pdcName = getPdcString(item, CacheKeys.CACHE_NAME);
        String pdcUuid = getPdcString(item, CacheKeys.KEY_UUID);
        String correctUuid = plugin.getConfigManager().getKeyUuid(cacheName);

        if (pdcName != null && !pdcName.isEmpty()) {
            if (!pdcName.equals(cacheName)) {
                return false;
            }
            if (pdcUuid != null && !pdcUuid.isEmpty()) {
                return correctUuid != null && !correctUuid.isEmpty() && pdcUuid.equals(correctUuid);
            }
            return true;
        }

        return matchesKeyAppearance(item, cacheName);
    }

    public boolean isAnyKey(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }
        String pdcName = getPdcString(item, CacheKeys.CACHE_NAME);
        return pdcName != null && !pdcName.isEmpty();
    }

    public void giveKey(Player player, String cacheName, int amount) {
        if (player == null || cacheName == null || cacheName.isEmpty()) {
            return;
        }

        FileConfiguration cacheConfig = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (cacheConfig == null) {
            return;
        }

        int remaining = Math.min(Math.max(1, amount), MAX_KEYS_PER_GIVE);
        while (remaining > 0) {
            ItemStack key = plugin.getConfigManager().getKeyItem(cacheName, cacheConfig);
            stampKeyData(key, cacheName);

            int stackSize = Math.max(1, key.getMaxStackSize());
            int give = Math.min(remaining, stackSize);
            key.setAmount(give);
            remaining -= give;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(key);
            if (!leftover.isEmpty() && player.getWorld() != null) {
                for (ItemStack drop : leftover.values()) {
                    if (drop != null && drop.getType() != Material.AIR && drop.getAmount() > 0) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }
    }

    public boolean consumeKey(Player player, EquipmentSlot hand, String cacheName) {
        if (player == null || hand == null || cacheName == null || cacheName.isEmpty()) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? inventory.getItemInOffHand()
                : inventory.getItemInMainHand();

        if (!isKey(stack, cacheName)) {
            return false;
        }

        stampKeyData(stack, cacheName);

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            if (hand == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(stack);
            } else {
                inventory.setItemInMainHand(stack);
            }
        } else if (hand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(null);
        } else {
            inventory.setItemInMainHand(null);
        }
        return true;
    }

    public void stampKeyData(ItemStack item, String cacheName) {
        if (!isValidItem(item) || cacheName == null || cacheName.isEmpty()) {
            return;
        }

        String uuid = plugin.getConfigManager().getKeyUuid(cacheName);
        if (uuid == null || uuid.isEmpty()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CacheKeys.CACHE_NAME.getNamespacedKey(), PersistentDataType.STRING, cacheName);
        pdc.set(CacheKeys.KEY_UUID.getNamespacedKey(), PersistentDataType.STRING, uuid);
        item.setItemMeta(meta);
    }

    public boolean matchesKeyAppearance(ItemStack item, String cacheName) {
        if (!isValidItem(item) || cacheName == null || cacheName.isEmpty()) {
            return false;
        }

        FileConfiguration cfg = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (cfg == null) {
            return false;
        }

        Material expected = MaterialCompat.match(cfg.getString("key.material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK);
        if (item.getType() != expected) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        int expectedCmd = cfg.getInt("key.custom-model-data", 0);
        int actualCmd = 0;
        try {
            if (meta.hasCustomModelData()) {
                actualCmd = meta.getCustomModelData();
            }
        } catch (Throwable ignored) {
        }
        if (expectedCmd != actualCmd) {
            return false;
        }

        String expectedName = HexColors.translate(
                cfg.getString("key.name", "&eКлюч от тайника " + cacheName).replace("{name-cache}", cacheName));
        String actualName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (!Objects.equals(expectedName, actualName)) {
            return false;
        }

        List<String> expectedLore = HexColors.translate(
                cfg.getStringList("key.lore").stream().map(s -> s.replace("{name-cache}", cacheName)).toList());
        List<String> actualLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
        return expectedLore.equals(actualLore);
    }

    private boolean isValidItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private String getPdcString(ItemStack item, CacheKeys key) {
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key.getNamespacedKey(), PersistentDataType.STRING);
    }
}
