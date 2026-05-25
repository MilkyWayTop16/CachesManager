package org.gw.cachesmanager.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.cachesmanager.CachesManager;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MenuConfigHandler {
    private final CachesManager plugin;
    private final File menusFolder;
    private final Map<String, FileConfiguration> menuConfigCache = new ConcurrentHashMap<>();

    public MenuConfigHandler(CachesManager plugin) {
        this.plugin = plugin;
        this.menusFolder = new File(plugin.getDataFolder(), "menus");
        if (!menusFolder.exists()) menusFolder.mkdirs();
    }

    public void createDefaultMenus() {
        createDefaultFile("menus/global-menu.yml");
        createDefaultFile("menus/loot-menu.yml");
        createDefaultFile("menus/chance-menu.yml");
        createDefaultFile("menus/hologram-menu.yml");
        createDefaultFile("menus/stats-menu.yml");
        createDefaultFile("menus/example-menu.yml");
        createDefaultFile("menus/history-menu.yml");
        createDefaultFile("menus/key-menu.yml");
        createDefaultFile("animations.yml");
    }

    private void createDefaultFile(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }

    public FileConfiguration loadMenuConfig(String fileName) {
        FileConfiguration cached = menuConfigCache.get(fileName);
        if (cached != null) return cached;

        File file;
        if (fileName.equals("animations.yml")) {
            file = new File(plugin.getDataFolder(), fileName);
        } else {
            file = new File(menusFolder, fileName);
        }

        if (!file.exists()) {
            if (fileName.equals("animations.yml")) {
                createDefaultFile("animations.yml");
            } else {
                createDefaultFile("menus/" + fileName);
            }
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8));
            menuConfigCache.put(fileName, cfg);
            return cfg;
        } catch (Exception e) {
            plugin.console("&#ffff00◆ CachesManager &f| Не удалось загрузить меню " + fileName);
            return null;
        }
    }

    public void clearCache() {
        menuConfigCache.clear();
    }

    public String sanitizeMenuFile(String file) {
        if (file == null) return "global-menu.yml";
        String s = file.trim();
        if (!s.toLowerCase().endsWith(".yml")) s += ".yml";
        s = new java.io.File(s).getName();
        if (s.length() > 64 || s.isEmpty() || !s.toLowerCase().endsWith(".yml")) return "global-menu.yml";
        return s;
    }
}