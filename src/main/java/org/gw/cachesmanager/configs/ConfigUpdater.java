package org.gw.cachesmanager.configs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.cachesmanager.CachesManager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConfigUpdater {

    private final CachesManager plugin;

    public ConfigUpdater(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void update(File targetFile, String resourcePath) {
        if (!targetFile.exists()) return;

        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
        FileConfiguration defaultConfig = loadDefaultConfig(resourcePath);

        if (defaultConfig == null) {
            plugin.error("Не удалось загрузить дефолтный конфиг: " + resourcePath);
            return;
        }

        int userVersion = getVersion(userConfig);
        int defaultVersion = getVersion(defaultConfig);

        if (userVersion >= defaultVersion) return;

        boolean changed = mergeMissingKeys(userConfig, defaultConfig, "");

        if (changed) {
            createBackup(targetFile);
        }
        userConfig.set("config-version", defaultConfig.getString("config-version", "1.0"));
        if (changed) {
            saveConfig(targetFile, userConfig, "Конфиг " + targetFile.getName() + " обновлён");
        } else {
            saveConfig(targetFile, userConfig, "Версия конфига " + targetFile.getName() + " обновлена до " + defaultConfig.getString("config-version", "1.0"));
        }
    }

    public boolean updateMenu(File targetFile, String resourcePath, List<String> forceUpdatePaths) {
        if (!targetFile.exists()) return false;

        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
        FileConfiguration defaultConfig = loadDefaultConfig(resourcePath);

        if (defaultConfig == null) return false;

        int userVersion = getVersion(userConfig);
        int defaultVersion = getVersion(defaultConfig);

        if (userVersion >= defaultVersion && (forceUpdatePaths == null || forceUpdatePaths.isEmpty())) {
            return false;
        }

        boolean changed = mergeMissingKeys(userConfig, defaultConfig, "");

        if (forceUpdatePaths != null) {
            for (String path : forceUpdatePaths) {
                if (defaultConfig.contains(path)) {
                    userConfig.set(path, defaultConfig.get(path));
                    changed = true;
                }
            }
        }

        if (changed) {
            createBackup(targetFile);
        }
        userConfig.set("config-version", defaultConfig.getString("config-version", "1.0"));
        if (changed) {
            saveConfig(targetFile, userConfig, "Меню " + targetFile.getName() + " обновлено");
        } else {
            saveConfig(targetFile, userConfig, "Версия меню " + targetFile.getName() + " обновлена до " + defaultConfig.getString("config-version", "1.0"));
        }

        return changed;
    }

    private int getVersion(FileConfiguration config) {
        String versionStr = config.getString("config-version", "1.0");
        try {
            return (int) (Double.parseDouble(versionStr) * 10);
        } catch (Exception e) {
            return 10;
        }
    }

    private boolean mergeMissingKeys(FileConfiguration user, FileConfiguration defaults, String path) {
        boolean changed = false;
        ConfigurationSection defaultSection = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);

        if (defaultSection == null) return false;

        for (String key : defaultSection.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;

            if (!user.contains(fullKey)) {
                user.set(fullKey, defaultSection.get(key));
                changed = true;
                continue;
            }

            if (defaultSection.isConfigurationSection(key)) {
                if (!user.isConfigurationSection(fullKey)) {
                    user.createSection(fullKey);
                }
                changed |= mergeMissingKeys(user, defaults, fullKey);
            }
        }
        return changed;
    }

    private FileConfiguration loadDefaultConfig(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void createBackup(File file) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backup = new File(file.getParentFile(), file.getName() + ".bak-" + timestamp);
            Files.copy(file.toPath(), backup.toPath());
        } catch (Exception e) {
            plugin.error("Не удалось создать бэкап файла " + file.getName() + ": " + e.getMessage());
        }
    }

    private void saveConfig(File file, FileConfiguration config, String successMessage) {
        try {
            config.save(file);
            plugin.log(successMessage);
        } catch (Exception e) {
            plugin.error("Ошибка сохранения файла " + file.getName() + ": " + e.getMessage());
        }
    }
}
