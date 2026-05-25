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

    public MainConfig(CachesManager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        try {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8));
            this.logsInConsole = config.getBoolean("settings.logs-in-console.enable", false);
            this.modeTimeoutSeconds = config.getInt("settings.mode-timeout.time", 30);
            this.historyMaxEntries = config.getInt("settings.history.max-entries", 225);
            this.historyMaxDays = config.getInt("settings.history.max-days", 90);
            if (!config.contains("settings.trim-hologram-item-name.enabled")) {
                config.set("settings.trim-hologram-item-name.enabled", true);
                config.save(configFile);
            }
            this.trimHologramItemName = config.getBoolean("settings.trim-hologram-item-name.enabled", true);
        } catch (Exception e) {
            config = new YamlConfiguration();
            this.logsInConsole = false;
            this.historyMaxEntries = 225;
            this.historyMaxDays = 90;
            this.trimHologramItemName = true;
            plugin.console("&#ffff00◆ CachesManager &f| Ошибка загрузки главного конфига config.yml...");
        }
    }
}