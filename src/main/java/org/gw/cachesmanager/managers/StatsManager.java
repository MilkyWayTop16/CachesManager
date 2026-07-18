package org.gw.cachesmanager.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.MaterialCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsManager {

    private final ConfigManager configManager;
    private final DateTimeFormatter dateFormatter;

    public StatsManager(CachesManager plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    }

    public ItemStack createRecordsItem(Cache cache) {
        if (cache == null) return new ItemStack(Material.BOOK);

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");
        Material material = MaterialCompat.match(menuCfg.getString("items.records.material", "CLOCK"), Material.CLOCK);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(HexColors.translate(menuCfg.getString("items.records.display-name", "")));

        List<String> lore = new ArrayList<>();
        String firstOpened = cache.getFirstOpenedTime() > 0 ? dateFormatter.format(Instant.ofEpochMilli(cache.getFirstOpenedTime())) : "&#FB8808Неизвестно";
        String lastOpened = cache.getLastOpenedTime() > 0 ? dateFormatter.format(Instant.ofEpochMilli(cache.getLastOpenedTime())) : "&#FB8808Неизвестно";
        String longestDrought = calculateLongestDrought(cache);
        String shortestInterval = calculateShortestInterval(cache);
        String mostActiveDay = calculateMostActiveDay(cache);

        for (String line : menuCfg.getStringList("items.records.lore")) {
            lore.add(HexColors.translate(line
                    .replace("{first-opened}", firstOpened)
                    .replace("{last-opened}", lastOpened)
                    .replace("{longest-drought}", longestDrought)
                    .replace("{shortest-interval}", shortestInterval)
                    .replace("{most-active-day}", mostActiveDay)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGeneralStatsItem(Cache cache) {
        if (cache == null) return new ItemStack(Material.BOOK);

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");
        Material material = MaterialCompat.match(menuCfg.getString("items.general-stats.material", "ENCHANTED_BOOK"), Material.ENCHANTED_BOOK);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(HexColors.translate(menuCfg.getString("items.general-stats.display-name", "")));

        List<String> lore = new ArrayList<>();
        String openCount = String.valueOf(cache.getOpenCount());
        String createdDate = dateFormatter.format(Instant.ofEpochMilli(cache.getCreatedTime()));
        String lastOpened = cache.getLastOpenedTime() > 0 ? dateFormatter.format(Instant.ofEpochMilli(cache.getLastOpenedTime())) : "&#FB8808Неизвестно";

        long opens7d = calculateOpensLast7Days(cache);
        double avgPerDay = calculateAvgOpensPerDay(cache);
        double avgPerPlayer = calculateAvgOpensPerPlayer(cache);
        String peakHour = "&#FB8808Неизвестно";

        for (String line : menuCfg.getStringList("items.general-stats.lore")) {
            lore.add(HexColors.translate(line
                    .replace("{cache-opens-total}", openCount)
                    .replace("{cache-opens-7d}", String.valueOf(opens7d))
                    .replace("{cache-opens-per-day-avg}", String.format("%.1f", avgPerDay))
                    .replace("{cache-avg-opens-per-player}", String.format("%.1f", avgPerPlayer))
                    .replace("{cache-peak-hour}", peakHour)
                    .replace("{created-date}", createdDate)
                    .replace("{last-opened}", lastOpened)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTopPlayersItem(Cache cache) {
        if (cache == null) return new ItemStack(Material.PLAYER_HEAD);

        FileConfiguration menuCfg = configManager.loadMenuConfig("stats-menu.yml");
        Material material = MaterialCompat.match(menuCfg.getString("items.top-players.material", "PLAYER_HEAD"), Material.PLAYER_HEAD);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(HexColors.translate(menuCfg.getString("items.top-players.display-name", "")));

        Map<String, Integer> top = cache.getTopPlayers();
        List<String> lore = new ArrayList<>();
        String noPlayersMsg = configManager.getConfig().getString("actions.stats.no-players", "&7Нет данных");

        for (String line : menuCfg.getStringList("items.top-players.lore")) {
            if (line.contains("{player}") || line.contains("{count}")) {
                if (top.isEmpty()) {
                    lore.add(HexColors.translate(noPlayersMsg));
                } else {
                    int rank = 1;
                    for (Map.Entry<String, Integer> entry : top.entrySet()) {
                        lore.add(HexColors.translate(line
                                .replace("{rank}", String.valueOf(rank))
                                .replace("{player}", entry.getKey())
                                .replace("{count}", String.valueOf(entry.getValue()))));
                        rank++;
                    }
                }
            } else {
                lore.add(HexColors.translate(line));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private long calculateOpensLast7Days(Cache cache) {
        if (cache.getDailyOpens().isEmpty()) return 0;
        long total = 0;
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate day = today.minusDays(i);
            total += cache.getDailyOpens().getOrDefault(day, new java.util.concurrent.atomic.AtomicInteger(0)).get();
        }
        return total;
    }

    private double calculateAvgOpensPerDay(Cache cache) {
        if (cache.getOpenCount() == 0) return 0;
        long daysSinceCreation = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(cache.getCreatedTime()).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                java.time.LocalDate.now()
        ));
        return (double) cache.getOpenCount() / daysSinceCreation;
    }

    private double calculateAvgOpensPerPlayer(Cache cache) {
        int uniquePlayers = Math.max(1, cache.getTopPlayers().size());
        return (double) cache.getOpenCount() / uniquePlayers;
    }

    private String calculateLongestDrought(Cache cache) {
        if (cache.getFirstOpenedTime() == 0) return "&#FB8808Неизвестно";

        java.time.LocalDate firstDay = java.time.Instant.ofEpochMilli(cache.getFirstOpenedTime())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate lastDay = java.time.Instant.ofEpochMilli(cache.getLastOpenedTime() > 0 ? cache.getLastOpenedTime() : System.currentTimeMillis())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        long maxGap = 0;
        java.time.LocalDate previous = firstDay;

        for (java.time.LocalDate day : cache.getDailyOpens().keySet().stream().sorted().toList()) {
            long gap = java.time.temporal.ChronoUnit.DAYS.between(previous, day);
            if (gap > maxGap) maxGap = gap;
            previous = day;
        }

        long gapToToday = java.time.temporal.ChronoUnit.DAYS.between(previous, java.time.LocalDate.now());
        if (gapToToday > maxGap) maxGap = gapToToday;

        return maxGap <= 0 ? "0 дней" : maxGap + " дн.";
    }

    private String calculateShortestInterval(Cache cache) {
        if (cache.getMinInterval() == Long.MAX_VALUE || cache.getMinInterval() <= 0) {
            return "&#FB8808Неизвестно";
        }
        long minutes = cache.getMinInterval() / (1000 * 60);
        long seconds = (cache.getMinInterval() / 1000) % 60;
        return minutes + " мин " + seconds + " сек";
    }

    private String calculateMostActiveDay(Cache cache) {
        if (cache.getDailyOpens().isEmpty()) return "&#FB8808Неизвестно";

        java.util.Map.Entry<java.time.LocalDate, java.util.concurrent.atomic.AtomicInteger> maxEntry = null;
        for (java.util.Map.Entry<java.time.LocalDate, java.util.concurrent.atomic.AtomicInteger> entry : cache.getDailyOpens().entrySet()) {
            if (maxEntry == null || entry.getValue().get() > maxEntry.getValue().get()) {
                maxEntry = entry;
            }
        }

        if (maxEntry == null) return "&#FB8808Неизвестно";

        String date = maxEntry.getKey().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        return date + " (" + maxEntry.getValue().get() + " откр.)";
    }
}