package org.gw.cachesmanager.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Setter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseManager {

    private final CachesManager plugin;
    private ExecutorService databaseExecutor;
    private HikariDataSource dataSource;
    @Setter
    private volatile boolean shuttingDown = false;

    private final ConcurrentLinkedQueue<HistoryWriteEntry> lootHistoryQueue = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> cachesWithRecentHistory = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private org.bukkit.scheduler.BukkitTask historyFlushTask;
    private org.bukkit.scheduler.BukkitTask historyPruneTask;

    public DatabaseManager(CachesManager plugin) {
        this.plugin = plugin;
        recreateDatabaseExecutor();
        initializeDatabase(true);
    }

    private void recreateDatabaseExecutor() {
        if (databaseExecutor != null && !databaseExecutor.isShutdown() && !databaseExecutor.isTerminated()) {
            return;
        }
        if (databaseExecutor != null) {
            try {
                databaseExecutor.shutdown();
                if (!databaseExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (Exception ignored) {}
        }
        this.databaseExecutor = Executors.newSingleThreadExecutor();
    }

    private void submitDatabaseTask(Runnable task) {
        recreateDatabaseExecutor();
        if (databaseExecutor != null && !databaseExecutor.isShutdown() && !databaseExecutor.isTerminated()) {
            databaseExecutor.submit(task);
        }
    }

    public void initializeDatabase(boolean showSuccessMessage) {
        recreateDatabaseExecutor();
        silenceHikariLogger();

        try {
            ConfigManager config = plugin.getConfigManager();
            String type = config != null ? config.getDatabaseType() : "sqlite";
            HikariConfig hikariConfig = new HikariConfig();

            if (type.equals("mysql")) {
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
                hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMySqlHost() + ":" + config.getMySqlPort() + "/" + config.getMySqlDatabase() + "?allowPublicKeyRetrieval=true");
                hikariConfig.setUsername(config.getMySqlUsername());
                hikariConfig.setPassword(config.getMySqlPassword());
                hikariConfig.addDataSourceProperty("useSSL", String.valueOf(config.isMySqlSsl()));
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
                hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
                hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
                hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
                hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
                hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
                hikariConfig.setMaximumPoolSize(10);
                hikariConfig.setMinimumIdle(2);
                hikariConfig.setIdleTimeout(600000);
                hikariConfig.setMaxLifetime(1800000);
                hikariConfig.setConnectionTimeout(10000);
            } else {
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "storage.db").getAbsolutePath());
                hikariConfig.addDataSourceProperty("journal_mode", "WAL");
                hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
                hikariConfig.addDataSourceProperty("busy_timeout", "5000");
                hikariConfig.setMaximumPoolSize(1);
                hikariConfig.setConnectionTestQuery("SELECT 1");
            }

            hikariConfig.setLeakDetectionThreshold(60000);

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            dataSource = new HikariDataSource(hikariConfig);
            silenceHikariLogger();
            scheduleHistoryFlushTask();
            scheduleHistoryPruneTask();

            if (showSuccessMessage) {
                if (type.equals("mysql")) {
                    plugin.console("&#00FF5A◆ CachesManager &f| Подключение к базе данных MySQL &#00FF5Aуспешно &fустановлено!");
                } else {
                    plugin.console("&#00FF5A◆ CachesManager &f| База данных SQLite &#00FF5Aуспешно &fподключена и загружена!");
                }
            }

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                if (type.equals("mysql")) {
                    statement.execute("CREATE TABLE IF NOT EXISTS cache_stats (" +
                            "cache_name VARCHAR(64) PRIMARY KEY, " +
                            "open_count INT, " +
                            "total_loot_given INT, " +
                            "last_opened BIGINT, " +
                            "first_opened BIGINT, " +
                            "max_daily_opens INT, " +
                            "created_time BIGINT, " +
                            "interval_sum BIGINT, " +
                            "interval_count INT)");

                    statement.execute("CREATE TABLE IF NOT EXISTS cache_top_players (" +
                            "cache_name VARCHAR(64), " +
                            "player_name VARCHAR(16), " +
                            "open_count INT, " +
                            "PRIMARY KEY (cache_name, player_name))");

                    statement.execute("CREATE TABLE IF NOT EXISTS cache_daily_opens (" +
                            "cache_name VARCHAR(64), " +
                            "open_date VARCHAR(10), " +
                            "open_count INT, " +
                            "PRIMARY KEY (cache_name, open_date))");

                    statement.execute("CREATE TABLE IF NOT EXISTS loot_history (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "cache_name VARCHAR(64), " +
                            "player_name VARCHAR(16), " +
                            "item_data TEXT, " +
                            "timestamp BIGINT)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_loot_history_cache ON loot_history(cache_name)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_loot_history_timestamp ON loot_history(timestamp)");

                    statement.execute("CREATE TABLE IF NOT EXISTS plugin_meta (" +
                            "meta_key VARCHAR(64) PRIMARY KEY, " +
                            "meta_value TEXT)");

                    statement.execute("CREATE TABLE IF NOT EXISTS v12_migration_status (" +
                            "cache_name VARCHAR(64) PRIMARY KEY, " +
                            "stats_migrated BOOLEAN DEFAULT FALSE, " +
                            "history_migrated BOOLEAN DEFAULT FALSE, " +
                            "stats_error TEXT, " +
                            "history_error TEXT, " +
                            "migrated_at BIGINT)");
                } else {
                    statement.execute("CREATE TABLE IF NOT EXISTS cache_stats (" +
                            "cache_name TEXT PRIMARY KEY, " +
                            "open_count INTEGER, " +
                            "total_loot_given INTEGER, " +
                            "last_opened INTEGER, " +
                            "first_opened INTEGER, " +
                            "max_daily_opens INTEGER, " +
                            "created_time INTEGER, " +
                            "interval_sum INTEGER, " +
                            "interval_count INTEGER)");

                    statement.execute("CREATE TABLE IF NOT EXISTS cache_top_players (" +
                            "cache_name TEXT, " +
                            "player_name TEXT, " +
                            "open_count INTEGER, " +
                            "PRIMARY KEY (cache_name, player_name))");

                    statement.execute("CREATE TABLE IF NOT EXISTS cache_daily_opens (" +
                            "cache_name TEXT, " +
                            "open_date TEXT, " +
                            "open_count INTEGER, " +
                            "PRIMARY KEY (cache_name, open_date))");

                    statement.execute("CREATE TABLE IF NOT EXISTS loot_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "cache_name TEXT, " +
                            "player_name TEXT, " +
                            "item_data TEXT, " +
                            "timestamp INTEGER)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_loot_history_cache ON loot_history(cache_name)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_loot_history_timestamp ON loot_history(timestamp)");

                    statement.execute("CREATE TABLE IF NOT EXISTS plugin_meta (" +
                            "meta_key TEXT PRIMARY KEY, " +
                            "meta_value TEXT)");

                    statement.execute("CREATE TABLE IF NOT EXISTS v12_migration_status (" +
                            "cache_name TEXT PRIMARY KEY, " +
                            "stats_migrated INTEGER DEFAULT 0, " +
                            "history_migrated INTEGER DEFAULT 0, " +
                            "stats_error TEXT, " +
                            "history_error TEXT, " +
                            "migrated_at INTEGER)");
                }
            }
        } catch (Exception e) {
            plugin.error("Критический сбой инициализации базы данных: " + e.getMessage());
        }
    }

    private void checkConnection() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                plugin.error("Соединение с базой данных потеряно, переподключение...");
                initializeDatabase(false);
            }
        } catch (Exception e) {
            plugin.error("Не удалось переинициализировать базу данных: " + e.getMessage());
        }
    }

    public boolean loadCacheStats(String cacheName, org.gw.cachesmanager.caches.Cache cache) {
        checkConnection();
        boolean hasData = false;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM cache_stats WHERE cache_name = ?")) {
                ps.setString(1, cacheName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cache.setOpenCount(rs.getInt("open_count"));
                        cache.setTotalLootGiven(rs.getInt("total_loot_given"));
                        cache.setLastOpenedTime(rs.getLong("last_opened"));
                        cache.setFirstOpenedTime(rs.getLong("first_opened"));
                        cache.setMaxDailyOpens(rs.getInt("max_daily_opens"));
                        cache.setCreatedTime(rs.getLong("created_time"));
                        cache.setTotalIntervalSum(rs.getLong("interval_sum"));
                        cache.setIntervalCount(rs.getInt("interval_count"));
                        hasData = true;
                    }
                }
            }

            if (hasData) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT player_name, open_count FROM cache_top_players WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    try (ResultSet rs = ps.executeQuery()) {
                        cache.getRawTopPlayers().clear();
                        while (rs.next()) {
                            cache.getRawTopPlayers().put(rs.getString("player_name"), rs.getInt("open_count"));
                        }
                    }
                }

                try (PreparedStatement ps = connection.prepareStatement("SELECT open_date, open_count FROM cache_daily_opens WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    try (ResultSet rs = ps.executeQuery()) {
                        cache.getDailyOpens().clear();
                        while (rs.next()) {
                            cache.getDailyOpens().put(LocalDate.parse(rs.getString("open_date")), new AtomicInteger(rs.getInt("open_count")));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.error("Ошибка при синхронной загрузке данных тайника " + cacheName + " из базы: " + e.getMessage());
        }
        return hasData;
    }

    public void saveCacheStatsSynchronously(org.gw.cachesmanager.caches.Cache cache) {
        checkConnection();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "REPLACE INTO cache_stats (cache_name, open_count, total_loot_given, last_opened, first_opened, max_daily_opens, created_time, interval_sum, interval_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, cache.getName());
                ps.setInt(2, cache.getOpenCount());
                ps.setInt(3, cache.getTotalLootGiven());
                ps.setLong(4, cache.getLastOpenedTime());
                ps.setLong(5, cache.getFirstOpenedTime());
                ps.setInt(6, cache.getMaxDailyOpens());
                ps.setLong(7, cache.getCreatedTime());
                ps.setLong(8, cache.getTotalIntervalSum());
                ps.setInt(9, cache.getIntervalCount());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "REPLACE INTO cache_top_players (cache_name, player_name, open_count) VALUES (?, ?, ?)")) {
                for (var entry : cache.getTopPlayers().entrySet()) {
                    ps.setString(1, cache.getName());
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "REPLACE INTO cache_daily_opens (cache_name, open_date, open_count) VALUES (?, ?, ?)")) {
                for (var entry : cache.getDailyOpens().entrySet()) {
                    ps.setString(1, cache.getName());
                    ps.setString(2, entry.getKey().toString());
                    ps.setInt(3, entry.getValue().get());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.error("Ошибка при синхронном сохранении статов тайника: " + e.getMessage());
        }
    }

    public void saveCacheStatsAsync(org.gw.cachesmanager.caches.Cache cache) {
        String name = cache.getName();
        int openCount = cache.getOpenCount();
        int totalLootGiven = cache.getTotalLootGiven();
        long lastOpened = cache.getLastOpenedTime();
        long firstOpened = cache.getFirstOpenedTime();
        int maxDaily = cache.getMaxDailyOpens();
        long created = cache.getCreatedTime();
        long intervalSum = cache.getTotalIntervalSum();
        int intervalCount = cache.getIntervalCount();

        List<Map.Entry<String, Integer>> topSnapshot = new ArrayList<>(cache.getTopPlayers().entrySet());

        List<Map.Entry<LocalDate, Integer>> dailySnapshot = new ArrayList<>();
        for (var entry : cache.getDailyOpens().entrySet()) {
            dailySnapshot.add(Map.entry(entry.getKey(), entry.getValue().get()));
        }

        submitDatabaseTask(() -> {
            checkConnection();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "REPLACE INTO cache_stats (cache_name, open_count, total_loot_given, last_opened, first_opened, max_daily_opens, created_time, interval_sum, interval_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, name);
                    ps.setInt(2, openCount);
                    ps.setInt(3, totalLootGiven);
                    ps.setLong(4, lastOpened);
                    ps.setLong(5, firstOpened);
                    ps.setInt(6, maxDaily);
                    ps.setLong(7, created);
                    ps.setLong(8, intervalSum);
                    ps.setInt(9, intervalCount);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "REPLACE INTO cache_top_players (cache_name, player_name, open_count) VALUES (?, ?, ?)")) {
                    for (var entry : topSnapshot) {
                        ps.setString(1, name);
                        ps.setString(2, entry.getKey());
                        ps.setInt(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "REPLACE INTO cache_daily_opens (cache_name, open_date, open_count) VALUES (?, ?, ?)")) {
                    for (var entry : dailySnapshot) {
                        ps.setString(1, name);
                        ps.setString(2, entry.getKey().toString());
                        ps.setInt(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.error("Ошибка асинхронного сохранения статистики тайника в базе: " + e.getMessage());
            }
        });
    }

    public void initializeDefaultStats(String cacheName) {
        submitDatabaseTask(() -> {
            checkConnection();
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM cache_stats WHERE cache_name = ?")) {
                    check.setString(1, cacheName);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            return;
                        }
                    }
                }

                long now = System.currentTimeMillis();

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO cache_stats (cache_name, open_count, total_loot_given, last_opened, first_opened, max_daily_opens, created_time, interval_sum, interval_count) " +
                        "VALUES (?, 0, 0, 0, ?, 0, ?, 0, 0)")) {
                    ps.setString(1, cacheName);
                    ps.setLong(2, now);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.error("Ошибка инициализации статистики тайника в базе: " + e.getMessage());
            }
        });
    }

    public void addLootHistoryEntryAsync(String cacheName, String playerName, ItemStack item) {
        long timestamp = System.currentTimeMillis();
        lootHistoryQueue.offer(new HistoryWriteEntry(cacheName, playerName, item.clone(), timestamp));
        cachesWithRecentHistory.add(cacheName);
    }

    private void scheduleHistoryFlushTask() {
        if (historyFlushTask != null) {
            historyFlushTask.cancel();
        }
        historyFlushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flushLootHistoryQueue, 200L, 200L);
    }

    private void scheduleHistoryPruneTask() {
        if (historyPruneTask != null) {
            historyPruneTask.cancel();
        }

        historyPruneTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shuttingDown) return;

            int maxEntries = plugin.getConfigManager().getHistoryMaxEntries();
            java.util.List<String> toPrune = new java.util.ArrayList<>(cachesWithRecentHistory);

            for (String cacheName : toPrune) {
                if (shuttingDown) break;
                pruneOldHistory(cacheName, maxEntries);
            }
        }, 6000L, 6000L);
    }

    public void forceFlushPendingHistory() {
        submitDatabaseTask(this::flushLootHistoryQueue);
    }

    private void flushLootHistoryQueue() {
        if (lootHistoryQueue.isEmpty()) return;

        java.util.List<HistoryWriteEntry> batch = new java.util.ArrayList<>();
        HistoryWriteEntry entry;
        while ((entry = lootHistoryQueue.poll()) != null && batch.size() < 100) {
            batch.add(entry);
        }
        if (batch.isEmpty()) return;

        checkConnection();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO loot_history (cache_name, player_name, item_data, timestamp) VALUES (?, ?, ?, ?)")) {
                for (HistoryWriteEntry e : batch) {
                    ps.setString(1, e.cacheName);
                    ps.setString(2, e.playerName);
                    ps.setString(3, serializeItem(e.item));
                    ps.setLong(4, e.timestamp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();

        } catch (SQLException e) {
            plugin.error("Ошибка пакетной записи истории лута: " + e.getMessage());
            for (HistoryWriteEntry failedEntry : batch) {
                lootHistoryQueue.offer(failedEntry);
            }
        }
    }

    public void migrateLegacyHistory(String cacheName, List<LegacyHistoryEntry> entries) {
        if (entries.isEmpty()) return;
        cachesWithRecentHistory.add(cacheName);

        submitDatabaseTask(() -> {
            checkConnection();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO loot_history (cache_name, player_name, item_data, timestamp) VALUES (?, ?, ?, ?)")) {
                    for (LegacyHistoryEntry entry : entries) {
                        ps.setString(1, cacheName);
                        ps.setString(2, entry.playerName);
                        ps.setString(3, serializeItem(entry.item));
                        ps.setLong(4, entry.timestamp);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.error("Ошибка при миграции старой истории тайника " + cacheName + ": " + e.getMessage());
            }
        });
    }

    public List<HistoryEntry> getLootHistorySynchronously(String cacheName) {
        List<HistoryEntry> history = new ArrayList<>();
        checkConnection();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_name, item_data, timestamp FROM loot_history WHERE cache_name = ? ORDER BY id DESC LIMIT 300")) {
            ps.setString(1, cacheName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = deserializeItem(rs.getString("item_data"));
                    if (item != null) {
                        history.add(new HistoryEntry(rs.getString("player_name"), item, rs.getLong("timestamp")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.error("Ошибка синхронного чтения истории лота из базы: " + e.getMessage());
        }
        return history;
    }

    public java.util.concurrent.CompletableFuture<List<HistoryEntry>> getLootHistoryAsync(String cacheName) {
        java.util.concurrent.CompletableFuture<List<HistoryEntry>> future = new java.util.concurrent.CompletableFuture<>();
        submitDatabaseTask(() -> {
            List<HistoryEntry> result = getLootHistorySynchronously(cacheName);
            future.complete(result);
        });
        return future;
    }

    public void deleteCacheStatsAndHistory(String cacheName) {
        submitDatabaseTask(() -> {
            checkConnection();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cache_stats WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cache_top_players WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cache_daily_opens WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM loot_history WHERE cache_name = ?")) {
                    ps.setString(1, cacheName);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                plugin.error("Ошибка при удалении данных тайника из базы: " + e.getMessage());
            }
        });
    }

    public void closeConnection() {
        shuttingDown = true;
        silenceHikariLogger();

        try {
            if (historyFlushTask != null) {
                historyFlushTask.cancel();
            }
            if (historyPruneTask != null) {
                historyPruneTask.cancel();
            }
            cachesWithRecentHistory.clear();
            flushLootHistoryQueueSynchronously();

            if (databaseExecutor != null) {
                databaseExecutor.shutdown();
                if (!databaseExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            }
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception e) {
            plugin.error("Ошибка закрытия сессии базы данных: " + e.getMessage());
        }
    }

    private void flushLootHistoryQueueSynchronously() {
        if (lootHistoryQueue.isEmpty() || dataSource == null) return;
        java.util.List<HistoryWriteEntry> batch = new java.util.ArrayList<>();
        HistoryWriteEntry entry;
        while ((entry = lootHistoryQueue.poll()) != null) {
            batch.add(entry);
        }
        if (batch.isEmpty()) return;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO loot_history (cache_name, player_name, item_data, timestamp) VALUES (?, ?, ?, ?)")) {
                for (HistoryWriteEntry e : batch) {
                    ps.setString(1, e.cacheName);
                    ps.setString(2, e.playerName);
                    ps.setString(3, serializeItem(e.item));
                    ps.setLong(4, e.timestamp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();

        } catch (SQLException e) {
            plugin.error("Ошибка финальной записи истории лута при выключении: " + e.getMessage());
        }
    }

    private void pruneOldHistory(String cacheName, int maxEntries) {
        if (maxEntries <= 0) return;

        checkConnection();
        try (Connection connection = dataSource.getConnection()) {
            String sql = "DELETE FROM loot_history " +
                    "WHERE cache_name = ? " +
                    "AND id < (SELECT id FROM loot_history WHERE cache_name = ? ORDER BY id DESC LIMIT 1 OFFSET ?)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, cacheName);
                ps.setString(2, cacheName);
                ps.setInt(3, maxEntries - 1);
                ps.executeUpdate();
            }
        } catch (SQLException e) {

        }
    }

    public void cleanupOldBackups() {
        if (!plugin.getConfigManager().isCleanupBackups()) {
            return;
        }

        int days = plugin.getConfigManager().getDeleteBackupsAfterDays();
        boolean deleteHistoryFolder = plugin.getConfigManager().isDeleteEmptyHistoryFolder();

        long cutoffTime = (days > 0)
                ? System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
                : Long.MAX_VALUE;

        cleanupBakFilesInFolder(new File(plugin.getDataFolder(), "caches"), cutoffTime);

        File historyFolder = new File(plugin.getDataFolder(), "caches/history");
        cleanupBakFilesInFolder(historyFolder, cutoffTime);

        if (deleteHistoryFolder && historyFolder.exists() && historyFolder.isDirectory()) {
            File[] files = historyFolder.listFiles();
            if (files != null && files.length == 0) {
                if (historyFolder.delete()) {
                    plugin.log("Пустая папка caches/history была автоматически удалена!");
                }
            }
        }
    }

    private void cleanupBakFilesInFolder(File folder, long cutoffTime) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return;

        File[] bakFiles = folder.listFiles((dir, name) -> name.endsWith(".yml.bak"));
        if (bakFiles == null) return;

        int deletedCount = 0;

        for (File bakFile : bakFiles) {
            if (bakFile.lastModified() < cutoffTime) {
                if (bakFile.delete()) {
                    deletedCount++;
                }
            }
        }

        if (deletedCount > 0) {
            plugin.log("Автоматически удалено " + deletedCount + " старых .bak файлов из папки " + folder.getName() + "!");
        }
    }

    private String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack deserializeItem(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private void silenceHikariLogger() {
        try {
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari", org.apache.logging.log4j.Level.WARN);
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari.pool", org.apache.logging.log4j.Level.WARN);
        } catch (Throwable ignored) {}

        try {
            java.util.logging.Logger.getLogger("com.zaxxer.hikari").setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("com.zaxxer.hikari.pool").setLevel(java.util.logging.Level.WARNING);
        } catch (Throwable ignored) {}
    }

    public boolean hasMigratedFromV12() {
        checkConnection();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT meta_value FROM plugin_meta WHERE meta_key = 'migrated_from_v12'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "true".equalsIgnoreCase(rs.getString(1));
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public void markMigrationFromV12Completed() {
        checkConnection();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "REPLACE INTO plugin_meta (meta_key, meta_value) VALUES ('migrated_from_v12', 'true')")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.error("Не удалось сохранить маркер миграции из версии 1.2: " + e.getMessage());
        }
    }

    private void recordV12MigrationStatus(String cacheName, boolean statsSuccess, boolean historySuccess,
                                          String statsError, String historyError) {
        checkConnection();
        try (Connection connection = dataSource.getConnection()) {
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

    public void performEfficientV12Migration(List<String> cacheNames) {
        if (cacheNames == null || cacheNames.isEmpty()) return;

        submitDatabaseTask(() -> {
            int migratedStats = 0;
            int migratedHistory = 0;
            int total = cacheNames.size();

            plugin.console("&#ffff00◆ CachesManager &f| Начинается миграция данных из версии 1.2 (" + total + " тайников)...");

            for (int i = 0; i < cacheNames.size(); i++) {
                String name = cacheNames.get(i);
                try {
                    if (migrateSingleCacheStatsFromYaml(name)) migratedStats++;
                    if (migrateSingleCacheHistoryFromV12(name)) migratedHistory++;
                } catch (Exception e) {
                    plugin.error("Ошибка миграции тайника " + name + ": " + e.getMessage());
                }

                if ((i + 1) % 25 == 0 || (i + 1) == total) {
                    plugin.console("&#ffff00◆ CachesManager &f| Миграция: &#ffff00" + (i + 1) + "&f/&#ffff00" + total + " (статистика: &#ffff00" + migratedStats + "&f, история: &#ffff00" + migratedHistory + "&f)");
                }
            }

            plugin.console("&#00FF5A◆ CachesManager &f| Миграция из версии &#ffff00успешно &f1.2 завершена! Статистика: &#ffff00" + migratedStats + "&f, история: &#ffff00" + migratedHistory);

            markMigrationFromV12Completed();
        });
    }

    private boolean migrateSingleCacheStatsFromYaml(String cacheName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM cache_stats WHERE cache_name = ?")) {
            ps.setString(1, cacheName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return false;
            }
        } catch (SQLException ignored) {}

        FileConfiguration config = plugin.getConfigManager().loadCacheConfig(cacheName);
        if (config == null) return false;

        ensureKeyUuidExists(config, cacheName);

        if (!config.contains("stats")) return false;

        try {
            File original = new File(plugin.getDataFolder(), "caches/" + cacheName + ".yml");
            if (original.exists()) {
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                File backup = new File(plugin.getDataFolder(), "caches/" + cacheName + ".yml.v12-" + timestamp + ".bak");
                java.nio.file.Files.copy(original.toPath(), backup.toPath());
            }

            long created = config.getLong("stats.created", System.currentTimeMillis());
            int opens = config.getInt("stats.open-count", 0);
            int loot = config.getInt("stats.total-loot-given", 0);
            long last = config.getLong("stats.last-opened", 0);
            long first = config.getLong("stats.first-opened", 0);
            int maxDaily = config.getInt("stats.max-daily", 0);
            long intervalSum = config.getLong("stats.interval-sum", 0);
            int intervalCount = config.getInt("stats.interval-count", 0);

            try (Connection connection = dataSource.getConnection()) {
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

            config.set("stats", null);
            plugin.getConfigManager().saveCacheConfig(cacheName);

            recordV12MigrationStatus(cacheName, true, false, null, null);
            return true;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            plugin.error("Не удалось мигрировать статистику " + cacheName + ": " + errorMsg);
            recordV12MigrationStatus(cacheName, false, false, errorMsg, null);
            return false;
        }
    }

    private void ensureKeyUuidExists(FileConfiguration config, String cacheName) {
        if (config.contains("key.uuid") && !config.getString("key.uuid", "").isEmpty()) {
            return;
        }

        String newUuid = java.util.UUID.randomUUID().toString();
        config.set("key.uuid", newUuid);

        plugin.getConfigManager().saveCacheConfig(cacheName);

        plugin.console("&#FB8808◆ CachesManager &f| Для тайника &#FB8808" + cacheName + " &fне был найден key.uuid в старой конфигурации, так что сгенерирован новый UUID ключа. А все старые ключи в инвентарях игроков больше не будут работать...");
    }

    private boolean migrateSingleCacheHistoryFromV12(String cacheName) {
        File historyFolder = new File(plugin.getDataFolder(), "caches/history");
        File oldFile = new File(historyFolder, cacheName + ".yml");
        if (!oldFile.exists()) return false;

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(oldFile);
            ConfigurationSection section = cfg.getConfigurationSection("history");
            if (section == null || section.getKeys(false).isEmpty()) {
                oldFile.delete();
                return false;
            }

            List<LegacyHistoryEntry> entries = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                ConfigurationSection e = section.getConfigurationSection(key);
                if (e == null) continue;
                String player = e.getString("player");
                long time = e.getLong("time", System.currentTimeMillis());
                String base64 = e.getString("item");
                if (player == null || base64 == null) continue;

                try {
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    ItemStack item = ItemStack.deserializeBytes(bytes);
                    if (item != null) entries.add(new LegacyHistoryEntry(player, item, time));
                } catch (Exception ignored) {}
            }

            if (entries.isEmpty()) {
                oldFile.delete();
                return false;
            }

            File backupFolder = new File(historyFolder, "v12_backup");
            if (!backupFolder.exists()) backupFolder.mkdirs();

            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backup = new File(backupFolder, cacheName + ".yml." + timestamp + ".bak");
            java.nio.file.Files.copy(oldFile.toPath(), backup.toPath());

            migrateLegacyHistory(cacheName, entries);
            oldFile.delete();

            recordV12MigrationStatus(cacheName, false, true, null, null);
            return true;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            plugin.error("Ошибка миграции истории " + cacheName + ": " + errorMsg);
            recordV12MigrationStatus(cacheName, false, false, null, errorMsg);
            return false;
        }
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

    private static class HistoryWriteEntry {
        final String cacheName;
        final String playerName;
        final ItemStack item;
        final long timestamp;

        HistoryWriteEntry(String cacheName, String playerName, ItemStack item, long timestamp) {
            this.cacheName = cacheName;
            this.playerName = playerName;
            this.item = item;
            this.timestamp = timestamp;
        }
    }
}