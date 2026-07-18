package org.gw.cachesmanager.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class V12Migrator {

    private final CachesManager plugin;
    private final DatabaseConnectionManager connections;
    private final LootHistoryRepository historyRepository;
    private final ItemSerializationService serialization;
    private final DatabaseTaskExecutor taskExecutor;
    private final Runnable connectionChecker;
    private final Supplier<Boolean> hasMigratedSupplier;
    private final Runnable markMigrated;

    public V12Migrator(CachesManager plugin,
                       DatabaseConnectionManager connections,
                       LootHistoryRepository historyRepository,
                       ItemSerializationService serialization,
                       DatabaseTaskExecutor taskExecutor,
                       Runnable connectionChecker,
                       Supplier<Boolean> hasMigratedSupplier,
                       Runnable markMigrated) {
        this.plugin = plugin;
        this.connections = connections;
        this.historyRepository = historyRepository;
        this.serialization = serialization;
        this.taskExecutor = taskExecutor;
        this.connectionChecker = connectionChecker;
        this.hasMigratedSupplier = hasMigratedSupplier;
        this.markMigrated = markMigrated;
    }

    public CompletableFuture<Void> migrateAsync(List<String> cacheNames) {
        if (cacheNames == null || cacheNames.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (Boolean.TRUE.equals(hasMigratedSupplier.get())) {
            return CompletableFuture.completedFuture(null);
        }

        return taskExecutor.runAsync(() -> {
            connectionChecker.run();
            if (Boolean.TRUE.equals(hasMigratedSupplier.get())) {
                return;
            }

            int migratedStats = 0;
            int migratedHistory = 0;
            int total = cacheNames.size();
            AtomicBoolean hadErrors = new AtomicBoolean(false);

            plugin.console("&#ffff00◆ CachesManager &f| Начинается миграция данных со старых версий (" + total + " тайников)...");

            for (int i = 0; i < cacheNames.size(); i++) {
                String name = cacheNames.get(i);
                try {
                    ensureKeyUuidForCache(name);

                    int statsResult = migrateSingleCacheStatsFromYaml(name);
                    if (statsResult > 0) migratedStats++;
                    if (statsResult < 0) hadErrors.set(true);

                    int historyResult = migrateSingleCacheHistoryFromV12(name);
                    if (historyResult > 0) migratedHistory++;
                    if (historyResult < 0) hadErrors.set(true);
                } catch (Exception e) {
                    hadErrors.set(true);
                    plugin.error("Ошибка миграции тайника " + name + ": " + e.getMessage());
                }

                if ((i + 1) % 25 == 0 || (i + 1) == total) {
                    plugin.console("&#ffff00◆ CachesManager &f| Миграция: &#ffff00" + (i + 1) + "&f/&#ffff00" + total
                            + " (статистика: &#ffff00" + migratedStats + "&f, история: &#ffff00" + migratedHistory + "&f)");
                }
            }

            migrateOrphanHistoryFiles(hadErrors);

            if (hadErrors.get()) {
                plugin.console("&#FB8808◆ CachesManager &f| Миграция завершена &#FB8808с ошибками&f, флаг migrated_from_v12 &#FB8808не установлен&f, и при следующем запуске незавершённые данные будут обработаны снова");
                return;
            }

            plugin.console("&#00FF5A◆ CachesManager &f| Миграция со старых версий &#00FF5Aуспешно &fзавершена! Статистика: &#ffff00"
                    + migratedStats + "&f, история: &#ffff00" + migratedHistory);

            markMigrated.run();
        });
    }

    public boolean hasMigratedFromV12() {
        connectionChecker.run();
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT meta_value FROM plugin_meta WHERE meta_key = 'migrated_from_v12'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "true".equalsIgnoreCase(rs.getString(1));
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public void markMigrationFromV12Completed() {
        connectionChecker.run();
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "REPLACE INTO plugin_meta (meta_key, meta_value) VALUES ('migrated_from_v12', 'true')")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.error("Не удалось сохранить маркер миграции: " + e.getMessage());
        }
    }

    private void ensureKeyUuidForCache(String cacheName) {
        FileConfiguration config = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (config == null) return;
        ensureKeyUuidExists(config, cacheName);
    }

    private void recordV12MigrationStatus(String cacheName, boolean statsSuccess, boolean historySuccess,
                                          String statsError, String historyError) {
        connectionChecker.run();
        try (Connection connection = connections.getConnection()) {
            boolean currentStats = false;
            boolean currentHistory = false;
            String currentStatsError = null;
            String currentHistoryError = null;

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT stats_migrated, history_migrated, stats_error, history_error FROM v12_migration_status WHERE cache_name = ?")) {
                select.setString(1, cacheName);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        currentStats = rs.getBoolean(1);
                        currentHistory = rs.getBoolean(2);
                        currentStatsError = rs.getString(3);
                        currentHistoryError = rs.getString(4);
                    }
                }
            }

            boolean finalStats = statsSuccess || currentStats;
            boolean finalHistory = historySuccess || currentHistory;
            String finalStatsError = statsSuccess ? null : (statsError != null ? statsError : currentStatsError);
            String finalHistoryError = historySuccess ? null : (historyError != null ? historyError : currentHistoryError);

            String sql = "REPLACE INTO v12_migration_status " +
                    "(cache_name, stats_migrated, history_migrated, stats_error, history_error, migrated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, cacheName);
                ps.setBoolean(2, finalStats);
                ps.setBoolean(3, finalHistory);
                ps.setString(4, finalStatsError);
                ps.setString(5, finalHistoryError);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.error("Не удалось сохранить статус миграции тайника " + cacheName + ": " + e.getMessage());
        }
    }

    private boolean isHistoryMarkedMigrated(String cacheName) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT history_migrated FROM v12_migration_status WHERE cache_name = ?")) {
            ps.setString(1, cacheName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private int migrateSingleCacheStatsFromYaml(String cacheName) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM cache_stats WHERE cache_name = ?")) {
            ps.setString(1, cacheName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return 0;
            }
        } catch (SQLException ignored) {}

        FileConfiguration config = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (config == null) return 0;

        try {
            if (!config.contains("stats")) return 0;

            File original = new File(plugin.getDataFolder(), "caches/" + cacheName + ".yml");
            if (original.exists()) {
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                File backup = new File(plugin.getDataFolder(), "caches/" + cacheName + ".yml.v12-" + timestamp + ".bak");
                if (!backup.exists()) {
                    java.nio.file.Files.copy(original.toPath(), backup.toPath());
                }
            }

            long created = config.getLong("stats.created", System.currentTimeMillis());
            int opens = config.getInt("stats.open-count", 0);
            int loot = config.getInt("stats.total-loot-given", 0);
            long last = config.getLong("stats.last-opened", 0);
            long first = config.getLong("stats.first-opened", 0);
            int maxDaily = firstNonZero(
                    config.getInt("stats.max-daily", 0),
                    config.getInt("stats.max_daily", 0),
                    config.getInt("stats.max-daily-opens", 0)
            );
            long intervalSum = config.getLong("stats.interval-sum", 0);
            int intervalCount = config.getInt("stats.interval-count", 0);

            try (Connection connection = connections.getConnection()) {
                connection.setAutoCommit(false);

                try (PreparedStatement ps = connection.prepareStatement(
                        "REPLACE INTO cache_stats (cache_name, open_count, total_loot_given, last_opened, first_opened, max_daily_opens, created_time, interval_sum, interval_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, cacheName);
                    ps.setInt(2, opens);
                    ps.setInt(3, loot);
                    ps.setLong(4, last);
                    ps.setLong(5, first);
                    ps.setInt(6, maxDaily);
                    ps.setLong(7, created);
                    ps.setLong(8, intervalSum);
                    ps.setInt(9, intervalCount);
                    ps.executeUpdate();
                }

                ConfigurationSection top = config.getConfigurationSection("stats.top-players");
                if (top == null) {
                    top = config.getConfigurationSection("stats.top_players");
                }
                if (top != null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "REPLACE INTO cache_top_players (cache_name, player_name, open_count) VALUES (?, ?, ?)")) {
                        for (String player : top.getKeys(false)) {
                            ps.setString(1, cacheName);
                            ps.setString(2, player);
                            ps.setInt(3, top.getInt(player));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                ConfigurationSection daily = config.getConfigurationSection("stats.daily-opens");
                if (daily == null) {
                    daily = config.getConfigurationSection("stats.daily_opens");
                }
                if (daily != null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "REPLACE INTO cache_daily_opens (cache_name, open_date, open_count) VALUES (?, ?, ?)")) {
                        for (String date : daily.getKeys(false)) {
                            ps.setString(1, cacheName);
                            ps.setString(2, date);
                            ps.setInt(3, daily.getInt(date));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                connection.commit();
            }

            recordV12MigrationStatus(cacheName, true, false, null, null);
            wipeStatsFromYaml(config, cacheName);
            return 1;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            plugin.error("Не удалось мигрировать статистику " + cacheName + ": " + errorMsg);
            recordV12MigrationStatus(cacheName, false, false, errorMsg, null);
            return -1;
        }
    }

    private int firstNonZero(int... values) {
        for (int v : values) {
            if (v != 0) return v;
        }
        return 0;
    }

    private void ensureKeyUuidExists(FileConfiguration config, String cacheName) {
        synchronized (config) {
            if (config.contains("key.uuid") && !config.getString("key.uuid", "").isEmpty()) {
                return;
            }
            String newUuid = java.util.UUID.randomUUID().toString();
            config.set("key.uuid", newUuid);
        }
        forceSaveCacheConfig(config, cacheName);
        plugin.console("&#FFFF00◆ CachesManager &f| Для тайника &#ffff00" + cacheName
                + " &fдобавлен key.uuid (его не было в старой конфигурации). Старые ключи без UUID продолжат работать и будут обновлены при использовании.");
    }

    private void wipeStatsFromYaml(FileConfiguration config, String cacheName) {
        synchronized (config) {
            config.set("stats", null);
        }
        forceSaveCacheConfig(config, cacheName);
    }

    private void forceSaveCacheConfig(FileConfiguration config, String cacheName) {
        File file = new File(plugin.getDataFolder(), "caches/" + cacheName + ".yml");
        try {
            synchronized (config) {
                config.save(file);
            }
        } catch (Exception e) {
            plugin.error("Не удалось сразу сохранить YAML тайника " + cacheName + " после миграции: " + e.getMessage());
            plugin.getConfigManager().saveCacheConfig(cacheName);
            return;
        }
        plugin.getConfigManager().saveCacheConfig(cacheName);
    }

    private int migrateSingleCacheHistoryFromV12(String cacheName) {
        File historyFolder = new File(plugin.getDataFolder(), "caches/history");
        File oldFile = new File(historyFolder, cacheName + ".yml");

        if (isHistoryMarkedMigrated(cacheName)) {
            if (oldFile.exists()) {
                                oldFile.delete();
            }
            return 0;
        }

        if (!oldFile.exists()) return 0;
        return migrateHistoryFile(cacheName, oldFile, historyFolder);
    }

    private void migrateOrphanHistoryFiles(AtomicBoolean hadErrors) {
        File historyFolder = new File(plugin.getDataFolder(), "caches/history");
        if (!historyFolder.isDirectory()) return;

        File[] files = historyFolder.listFiles((dir, name) -> name != null && name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String cacheName = fileName.substring(0, fileName.length() - 4);
            if (isHistoryMarkedMigrated(cacheName)) {
                                file.delete();
                continue;
            }
            int result = migrateHistoryFile(cacheName, file, historyFolder);
            if (result < 0) {
                hadErrors.set(true);
            }
        }
    }

    private int migrateHistoryFile(String cacheName, File oldFile, File historyFolder) {
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(oldFile);
            ConfigurationSection section = cfg.getConfigurationSection("history");
            if (section == null || section.getKeys(false).isEmpty()) {
                                oldFile.delete();
                recordV12MigrationStatus(cacheName, false, true, null, null);
                return 0;
            }

            List<DatabaseManager.LegacyHistoryEntry> entries = new ArrayList<>();
            int sourceCount = 0;
            int failedCount = 0;

            for (String key : section.getKeys(false)) {
                ConfigurationSection e = section.getConfigurationSection(key);
                if (e == null) continue;
                sourceCount++;
                String player = e.getString("player");
                long time = e.getLong("time", System.currentTimeMillis());
                String rawItem = e.getString("item");
                if (player == null || rawItem == null || rawItem.isEmpty()) {
                    failedCount++;
                    continue;
                }

                ItemStack item = serialization.deserializeItem(rawItem);
                if (item == null) {
                    failedCount++;
                    continue;
                }
                entries.add(new DatabaseManager.LegacyHistoryEntry(player, item, time));
            }

            if (entries.isEmpty()) {
                if (sourceCount > 0) {
                    String errorMsg = "Не удалось десериализовать историю (" + failedCount + "/" + sourceCount + " записей)";
                    plugin.error("Миграция истории " + cacheName + ": " + errorMsg + ". Файл сохранён для повторной попытки.");
                    recordV12MigrationStatus(cacheName, false, false, null, errorMsg);
                    return -1;
                }
                                oldFile.delete();
                recordV12MigrationStatus(cacheName, false, true, null, null);
                return 0;
            }

            if (failedCount > 0) {
                plugin.console("&#FB8808◆ CachesManager &f| История &#ffff00" + cacheName + "&f: перенесено &#ffff00"
                        + entries.size() + "&f/&#ffff00" + sourceCount + "&f записей, &#FB8808" + failedCount + " &fне удалось прочитать.");
            }

            File backupFolder = new File(historyFolder, "v12_backup");
            if (!backupFolder.exists()) {
                                backupFolder.mkdirs();
            }

            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backup = new File(backupFolder, cacheName + ".yml." + timestamp + ".bak");
            if (!backup.exists()) {
                java.nio.file.Files.copy(oldFile.toPath(), backup.toPath());
            }

            if (!historyRepository.insertHistoryEntriesSynchronously(cacheName, entries)) {
                String errorMsg = "Ошибка записи истории в базу данных...";
                recordV12MigrationStatus(cacheName, false, false, null, errorMsg);
                return -1;
            }

            recordV12MigrationStatus(cacheName, false, true, null, null);

            if (!oldFile.delete()) {
                plugin.log("История тайника " + cacheName + " перенесена в базу данных, но исходный .yml не удалён, и будет проигнорирован при следующем запуске...");
            }
            return 1;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            plugin.error("Ошибка миграции истории " + cacheName + ": " + errorMsg);
            recordV12MigrationStatus(cacheName, false, false, null, errorMsg);
            return -1;
        }
    }
}
