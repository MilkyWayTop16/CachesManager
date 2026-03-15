package org.gw.cachesmanager.managers;

import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Getter
public class ConfigManager {

    private final CachesManager plugin;
    private FileConfiguration config;
    private final File cachesFolder;
    private final File menusFolder;

    private boolean logsInConsole;

    private final Map<String, FileConfiguration> menuConfigCache = new ConcurrentHashMap<>();
    private final Map<String, FileConfiguration> cacheConfigs = new ConcurrentHashMap<>();

    private final Set<String> dirtyCacheNames = ConcurrentHashMap.newKeySet();
    private BukkitRunnable batchSaveTask;

    private final AtomicBoolean savingInProgress = new AtomicBoolean(false);
    private int modeTimeoutSeconds;
    private int historyMaxEntries;
    private int historyMaxDays;

    public ConfigManager(CachesManager plugin) {
        this.plugin = plugin;
        this.cachesFolder = new File(plugin.getDataFolder(), "caches");
        this.menusFolder = new File(plugin.getDataFolder(), "menus");
        loadMainConfig();
        createFolders();
        createDefaultMenus();
        preloadConfigs();
        startBatchSaver();
    }

    private void startBatchSaver() {
        if (batchSaveTask != null) batchSaveTask.cancel();

        batchSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dirtyCacheNames.isEmpty() || savingInProgress.get()) {
                    return;
                }

                Set<String> toSave = new HashSet<>(dirtyCacheNames);
                dirtyCacheNames.clear();

