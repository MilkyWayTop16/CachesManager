package org.gw.cachesmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import java.lang.reflect.Field;
import java.util.UUID;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.listeners.*;
import org.gw.cachesmanager.utils.HexColors;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class MenuManager implements Listener {
    private final CachesManager plugin;
    private final ConfigManager configManager;
    private final CacheManager cacheManager;
    private final AnimationsManager animationsManager;
    private final CacheModeListener cacheModeListener;
    private final StatsManager statsManager;
    private final LootHistoryManager lootHistoryManager;

    private final Map<UUID, String> playerMenus = new HashMap<>();
    private final Map<UUID, String> playerCaches = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<String, List<Integer>> slotRangeCache = new HashMap<>();
    private final Map<String, Map<Integer, List<ItemStack>>> cachePageLoot = new HashMap<>();
    private final Map<String, BiConsumer<Player, Integer>> specialHandlers = new HashMap<>();
    private final Map<String, ItemStack> staticItemCache = new ConcurrentHashMap<>();
    private final Map<String, String> translatedNameCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> translatedLoreCache = new ConcurrentHashMap<>();

    public MenuManager(CachesManager plugin, ConfigManager configManager, CacheManager cacheManager,
                       ItemManager itemManager, CacheModeListener cacheModeListener,
                       AnimationsManager animationsManager, StatsManager statsManager,
                       LootHistoryManager lootHistoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cacheManager = cacheManager;
        this.cacheModeListener = cacheModeListener;
        this.animationsManager = animationsManager;
        this.statsManager = statsManager;
        this.lootHistoryManager = lootHistoryManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initSpecialHandlers();
    }

    private void initSpecialHandlers() {
        specialHandlers.put("[close-menu]", (p, i) -> p.closeInventory());
        specialHandlers.put("[selection-mode]", (p, i) -> enableMode(p, cacheModeListener::enableSelectionMode));
        specialHandlers.put("[rename-cache]", (p, i) -> enableMode(p, cacheModeListener::enableRenameMode));
        specialHandlers.put("[replace-block-cache]", (p, i) -> enableMode(p, cacheModeListener::enableReplaceBlockMode));
        specialHandlers.put("[change-hologram-text]", (p, i) -> enableMode(p, cacheModeListener::enableHologramTextMode));
        specialHandlers.put("[toggle-unbreakable]", this::handleToggleUnbreakable);
        specialHandlers.put("[update-menu]", this::handleUpdateMenu);
        specialHandlers.put("[next-page]", this::handleNextPage);
        specialHandlers.put("[previous-page]", this::handlePreviousPage);
        specialHandlers.put("[next-animation]", this::handleNextAnimation);
        specialHandlers.put("[last-animation]", this::handlePreviousAnimation);
        specialHandlers.put("[increase-chance]", this::handleIncreaseChance);
        specialHandlers.put("[reduce-chance]", this::handleReduceChance);
        specialHandlers.put("[toggle-hologram]", this::handleToggleHologram);
        specialHandlers.put("[hologram-offset-x]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetXMode));
        specialHandlers.put("[hologram-offset-y]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetYMode));
        specialHandlers.put("[hologram-offset-z]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetZMode));
        specialHandlers.put("[change-key-material]", this::handleChangeKeyMaterial);
        specialHandlers.put("[change-key-name]", this::handleChangeKeyName);
        specialHandlers.put("[change-key-lore]", this::handleChangeKeyLore);
        specialHandlers.put("[change-key-cmd]", this::handleChangeKeyCMD);
        specialHandlers.put("[toggle-key-glow]", this::handleToggleKeyGlow);
        specialHandlers.put("[reset-key-to-default]", this::handleResetKeyToDefault);
        specialHandlers.put("[change-flags]", this::handleChangeKeyFlags);
        specialHandlers.put("[open-stats]", (p, i) -> {
            String cache = playerCaches.get(p.getUniqueId());
            if (cache != null) p.closeInventory();
            plugin.getMenuManager().openMenu(p, cache, "stats-menu.yml");
        });
    }

    private void handleToggleHologram(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        boolean newState = !cache.isHologramEnabled();
        cache.setHologramEnabled(newState);
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());

        configManager.executeActions(p, newState ? "hologram.toggle-enabled" : "hologram.toggle-disabled", ph);
        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    private void enableMode(Player p, BiConsumer<Player, String> enabler) {
        String cache = playerCaches.get(p.getUniqueId());
        if (cache != null) enabler.accept(p, cache);
        p.closeInventory();
    }

    public void openMenu(Player player, String cacheName, String menuFile, int page) {
        FileConfiguration menuConfig = configManager.loadMenuConfig(menuFile);
        if (menuConfig == null) return;

        int size = menuConfig.getInt("menu.size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) size = 54;

        int maxPages = configManager.loadMenuConfig("loot-menu.yml").getInt("menu.max-pages", 5);
        page = Math.max(1, Math.min(page, maxPages));

        String title = HexColors.translate(menuConfig.getString("menu.title")
                .replace("{name-cache}", getCacheDisplayName(cacheName))
                .replace("{current-page}", String.valueOf(page))
                .replace("{max-pages}", String.valueOf(maxPages)));

        Inventory inventory = Bukkit.createInventory(player, size, title);

        fillMenuItems(inventory, menuConfig, cacheName, menuFile);

        if ("history-menu.yml".equals(menuFile)) {
            fillHistoryItems(inventory, cacheName, page);
        } else {
            fillLootItems(inventory, menuConfig, cacheName, page, menuFile);
        }

        player.openInventory(inventory);
        playerMenus.put(player.getUniqueId(), menuFile);
        playerCaches.put(player.getUniqueId(), cacheName);
        playerPages.put(player.getUniqueId(), page);

        int updateInterval = menuConfig.getInt("menu.update-interval", 0);
        if (updateInterval > 0) {
            int finalPage = page;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.getOpenInventory().getTopInventory().equals(inventory)) cancel();
                    updateMenu(player, inventory, menuFile, cacheName, finalPage);
                }
            }.runTaskTimer(plugin, 0L, updateInterval);
        }
    }

    public void openMenu(Player player, String cacheName, String menuFile) {
        openMenu(player, cacheName, menuFile, 1);
    }

    private String getCacheDisplayName(String cacheName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        return cache != null ? cache.getDisplayName() : cacheName;
    }

    private void fillMenuItems(Inventory inventory, FileConfiguration menuConfig, String cacheName, String menuFile) {
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection == null) return;

        List<Integer> lootSlots = parseSlotRange(menuConfig.getStringList("loot.slots"), inventory.getSize());

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) continue;

            if ("key-preview".equals(key)) {
                FileConfiguration cacheConfig = configManager.loadCacheConfig(cacheName);
                if (cacheConfig != null) {
                    ItemStack preview = configManager.getKeyItem(cacheName, cacheConfig);
                    int slot = section.getInt("slot", 4);
                    if (slot >= 0 && slot < inventory.getSize() && !lootSlots.contains(slot)) {
                        inventory.setItem(slot, preview);
                    }
                }
                continue;
            }

            if ("stats-menu.yml".equals(menuFile)) {
                CacheManager.Cache cacheObj = cacheManager.getCache(cacheName);
                if (cacheObj == null) continue;

                if ("general-stats".equals(key)) {
                    inventory.setItem(section.getInt("slot"), statsManager.createGeneralStatsItem(cacheObj));
                    continue;
                }
                if ("top-players".equals(key)) {
                    inventory.setItem(section.getInt("slot"), statsManager.createTopPlayersItem(cacheObj));
                    continue;
                }
                if ("records".equals(key)) {
                    inventory.setItem(section.getInt("slot"), statsManager.createRecordsItem(cacheObj));
                    continue;
                }
            }

            ItemStack item = createMenuItem(section, cacheName);
            if (section.contains("slot")) {
                int slot = section.getInt("slot");
                if (slot >= 0 && slot < inventory.getSize() && !lootSlots.contains(slot)) {
                    inventory.setItem(slot, item);
                }
            } else if (section.contains("slots")) {
                for (int slot : parseSlotRange(section.getStringList("slots"), inventory.getSize())) {
                    if (!lootSlots.contains(slot)) {
                        inventory.setItem(slot, item);
                    }
                }
            }
        }
    }

    private void fillLootItems(Inventory inventory, FileConfiguration menuConfig, String cacheName, int page, String menuFile) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        List<Integer> lootSlots = parseSlotRange(menuConfig.getStringList("loot.slots"), inventory.getSize());
        Map<Integer, List<ItemStack>> pageLoot = cachePageLoot.computeIfAbsent(cacheName, k -> new HashMap<>());
        List<ItemStack> loot = pageLoot.getOrDefault(page, Collections.emptyList());

        for (int slot : lootSlots) inventory.setItem(slot, null);

        ConfigurationSection lootSettings = menuConfig.getConfigurationSection("loot-item-settings");
        boolean isChance = "chance-menu.yml".equals(menuFile);
        int perPage = lootSlots.size();

        for (int i = 0; i < lootSlots.size() && i < loot.size(); i++) {
            ItemStack item = loot.get(i);
            if (item == null) continue;
            ItemStack display = item.clone();
            if (isChance && lootSettings != null) applyChanceLore(display, lootSettings, cache, (page - 1) * perPage + i);
            inventory.setItem(lootSlots.get(i), display);
        }
    }

    private void applyChanceLore(ItemStack item, ConfigurationSection settings, CacheManager.Cache cache, int index) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(l -> l.contains("{chance}"));
        List<String> add = settings.getStringList("lore").stream()
                .map(s -> HexColors.translate(s.replace("{chance}", String.valueOf(
                        index < cache.getLootWithChances().size() ? cache.getLootWithChances().get(index).getValue() : 50))))
                .collect(Collectors.toList());
        lore.addAll(add);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public void updateMenu(Player player, Inventory inventory, String menuFile, String cacheName, int page) {
        FileConfiguration cfg = configManager.loadMenuConfig(menuFile);
        if (cfg == null) return;

        invalidateStaticCache(cacheName);

        fillMenuItems(inventory, cfg, cacheName, menuFile);

        if ("history-menu.yml".equals(menuFile)) {
            fillHistoryItems(inventory, cacheName, page);
        } else {
            fillLootItems(inventory, cfg, cacheName, page, menuFile);
        }
    }

    private void updateSingleItem(Player player, Inventory inventory, String menuFile, String cacheName, int page, int slot) {
        FileConfiguration cfg = configManager.loadMenuConfig(menuFile);
        if (cfg == null) return;

        List<Integer> lootSlots = parseSlotRange(cfg.getStringList("loot.slots"), inventory.getSize());
        int idx = lootSlots.indexOf(slot);
        if (idx == -1) return;

        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        cache.load();
        Map<Integer, List<ItemStack>> pageLoot = cachePageLoot.getOrDefault(cacheName, Collections.emptyMap());
        List<ItemStack> loot = pageLoot.getOrDefault(page, Collections.emptyList());
        if (idx >= loot.size()) return;

        ItemStack item = loot.get(idx);
        if (item == null) return;

        ItemStack display = item.clone();
        ConfigurationSection settings = cfg.getConfigurationSection("loot-item-settings");
        if (settings != null) applyChanceLore(display, settings, cache, (page - 1) * lootSlots.size() + idx);
        inventory.setItem(slot, display);
    }

    private ItemStack createCustomHead(String base64Value) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null || base64Value == null || base64Value.isEmpty()) return head;

        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), null);

            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", base64Value);

            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            plugin.console("Ошибка создания кастомной головы (Base64)");
        }

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createMenuItem(ConfigurationSection section, String cacheName) {
        String cacheKey = section.getName() + "|" + cacheName;
        if (staticItemCache.containsKey(cacheKey)) {
            return staticItemCache.get(cacheKey).clone();
        }

        String matStr = section.getString("material", "STONE").toUpperCase();
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat, section.getInt("amount", 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            staticItemCache.put(cacheKey, item);
            return item;
        }

        if (mat == Material.PLAYER_HEAD && section.contains("value")) {
            String value = section.getString("value");
            if (value != null && !value.isEmpty()) {
                item = createCustomHead(value);
                meta = item.getItemMeta();
            }
        }

        CacheManager.Cache cache = cacheManager.getCache(cacheName);

        if (section.contains("display-name")) {
            String nameKey = cacheKey + "|name";
            String name = translatedNameCache.computeIfAbsent(nameKey, k -> {
                String raw = section.getString("display-name");
                return cache != null ? applyCachePlaceholders(raw, cache) : raw;
            });
            meta.setDisplayName(HexColors.translate(name));
        }

        if (section.contains("lore")) {
            String loreKey = cacheKey + "|lore";
            List<String> lore = translatedLoreCache.computeIfAbsent(loreKey, k -> {
                List<String> raw = new ArrayList<>(section.getStringList("lore"));
                return cache != null ? applyCachePlaceholdersToLore(raw, cache) : raw;
            });
            meta.setLore(HexColors.translate(lore));
        }

        if (section.contains("enchantments")) {
            for (String ench : section.getStringList("enchantments")) {
                String[] parts = ench.split(";");
                if (parts.length == 2) {
                    Enchantment e = Enchantment.getByName(parts[0].trim().toUpperCase());
                    if (e != null) meta.addEnchant(e, Integer.parseInt(parts[1].trim()), true);
                }
            }
        }

        if (section.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (section.contains("custom-model-data")) meta.setCustomModelData(section.getInt("custom-model-data"));

        if (meta.hasAttributeModifiers()) meta.getAttributeModifiers().keySet().forEach(meta::removeAttributeModifier);

        if (section.contains("flags")) {
            for (String f : section.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(f.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        item.setItemMeta(meta);
        staticItemCache.put(cacheKey, item.clone());
        return item;
    }

    public void clearCacheForCache(String cacheName) {
        cachePageLoot.remove(cacheName);
        slotRangeCache.clear();
    }

    private List<Integer> parseSlotRange(List<String> ranges, int maxSize) {
        if (ranges.isEmpty()) return Collections.emptyList();

        String key = ranges.toString() + "|" + maxSize;
        List<Integer> cached = slotRangeCache.get(key);
        if (cached != null) return cached;

        List<Integer> slots = new ArrayList<>();
        for (String r : ranges) {
            try {
                if (r.contains("-")) {
                    String[] p = r.split("-");
                    int start = Integer.parseInt(p[0].trim());
                    int end = Integer.parseInt(p[1].trim());
                    if (start < 0 || end < 0 || start > end || end >= maxSize) continue;
                    for (int i = start; i <= end; i++) slots.add(i);
                } else {
                    int s = Integer.parseInt(r.trim());
                    if (s >= 0 && s < maxSize) slots.add(s);
                }
            } catch (NumberFormatException ignored) {}
        }
        slotRangeCache.put(key, slots);
        return slots;
    }

    private void saveLootForPage(Player player, String cacheName, int page, Inventory inventory) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        FileConfiguration cfg = configManager.loadMenuConfig("loot-menu.yml");
        if (cfg == null) return;

        updatePageLootCache(cacheName, page, inventory);

        List<ItemStack> allLoot = new ArrayList<>();
        for (List<ItemStack> pageLoot : cachePageLoot.getOrDefault(cacheName, Collections.emptyMap()).values()) {
            allLoot.addAll(pageLoot);
        }

        List<Entry<ItemStack, Integer>> newLootWithChances = new ArrayList<>();

        for (ItemStack item : allLoot) {
            int chance = 50;
            for (Entry<ItemStack, Integer> oldEntry : cache.getLootWithChances()) {
                if (oldEntry.getKey() != null && item.isSimilar(oldEntry.getKey())) {
                    chance = oldEntry.getValue();
                    break;
                }
            }
            newLootWithChances.add(new AbstractMap.SimpleEntry<>(item, chance));
        }

        cache.setLootWithChances(newLootWithChances);
        configManager.setCacheLoot(cacheName, newLootWithChances);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player p = (Player) event.getWhoClicked();
        UUID id = p.getUniqueId();

        String menuFile = playerMenus.get(id);
        String cacheName = playerCaches.get(id);
        Integer page = playerPages.get(id);

        if (menuFile == null || cacheName == null || page == null) return;

        FileConfiguration cfg = configManager.loadMenuConfig(menuFile);
        if (cfg == null) return;

        List<Integer> lootSlots = parseSlotRange(cfg.getStringList("loot.slots"), event.getInventory().getSize());
        boolean isLootMenu = "loot-menu.yml".equals(menuFile);
        boolean isChanceMenu = "chance-menu.yml".equals(menuFile);
        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!clickedTop) {
            if (isLootMenu) {
                event.setCancelled(false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> updatePageLootCache(cacheName, page, event.getView().getTopInventory()), 1L);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (isLootMenu && lootSlots.contains(slot)) {
            event.setCancelled(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> updatePageLootCache(cacheName, page, event.getInventory()), 1L);
            return;
        }

        if (isChanceMenu && lootSlots.contains(slot)) {
            handleChanceClick(p, cfg, cacheName, event, page, lootSlots);
            return;
        }

        if (!lootSlots.contains(slot)) {
            handleNonLootClick(p, cfg, cacheName, event, page);
        }
    }

    private void handleToggleKeyGlow(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        boolean newGlow = !cache.isKeyGlowEnabled();
        cache.setKeyGlow(newGlow);
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(p, newGlow ? "key.glow-enabled" : "key.glow-disabled", ph);

        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    private void handleChanceClick(Player p, FileConfiguration cfg, String cacheName, InventoryClickEvent e, int page, List<Integer> lootSlots) {
        ConfigurationSection settings = cfg.getConfigurationSection("loot-item-settings");
        if (settings == null) return;

        List<String> cmds = getClickCommands(settings, e.getClick());
        if (cmds.isEmpty()) return;

        int slotIdx = lootSlots.indexOf(e.getSlot());
        if (slotIdx == -1) return;

        int itemIdx = (page - 1) * lootSlots.size() + slotIdx;
        executeClickCommands(p, cmds, cacheName, e.getInventory(), page, itemIdx);
    }

    private void handleNonLootClick(Player p, FileConfiguration cfg, String cacheName, InventoryClickEvent e, int page) {
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(key);
            if (sec == null) continue;

            List<Integer> slots = sec.contains("slot")
                    ? Collections.singletonList(sec.getInt("slot"))
                    : parseSlotRange(sec.getStringList("slots"), e.getInventory().getSize());

            if (slots.contains(e.getSlot())) {
                List<String> commands = getClickCommands(sec, e.getClick());
                if (!commands.isEmpty()) {
                    executeClickCommands(p, commands, cacheName, e.getInventory(), page, e.getSlot());
                }
                return;
            }
        }
    }

    private List<String> getClickCommands(ConfigurationSection sec, ClickType type) {
        List<String> cmds;

        switch (type) {
            case LEFT:
                cmds = sec.getStringList("left-click-commands");
                break;
            case RIGHT:
                cmds = sec.getStringList("right-click-commands");
                break;
            case SHIFT_LEFT:
                cmds = sec.getStringList("shift-left-click-commands");
                break;
            case SHIFT_RIGHT:
                cmds = sec.getStringList("shift-right-click-commands");
                break;
            default:
                cmds = sec.getStringList("click-commands");
                break;
        }
        if (cmds.isEmpty()) {
            cmds = sec.getStringList("click-commands");
        }
        return cmds;
    }

    private void executeClickCommands(Player p, List<String> commands, String cacheName, Inventory inv, int page, int index) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        for (String rawCmd : commands) {
            String processed = rawCmd.replace("{name-cache}", cache.getDisplayName())
                    .replace("{player}", p.getName()).trim();

            if (processed.startsWith("[increase-chance]")) {
                int delta = extractDelta(processed, 1);
                handleChanceChange(p, index, true, delta);
                continue;
            }
            if (processed.startsWith("[reduce-chance]")) {
                int delta = extractDelta(processed, 1);
                handleChanceChange(p, index, false, delta);
                continue;
            }

            BiConsumer<Player, Integer> handler = specialHandlers.get(processed.toLowerCase());
            if (handler != null) {
                handler.accept(p, index);
                continue;
            }

            if (processed.startsWith("[open-menu]")) {
                String file = processed.replace("[open-menu]", "").trim();
                openMenu(p, cacheName, file, 1);
            } else if (processed.startsWith("[sound]")) {
                String[] parts = processed.replace("[sound]", "").trim().split(" ");
                if (parts.length >= 3) {
                    try {
                        p.playSound(p.getLocation(), Sound.valueOf(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
                    } catch (Exception ignored) {}
                }
            } else {
                plugin.getServer().dispatchCommand(p, processed);
            }
        }
    }

    private int extractDelta(String command, int defaultValue) {
        try {
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private void handleIncreaseChance(Player p, int index) {
        handleChanceChange(p, index, true, 1);
    }

    private void handleReduceChance(Player p, int index) {
        handleChanceChange(p, index, false, 1);
    }

    private void handleToggleUnbreakable(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        boolean newState = !cache.isUnbreakable();
        cache.setUnbreakable(newState);
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());

        configManager.executeActions(p, newState ? "unbreakable-enabled" : "unbreakable-disabled", ph);

        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    private void handleUpdateMenu(Player p, int idx) {
        String menuFile = playerMenus.get(p.getUniqueId());
        String cacheName = playerCaches.get(p.getUniqueId());
        Integer page = playerPages.get(p.getUniqueId());
        if (menuFile != null && cacheName != null && page != null) {
            updateMenu(p, p.getOpenInventory().getTopInventory(), menuFile, cacheName, page);
        }
    }

    private void handleNextPage(Player p, int idx) {
        String menuFile = playerMenus.get(p.getUniqueId());
        String cacheName = playerCaches.get(p.getUniqueId());
        Integer page = playerPages.get(p.getUniqueId());
        if (page == null || menuFile == null || cacheName == null) return;
        int max = configManager.loadMenuConfig("loot-menu.yml").getInt("menu.max-pages", 5);
        if (page < max) openMenu(p, cacheName, menuFile, page + 1);
    }

    private void handlePreviousPage(Player p, int idx) {
        String menuFile = playerMenus.get(p.getUniqueId());
        String cacheName = playerCaches.get(p.getUniqueId());
        Integer page = playerPages.get(p.getUniqueId());
        if (page == null || menuFile == null || cacheName == null) return;
        if (page > 1) openMenu(p, cacheName, menuFile, page - 1);
    }

    private void handleNextAnimation(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;
        String next = animationsManager.getNextAnimation(cache.getAnimation());
        if (next == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        cache.setAnimation(next);
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("animation", animationsManager.getAnimations().get(next).getName());
        configManager.executeActions(p, "cache.animation-changed", ph);
        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    private void handlePreviousAnimation(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;
        String prev = animationsManager.getPreviousAnimation(cache.getAnimation());
        if (prev == null) {
            p.sendMessage(HexColors.translate("&cНе удалось переключить анимацию..."));
            return;
        }
        cache.setAnimation(prev);
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("animation", animationsManager.getAnimations().get(prev).getName());
        configManager.executeActions(p, "cache.animation-changed", ph);
        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    private void handleChanceChange(Player p, int index, boolean increase, int delta) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;

        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null || index < 0 || index >= cache.getLootWithChances().size()) return;

        int oldChance = cache.getLootWithChances().get(index).getValue();
        int newChance = Math.max(0, Math.min(100, oldChance + (increase ? delta : -delta)));

        configManager.setItemChance(cacheName, index, newChance);
        cache.getLootWithChances().set(index, new AbstractMap.SimpleEntry<>(
                cache.getLootWithChances().get(index).getKey(), newChance));

        initializeCachePageLoot(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("chance", String.valueOf(newChance));
        configManager.executeActions(p, "cache.chance-updated", ph);

        int slot = calculateSlotFromIndex(index, playerPages.get(p.getUniqueId()), p.getOpenInventory().getTopInventory().getSize());
        updateSingleItem(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()), slot);
    }

    private int calculateSlotFromIndex(int itemIndex, int page, int invSize) {
        FileConfiguration cfg = configManager.loadMenuConfig("chance-menu.yml");
        if (cfg == null) return 0;
        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), invSize);
        int perPage = slots.size();
        int slotIdx = itemIndex % perPage;
        return slotIdx < slots.size() ? slots.get(slotIdx) : 0;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player p = (Player) event.getPlayer();
        UUID id = p.getUniqueId();
        String menuFile = playerMenus.get(id);
        String cacheName = playerCaches.get(id);
        Integer page = playerPages.get(id);

        if ("loot-menu.yml".equals(menuFile) && cacheName != null && page != null) {
            saveLootForPage(p, cacheName, page, event.getInventory());
        }

        playerMenus.remove(id);
        playerCaches.remove(id);
        playerPages.remove(id);
    }

    public void initializeCachePageLoot(String cacheName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        FileConfiguration cfg = configManager.loadMenuConfig("loot-menu.yml");
        if (cfg == null) return;

        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), cfg.getInt("menu.size", 54));
        int perPage = slots.size();
        List<ItemStack> loot = cache.getLoot();

        Map<Integer, List<ItemStack>> pages = new HashMap<>();
        for (int i = 0; i < loot.size(); i++) {
            int pageNum = (i / perPage) + 1;
            pages.computeIfAbsent(pageNum, k -> new ArrayList<>()).add(loot.get(i));
        }
        cachePageLoot.put(cacheName, pages);
    }

    private void handleChangeKeyMaterial(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        invalidateStaticCache(cacheName);
        plugin.getCacheModeListener().enableKeyMaterialMode(p, cacheName);
        p.closeInventory();
    }

    private void handleChangeKeyName(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        invalidateStaticCache(cacheName);
        plugin.getCacheModeListener().enableKeyNameMode(p, cacheName);
        p.closeInventory();
    }

    private void handleChangeKeyLore(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        invalidateStaticCache(cacheName);
        plugin.getCacheModeListener().enableKeyLoreMode(p, cacheName);
        p.closeInventory();
    }

    private void handleChangeKeyCMD(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        invalidateStaticCache(cacheName);
        plugin.getCacheModeListener().enableKeyCMDMode(p, cacheName);
        p.closeInventory();
    }

    private void handleChangeKeyFlags(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        invalidateStaticCache(cacheName);
        plugin.getCacheModeListener().enableKeyFlagsMode(p, cacheName);
        p.closeInventory();
    }

    private void handleResetKeyToDefault(Player p, int idx) {
        String cacheName = playerCaches.get(p.getUniqueId());
        if (cacheName == null) return;
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        cache.resetKeyToDefault();
        configManager.saveCacheConfig(cacheName);

        invalidateStaticCache(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(p, "key.reset-to-default", ph);

        updateMenu(p, p.getOpenInventory().getTopInventory(), playerMenus.get(p.getUniqueId()), cacheName, playerPages.get(p.getUniqueId()));
    }

    public String getCurrentMenu(Player player) {
        return playerMenus.get(player.getUniqueId());
    }

    private String applyCachePlaceholders(String text, CacheManager.Cache cache) {
        String anim = animationsManager.getAnimations().containsKey(cache.getAnimation())
                ? animationsManager.getAnimations().get(cache.getAnimation()).getName() : "Неизвестная анимация";

        return text
                .replace("{name-cache}", cache.getDisplayName())
                .replace("{unbreakable-status}", cache.isUnbreakable() ? "Включена" : "Выключена")
                .replace("{animation-name}", anim)
                .replace("{hologram-status}", cache.isHologramEnabled() ? "Включена" : "Выключена")
                .replace("{hologram-offset-x}", String.valueOf(cache.getHologramOffsetX()))
                .replace("{hologram-offset-y}", String.valueOf(cache.getHologramOffsetY()))
                .replace("{hologram-offset-z}", String.valueOf(cache.getHologramOffsetZ()))
                .replace("{key-name}", cache.getKeyName())
                .replace("{key-material}", cache.getKeyMaterial())
                .replace("{key-glow-status}", cache.isKeyGlowEnabled() ? "Включено" : "Выключено")
                .replace("{key-cmd}", String.valueOf(cache.getKeyCustomModelData()))
                .replace("{key-flags}", cache.getKeyFlagsString());
    }

    private List<String> applyCachePlaceholdersToLore(List<String> lore, CacheManager.Cache cache) {
        List<String> result = new ArrayList<>();
        String anim = animationsManager.getAnimations().containsKey(cache.getAnimation())
                ? animationsManager.getAnimations().get(cache.getAnimation()).getName() : "Неизвестная анимация";

        for (String line : lore) {
            String processed = line
                    .replace("{name-cache}", cache.getDisplayName())
                    .replace("{unbreakable-status}", cache.isUnbreakable() ? "Включена" : "Выключена")
                    .replace("{animation-name}", anim)
                    .replace("{hologram-status}", cache.isHologramEnabled() ? "Включена" : "Выключена")
                    .replace("{hologram-offset-x}", String.valueOf(cache.getHologramOffsetX()))
                    .replace("{hologram-offset-y}", String.valueOf(cache.getHologramOffsetY()))
                    .replace("{hologram-offset-z}", String.valueOf(cache.getHologramOffsetZ()))
                    .replace("{key-name}", cache.getKeyName())
                    .replace("{key-material}", cache.getKeyMaterial())
                    .replace("{key-glow-status}", cache.isKeyGlowEnabled() ? "Включено" : "Выключено")
                    .replace("{key-cmd}", String.valueOf(cache.getKeyCustomModelData()))
                    .replace("{key-flags}", cache.getKeyFlagsString());

            if (processed.contains("{key-lore}")) {
                String indent = processed.substring(0, processed.indexOf("{key-lore}"));
                List<String> keyLore = cache.getKeyLore();

                if (keyLore.isEmpty()) {
                    result.add(indent + "&#FFFF00Отсутствует");
                } else {
                    for (String loreLine : keyLore) {
                        result.add(indent + loreLine);
                    }
                }
            } else {
                result.add(processed);
            }
        }
        return result;
    }

    private void invalidateStaticCache(String cacheName) {
        staticItemCache.keySet().removeIf(key -> key.contains("|" + cacheName));
        translatedNameCache.keySet().removeIf(key -> key.contains("|" + cacheName));
        translatedLoreCache.keySet().removeIf(key -> key.contains("|" + cacheName));
    }

    private void updatePageLootCache(String cacheName, int page, Inventory inventory) {
        FileConfiguration cfg = configManager.loadMenuConfig("loot-menu.yml");
        if (cfg == null) return;

        List<Integer> lootSlots = parseSlotRange(cfg.getStringList("loot.slots"), inventory.getSize());

        List<ItemStack> currentLoot = new ArrayList<>();
        for (int slot : lootSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                currentLoot.add(item.clone());
            }
        }

        cachePageLoot.computeIfAbsent(cacheName, k -> new HashMap<>()).put(page, currentLoot);
    }

    private void fillHistoryItems(Inventory inventory, String cacheName, int page) {
        Deque<LootHistoryManager.HistoryEntry> history = lootHistoryManager.getHistory(cacheName);
        FileConfiguration cfg = configManager.loadMenuConfig("history-menu.yml");
        if (cfg == null) return;

        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), inventory.getSize());
        ConfigurationSection settings = cfg.getConfigurationSection("history-item-settings");

        int start = (page - 1) * slots.size();
        int index = 0;

        for (LootHistoryManager.HistoryEntry entry : history) {
            if (index < start) {
                index++;
                continue;
            }
            if (index - start >= slots.size()) break;

            ItemStack display = entry.item.clone();
            if (settings != null) {
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    List<String> add = settings.getStringList("lore").stream()
                            .map(s -> HexColors.translate(s
                                    .replace("{dropped-by}", entry.playerName)
                                    .replace("{dropped-at}", entry.formattedTime)))
                            .collect(Collectors.toList());
                    lore.addAll(add);
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
            }
            inventory.setItem(slots.get(index - start), display);
            index++;
        }
    }

    public void reload() {
        playerMenus.clear();
        playerCaches.clear();
        playerPages.clear();
        slotRangeCache.clear();
        cachePageLoot.clear();
        staticItemCache.clear();
        translatedNameCache.clear();
        translatedLoreCache.clear();

        for (String name : cacheManager.getCacheNames()) {
            initializeCachePageLoot(name);
        }

        plugin.log("Системы менюшек успешно перезагружены!");
    }
}