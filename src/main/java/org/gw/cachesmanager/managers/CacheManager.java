package org.gw.cachesmanager.managers;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CacheManager {
    private final CachesManager plugin;
    private final ConfigManager configManager;
    @Setter
    private HologramManager hologramManager;
    private final ItemManager itemManager;
    private final AnimationsManager animationsManager;
    private final StatsManager statsManager;
    private final LootHistoryManager lootHistoryManager;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private volatile boolean isReloading = false;

    public CacheManager(CachesManager plugin, ConfigManager configManager, HologramManager hologramManager,
                        ItemManager itemManager, AnimationsManager animationsManager, StatsManager statsManager,
                        LootHistoryManager lootHistoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.hologramManager = hologramManager;
        this.itemManager = itemManager;
        this.animationsManager = animationsManager;
        this.statsManager = statsManager;
        this.lootHistoryManager = lootHistoryManager;
    }

    public void loadCaches() {
        if (isReloading) return;
        isReloading = true;

        if (hologramManager != null) {
            hologramManager.clearAllHolograms();
        }
        caches.clear();

        List<String> cacheNames = configManager.getCacheNames();

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int batch = Math.min(10, cacheNames.size() - index);
                for (int i = 0; i < batch; i++) {
                    String cacheName = cacheNames.get(index++);
                    Cache cache = new Cache(cacheName);
                    caches.put(cacheName, cache);

                    if (cache.getLocation() != null && cache.isHologramEnabled() && hologramManager != null) {
                        hologramManager.createHologram(cacheName, cache.getLocation(), cache.getHologramText());
                    }
                    plugin.getMenuManager().initializeCachePageLoot(cacheName);
                }

                if (index >= cacheNames.size()) {
                    isReloading = false;
                    cancel();
                    plugin.console("&#ffff00◆ CachesManager &f| Загружено " + caches.size() + " тайников");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public boolean createCache(String cacheName) {
        if (caches.containsKey(cacheName)) {
            plugin.log("Тайник с таким именем уже существует: &#ffff00" + cacheName);
            return false;
        }
        configManager.createCacheConfig(cacheName);
        Cache cache = new Cache(cacheName);
        caches.put(cacheName, cache);

        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().initializeCachePageLoot(cacheName);
        }

        plugin.log("Тайник успешно создан: &#ffff00" + cacheName);
        return true;
    }

    public boolean deleteCache(String cacheName) {
        Cache cache = caches.remove(cacheName);
        if (cache != null) {
            cache.removeHologram();
            if (plugin.getMenuManager() != null) {
                plugin.getMenuManager().clearCacheForCache(cacheName);
            }
            if (plugin.getLootHistoryManager() != null) {
                plugin.getLootHistoryManager().deleteHistory(cacheName);
            }
            configManager.deleteCacheConfig(cacheName);
            plugin.log("Тайник успешно удалён: &#ffff00" + cacheName);
            return true;
        }
        plugin.log("Не удалось удалить тайник (не найден): &#ffff00" + cacheName);
        return false;
    }

    public boolean renameCache(String oldName, String newName) {
        if (caches.containsKey(newName)) {
            plugin.log("Не удалось переименовать — имя уже существует: &#ffff00" + newName);
            return false;
        }
        Cache cache = caches.remove(oldName);
        if (cache == null) {
            plugin.log("Тайник для переименования не найден: &#ffff00" + oldName);
            return false;
        }

        if (hologramManager != null) {
            hologramManager.removeCacheHologram(oldName);
        }

        if (!configManager.renameCacheConfig(oldName, newName)) {
            caches.put(oldName, cache);
            plugin.log("Не удалось переименовать конфиг: &#ffff00" + oldName);
            return false;
        }

        cache.setName(newName);
        caches.put(newName, cache);

        if (cache.isHologramEnabled() && cache.getLocation() != null && hologramManager != null) {
            hologramManager.createHologram(newName, cache.getLocation(), cache.getHologramText());
        }

        plugin.log("Тайник переименован: &#ffff00" + oldName + " &f→ &#ffff00" + newName);
        return true;
    }

    public Cache getCache(String cacheName) {
        return caches.get(cacheName);
    }

    public Cache getCacheByLocation(Location location) {
        for (Cache cache : caches.values()) {
            if (cache.getLocation() != null && cache.getLocation().equals(location)) {
                return cache;
            }
        }
        return null;
    }

    public Map<String, Cache> getCaches() {
        return new ConcurrentHashMap<>(caches);
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(caches.keySet());
    }

    public void removeAllHolograms() {
        if (hologramManager != null) {
            hologramManager.clearAllHolograms();
        }
    }

    public class Cache {
        public String name;
        private String displayName;
        private String hologramText;
        @Getter private Location location;
        @Getter private Material blockType;
        private List<Entry<ItemStack, Integer>> lootWithChances;
        private final AtomicBoolean inUse = new AtomicBoolean(false);
        @Getter private boolean unbreakable;
        @Getter private String animation;
        @Getter private boolean hologramEnabled;
        @Getter private double hologramOffsetX;
        @Getter private double hologramOffsetY;
        @Getter private double hologramOffsetZ;

        @Getter private int openCount;
        @Getter private int totalLootGiven;
        @Getter private long createdTime;
        @Getter private long lastOpenedTime;

        private final Map<LocalDate, AtomicInteger> dailyOpens = new ConcurrentHashMap<>();
        @Getter private int maxDailyOpens;
        @Getter private long firstOpenedTime;
        private long lastOpenTimestamp;
        private long totalIntervalSum;
        private int intervalCount;

        @Getter
        private String keyMaterial = "TRIPWIRE_HOOK";
        private String keyName;
        private List<String> keyLore = new ArrayList<>();
        @Getter
        private int keyCustomModelData = 0;
        private boolean keyGlow = false;
        private List<String> keyFlags = Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");

        private final LinkedHashMap<String, Integer> topPlayers = new LinkedHashMap<>();

        public Cache(String name) {
            this.name = name;
            this.unbreakable = true;
            this.hologramEnabled = true;
            this.hologramOffsetX = 0.0;
            this.hologramOffsetY = 0.5;
            this.hologramOffsetZ = 0.0;
            this.openCount = 0;
            this.totalLootGiven = 0;
            this.createdTime = Instant.now().toEpochMilli();
            this.lastOpenedTime = 0;
            this.firstOpenedTime = 0;
            this.maxDailyOpens = 0;
            this.lastOpenTimestamp = 0;
            this.totalIntervalSum = 0;
            this.intervalCount = 0;
            this.keyMaterial = "TRIPWIRE_HOOK";
            this.keyName = null;
            this.keyLore = new ArrayList<>();
            this.keyCustomModelData = 0;
            this.keyGlow = false;
            this.keyFlags = Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");
            load();
        }

        void load() {
            FileConfiguration config = configManager.loadCacheConfig(name);
            if (config == null) {
                this.lootWithChances = new ArrayList<>();
                plugin.log("Не удалось загрузить конфигурацию для тайника: &#ffff00" + name);
                return;
            }
            this.location = configManager.getCacheLocation(config);
            this.blockType = configManager.getCacheBlock(name);
            this.lootWithChances = configManager.getCacheLootWithChances(config);
            this.unbreakable = configManager.isCacheUnbreakable(name);
            this.displayName = configManager.getCacheDisplayName(name);
            this.hologramText = config.getString("hologram.text");
            this.animation = configManager.getCacheAnimation(name);
            this.hologramEnabled = config.getBoolean("hologram.enabled", true);
            this.hologramOffsetX = config.getDouble("hologram.offset.x", 0.0);
            this.hologramOffsetY = config.getDouble("hologram.offset.y", 0.5);
            this.hologramOffsetZ = config.getDouble("hologram.offset.z", 0.0);

            this.createdTime = config.getLong("stats.created", Instant.now().toEpochMilli());
            this.openCount = config.getInt("stats.open-count", 0);
            this.totalLootGiven = config.getInt("stats.total-loot-given", 0);
            this.lastOpenedTime = config.getLong("stats.last-opened", 0);
            this.firstOpenedTime = config.getLong("stats.first-opened", 0);
            this.maxDailyOpens = config.getInt("stats.max-daily", 0);
            this.totalIntervalSum = config.getLong("stats.interval-sum", 0);
            this.intervalCount = config.getInt("stats.interval-count", 0);
            this.keyMaterial = config.getString("key.material", "TRIPWIRE_HOOK");
            this.keyName = config.getString("key.name");
            this.keyLore = config.getStringList("key.lore");
            this.keyCustomModelData = config.getInt("key.custom-model-data", 0);
            this.keyGlow = config.getBoolean("key.glow", false);
            this.keyFlags = configManager.getKeyFlags(name);

            dailyOpens.clear();
            ConfigurationSection dailySection = config.getConfigurationSection("stats.daily-opens");
            if (dailySection != null) {
                for (String key : dailySection.getKeys(false)) {
                    dailyOpens.put(LocalDate.parse(key), new AtomicInteger(dailySection.getInt(key)));
                }
            }

            ConfigurationSection topSection = config.getConfigurationSection("stats.top-players");
            if (topSection != null) {
                topPlayers.clear();
                for (String key : topSection.getKeys(false)) {
                    topPlayers.put(key, topSection.getInt(key));
                }
            }

            if (!animationsManager.getAnimations().containsKey(this.animation)) {
                this.animation = "default";
                configManager.setCacheAnimation(name, this.animation);
            }
            if (this.lootWithChances == null) this.lootWithChances = new ArrayList<>();
        }

        public void incrementOpenCount() {
            long now = Instant.now().toEpochMilli();
            openCount++;
            lastOpenedTime = now;
            if (firstOpenedTime == 0) firstOpenedTime = now;

            if (lastOpenTimestamp > 0) {
                totalIntervalSum += (now - lastOpenTimestamp);
                intervalCount++;
            }
            lastOpenTimestamp = now;

            LocalDate today = LocalDate.now();
            int count = dailyOpens.computeIfAbsent(today, k -> new AtomicInteger(0)).incrementAndGet();
            if (count > maxDailyOpens) maxDailyOpens = count;

            plugin.getConfigManager().saveCacheConfig(name);
        }

        public void addLootGiven(int amount) {
            totalLootGiven += amount;
            plugin.getConfigManager().saveCacheConfig(name);
        }

        public void recordPlayerOpen(String playerName) {
            if (playerName == null) return;

            topPlayers.merge(playerName, 1, Integer::sum);
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(topPlayers.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            if (sorted.size() > 5) sorted = sorted.subList(0, 5);
            topPlayers.clear();
            for (Map.Entry<String, Integer> e : sorted) topPlayers.put(e.getKey(), e.getValue());

            plugin.getConfigManager().saveCacheConfig(name);
        }

        public double getAverageIntervalMinutes() {
            if (intervalCount == 0) return 0;
            return Math.round((totalIntervalSum / (double) intervalCount) / 60000.0 * 10) / 10.0;
        }

        public Map<String, Integer> getTopPlayers() {
            return new LinkedHashMap<>(topPlayers);
        }

        public boolean isInUse() {
            return inUse.get();
        }

        public void setInUse(boolean value) {
            inUse.set(value);
        }

        public String getKeyName() { return keyName != null ? keyName : "&eКлюч от тайника " + name; }
        public List<String> getKeyLore() { return new ArrayList<>(keyLore); }

        public boolean isKeyGlowEnabled() { return keyGlow; }
        public String getKeyFlagsString() {
            if (keyFlags.isEmpty()) return "Отсутствуют";
            return String.join(", ", keyFlags);
        }

        public void setKeyMaterial(String material) {
            this.keyMaterial = material;
            configManager.setKeyMaterial(name, material);
        }
        public void setKeyName(String name) {
            this.keyName = name;
            configManager.setKeyName(this.name, name);
        }
        public void setKeyLore(List<String> lore) {
            this.keyLore = new ArrayList<>(lore);
            configManager.setKeyLore(this.name, lore);
        }
        public void setKeyCustomModelData(int cmd) {
            this.keyCustomModelData = cmd;
            configManager.setKeyCustomModelData(name, cmd);
        }
        public void setKeyGlow(boolean glow) {
            this.keyGlow = glow;
            configManager.setKeyGlow(name, glow);
        }
        public void setKeyFlags(List<String> flags) {
            this.keyFlags = new ArrayList<>(flags);
            configManager.setKeyFlags(name, flags);
        }

        public void setLocation(Location loc) {
            this.location = loc;
            configManager.setCacheLocation(name, loc);
            if (loc != null && blockType != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(blockType));
            }
            if (isHologramEnabled()) {
                hologramManager.removeHologram(name);
                if (loc != null) {
                    hologramManager.createHologram(name, loc, getHologramText());
                }
            }
        }

        public void setBlockType(Material newBlockType) {
            this.blockType = newBlockType;
            if (location != null && newBlockType != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> location.getBlock().setType(newBlockType));
            }
            configManager.setCacheBlock(name, newBlockType);
        }

        public void setName(String newName) {
            this.name = newName;
            this.displayName = newName;
            this.hologramText = "&eТайник " + newName;
        }

        public void setHologramText(String newText) {
            this.hologramText = newText;
            configManager.setCacheHologramText(name, newText);
            if (isHologramEnabled() && location != null) {
                hologramManager.updateHologram(name, newText);
            }
        }

        public void setHologramEnabled(boolean enabled) {
            this.hologramEnabled = enabled;
            configManager.setCacheHologramEnabled(name, enabled);
            if (location != null) {
                if (enabled) hologramManager.createHologram(name, location, getHologramText());
                else hologramManager.removeHologram(name);
            }
        }

        public void setHologramOffsetX(double x) {
            this.hologramOffsetX = x;
            configManager.setCacheHologramOffset(name, x, hologramOffsetY, hologramOffsetZ);
            if (location != null && isHologramEnabled()) hologramManager.updateHologram(name, getHologramText());
        }

        public void setHologramOffsetY(double y) {
            this.hologramOffsetY = y;
            configManager.setCacheHologramOffset(name, hologramOffsetX, y, hologramOffsetZ);
            if (location != null && isHologramEnabled()) hologramManager.updateHologram(name, getHologramText());
        }

        public void setHologramOffsetZ(double z) {
            this.hologramOffsetZ = z;
            configManager.setCacheHologramOffset(name, hologramOffsetX, hologramOffsetY, z);
            if (location != null && isHologramEnabled()) hologramManager.updateHologram(name, getHologramText());
        }

        public void setAnimation(String animation) {
            this.animation = animation;
            configManager.setCacheAnimation(name, animation);
        }

        public boolean open(Player player) {
            if (!inUse.compareAndSet(false, true)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("name-cache", getDisplayName());
                configManager.executeActions(player, "cache.in-use", ph);
                return false;
            }

            try {
                Map<String, String> ph = new HashMap<>();
                ph.put("name-cache", getDisplayName());

                if (lootWithChances == null || lootWithChances.isEmpty()) {
                    configManager.executeActions(player, "cache.no-loot", ph);
                    setInUse(false);
                    return false;
                }

                ItemStack lootItem = selectRandomItem();
                if (lootItem == null) {
                    configManager.executeActions(player, "cache.zero-chance", ph);
                    setInUse(false);
                    return false;
                }

                statsManager.incrementOpenCount(this);
                statsManager.recordPlayerOpen(this, player.getName());
                lootHistoryManager.addEntry(name, player.getName(), lootItem);
                if (lootItem != null) statsManager.addLootGiven(this, 1);

                hologramManager.removeHologram(name);

                AnimationsManager.Animation anim = animationsManager.getAnimations().get(animation);
                if (anim == null) {
                    giveLoot(player, lootItem, ph);
                    return true;
                }

                animationsManager.playAnimation(player, animation, location, lootItem, name);
                return true;
            } catch (Exception e) {
                setInUse(false);
                return false;
            } finally {
                if (animationsManager.getAnimations().get(animation) == null) {
                    setInUse(false);
                }
            }
        }

        private void giveLoot(Player player, ItemStack item, Map<String, String> ph) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                configManager.executeActions(player, "cache.inventory-full", ph);
            } else {
                player.getInventory().addItem(item);
            }
            setInUse(false);

            if (isHologramEnabled() && location != null) {
                hologramManager.createHologram(name, location, getHologramText());
            }
        }

        public void resetKeyToDefault() {
            this.keyMaterial = "TRIPWIRE_HOOK";
            this.keyName = null;
            this.keyLore = new ArrayList<>();
            this.keyCustomModelData = 0;
            this.keyGlow = false;
            this.keyFlags = Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES");
            configManager.setKeyMaterial(name, "TRIPWIRE_HOOK");
            configManager.setKeyName(name, "&eКлюч от тайника " + name);
            configManager.setKeyLore(name, Arrays.asList("&7Для тайника: " + name, "&7Одноразовый предмет"));
            configManager.setKeyCustomModelData(name, 0);
            configManager.setKeyGlow(name, false);
            configManager.setKeyFlags(name, this.keyFlags);
        }

        private ItemStack selectRandomItem() {
            if (lootWithChances == null || lootWithChances.isEmpty()) {
                return null;
            }

            int totalChance = lootWithChances.stream().mapToInt(Entry::getValue).sum();
            if (totalChance <= 0) {
                return null;
            }

            int random = new Random().nextInt(totalChance);
            int current = 0;

            for (Entry<ItemStack, Integer> entry : lootWithChances) {
                current += entry.getValue();
                if (random < current) {
                    return entry.getKey().clone();
                }
            }
            return null;
        }

        public void setLoot(List<ItemStack> loot) {
            List<Entry<ItemStack, Integer>> newLootWithChances = new ArrayList<>();

            for (ItemStack newItem : loot) {
                if (newItem == null || newItem.getType() == Material.AIR) continue;

                int existingChance = 50;
                boolean found = false;

                for (Entry<ItemStack, Integer> entry : lootWithChances) {
                    if (entry.getKey() != null && newItem.isSimilar(entry.getKey())) {
                        existingChance = entry.getValue();
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    for (Entry<ItemStack, Integer> entry : lootWithChances) {
                        ItemStack oldItem = entry.getKey();
                        if (oldItem != null &&
                                oldItem.getType() == newItem.getType() &&
                                oldItem.getItemMeta() != null && newItem.getItemMeta() != null &&
                                Objects.equals(oldItem.getItemMeta().getCustomModelData(), newItem.getItemMeta().getCustomModelData()) &&
                                Objects.equals(oldItem.getItemMeta().getDisplayName(), newItem.getItemMeta().getDisplayName())) {

                            existingChance = entry.getValue();
                            break;
                        }
                    }
                }

                newLootWithChances.add(new AbstractMap.SimpleEntry<>(newItem.clone(), existingChance));
            }

            this.lootWithChances = newLootWithChances;
            configManager.setCacheLoot(name, newLootWithChances);
        }

        public void setLootWithChances(List<Entry<ItemStack, Integer>> lootWithChances) {
            this.lootWithChances = lootWithChances != null ? new ArrayList<>(lootWithChances) : new ArrayList<>();
            configManager.setCacheLoot(name, this.lootWithChances);
        }

        public List<ItemStack> getLoot() {
            return lootWithChances.stream()
                    .map(Entry::getKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        public List<Entry<ItemStack, Integer>> getLootWithChances() {
            return new ArrayList<>(lootWithChances);
        }

        public String getDisplayName() {
            return displayName != null ? displayName : name;
        }

        public String getHologramText() {
            return hologramText != null ? hologramText : "&eТайник " + getDisplayName();
        }

        public void setUnbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            configManager.setCacheUnbreakable(name, unbreakable);
        }

        public boolean addKeyFlag(String flag) {
            if (!keyFlags.contains(flag)) {
                keyFlags.add(flag);
                configManager.setKeyFlags(name, keyFlags);
                return true;
            }
            return false;
        }

        public boolean removeKeyFlag(String flag) {
            boolean removed = keyFlags.remove(flag);
            if (removed) {
                configManager.setKeyFlags(name, keyFlags);
            }
            return removed;
        }

        public void removeHologram() {
            if (hologramManager != null) {
                hologramManager.removeHologram(name);
            }
        }
    }
}