                savingInProgress.set(true);

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        for (String name : toSave) {
                            FileConfiguration cfg = cacheConfigs.get(name);
                            if (cfg != null) {
                                File file = new File(cachesFolder, name + ".yml");
                                try {
                                    cfg.save(file);
                                } catch (IOException e) {
                                    logError("Ошибка асинхронного сохранения тайника " + name);
                                }
                            }
                        }
                    } finally {
                        savingInProgress.set(false);
                    }
                });
            }
        };

        batchSaveTask.runTaskTimer(plugin, 100L, 100L);
    }

    private void loadMainConfig() {
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

        } catch (Exception e) {
            config = new YamlConfiguration();
            this.logsInConsole = false;
            this.historyMaxEntries = 225;
            this.historyMaxDays = 90;
            plugin.console("&#ffff00◆ CachesManager &f| Ошибка загрузки главного конфига config.yml");
        }
    }

    public boolean isLogsInConsoleEnabled() {
        return logsInConsole;
    }

    private void logError(String message) {
        plugin.console("&#ffff00◆ CachesManager &f| " + message);
    }

    private void logInfo(String message) {
        if (isLogsInConsoleEnabled()) {
            plugin.console("&#ffff00◆ CachesManager &f| " + message);
        }
    }

    private void createFolders() {
        if (!cachesFolder.exists()) cachesFolder.mkdirs();
        if (!menusFolder.exists()) menusFolder.mkdirs();
    }

    private void createDefaultMenus() {
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

    private void preloadConfigs() {
        File[] cacheFiles = cachesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (cacheFiles != null) {
            for (File file : cacheFiles) {
                String cacheName = file.getName().replace(".yml", "");
                loadCacheConfig(cacheName);
            }
        }
        File[] menuFiles = menusFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("example-menu.yml"));
        if (menuFiles != null) {
            for (File file : menuFiles) {
                loadMenuConfig(file.getName());
            }
        }
        loadMenuConfig("animations.yml");
    }

    public String reloadConfig() {
        try {
            if (batchSaveTask != null) batchSaveTask.cancel();
            loadMainConfig();
            menuConfigCache.clear();
            cacheConfigs.clear();
            dirtyCacheNames.clear();
            createFolders();
            createDefaultMenus();
            preloadConfigs();
            startBatchSaver();
            logInfo("Конфигурация успешно перезагружена");
            return null;
        } catch (Exception e) {
            String error = "Ошибка при перезагрузке конфигурации: " + e.getMessage();
            logError("Критическая ошибка перезагрузки конфигурации");
            return error;
        }
    }

    public FileConfiguration loadMenuConfig(String fileName) {
        FileConfiguration cached = menuConfigCache.get(fileName);
        if (cached != null) return cached;

        File file = new File(fileName.equals("animations.yml") ? plugin.getDataFolder() : menusFolder, fileName);
        if (!file.exists()) {
            createDefaultFile(fileName.equals("animations.yml") ? "animations.yml" : "menus/" + fileName);
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            menuConfigCache.put(fileName, cfg);
            return cfg;
        } catch (Exception e) {
            logError("Не удалось загрузить меню " + fileName);
            return null;
        }
    }

    public void executeActions(Player player, String path) {
        executeActions(player, path, null);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        List<String> actions = config.getStringList("actions." + path);
        if (actions.isEmpty()) return;
        Map<String, String> ph = placeholders != null ? new HashMap<>(placeholders) : new HashMap<>();
        if (player != null) {
            ph.put("player", player.getName());
            ph.put("player_display", player.getDisplayName());
        }

        for (String action : actions) {
            String processed = action;
            for (Entry<String, String> entry : ph.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue())
                        .replace("<" + entry.getKey() + ">", entry.getValue());
            }

            if (processed.startsWith("[message]")) {
                if (player != null) player.sendMessage(HexColors.translate(processed.substring(9).trim()));
            } else if (processed.startsWith("[message-console]")) {
                plugin.console(HexColors.translate(processed.substring(17).trim()));
            } else if (processed.startsWith("[broadcast]")) {
                Bukkit.broadcastMessage(HexColors.translate(processed.substring(11).trim()));
            } else if (processed.startsWith("[sound]")) {
                String[] parts = processed.substring(7).trim().split(" ");
                if (parts.length >= 1 && player != null) {
                    try {
                        Sound sound = Sound.valueOf(parts[0].toUpperCase());
                        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                        player.playSound(player.getLocation(), sound, volume, pitch);
                    } catch (Exception ignored) {}
                }
            } else if (processed.startsWith("[title]")) {
                String[] parts = processed.substring(7).trim().split(" ");
                if (parts.length >= 1 && player != null) {
                    String title = HexColors.translate(parts[0]);
                    int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                    int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                    int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                    player.sendTitle(title, "", fadeIn, stay, fadeOut);
                }
            } else if (processed.startsWith("[subtitle]")) {
                String[] parts = processed.substring(10).trim().split(" ");
                if (parts.length >= 1 && player != null) {
                    String subtitle = HexColors.translate(parts[0]);
                    int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                    int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                    int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                    player.sendTitle("", subtitle, fadeIn, stay, fadeOut);
                }
            } else if (processed.startsWith("[actionbar]")) {
                if (player != null) {
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(HexColors.translate(processed.substring(11).trim())));
                }
            } else if (processed.startsWith("[console-command]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed.substring(17).trim());
            } else if (processed.startsWith("[player-command]")) {
                if (player != null) Bukkit.dispatchCommand(player, processed.substring(16).trim());
            } else if (processed.startsWith("[effect]")) {
                String[] parts = processed.substring(8).trim().split(" ");
                if (parts.length >= 1 && player != null) {
                    try {
                        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                        int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
                        int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
                        player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                    } catch (Exception ignored) {}
                }
            } else if (processed.startsWith("[teleport]")) {
                String[] parts = processed.substring(10).trim().split(" ");
                if (parts.length >= 4 && player != null) {
                    try {
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        double z = Double.parseDouble(parts[2]);
                        World world = Bukkit.getWorld(parts[3]);
                        if (world != null) player.teleport(new Location(world, x, y, z));
                    } catch (Exception ignored) {}
                }
            } else if (processed.startsWith("[give-item]")) {
                String[] parts = processed.substring(11).trim().split(" ");
                if (parts.length >= 2 && player != null) {
                    try {
                        Material material = Material.valueOf(parts[0].toUpperCase());
                        int amount = Integer.parseInt(parts[1]);
                        player.getInventory().addItem(new ItemStack(material, amount));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public FileConfiguration loadCacheConfig(String cacheName) {
        FileConfiguration cached = cacheConfigs.get(cacheName);
        if (cached != null) return cached;

        File file = new File(cachesFolder, cacheName + ".yml");
        if (!file.exists()) return null;

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            cacheConfigs.put(cacheName, cfg);
            return cfg;
        } catch (Exception e) {
            logError("Не удалось загрузить конфиг тайника " + cacheName + ".yml");
            return null;
        }
    }

    public void saveCacheConfig(String cacheName) {
        if (cacheConfigs.containsKey(cacheName)) {
            dirtyCacheNames.add(cacheName);
        }
    }

    public void forceSaveAllCacheConfigs() {
        if (batchSaveTask != null) batchSaveTask.cancel();

        Set<String> toSave = new HashSet<>(dirtyCacheNames);
        dirtyCacheNames.clear();

        if (toSave.isEmpty()) {
            if (plugin.isEnabled()) startBatchSaver();
            return;
        }

        savingInProgress.set(true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (String name : toSave) {
                    FileConfiguration cfg = cacheConfigs.get(name);
                    if (cfg != null) {
                        try {
                            cfg.save(new File(cachesFolder, name + ".yml"));
                        } catch (IOException e) {
                            logError("Ошибка принудительного сохранения " + name);
                        }
                    }
                }
            } finally {
                savingInProgress.set(false);
                if (plugin.isEnabled()) startBatchSaver();
            }
        });
    }

    public boolean renameCacheConfig(String oldName, String newName) {
        File oldFile = new File(cachesFolder, oldName + ".yml");
        File newFile = new File(cachesFolder, newName + ".yml");
        if (!oldFile.exists() || newFile.exists()) return false;
        if (!oldFile.renameTo(newFile)) {
            logError("Не удалось переименовать файл конфига");
            return false;
        }
        FileConfiguration cfg = loadCacheConfig(newName);
        if (cfg == null) return false;
        cfg.set("cache-name", newName);
        cfg.set("key.name", "&eКлюч от тайника " + newName);
        cfg.set("key.lore", Arrays.asList("&7Для тайника: " + newName, "&7Одноразовый предмет"));
        saveCacheConfig(newName);
        cacheConfigs.remove(oldName);
        dirtyCacheNames.remove(oldName);
        return true;
    }

    public void setCacheHologramText(String cacheName, String hologramText) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("hologram.text", hologramText.replace("\\n", "\n"));
            saveCacheConfig(cacheName);
        }
    }

    public void createCacheConfig(String cacheName) {
        File file = new File(cachesFolder, cacheName + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                cfg.set("cache-name", cacheName);
                cfg.set("hologram.enabled", true);
                cfg.set("hologram.text", "&eТайник " + cacheName);
                cfg.set("hologram.offset.x", 0.0);
                cfg.set("hologram.offset.y", 0.5);
                cfg.set("hologram.offset.z", 0.0);
                cfg.set("hologram.visible-to-all", true);
                cfg.set("key.material", "TRIPWIRE_HOOK");
                cfg.set("key.name", "&eКлюч от тайника " + cacheName);
                cfg.set("key.lore", Arrays.asList("&7Для тайника: " + cacheName, "&7Одноразовый предмет"));
                cfg.set("key.flags", Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES"));

                cfg.set("key.custom-model-data", 0);
                cfg.set("animation", "up_and_down");
                cfg.set("loot", new ArrayList<>());
                cfg.set("unbreakable", true);
                cfg.set("stats.created", System.currentTimeMillis());
                cfg.set("stats.open-count", 0);
                cfg.set("stats.total-loot-given", 0);
                cfg.set("stats.last-opened", 0);
                cfg.set("stats.top-players", new LinkedHashMap<>());
                cfg.set("stats.first-opened", System.currentTimeMillis());
                cfg.set("stats.max-daily", 0);
                cfg.set("stats.daily-opens", new LinkedHashMap<>());
                cfg.set("stats.interval-sum", 0);
                cfg.set("stats.interval-count", 0);
                cfg.save(file);
                cacheConfigs.put(cacheName, cfg);
                logInfo("Создан новый конфиг тайника: " + cacheName);
            } catch (IOException e) {
                logError("Ошибка создания конфига тайника " + cacheName);
            }
        }
    }

    public boolean isCacheUnbreakable(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        return cfg != null ? cfg.getBoolean("unbreakable", true) : true;
    }

    public void setCacheUnbreakable(String cacheName, boolean unbreakable) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("unbreakable", unbreakable);
            saveCacheConfig(cacheName);
        }
    }

    public void deleteCacheConfig(String cacheName) {
        File file = new File(cachesFolder, cacheName + ".yml");
        if (file.exists() && file.delete()) {
            cacheConfigs.remove(cacheName);
            dirtyCacheNames.remove(cacheName);
        } else {
            logError("Не удалось удалить файл конфига тайника " + cacheName);
        }
    }

    public ItemStack getKeyItem(String cacheName, FileConfiguration cacheConfig) {
        Material material = Material.matchMaterial(cacheConfig.getString("key.material", "TRIPWIRE_HOOK"));
        if (material == null) material = Material.TRIPWIRE_HOOK;

        ItemStack key = new ItemStack(material);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexColors.translate(cacheConfig.getString("key.name", "&eКлюч от тайника " + cacheName)
                    .replace("{name-cache}", cacheName)));

            List<String> lore = cacheConfig.getStringList("key.lore");
            meta.setLore(HexColors.translate(lore.stream()
                    .map(s -> s.replace("{name-cache}", cacheName))
                    .collect(Collectors.toList())));

            meta.getEnchants().keySet().forEach(meta::removeEnchant);

            boolean glow = cacheConfig.getBoolean("key.glow", false);
            if (glow) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            for (String flag : cacheConfig.getStringList("key.flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag));
                } catch (Exception ignored) {}
            }

            meta.setCustomModelData(cacheConfig.getInt("key.custom-model-data", 0));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "cache-name"),
                    PersistentDataType.STRING,
                    cacheName
            );

            key.setItemMeta(meta);
        }
        return key;
    }

    public String getCacheDisplayName(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        return cfg != null ? cfg.getString("cache-name", cacheName) : cacheName;
    }

    public Location getCacheLocation(FileConfiguration cacheConfig) {
        String worldName = cacheConfig.getString("location.world");
        if (worldName == null) return null;
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || !cacheConfig.isSet("location.x") || !cacheConfig.isSet("location.y") || !cacheConfig.isSet("location.z")) {
            return null;
        }
        return new Location(world, cacheConfig.getDouble("location.x"), cacheConfig.getDouble("location.y"), cacheConfig.getDouble("location.z"));
    }

    public void setCacheLocation(String cacheName, Location location) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            if (location == null) {
                cfg.set("location", null);
            } else {
                cfg.set("location.world", location.getWorld().getName());
                cfg.set("location.x", location.getX());
                cfg.set("location.y", location.getY());
                cfg.set("location.z", location.getZ());
            }
            saveCacheConfig(cacheName);
        }
    }

    public Material getCacheBlock(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return null;
        String blockType = cfg.getString("block-type");
        if (blockType == null) return null;
        try {
            return Material.valueOf(blockType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setCacheBlock(String cacheName, Material blockType) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("block-type", blockType != null ? blockType.name() : null);
            saveCacheConfig(cacheName);
        }
    }

    public List<Entry<ItemStack, Integer>> getCacheLootWithChances(FileConfiguration cacheConfig) {
        List<Entry<ItemStack, Integer>> lootWithChances = new ArrayList<>();
        ConfigurationSection lootSection = cacheConfig.getConfigurationSection("loot");
        if (lootSection == null) return lootWithChances;
        List<String> keys = new ArrayList<>(lootSection.getKeys(false));
        keys.sort(Comparator.comparingInt(k -> Integer.parseInt(k.replace("item", ""))));
        for (String key : keys) {
            ConfigurationSection itemSection = lootSection.getConfigurationSection(key);
            if (itemSection != null) {
                int chance = itemSection.getInt("chance", 50);
                ItemStack item = itemSection.getItemStack("item");
                if (item != null && item.getType() != Material.AIR) {
                    lootWithChances.add(new AbstractMap.SimpleEntry<>(item, chance));
                }
            }
        }
        return lootWithChances;
    }

    public void setCacheHologramEnabled(String cacheName, boolean enabled) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("hologram.enabled", enabled);
            saveCacheConfig(cacheName);
        }
    }

    public void setCacheHologramOffset(String cacheName, double x, double y, double z) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("hologram.offset.x", x);
            cfg.set("hologram.offset.y", y);
            cfg.set("hologram.offset.z", z);
            saveCacheConfig(cacheName);
        }
    }

    public void setCacheLoot(String cacheName, List<Entry<ItemStack, Integer>> lootWithChances) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("loot", null);
            for (int i = 0; i < lootWithChances.size(); i++) {
                Entry<ItemStack, Integer> entry = lootWithChances.get(i);
                if (entry.getKey() != null && entry.getKey().getType() != Material.AIR) {
                    String path = "loot.item" + i;
                    cfg.set(path + ".item", entry.getKey());
                    cfg.set(path + ".chance", Math.max(0, Math.min(100, entry.getValue())));
                }
            }
            saveCacheConfig(cacheName);
        }
    }

    public String getCacheAnimation(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        return cfg != null ? cfg.getString("animation", "up_and_down") : "up_and_down";
    }

    public void setCacheAnimation(String cacheName, String animation) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("animation", animation);
            saveCacheConfig(cacheName);
        }
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(cacheConfigs.keySet());
    }

    public void setItemChance(String cacheName, int index, int chance) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            int newChance = Math.max(0, Math.min(100, chance));
            cfg.set("loot.item" + index + ".chance", newChance);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyMaterial(String cacheName, String material) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.material", material);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyName(String cacheName, String name) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.name", name);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyLore(String cacheName, List<String> lore) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.lore", lore);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyCustomModelData(String cacheName, int cmd) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.custom-model-data", cmd);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyGlow(String cacheName, boolean glow) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.glow", glow);
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyFlags(String cacheName, List<String> flags) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            cfg.set("key.flags", flags);
            saveCacheConfig(cacheName);
        }
    }

    public List<String> getKeyFlags(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        return cfg != null ? cfg.getStringList("key.flags") : Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");
    }

    public boolean isUpdateCheckerEnabled() {
        return config.getBoolean("settings.update-checker.enabled", true);
    }

    public String getUpdateNotifyMode() {
        return config.getString("settings.update-checker.notify-mode", "both").toLowerCase();
    }

    public int getUpdatePeriodicIntervalHours() {
        return Math.max(1, config.getInt("settings.update-checker.periodic-interval-hours", 6));
    }

    public boolean isBStatsEnabled() {
        return config.getBoolean("settings.bstats.enabled", true);
    }

}