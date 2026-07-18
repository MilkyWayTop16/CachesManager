package org.gw.cachesmanager.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

public final class DatabaseConnectionManager {

    private final CachesManager plugin;
    private final SchemaManager schemaManager;
    private final BooleanSupplier shuttingDown;
    private HikariDataSource dataSource;
    private String databaseType = "sqlite";

    public DatabaseConnectionManager(CachesManager plugin, SchemaManager schemaManager, BooleanSupplier shuttingDown) {
        this.plugin = plugin;
        this.schemaManager = schemaManager;
        this.shuttingDown = shuttingDown;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not available");
        }
        return dataSource.getConnection();
    }

    public void initialize(boolean showSuccessMessage, Runnable onReady) {
        silenceHikariLogger();

        try {
            ConfigManager config = plugin.getConfigManager();
            String type = config != null ? config.getDatabaseType() : "sqlite";
            this.databaseType = type;

            HikariConfig hikariConfig = new HikariConfig();

            if ("mysql".equalsIgnoreCase(type)) {
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

            if (showSuccessMessage) {
                if ("mysql".equalsIgnoreCase(type)) {
                    plugin.console("&#00FF5A◆ CachesManager &f| Подключение к базе данных MySQL &#00FF5Aуспешно &fустановлено!");
                } else {
                    plugin.console("&#00FF5A◆ CachesManager &f| База данных SQLite &#00FF5Aуспешно &fподключена и загружена!");
                }
            }

            try (Connection connection = dataSource.getConnection()) {
                schemaManager.createTables(connection, type);
            }

            if (onReady != null) {
                onReady.run();
            }
        } catch (Exception e) {
            plugin.error("Критический сбой инициализации базы данных: " + e.getMessage());
        }
    }

    public void checkConnection(Runnable reinitialize) {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                plugin.error("Соединение с базой данных потеряно, переподключение...");
                if (reinitialize != null && !shuttingDown.getAsBoolean()) {
                    reinitialize.run();
                }
            }
        } catch (Exception e) {
            plugin.error("Не удалось переинициализировать базу данных: " + e.getMessage());
        }
    }

    public void close() {
        silenceHikariLogger();
        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds == null) {
            return;
        }
        try {
            if (!ds.isClosed()) {
                ds.close();
            }
        } catch (Exception e) {
            plugin.error("Ошибка закрытия пула соединений БД: " + e.getMessage());
        }
    }

    public void silenceHikariLogger() {
        try {
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari", org.apache.logging.log4j.Level.WARN);
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari.pool", org.apache.logging.log4j.Level.WARN);
        } catch (Throwable ignored) {}

        try {
            java.util.logging.Logger.getLogger("com.zaxxer.hikari").setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("com.zaxxer.hikari.pool").setLevel(java.util.logging.Level.WARNING);
        } catch (Throwable ignored) {}
    }
}
