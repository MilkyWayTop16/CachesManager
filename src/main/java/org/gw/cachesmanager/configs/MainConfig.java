package org.gw.cachesmanager.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.cachesmanager.CachesManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Getter
public class MainConfig {
    private final CachesManager plugin;
    private FileConfiguration config;
    private boolean logsInConsole;
    private int modeTimeoutSeconds;
    private int historyMaxEntries;
    private int historyMaxDays;
    private boolean trimHologramItemName;
    private boolean continueAnimationIfPlayersNearby;
    private int orphanedAnimationRadius;

    private boolean cleanupBackups;
    private int deleteBackupsAfterDays;
    private boolean deleteEmptyHistoryFolder;

    public MainConfig(CachesManager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }

        new ConfigUpdater(plugin).update(configFile, "config.yml");

        try {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8));
            this.logsInConsole = config.getBoolean("settings.logs-in-console.enable", false);
            this.modeTimeoutSeconds = config.getInt("settings.mode-timeout.time", 30);
            this.historyMaxEntries = config.getInt("settings.history.max-entries", 225);
            this.historyMaxDays = config.getInt("settings.history.max-days", 90);
            this.trimHologramItemName = config.getBoolean("settings.trim-hologram-item-name.enabled", true);

            this.continueAnimationIfPlayersNearby = config.getBoolean("settings.animations.continue-if-players-nearby", true);
            this.orphanedAnimationRadius = config.getInt("settings.animations.orphaned-radius", 64);

            this.cleanupBackups = config.getBoolean("settings.database.cleanup-backups", true);
            this.deleteBackupsAfterDays = config.getInt("settings.database.delete-backups-after-days", 1);
            this.deleteEmptyHistoryFolder = config.getBoolean("settings.database.delete-empty-history-folder", true);

        } catch (Exception e) {
            config = new YamlConfiguration();
            this.logsInConsole = false;
            this.historyMaxEntries = 225;
            this.historyMaxDays = 90;
            this.trimHologramItemName = true;
            this.continueAnimationIfPlayersNearby = true;
            this.orphanedAnimationRadius = 64;

            this.cleanupBackups = true;
            this.deleteBackupsAfterDays = 7;
            this.deleteEmptyHistoryFolder = true;

            plugin.console("&#FF5D00◆ CachesManager &f| Ошибка загрузки главного конфига &#FF5D00config.yml&f...");
        }
    }
}