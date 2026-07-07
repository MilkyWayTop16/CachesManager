package org.gw.cachesmanager.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.storage.DatabaseManager;
import org.gw.cachesmanager.utils.HexColors;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LootHistoryManager {

    private final CachesManager plugin;
    private final ConfigManager configManager;
    private final DateTimeFormatter dateFormatter;

    public LootHistoryManager(CachesManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    }

    public List<ItemStack> getHistoryItems(String cacheName) {
        List<ItemStack> items = new ArrayList<>();
        if (plugin.getDatabaseManager() == null) return items;

        List<DatabaseManager.HistoryEntry> entries = plugin.getDatabaseManager().getLootHistorySynchronously(cacheName);
        return buildHistoryItems(entries);
    }

    public void getHistoryItemsAsync(String cacheName, Consumer<List<ItemStack>> callback) {
        if (plugin.getDatabaseManager() == null) {
            callback.accept(new ArrayList<>());
            return;
        }

        plugin.getDatabaseManager().getLootHistoryAsync(cacheName).thenAccept(entries -> {
            List<ItemStack> items = buildHistoryItems(entries);
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(items));
        });
    }

    private List<ItemStack> buildHistoryItems(List<DatabaseManager.HistoryEntry> entries) {
        List<ItemStack> items = new ArrayList<>();
        if (entries == null || entries.isEmpty()) return items;

        FileConfiguration menuCfg = configManager.loadMenuConfig("history-menu.yml");
        List<String> loreTemplate = menuCfg.getStringList("history-item-settings.lore");

        for (DatabaseManager.HistoryEntry entry : entries) {
            ItemStack item = entry.getItem().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            List<String> currentLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (currentLore == null) currentLore = new ArrayList<>();

            String formattedDate = dateFormatter.format(Instant.ofEpochMilli(entry.getTimestamp()));

            for (String line : loreTemplate) {
                currentLore.add(HexColors.translate(line
                        .replace("{dropped-by}", entry.getPlayerName())
                        .replace("{dropped-at}", formattedDate)));
            }

            meta.setLore(currentLore);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    public void deleteHistory(String cacheName) {
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().deleteCacheStatsAndHistory(cacheName);
        }
    }
}