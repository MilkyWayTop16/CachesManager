package org.gw.cachesmanager.storage;

import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final CachesManager plugin;
    private final DatabaseTaskExecutor taskExecutor;
    private final ItemSerializationService serialization;
    private final SchemaManager schemaManager;
    private final DatabaseConnectionManager connections;
    private final CacheStatsRepository statsRepository;
    private final LootHistoryRepository historyRepository;
    private final V12Migrator v12Migrator;
    private final BackupCleanupService backupCleanupService;

    @Setter
    private volatile boolean shuttingDown = false;

    public DatabaseManager(CachesManager plugin) {
        this.plugin = plugin;
        this.taskExecutor = new DatabaseTaskExecutor();
        this.serialization = new ItemSerializationService();
        this.schemaManager = new SchemaManager(plugin);
        this.connections = new DatabaseConnectionManager(plugin, schemaManager, () -> shuttingDown);
        this.statsRepository = new CacheStatsRepository(
                plugin,
                connections,
                taskExecutor::submit,
                this::checkConnection
        );
        this.historyRepository = new LootHistoryRepository(
                plugin,
                connections,
                serialization,
                taskExecutor::submit,
                this::checkConnection,
                () -> shuttingDown
        );
        this.v12Migrator = new V12Migrator(
                plugin,
                connections,
                historyRepository,
                serialization,
                taskExecutor,
                this::checkConnection,
                this::hasMigratedFromV12,
                this::markMigrationFromV12Completed
        );
        this.backupCleanupService = new BackupCleanupService(plugin);
        initializeDatabase(true);
    }

    public void initializeDatabase(boolean showSuccessMessage) {
        this.shuttingDown = false;
        taskExecutor.ensureRunning();
        connections.initialize(showSuccessMessage, historyRepository::startTasks);
    }

    public void closeConnection() {
        shuttingDown = true;
        try {
            historyRepository.stopTasks();
            historyRepository.flushLootHistoryQueueSynchronously();
            taskExecutor.shutdown(5);
            connections.close();
        } catch (Exception e) {
            plugin.error("Ошибка закрытия сессии базы данных: " + e.getMessage());
        }
    }

    private void checkConnection() {
        connections.checkConnection(() -> initializeDatabase(false));
    }

    public boolean loadCacheStats(String cacheName, Cache cache) {
        return statsRepository.loadCacheStats(cacheName, cache);
    }

    public void saveCacheStatsSynchronously(Cache cache) {
        statsRepository.saveCacheStatsSynchronously(cache);
    }

    public void saveCacheStatsAsync(Cache cache) {
        statsRepository.saveCacheStatsAsync(cache);
    }

    public void initializeDefaultStats(String cacheName) {
        statsRepository.initializeDefaultStats(cacheName);
    }

    public void addLootHistoryEntryAsync(String cacheName, String playerName, ItemStack item) {
        historyRepository.addLootHistoryEntryAsync(cacheName, playerName, item);
    }

    public void forceFlushPendingHistory() {
        historyRepository.forceFlushPendingHistory();
    }

    public List<HistoryEntry> getLootHistorySynchronously(String cacheName) {
        return historyRepository.getLootHistorySynchronously(cacheName);
    }

    public CompletableFuture<List<HistoryEntry>> getLootHistoryAsync(String cacheName) {
        return historyRepository.getLootHistoryAsync(cacheName);
    }

    public void deleteCacheStatsAndHistory(String cacheName) {
        taskExecutor.submit(() -> {
            checkConnection();
            try (Connection connection = connections.getConnection()) {
                connection.setAutoCommit(false);
                statsRepository.deleteCacheStats(cacheName, connection);
                historyRepository.deleteLootHistory(cacheName, connection);
                connection.commit();
            } catch (SQLException e) {
                plugin.error("Ошибка при удалении данных тайника из базы: " + e.getMessage());
            }
        });
    }

    public void cleanupOldBackups() {
        backupCleanupService.cleanupOldBackups();
    }

    public boolean hasMigratedFromV12() {
        return v12Migrator.hasMigratedFromV12();
    }

    public void markMigrationFromV12Completed() {
        v12Migrator.markMigrationFromV12Completed();
    }

    public CompletableFuture<Void> performEfficientV12MigrationAsync(List<String> cacheNames) {
        return v12Migrator.migrateAsync(cacheNames);
    }

    public void performEfficientV12Migration(List<String> cacheNames) {
        performEfficientV12MigrationAsync(cacheNames).join();
    }

    public static class HistoryEntry {
        private final String playerName;
        private final ItemStack item;
        private final long timestamp;

        public HistoryEntry(String playerName, ItemStack item, long timestamp) {
            this.playerName = playerName;
            this.item = item;
            this.timestamp = timestamp;
        }

        public String getPlayerName() { return playerName; }
        public ItemStack getItem() { return item; }
        public long getTimestamp() { return timestamp; }
    }

    public static class LegacyHistoryEntry {
        public final String playerName;
        public final ItemStack item;
        public final long timestamp;

        public LegacyHistoryEntry(String playerName, ItemStack item, long timestamp) {
            this.playerName = playerName;
            this.item = item;
            this.timestamp = timestamp;
        }
    }
}
