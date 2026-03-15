package org.gw.cachesmanager.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class LootHistoryManager {
    private final CachesManager plugin;
    private final File historyFolder;
    private final Map<String, Deque<HistoryEntry>> historyCache = new ConcurrentHashMap<>();
    private final Set<String> dirtyHistory = ConcurrentHashMap.newKeySet();
    private BukkitRunnable batchSaveTask;
    private final AtomicBoolean savingInProgress = new AtomicBoolean(false);

    public LootHistoryManager(CachesManager plugin) {
        this.plugin = plugin;
        this.historyFolder = new File(plugin.getDataFolder(), "caches/history");
        if (!historyFolder.exists()) historyFolder.mkdirs();

        startBatchSaver();
        loadAllHistories();
    }

    private void startBatchSaver() {
        if (batchSaveTask != null) batchSaveTask.cancel();
        batchSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dirtyHistory.isEmpty() || savingInProgress.get()) return;
                Set<String> toSave = new HashSet<>(dirtyHistory);
                dirtyHistory.clear();
                savingInProgress.set(true);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        for (String cacheName : toSave) saveHistory(cacheName);
                    } finally {
                        savingInProgress.set(false);
                    }
                });
            }
        };
        batchSaveTask.runTaskTimer(plugin, 200L, 200L);
    }

    public void addEntry(String cacheName, String playerName, ItemStack item) {
        if (cacheName == null || playerName == null || item == null) return;

        int limit = plugin.getConfigManager().getHistoryMaxEntries();

        Deque<HistoryEntry> deque = historyCache.computeIfAbsent(cacheName, k -> new ArrayDeque<>());
        deque.addFirst(new HistoryEntry(playerName, Instant.now().toEpochMilli(), item.clone()));

        while (deque.size() > limit) deque.removeLast();

        dirtyHistory.add(cacheName);
    }

    public Deque<HistoryEntry> getHistory(String cacheName) {
        return historyCache.computeIfAbsent(cacheName, this::loadHistory);
    }

    private Deque<HistoryEntry> loadHistory(String cacheName) {
        File file = new File(historyFolder, cacheName + ".yml");
        if (!file.exists()) return new ArrayDeque<>();

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Deque<HistoryEntry> deque = new ArrayDeque<>();
        ConfigurationSection section = cfg.getConfigurationSection("history");
        if (section == null) return deque;

        int maxDaysLimit = plugin.getConfigManager().getHistoryMaxDays();
        long cutoff = Instant.now().minusSeconds((long) maxDaysLimit * 86400).toEpochMilli();

        for (String key : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(key);
            if (e == null) continue;

            long time = e.getLong("time");
            if (time < cutoff) continue;

            String base64 = e.getString("item");
            ItemStack item = deserializeItem(base64);
            if (item != null) {
                deque.addLast(new HistoryEntry(e.getString("player"), time, item));
            }
        }
        return deque;
    }

    private void saveHistory(String cacheName) {
        Deque<HistoryEntry> deque = historyCache.get(cacheName);
        if (deque == null || deque.isEmpty()) return;

        File file = new File(historyFolder, cacheName + ".yml");
        FileConfiguration cfg = new YamlConfiguration();

        int i = 0;
        for (HistoryEntry entry : deque) {
            String base = "history." + i + ".";
            cfg.set(base + "player", entry.playerName);
            cfg.set(base + "time", entry.time);
            cfg.set(base + "item", serializeItem(entry.item));
            i++;
        }
        try {
            cfg.save(file);
        } catch (IOException ignored) {}
    }

    public void deleteHistory(String cacheName) {
        File file = new File(historyFolder, cacheName + ".yml");
        if (file.exists()) file.delete();

        historyCache.remove(cacheName);
        dirtyHistory.remove(cacheName);
    }

    public void saveAll() {
        if (batchSaveTask != null) batchSaveTask.cancel();
        for (String cacheName : new ArrayList<>(historyCache.keySet())) {
            saveHistory(cacheName);
        }
        if (plugin.isEnabled()) startBatchSaver();
    }

    private String serializeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack deserializeItem(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadAllHistories() {
        File[] files = historyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String cacheName = file.getName().replace(".yml", "");
            historyCache.put(cacheName, loadHistory(cacheName));
        }
    }

    public void reload() {
        historyCache.clear();
        loadAllHistories();
    }

    public static class HistoryEntry {
        public final String playerName;
        public final long time;
        public final ItemStack item;
        public final String formattedTime;

        HistoryEntry(String playerName, long time, ItemStack item) {
            this.playerName = playerName;
            this.time = time;
            this.item = item;
            this.formattedTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(time));
        }
    }
}