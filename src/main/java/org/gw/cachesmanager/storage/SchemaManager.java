package org.gw.cachesmanager.storage;

import org.gw.cachesmanager.CachesManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class SchemaManager {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final CachesManager plugin;

    public SchemaManager(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void createTables(Connection connection, String type) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if ("mysql".equalsIgnoreCase(type)) {
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
                        "item_data MEDIUMTEXT, " +
                        "timestamp BIGINT)");
                createIndexSafely(statement, "CREATE INDEX idx_loot_history_cache ON loot_history(cache_name)");
                createIndexSafely(statement, "CREATE INDEX idx_loot_history_timestamp ON loot_history(timestamp)");

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
                createIndexSafely(statement, "CREATE INDEX IF NOT EXISTS idx_loot_history_cache ON loot_history(cache_name)");
                createIndexSafely(statement, "CREATE INDEX IF NOT EXISTS idx_loot_history_timestamp ON loot_history(timestamp)");

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
        applySchemaMigrations(connection, type);
    }

    public int getSchemaVersion(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT meta_value FROM plugin_meta WHERE meta_key = 'schema_version'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null) {
                        return Integer.parseInt(value.trim());
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "REPLACE INTO plugin_meta (meta_key, meta_value) VALUES ('schema_version', ?)")) {
            ps.setString(1, String.valueOf(version));
            ps.executeUpdate();
        }
    }

    public boolean columnExists(Connection connection, String table, String column) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = meta.getColumns(null, null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
                if (rs.next()) {
                    return true;
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    public void applySchemaMigrations(Connection connection, String type) {
        try {
            int version = getSchemaVersion(connection);
            if (version >= CURRENT_SCHEMA_VERSION) {
                return;
            }

            if (version < 2) {
                if ("mysql".equalsIgnoreCase(type) && columnExists(connection, "loot_history", "item_data")) {
                    try (Statement st = connection.createStatement()) {
                        st.execute("ALTER TABLE loot_history MODIFY COLUMN item_data MEDIUMTEXT");
                    } catch (SQLException e) {
                        plugin.error("Миграция схемы базы данных MySQL параметра loot_history.item_data в MEDIUMTEXT не применена: "
                                + e.getMessage() + ", schema_version не повышен, повтор при следующем запуске...");
                        return;
                    }
                }
            }

            setSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
            if (version < CURRENT_SCHEMA_VERSION) {
                plugin.log("Схема базы данных обновлена до версии " + CURRENT_SCHEMA_VERSION);
            }
        } catch (Exception e) {
            plugin.error("Не удалось применить миграции схемы БД: " + e.getMessage());
        }
    }

    private void createIndexSafely(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException ignored) {
        }
    }
}
