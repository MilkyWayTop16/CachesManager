package org.gw.cachesmanager.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemManager {
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

        if (pdcName != null
                && pdcName.equals(cacheName)
                && pdcUuid != null
                && correctUuid != null
                && pdcUuid.equals(correctUuid)) {
            return true;
        }

        if (pdcName != null
                && pdcName.equals(cacheName)
                && pdcUuid != null
                && !pdcUuid.isEmpty()
                && correctUuid != null
                && !pdcUuid.equals(correctUuid)) {
            return false;
        }

        if (!matchesKeyAppearance(item, cacheName)) {
            return false;
        }

        stampKeyData(item, cacheName);
        return true;
    }

    public boolean isAnyKey(ItemStack item) {
        if (!isValidItem(item)) {
            return false;
        }

        String pdcName = getPdcString(item, CacheKeys.CACHE_NAME);
        if (pdcName != null && !pdcName.isEmpty()) {
            String pdcUuid = getPdcString(item, CacheKeys.KEY_UUID);
            String correctUuid = plugin.getConfigManager().getKeyUuid(pdcName);
            if (pdcUuid != null && correctUuid != null && pdcUuid.equals(correctUuid)) {
                return true;
            }
            if (pdcUuid != null && !pdcUuid.isEmpty() && correctUuid != null && !pdcUuid.equals(correctUuid)) {
                return true;
            }
            if (matchesKeyAppearance(item, pdcName)) {
                stampKeyData(item, pdcName);
                return true;
            }
            return true;
        }

        if (plugin.getCacheManager() == null) {
            return false;
        }

        for (String cacheName : plugin.getCacheManager().getCacheNames()) {
            if (matchesKeyAppearance(item, cacheName)) {
                stampKeyData(item, cacheName);
                return true;
            }
        }

        return false;
    }

    public void giveKey(Player player, String cacheName, int amount) {
        FileConfiguration cacheConfig = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (cacheConfig == null) return;

        ItemStack key = plugin.getConfigManager().getKeyItem(cacheName, cacheConfig);
        stampKeyData(key, cacheName);
        key.setAmount(Math.max(1, amount));

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), key);
        } else {
            player.getInventory().addItem(key);
        }
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

    public int repairKeysInInventory(Player player) {
        if (player == null || plugin.getCacheManager() == null) {
            return 0;
        }

        int repaired = 0;
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (repairKeyStack(stack)) {
                inventory.setItem(slot, stack);
                repaired++;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (repairKeyStack(cursor)) {
            player.setItemOnCursor(cursor);
            repaired++;
        }

        return repaired;
    }

    public boolean repairKeyStack(ItemStack item) {
        if (!isValidItem(item) || plugin.getCacheManager() == null) {
            return false;
        }

        String pdcName = getPdcString(item, CacheKeys.CACHE_NAME);
        String pdcUuid = getPdcString(item, CacheKeys.KEY_UUID);

        if (pdcName != null && !pdcName.isEmpty()) {
            String correctUuid = plugin.getConfigManager().getKeyUuid(pdcName);
            if (correctUuid != null && correctUuid.equals(pdcUuid)) {
                return false;
            }
            if (pdcUuid != null && !pdcUuid.isEmpty() && correctUuid != null && !pdcUuid.equals(correctUuid)) {
                return false;
            }
            if (matchesKeyAppearance(item, pdcName) || pdcUuid == null || pdcUuid.isEmpty()) {
                stampKeyData(item, pdcName);
                return true;
            }
            return false;
        }

        for (String cacheName : plugin.getCacheManager().getCacheNames()) {
            if (matchesKeyAppearance(item, cacheName)) {
                stampKeyData(item, cacheName);
                return true;
            }
        }

        return false;
    }

    public boolean matchesKeyAppearance(ItemStack item, String cacheName) {
        if (!isValidItem(item) || cacheName == null || cacheName.isEmpty()) {
            return false;
        }

        FileConfiguration cacheConfig = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (cacheConfig == null) {
            return false;
        }

        Material expectedMaterial;
        String expectedName;
        List<String> expectedLore;
        int expectedCmd;

        synchronized (cacheConfig) {
            expectedMaterial = Material.matchMaterial(cacheConfig.getString("key.material", "TRIPWIRE_HOOK"));
            if (expectedMaterial == null) {
                expectedMaterial = Material.TRIPWIRE_HOOK;
            }

            expectedName = HexColors.translate(
                    cacheConfig.getString("key.name", "&eКлюч от тайника " + cacheName)
                            .replace("{name-cache}", cacheName)
            );

            List<String> lore = cacheConfig.getStringList("key.lore");
            expectedLore = HexColors.translate(
                    lore.stream()
                            .map(line -> line.replace("{name-cache}", cacheName))
                            .collect(Collectors.toList())
            );

            expectedCmd = cacheConfig.getInt("key.custom-model-data", 0);
        }

        if (item.getType() != expectedMaterial) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String actualName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        if (!normalizeText(expectedName).equals(normalizeText(actualName))) {
            return false;
        }

        List<String> actualLore = meta.hasLore() && meta.getLore() != null
                ? meta.getLore()
                : Collections.emptyList();
        if (!normalizeLore(expectedLore).equals(normalizeLore(actualLore))) {
            return false;
        }

        int actualCmd = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
        return expectedCmd == actualCmd;
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

    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ChatColor.stripColor(text).trim();
    }

    private List<String> normalizeLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            result.add(normalizeText(line));
        }
        return result;
    }
}
