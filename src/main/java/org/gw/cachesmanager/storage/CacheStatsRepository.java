package org.gw.cachesmanager.storage;

import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class CacheStatsRepository {

    private final CachesManager plugin;
    private final DatabaseConnectionManager connections;
    private final Consumer<Runnable> taskSubmitter;
    private final Runnable connectionChecker;

    public CacheStatsRepository(CachesManager plugin,
                                DatabaseConnectionManager connections,
                                Consumer<Runnable> taskSubmitter,
                                Runnable connectionChecker) {
        this.plugin = plugin;
        this.connections = connections;
        this.taskSubmitter = taskSubmitter;
        this.connectionChecker = connectionChecker;
    }

    public boolean loadCacheStats(String cacheName, Cache cache) {
        connectionChecker.run();
        boolean hasData = false;
        try (Connection connection = connections.getConnection()) {
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

    public void saveCacheStatsSynchronously(Cache cache) {
        connectionChecker.run();
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            writeStats(connection, cache.getName(), cache.getOpenCount(), cache.getTotalLootGiven(),
                    cache.getLastOpenedTime(), cache.getFirstOpenedTime(), cache.getMaxDailyOpens(),
                    cache.getCreatedTime(), cache.getTotalIntervalSum(), cache.getIntervalCount(),
                    new ArrayList<>(cache.getTopPlayers().entrySet()),
                    snapshotDaily(cache));
            connection.commit();
        } catch (SQLException e) {
            plugin.error("Ошибка при синхронном сохранении статов тайника: " + e.getMessage());
        }
    }

    public void saveCacheStatsAsync(Cache cache) {
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
        List<Map.Entry<LocalDate, Integer>> dailySnapshot = snapshotDaily(cache);

        taskSubmitter.accept(() -> {
            connectionChecker.run();
            try (Connection connection = connections.getConnection()) {
                connection.setAutoCommit(false);
                writeStats(connection, name, openCount, totalLootGiven, lastOpened, firstOpened,
                        maxDaily, created, intervalSum, intervalCount, topSnapshot, dailySnapshot);
                connection.commit();
            } catch (SQLException e) {
                plugin.error("Ошибка асинхронного сохранения статистики тайника в базе: " + e.getMessage());
            }
        });
    }

    public void initializeDefaultStats(String cacheName) {
        taskSubmitter.accept(() -> {
            connectionChecker.run();
            try (Connection connection = connections.getConnection()) {
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

    public void deleteCacheStats(String cacheName, Connection connection) throws SQLException {
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
    }

    private List<Map.Entry<LocalDate, Integer>> snapshotDaily(Cache cache) {
        List<Map.Entry<LocalDate, Integer>> dailySnapshot = new ArrayList<>();
        for (var entry : cache.getDailyOpens().entrySet()) {
            dailySnapshot.add(Map.entry(entry.getKey(), entry.getValue().get()));
        }
        return dailySnapshot;
    }

    private void writeStats(Connection connection,
                            String name,
                            int openCount,
                            int totalLootGiven,
                            long lastOpened,
                            long firstOpened,
                            int maxDaily,
                            long created,
                            long intervalSum,
                            int intervalCount,
                            List<Map.Entry<String, Integer>> topSnapshot,
                            List<Map.Entry<LocalDate, Integer>> dailySnapshot) throws SQLException {
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
    }
}
