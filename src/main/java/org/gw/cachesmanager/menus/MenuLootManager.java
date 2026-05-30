package org.gw.cachesmanager.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.util.*;
import java.util.stream.Collectors;

public class MenuLootManager {
    private final CachesManager plugin;

    public MenuLootManager(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void fillLootItems(Player player, Inventory inventory, FileConfiguration menuConfig, String cacheName, int page, String menuFile,
                              Map<String, Map<Integer, List<ItemStack>>> cachePageLoot, List<Integer> lootSlots) {
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        Map<Integer, List<ItemStack>> pageLoot = cachePageLoot.computeIfAbsent(cacheName, k -> new HashMap<>());
        List<ItemStack> loot = pageLoot.getOrDefault(page, Collections.emptyList());
        for (int slot : lootSlots) inventory.setItem(slot, null);
        ConfigurationSection lootSettings = menuConfig.getConfigurationSection("loot-item-settings");
        boolean isChance = "chance-menu.yml".equals(menuFile);
        int perPage = lootSlots.size();
        for (int i = 0; i < lootSlots.size() && i < loot.size(); i++) {
            ItemStack item = loot.get(i);
            if (item == null) continue;
            ItemStack display = item.clone();
            if (isChance && lootSettings != null) applyChanceLore(player, display, lootSettings, cache, (page - 1) * perPage + i);
            inventory.setItem(lootSlots.get(i), display);
        }
    }

    public void applyChanceLore(Player player, ItemStack item, ConfigurationSection settings, Cache cache, int index) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(l -> l.contains("{chance}"));
        List<String> add = settings.getStringList("lore").stream()
                .map(s -> {
                    String raw = s.replace("{chance}", String.valueOf(
                            index < cache.getLootWithChances().size() ? cache.getLootWithChances().get(index).getValue() : 50));
                    raw = PlaceholderAPIHook.parse(player, raw);
                    return HexColors.translate(raw);
                })
                .collect(Collectors.toList());
        lore.addAll(add);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public void fillHistoryItemsAsync(Player player, Inventory inventory, String cacheName, int page, List<Integer> slots) {
        for (int slot : slots) inventory.setItem(slot, null);

        plugin.getLootHistoryManager().getHistoryItemsAsync(cacheName, historyItems -> {
            if (player.getOpenInventory() == null || !inventory.equals(player.getOpenInventory().getTopInventory())) {
                return;
            }
            int start = (page - 1) * slots.size();
            for (int i = 0; i < slots.size() && (start + i) < historyItems.size(); i++) {
                inventory.setItem(slots.get(i), historyItems.get(start + i));
            }
        });
    }

    public void saveLootForPage(Player player, String cacheName, int page, Inventory inventory,
                                Map<String, Map<Integer, List<ItemStack>>> cachePageLoot, List<Integer> lootSlots) {
        Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;
        List<ItemStack> currentLoot = new ArrayList<>();
        for (int slot : lootSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                currentLoot.add(item.clone());
            }
        }
        cachePageLoot.computeIfAbsent(cacheName, k -> new HashMap<>()).put(page, currentLoot);
        List<ItemStack> allLoot = new ArrayList<>();
        for (List<ItemStack> pLoot : cachePageLoot.getOrDefault(cacheName, Collections.emptyMap()).values()) {
            allLoot.addAll(pLoot);
        }
        List<Map.Entry<ItemStack, Integer>> newLootWithChances = new ArrayList<>();
        for (ItemStack item : allLoot) {
            int chance = 50;
            for (Map.Entry<ItemStack, Integer> oldEntry : cache.getLootWithChances()) {
                if (oldEntry.getKey() != null && item.isSimilar(oldEntry.getKey())) {
                    chance = oldEntry.getValue();
                    break;
                }
            }
            newLootWithChances.add(new AbstractMap.SimpleEntry<>(item, chance));
        }
        plugin.getCacheManager().setCacheLootWithChances(cache, newLootWithChances);
        plugin.getConfigManager().setCacheLoot(cacheName, newLootWithChances);
    }
}