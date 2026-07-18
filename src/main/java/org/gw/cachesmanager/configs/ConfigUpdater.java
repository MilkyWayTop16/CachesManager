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
import java.nio.file.StandardCopyOption;
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

        String defaultVersion = defaultConfig.getString("config-version", "1.0");
        String userVersion = userConfig.getString("config-version", "1.0");
        if (compareVersions(userVersion, defaultVersion) >= 0) {
            return;
        }

        boolean changed = mergeMissingKeys(userConfig, defaultConfig, "");
        userConfig.set("config-version", defaultVersion);

        if (changed) {
            createBackup(targetFile);
            saveConfig(targetFile, userConfig, "Конфиг " + targetFile.getName() + " обновлён");
        } else {
            saveConfig(targetFile, userConfig, "Версия конфига " + targetFile.getName() + " обновлена до " + defaultVersion);
        }
    }

    public boolean replaceFromResourceIfOutdated(File targetFile, String resourcePath) {
        if (targetFile == null || resourcePath == null) return false;

        FileConfiguration defaultConfig = loadDefaultConfig(resourcePath);
        if (defaultConfig == null) {
            plugin.error("Не удалось загрузить дефолтный конфиг: " + resourcePath);
            return false;
        }

        String defaultVersion = defaultConfig.getString("config-version", "1.0");
        String userVersion = "0";
        if (targetFile.exists()) {
            FileConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
            userVersion = userConfig.getString("config-version", "0");
            if (compareVersions(userVersion, defaultVersion) >= 0) {
                return false;
            }
            createBackup(targetFile);
        }

        if (!copyResourceToFile(resourcePath, targetFile)) {
            plugin.error("Не удалось полностью обновить файл " + targetFile.getName() + " из " + resourcePath);
            return false;
        }

        plugin.log("Справочный файл " + targetFile.getName() + " полностью обновлён до версии " + defaultVersion
                + " (было: " + userVersion + ")");
        return true;
    }

    public boolean updateMenu(File targetFile, String resourcePath, List<String> forceUpdatePaths) {
        if (!targetFile.exists()) return false;

        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
        FileConfiguration defaultConfig = loadDefaultConfig(resourcePath);

        if (defaultConfig == null) return false;

        String defaultVersion = defaultConfig.getString("config-version", "1.0");
        String userVersion = userConfig.getString("config-version", "1.0");
        boolean versionOutdated = compareVersions(userVersion, defaultVersion) < 0;
        boolean hasForce = forceUpdatePaths != null && !forceUpdatePaths.isEmpty();

        if (!versionOutdated && !hasForce) {
            return false;
        }

        boolean changed = false;
        if (versionOutdated) {
            changed = mergeMissingKeys(userConfig, defaultConfig, "");
            if (hasForce) {
                for (String path : forceUpdatePaths) {
                    if (defaultConfig.contains(path) && !userConfig.contains(path)) {
                        userConfig.set(path, defaultConfig.get(path));
                        changed = true;
                    } else if (defaultConfig.isConfigurationSection(path)) {
                        changed |= mergeMissingKeys(userConfig, defaultConfig, path);
                    }
                }
            }
        }

        if (versionOutdated) {
            userConfig.set("config-version", defaultVersion);
        }

        if (!changed && !versionOutdated) {
            return false;
        }

        if (changed) {
            createBackup(targetFile);
            saveConfig(targetFile, userConfig, "Меню " + targetFile.getName() + " обновлено");
        } else {
            saveConfig(targetFile, userConfig, "Версия меню " + targetFile.getName() + " обновлена до " + defaultVersion);
        }
        return changed || versionOutdated;
    }

    private int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private int[] parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return new int[]{1, 0};
        }
        String[] parts = version.trim().split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private boolean mergeMissingKeys(FileConfiguration user, FileConfiguration defaults, String path) {
        boolean changed = false;
        ConfigurationSection defaultSection = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);

        if (defaultSection == null) return false;

        for (String key : defaultSection.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;

            if (!user.contains(fullKey)) {
                user.set(fullKey, defaults.get(fullKey));
                changed = true;
                continue;
            }

            if (defaultSection.isConfigurationSection(key)) {
                if (!user.isConfigurationSection(fullKey)) {
                    user.createSection(fullKey);
                    changed = true;
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

    private boolean copyResourceToFile(String resourcePath, File targetFile) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return false;
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            plugin.error("Ошибка копирования ресурса " + resourcePath + ": " + e.getMessage());
            return false;
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
