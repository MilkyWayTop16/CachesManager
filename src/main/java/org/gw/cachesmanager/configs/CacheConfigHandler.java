package org.gw.cachesmanager.configs;

import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
public class CacheConfigHandler {
    private final CachesManager plugin;
    private final File cachesFolder;
    private final Map<String, FileConfiguration> cacheConfigs = new ConcurrentHashMap<>();
    private final Set<String> dirtyCacheNames = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean savingInProgress = new AtomicBoolean(false);
    private BukkitRunnable batchSaveTask;

    public CacheConfigHandler(CachesManager plugin) {
        this.plugin = plugin;
        this.cachesFolder = new File(plugin.getDataFolder(), "caches");
        if (!cachesFolder.exists()) cachesFolder.mkdirs();
        startBatchSaver();
    }

    public void startBatchSaver() {
        if (batchSaveTask != null) batchSaveTask.cancel();
        batchSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dirtyCacheNames.isEmpty() || savingInProgress.get()) return;
                Set<String> toSave = new HashSet<>(dirtyCacheNames);
                dirtyCacheNames.clear();
                savingInProgress.set(true);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        for (String name : toSave) {
                            FileConfiguration cfg = cacheConfigs.get(name);
                            if (cfg != null) {
                                File file = new File(cachesFolder, name + ".yml");
                                synchronized (cfg) {
                                    try {
                                        cfg.save(file);
                                    } catch (IOException e) {
                                        plugin.error("Ошибка асинхронного сохранения конфигурации тайника &#FB8808" + name + " &f(Ошибка: &#FB8808" + e.getMessage() + "&f)...");
                                    }
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

    public void stopBatchSaver() {
        if (batchSaveTask != null) batchSaveTask.cancel();
    }

    public void clearCache() {
        cacheConfigs.clear();
        dirtyCacheNames.clear();
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
            plugin.error("Не удалось загрузить .yml-конфигурацию тайника &#FB8808" + cacheName + ".yml &f(Ошибка: &#FB8808" + e.getMessage() + "&f)...");
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
        try {
            for (String name : toSave) {
                FileConfiguration cfg = cacheConfigs.get(name);
                if (cfg != null) {
                    synchronized (cfg) {
                        try {
                            cfg.save(new File(cachesFolder, name + ".yml"));
                        } catch (IOException e) {
                            plugin.error("Ошибка принудительного сохранения конфигурации тайника &#FB8808" + name + " &f(Ошибка: &#FB8808" + e.getMessage() + "&f)...");
                        }
                    }
                }
            }
        } finally {
            savingInProgress.set(false);
            if (plugin.isEnabled()) startBatchSaver();
        }
    }

    public void preloadConfigs() {
        File[] cacheFiles = cachesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (cacheFiles != null) {
            for (File file : cacheFiles) {
                loadCacheConfig(file.getName().replace(".yml", ""));
            }
        }
        plugin.log("Успешно подгружено конфигураций тайников в кэш памяти: &#ffff00" + cacheConfigs.size());
    }

    public boolean renameCacheConfig(String oldName, String newName) {
        File oldFile = new File(cachesFolder, oldName + ".yml");
        File newFile = new File(cachesFolder, newName + ".yml");
        if (!oldFile.exists() || newFile.exists()) {
            plugin.error("Не удалось переименовать файл конфигурации тайника, целевой файл уже существует или исходный отсутствует...");
            return false;
        }
        if (!oldFile.renameTo(newFile)) {
            plugin.error("Не удалось переименовать файл конфигурации тайника на уровне файловой системы...");
            return false;
        }
        FileConfiguration cfg = loadCacheConfig(newName);
        if (cfg == null) return false;
        synchronized (cfg) {
            cfg.set("cache-name", newName);
            cfg.set("key.name", "&eКлюч от тайника " + newName);
            cfg.set("key.lore", Arrays.asList("&7Для тайника: " + newName, "&7Одноразовый предмет"));
        }
        saveCacheConfig(newName);
        cacheConfigs.remove(oldName);
        dirtyCacheNames.remove(oldName);
        return true;
    }

    public void createCacheConfig(String cacheName) {
        File file = new File(cachesFolder, cacheName + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                synchronized (cfg) {
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
                    cfg.set("key.uuid", UUID.randomUUID().toString());
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
                }
                cacheConfigs.put(cacheName, cfg);
                plugin.log("Успешно создан новый файл конфигурации тайника на диске: &#ffff00" + cacheName + ".yml");
            } catch (IOException e) {
                plugin.error("Ошибка создания файла конфигурации тайника &#FB8808" + cacheName + " &f(Ошибка: &#FB8808" + e.getMessage() + "&f)...");
            }
        }
    }

    public void deleteCacheConfig(String cacheName) {
        File file = new File(cachesFolder, cacheName + ".yml");
        if (file.exists() && file.delete()) {
            cacheConfigs.remove(cacheName);
            dirtyCacheNames.remove(cacheName);
            plugin.log("Файл конфигурации тайника &#ffff00" + cacheName + ".yml &fуспешно удален с диска");
        } else {
            plugin.error("Не удалось корректно удалить файл конфигурации тайника &#FB8808" + cacheName + ".yml &fс диска...");
        }
    }

    public void setCacheHologramText(String cacheName, String hologramText) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("hologram.text", hologramText.replace("\\n", "\n"));
            }
            saveCacheConfig(cacheName);
        }
    }

    public boolean isCacheUnbreakable(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return true;
        synchronized (cfg) {
            return cfg.getBoolean("unbreakable", true);
        }
    }

    public void setCacheUnbreakable(String cacheName, boolean unbreakable) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("unbreakable", unbreakable);
            }
            saveCacheConfig(cacheName);
        }
    }

    public ItemStack getKeyItem(String cacheName, FileConfiguration cacheConfig) {
        if (cacheConfig == null) return new ItemStack(Material.TRIPWIRE_HOOK);
        synchronized (cacheConfig) {
            Material material = Material.matchMaterial(cacheConfig.getString("key.material", "TRIPWIRE_HOOK"));
            if (material == null) material = Material.TRIPWIRE_HOOK;
            ItemStack key = new ItemStack(material);
            ItemMeta meta = key.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(HexColors.translate(cacheConfig.getString("key.name", "&eКлюч от тайника " + cacheName).replace("{name-cache}", cacheName)));
                List<String> lore = cacheConfig.getStringList("key.lore");
                meta.setLore(HexColors.translate(lore.stream().map(s -> s.replace("{name-cache}", cacheName)).collect(Collectors.toList())));
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
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cache-name"), PersistentDataType.STRING, cacheName);
                key.setItemMeta(meta);
            }
            return key;
        }
    }

    public String getCacheDisplayName(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return cacheName;
        synchronized (cfg) {
            return cfg.getString("cache-name", cacheName);
        }
    }

    public Location getCacheLocation(FileConfiguration cacheConfig) {
        if (cacheConfig == null) return null;
        synchronized (cacheConfig) {
            String worldName = cacheConfig.getString("location.world");
            if (worldName == null) return null;
            World world = plugin.getServer().getWorld(worldName);
            if (world == null || !cacheConfig.isSet("location.x") || !cacheConfig.isSet("location.y") || !cacheConfig.isSet("location.z")) return null;
            return new Location(world, cacheConfig.getDouble("location.x"), cacheConfig.getDouble("location.y"), cacheConfig.getDouble("location.z"));
        }
    }

    public void setCacheLocation(String cacheName, Location location) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                if (location == null) {
                    cfg.set("location", null);
                } else {
                    cfg.set("location.world", location.getWorld().getName());
                    cfg.set("location.x", location.getX());
                    cfg.set("location.y", location.getY());
                    cfg.set("location.z", location.getZ());
                }
            }
            saveCacheConfig(cacheName);
        }
    }

    public Material getCacheBlock(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return null;
        synchronized (cfg) {
            String blockType = cfg.getString("block-type");
            if (blockType == null) return null;
            try {
                return Material.valueOf(blockType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public void setCacheBlock(String cacheName, Material blockType) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("block-type", blockType != null ? blockType.name() : null);
            }
            saveCacheConfig(cacheName);
        }
    }

    public List<Entry<ItemStack, Integer>> getCacheLootWithChances(FileConfiguration cacheConfig) {
        List<Entry<ItemStack, Integer>> lootWithChances = new ArrayList<>();
        if (cacheConfig == null) return lootWithChances;
        synchronized (cacheConfig) {
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
    }

    public void setCacheHologramEnabled(String cacheName, boolean enabled) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("hologram.enabled", enabled);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setCacheHologramOffset(String cacheName, double x, double y, double z) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("hologram.offset.x", x);
                cfg.set("hologram.offset.y", y);
                cfg.set("hologram.offset.z", z);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setCacheLoot(String cacheName, List<Entry<ItemStack, Integer>> lootWithChances) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("loot", null);
                for (int i = 0; i < lootWithChances.size(); i++) {
                    Entry<ItemStack, Integer> entry = lootWithChances.get(i);
                    if (entry.getKey() != null && entry.getKey().getType() != Material.AIR) {
                        String path = "loot.item" + i;
                        cfg.set(path + ".item", entry.getKey());
                        cfg.set(path + ".chance", Math.max(0, Math.min(100, entry.getValue())));
                    }
                }
            }
            saveCacheConfig(cacheName);
        }
    }

    public String getCacheAnimation(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return "up_and_down";
        synchronized (cfg) {
            return cfg.getString("animation", "up_and_down");
        }
    }

    public void setCacheAnimation(String cacheName, String animation) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("animation", animation);
            }
            saveCacheConfig(cacheName);
        }
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(cacheConfigs.keySet());
    }

    public void setItemChance(String cacheName, int index, int chance) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                int newChance = Math.max(0, Math.min(100, chance));
                cfg.set("loot.item" + index + ".chance", newChance);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyMaterial(String cacheName, String material) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.material", material);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyName(String cacheName, String name) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.name", name);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyLore(String cacheName, List<String> lore) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.lore", lore);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyCustomModelData(String cacheName, int cmd) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.custom-model-data", cmd);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyGlow(String cacheName, boolean glow) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.glow", glow);
            }
            saveCacheConfig(cacheName);
        }
    }

    public void setKeyFlags(String cacheName, List<String> flags) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg != null) {
            synchronized (cfg) {
                cfg.set("key.flags", flags);
            }
            saveCacheConfig(cacheName);
        }
    }

    public String getKeyUuid(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return null;
        synchronized (cfg) {
            String uuid = cfg.getString("key.uuid");
            if (uuid == null || uuid.isEmpty()) {
                uuid = UUID.randomUUID().toString();
                cfg.set("key.uuid", uuid);
                saveCacheConfig(cacheName);
            }
            return uuid;
        }
    }

    public List<String> getKeyFlags(String cacheName) {
        FileConfiguration cfg = loadCacheConfig(cacheName);
        if (cfg == null) return Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");
        synchronized (cfg) {
            return cfg.getStringList("key.flags");
        }
    }

    public String sanitizeCacheName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_").replace("..", "_").replace("../", "_").replace("./", "_").replaceAll("^[.]+", "").replaceAll("[.]+$", "");
        if (s.length() > 64 || s.isEmpty()) return "";
        return s;
    }
}