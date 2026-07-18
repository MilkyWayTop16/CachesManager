package org.gw.cachesmanager.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.gw.cachesmanager.CachesManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LootHistoryRepository {

    private final CachesManager plugin;
    private final DatabaseConnectionManager connections;
    private final ItemSerializationService serialization;
    private final Consumer<Runnable> taskSubmitter;
    private final Runnable connectionChecker;
    private final BooleanSupplier shuttingDown;

    private final ConcurrentLinkedQueue<HistoryWriteEntry> lootHistoryQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> cachesWithRecentHistory = ConcurrentHashMap.newKeySet();
    private BukkitTask historyFlushTask;
    private BukkitTask historyPruneTask;

    public LootHistoryRepository(CachesManager plugin,
                                 DatabaseConnectionManager connections,
                                 ItemSerializationService serialization,
                                 Consumer<Runnable> taskSubmitter,
                                 Runnable connectionChecker,
                                 BooleanSupplier shuttingDown) {
        this.plugin = plugin;
        this.connections = connections;
        this.serialization = serialization;
        this.taskSubmitter = taskSubmitter;
        this.connectionChecker = connectionChecker;
        this.shuttingDown = shuttingDown;
    }

    public void startTasks() {
        scheduleHistoryFlushTask();
        scheduleHistoryPruneTask();
    }

    public void stopTasks() {
        if (historyFlushTask != null) {
            historyFlushTask.cancel();
            historyFlushTask = null;
        }
        if (historyPruneTask != null) {
            historyPruneTask.cancel();
            historyPruneTask = null;
        }
        cachesWithRecentHistory.clear();
    }

    public void addLootHistoryEntryAsync(String cacheName, String playerName, ItemStack item) {
        long timestamp = System.currentTimeMillis();
        lootHistoryQueue.offer(new HistoryWriteEntry(cacheName, playerName, item.clone(), timestamp));
        cachesWithRecentHistory.add(cacheName);
    }

    public void forceFlushPendingHistory() {
        taskSubmitter.accept(this::flushLootHistoryQueue);
    }

    public void flushLootHistoryQueueSynchronously() {
        if (lootHistoryQueue.isEmpty() || connections.getDataSource() == null) return;
        List<HistoryWriteEntry> batch = drainQueue(Integer.MAX_VALUE);
        if (batch.isEmpty()) return;
        writeBatch(batch, true);
    }

    public void flushLootHistoryQueue() {
        if (lootHistoryQueue.isEmpty()) return;
        List<HistoryWriteEntry> batch = drainQueue(100);
        if (batch.isEmpty()) return;
        connectionChecker.run();
        if (!writeBatch(batch, false)) {
            for (HistoryWriteEntry failedEntry : batch) {
                lootHistoryQueue.offer(failedEntry);
            }
        }
    }

    public boolean insertHistoryEntriesSynchronously(String cacheName, List<DatabaseManager.LegacyHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) return true;
        cachesWithRecentHistory.add(cacheName);
        connectionChecker.run();
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO loot_history (cache_name, player_name, item_data, timestamp) VALUES (?, ?, ?, ?)")) {
                for (DatabaseManager.LegacyHistoryEntry entry : entries) {
                    String itemData = serialization.serializeItem(entry.item);
                    if (itemData == null || itemData.isEmpty()) {
                        throw new SQLException("Не удалось сериализовать предмет истории для " + cacheName);
                    }
                    ps.setString(1, cacheName);
                    ps.setString(2, entry.playerName);
                    ps.setString(3, itemData);
                    ps.setLong(4, entry.timestamp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            plugin.error("Ошибка при миграции старой истории тайника " + cacheName + ": " + e.getMessage());
            return false;
        }
    }

    public List<DatabaseManager.HistoryEntry> getLootHistorySynchronously(String cacheName) {
        List<DatabaseManager.HistoryEntry> history = new ArrayList<>();
        connectionChecker.run();
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_name, item_data, timestamp FROM loot_history WHERE cache_name = ? ORDER BY id DESC LIMIT 300")) {
            ps.setString(1, cacheName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = serialization.deserializeItem(rs.getString("item_data"));
                    if (item != null) {
                        history.add(new DatabaseManager.HistoryEntry(rs.getString("player_name"), item, rs.getLong("timestamp")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.error("Ошибка синхронного чтения истории лота из базы: " + e.getMessage());
        }
        return history;
    }

    public CompletableFuture<List<DatabaseManager.HistoryEntry>> getLootHistoryAsync(String cacheName) {
        CompletableFuture<List<DatabaseManager.HistoryEntry>> future = new CompletableFuture<>();
        taskSubmitter.accept(() -> future.complete(getLootHistorySynchronously(cacheName)));
        return future;
    }

    public void deleteLootHistory(String cacheName, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM loot_history WHERE cache_name = ?")) {
            ps.setString(1, cacheName);
            ps.executeUpdate();
        }
    }

    public void pruneOldHistory(String cacheName, int maxEntries) {
        if (maxEntries <= 0) return;

        connectionChecker.run();
        try (Connection connection = connections.getConnection()) {
            String sql = "DELETE FROM loot_history " +
                    "WHERE cache_name = ? " +
                    "AND id < (SELECT id FROM loot_history WHERE cache_name = ? ORDER BY id DESC LIMIT 1 OFFSET ?)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, cacheName);
                ps.setString(2, cacheName);
                ps.setInt(3, maxEntries - 1);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    cachesWithRecentHistory.remove(cacheName);
                }
            }
        } catch (SQLException e) {
            plugin.error("Ошибка очистки старой истории лута тайника " + cacheName + ": " + e.getMessage());
        }
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
            if (shuttingDown.getAsBoolean()) return;

            int maxEntries = plugin.getConfigManager().getHistoryMaxEntries();
            List<String> toPrune = new ArrayList<>(cachesWithRecentHistory);

            for (String cacheName : toPrune) {
                if (shuttingDown.getAsBoolean()) break;
                pruneOldHistory(cacheName, maxEntries);
            }
        }, 6000L, 6000L);
    }

    private List<HistoryWriteEntry> drainQueue(int limit) {
        List<HistoryWriteEntry> batch = new ArrayList<>();
        HistoryWriteEntry entry;
        while ((entry = lootHistoryQueue.poll()) != null && batch.size() < limit) {
            batch.add(entry);
        }
        return batch;
    }

    private boolean writeBatch(List<HistoryWriteEntry> batch, boolean shutdownMode) {
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO loot_history (cache_name, player_name, item_data, timestamp) VALUES (?, ?, ?, ?)")) {
                for (HistoryWriteEntry e : batch) {
                    ps.setString(1, e.cacheName);
                    ps.setString(2, e.playerName);
                    ps.setString(3, serialization.serializeItem(e.item));
                    ps.setLong(4, e.timestamp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            if (shutdownMode) {
                plugin.error("Ошибка финальной записи истории лута при выключении: " + e.getMessage());
            } else {
                plugin.error("Ошибка пакетной записи истории лута: " + e.getMessage());
            }
            return false;
        }
    }

    private static final class HistoryWriteEntry {
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
