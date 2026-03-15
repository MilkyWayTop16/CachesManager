package org.gw.cachesmanager.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StatsManager {

    private final CachesManager plugin;
    private final ConfigManager configManager;
    private final DateTimeFormatter dateFormatter;

    public StatsManager(CachesManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    }

    public void incrementOpenCount(CacheManager.Cache cache) {
        if (cache == null) return;
        cache.incrementOpenCount();
    }

    public void addLootGiven(CacheManager.Cache cache, int amount) {
        if (cache == null) return;
        cache.addLootGiven(amount);
    }

    public void recordPlayerOpen(CacheManager.Cache cache, String playerName) {
        if (cache == null || playerName == null) return;
        cache.recordPlayerOpen(playerName);
    }

    public ItemStack createRecordsItem(CacheManager.Cache cache) {
        if (cache == null) return new ItemStack(Material.BOOK);

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        String title = menuCfg.getString("items.records.display-name");
        meta.setDisplayName(HexColors.translate(title));

        List<String> loreTemplate = menuCfg.getStringList("items.records.lore");
        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            lore.add(HexColors.translate(line
                    .replace("{first-date}", cache.getFirstOpenedTime() > 0
                            ? dateFormatter.format(Instant.ofEpochMilli(cache.getFirstOpenedTime()))
                            : "—")
                    .replace("{max-daily}", String.valueOf(cache.getMaxDailyOpens()))
                    .replace("{avg-interval}", String.valueOf(cache.getAverageIntervalMinutes()))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGeneralStatsItem(CacheManager.Cache cache) {
        if (cache == null) return new ItemStack(Material.BOOK);

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        String title = menuCfg.getString("items.general-stats.display-name");
        meta.setDisplayName(HexColors.translate(title));

        List<String> loreTemplate = menuCfg.getStringList("items.general-stats.lore");
        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            lore.add(HexColors.translate(line
                    .replace("{open-count}", String.valueOf(cache.getOpenCount()))
                    .replace("{total-loot-given}", String.valueOf(cache.getTotalLootGiven()))
                    .replace("{created-date}", dateFormatter.format(Instant.ofEpochMilli(cache.getCreatedTime())))
                    .replace("{last-opened}", cache.getLastOpenedTime() > 0
                            ? dateFormatter.format(Instant.ofEpochMilli(cache.getLastOpenedTime()))
                            : "&#FB8808Неизвестно")));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTopPlayersItem(CacheManager.Cache cache) {
        if (cache == null) return new ItemStack(Material.PLAYER_HEAD);

        Map<String, Integer> top = cache.getTopPlayers();

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        String title = menuCfg.getString("items.top-players.display-name");
        meta.setDisplayName(HexColors.translate(title));

        List<String> lore = new ArrayList<>();
        lore.add("");

        List<String> loreTemplate = menuCfg.getStringList("items.top-players.lore");
        String template = loreTemplate.size() > 1 ? loreTemplate.get(1)
                : "  &#FFFF00◆ &f{rank}. &#FFFF00{player} &f— &#FFFF00{count} открытий";

        if (top.isEmpty()) {
            lore.add(HexColors.translate(configManager.getConfig().getString("actions.stats.no-players")));
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : top.entrySet()) {
                lore.add(HexColors.translate(template
                        .replace("{rank}", String.valueOf(rank))
                        .replace("{player}", entry.getKey())
                        .replace("{count}", String.valueOf(entry.getValue()))));
                rank++;
            }
        }

        lore.add("");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}