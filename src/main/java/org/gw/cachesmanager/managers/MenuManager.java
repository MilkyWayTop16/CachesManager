package org.gw.cachesmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.listeners.CacheModeListener;
import org.gw.cachesmanager.menus.*;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class MenuManager implements Listener, MenuClickDelegate {
    private final CachesManager plugin;
    private final ConfigManager configManager;
    private final CacheManager cacheManager;
    private final AnimationsManager animationsManager;
    private final CacheModeListener cacheModeListener;
    private final StatsManager statsManager;
    private final LootHistoryManager lootHistoryManager;

    private final MenuItemBuilder itemBuilder;
    private final MenuLootManager lootManager;
    private final MenuActionHandler actionHandler;
    private MenuClickActionHandler clickActionHandler;

    private final Map<String, List<Integer>> slotRangeCache = new HashMap<>();
    private final Map<String, Map<Integer, List<ItemStack>>> cachePageLoot = new HashMap<>();
    private final SpecialActionRegistry specialActionRegistry = new SpecialActionRegistry();
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

        this.itemBuilder = new MenuItemBuilder(plugin);
        this.lootManager = new MenuLootManager(plugin);
        this.actionHandler = new MenuActionHandler(plugin);

        this.clickActionHandler = new MenuClickActionHandler(
                plugin,
                cacheManager,
                configManager,
                actionHandler,
                this,
                specialHandlers
        );

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initSpecialHandlers();
    }

    private void initSpecialHandlers() {
        registerSpecialAction("[close-menu]", (p, i) -> p.closeInventory());
        registerSpecialAction("[selection-mode]", (p, i) -> enableMode(p, cacheModeListener::enableSelectionMode));
        registerSpecialAction("[rename-cache]", (p, i) -> enableMode(p, cacheModeListener::enableRenameMode));
        registerSpecialAction("[replace-block-cache]", (p, i) -> enableMode(p, cacheModeListener::enableReplaceBlockMode));
        registerSpecialAction("[change-hologram-text]", (p, i) -> enableMode(p, cacheModeListener::enableHologramTextMode));
        registerSpecialAction("[toggle-unbreakable]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleUnbreakable(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[update-menu]", this::handleUpdateMenu);
        registerSpecialAction("[next-page]", this::handleNextPage);
        registerSpecialAction("[previous-page]", this::handlePreviousPage);
        registerSpecialAction("[next-animation]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleNextAnimation(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[last-animation]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handlePreviousAnimation(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[increase-chance]", this::handleIncreaseChance);
        registerSpecialAction("[reduce-chance]", this::handleReduceChance);
        registerSpecialAction("[toggle-hologram]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleHologram(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[hologram-offset-x]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetXMode));
        registerSpecialAction("[hologram-offset-y]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetYMode));
        registerSpecialAction("[hologram-offset-z]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetZMode));
        registerSpecialAction("[change-key-material]", this::handleChangeKeyMaterial);
        registerSpecialAction("[change-key-name]", this::handleChangeKeyName);
        registerSpecialAction("[change-key-lore]", this::handleChangeKeyLore);
        registerSpecialAction("[change-key-cmd]", this::handleChangeKeyCMD);
        registerSpecialAction("[toggle-key-glow]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleKeyGlow(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[reset-key-to-default]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleResetKeyToDefault(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        registerSpecialAction("[change-flags]", this::handleChangeKeyFlags);
        registerSpecialAction("[open-stats]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                String cache = holder.getCacheName();
                p.closeInventory();
                openMenu(p, cache, "stats-menu.yml");
            }
        });
    }

    private void registerSpecialAction(String tag, BiConsumer<Player, Integer> action) {
        specialHandlers.put(tag, action);
        specialActionRegistry.register(tag, action);
    }

    private void enableMode(Player p, BiConsumer<Player, String> enabler) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            enabler.accept(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    public void openMenu(Player player, String cacheName, String menuFile, int page) {
        FileConfiguration menuConfig = configManager.loadMenuConfig(menuFile);
        if (menuConfig == null) return;
        int size = menuConfig.getInt("menu.size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) size = 54;
        int maxPages = configManager.loadMenuConfig("loot-menu.yml").getInt("menu.max-pages", 5);
        page = Math.max(1, Math.min(page, maxPages));
        String titleRaw = menuConfig.getString("menu.title", "")
                .replace("{name-cache}", getCacheDisplayName(cacheName))
                .replace("{current-page}", String.valueOf(page))
                .replace("{max-pages}", String.valueOf(maxPages));
        titleRaw = PlaceholderAPIHook.parse(player, titleRaw);
        String title = HexColors.translate(titleRaw);

        CacheMenuHolder holder = new CacheMenuHolder(menuFile, cacheName, page);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        fillMenuItems(player, inventory, menuConfig, cacheName, menuFile);
        List<Integer> slots = parseSlotRange(menuConfig.getStringList("loot.slots"), inventory.getSize());
        if ("history-menu.yml".equals(menuFile)) {
            lootManager.fillHistoryItemsAsync(player, inventory, cacheName, page, slots);
        } else {
            lootManager.fillLootItems(player, inventory, menuConfig, cacheName, page, menuFile, cachePageLoot, slots);
        }
        player.openInventory(inventory);
        int updateInterval = menuConfig.getInt("menu.update-interval", 0);
        if (updateInterval > 0) {
            int finalPage = page;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getOpenInventory() == null || !inventory.equals(player.getOpenInventory().getTopInventory())) {
                        cancel();
                        return;
                    }
                    updateMenu(player, inventory, menuFile, cacheName, finalPage);
                }
            }.runTaskTimer(plugin, 0L, updateInterval);
        }
    }

    public void openMenu(Player player, String cacheName, String menuFile) {
        openMenu(player, cacheName, menuFile, 1);
    }

    private String getCacheDisplayName(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache != null ? cache.getDisplayName() : cacheName;
    }

    private void fillMenuItems(Player player, Inventory inventory, FileConfiguration menuConfig, String cacheName, String menuFile) {
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
                Cache cacheObj = cacheManager.getCache(cacheName);
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
            ItemStack item = itemBuilder.createMenuItem(player, section, cacheName, staticItemCache, translatedNameCache, translatedLoreCache);
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

    public void updateMenu(Player player, Inventory inventory, String menuFile, String cacheName, int page) {
        FileConfiguration cfg = configManager.loadMenuConfig(menuFile);
        if (cfg == null) return;
        invalidateStaticCache(cacheName);
        fillMenuItems(player, inventory, cfg, cacheName, menuFile);
        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), inventory.getSize());
        if ("history-menu.yml".equals(menuFile)) {
            lootManager.fillHistoryItemsAsync(player, inventory, cacheName, page, slots);
        } else {
            lootManager.fillLootItems(player, inventory, cfg, cacheName, page, menuFile, cachePageLoot, slots);
        }
    }

    private void updateSingleItem(Player player, Inventory inventory, String menuFile, String cacheName, int page, int slot) {
        FileConfiguration cfg = configManager.loadMenuConfig(menuFile);
        if (cfg == null) return;
        List<Integer> lootSlots = parseSlotRange(cfg.getStringList("loot.slots"), inventory.getSize());
        int idx = lootSlots.indexOf(slot);
        if (idx == -1) return;
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;
        cacheManager.loadCache(cache);
        Map<Integer, List<ItemStack>> pageLoot = cachePageLoot.getOrDefault(cacheName, Collections.emptyMap());
        List<ItemStack> loot = pageLoot.getOrDefault(page, Collections.emptyList());
        if (idx >= loot.size()) return;
        ItemStack item = loot.get(idx);
        if (item == null) return;
        ItemStack display = item.clone();
        ConfigurationSection settings = cfg.getConfigurationSection("loot-item-settings");
        if (settings != null) lootManager.applyChanceLore(player, display, settings, cache, (page - 1) * lootSlots.size() + idx);
        inventory.setItem(slot, display);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CacheMenuHolder holder)) return;
        Player p = (Player) event.getWhoClicked();
        String menuFile = holder.getMenuFile();
        String cacheName = holder.getCacheName();
        int page = holder.getCurrentPage();
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
        if (isLootMenu && lootSlots.contains(slot)) {
            ClickType click = event.getClick();
            if (click == ClickType.LEFT ||
                    click == ClickType.RIGHT ||
                    click == ClickType.SHIFT_LEFT ||
                    click == ClickType.SHIFT_RIGHT ||
                    click == ClickType.DROP ||
                    click == ClickType.CONTROL_DROP ||
                    click == ClickType.MIDDLE ||
                    click == ClickType.SWAP_OFFHAND) {
                event.setCancelled(false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> updatePageLootCache(cacheName, page, event.getInventory()), 1L);
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (isChanceMenu && lootSlots.contains(slot)) {
            event.setCancelled(true);
            handleChanceClick(p, cfg, cacheName, event, page, lootSlots, holder);
            return;
        }
        if (!lootSlots.contains(slot)) {
            handleNonLootClick(p, cfg, cacheName, event, page, holder);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CacheMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleChanceClick(Player p, FileConfiguration cfg, String cacheName, InventoryClickEvent e, int page, List<Integer> lootSlots, CacheMenuHolder holder) {
        ConfigurationSection settings = cfg.getConfigurationSection("loot-item-settings");
        if (settings == null) return;
        List<String> cmds = clickActionHandler.getClickCommands(settings, e.getClick());
        if (cmds.isEmpty()) return;
        int slotIdx = lootSlots.indexOf(e.getSlot());
        if (slotIdx == -1) return;
        int itemIdx = (page - 1) * lootSlots.size() + slotIdx;
        clickActionHandler.executeClickCommands(p, cmds, cacheName, e.getInventory(), page, itemIdx, holder);
    }

    private void handleNonLootClick(Player p, FileConfiguration cfg, String cacheName, InventoryClickEvent e, int page, CacheMenuHolder holder) {
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
                List<String> commands = clickActionHandler.getClickCommands(sec, e.getClick());
                if (!commands.isEmpty()) {
                    clickActionHandler.executeClickCommands(p, commands, cacheName, e.getInventory(), page, e.getSlot(), holder);
                }
                return;
            }
        }
    }

    private void handleIncreaseChance(Player p, int index) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            actionHandler.handleChanceChange(p, index, true, 1, holder, (name, idx) -> initializeCachePageLoot(name), idx -> {
                int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                updateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), holder.getCacheName(), holder.getCurrentPage(), slot);
            });
        }
    }

    private void handleReduceChance(Player p, int index) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            actionHandler.handleChanceChange(p, index, false, 1, holder, (name, idx) -> initializeCachePageLoot(name), idx -> {
                int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                updateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), holder.getCacheName(), holder.getCurrentPage(), slot);
            });
        }
    }

    private void handleUpdateMenu(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            updateMenu(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), holder.getCacheName(), holder.getCurrentPage());
        }
    }

    private void handleNextPage(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            int max = configManager.loadMenuConfig("loot-menu.yml").getInt("menu.max-pages", 5);
            if (holder.getCurrentPage() < max) openMenu(p, holder.getCacheName(), holder.getMenuFile(), holder.getCurrentPage() + 1);
        }
    }

    private void handlePreviousPage(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            if (holder.getCurrentPage() > 1) openMenu(p, holder.getCacheName(), holder.getMenuFile(), holder.getCurrentPage() - 1);
        }
    }

    private void handleChangeKeyMaterial(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            invalidateStaticCache(holder.getCacheName());
            plugin.getCacheModeListener().enableKeyMaterialMode(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    private void handleChangeKeyName(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            invalidateStaticCache(holder.getCacheName());
            plugin.getCacheModeListener().enableKeyNameMode(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    private void handleChangeKeyLore(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            invalidateStaticCache(holder.getCacheName());
            plugin.getCacheModeListener().enableKeyLoreMode(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    private void handleChangeKeyCMD(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            invalidateStaticCache(holder.getCacheName());
            plugin.getCacheModeListener().enableKeyCMDMode(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    private void handleChangeKeyFlags(Player p, int idx) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            invalidateStaticCache(holder.getCacheName());
            plugin.getCacheModeListener().enableKeyFlagsMode(p, holder.getCacheName());
        }
        p.closeInventory();
    }

    public void clearCacheForCache(String cacheName) {
        cachePageLoot.remove(cacheName);
        invalidateStaticCache(cacheName);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CacheMenuHolder holder) {
            Player p = (Player) event.getPlayer();
            String menuFile = holder.getMenuFile();
            String cacheName = holder.getCacheName();
            int page = holder.getCurrentPage();
            if ("loot-menu.yml".equals(menuFile)) {
                List<Integer> slots = parseSlotRange(configManager.loadMenuConfig("loot-menu.yml").getStringList("loot.slots"), event.getInventory().getSize());
                lootManager.saveLootForPage(p, cacheName, page, event.getInventory(), cachePageLoot, slots);
            }
        }
    }

    public void initializeCachePageLoot(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
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

    public String getCurrentMenu(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
            return holder.getMenuFile();
        }
        return null;
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

    public void reload() {
        slotRangeCache.clear();
        cachePageLoot.clear();
        staticItemCache.clear();
        translatedNameCache.clear();
        translatedLoreCache.clear();
        for (String name : cacheManager.getCacheNames()) {
            initializeCachePageLoot(name);
        }
        plugin.log("Системы меню успешно перезагружены!");
    }

    public void closeAllMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder) {
                player.closeInventory();
            }
        }
    }

    public void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                openMenu(player, holder.getCacheName(), holder.getMenuFile(), holder.getCurrentPage());
            }
        }
    }

    @Override
    public void delegateOpenMenu(Player player, String cacheName, String menuFile, int page) {
        openMenu(player, cacheName, menuFile, page);
    }

    @Override
    public void delegateInitializeCachePageLoot(String cacheName) {
        initializeCachePageLoot(cacheName);
    }

    @Override
    public void delegateUpdateSingleItem(Player player, Inventory inventory, String menuFile, String cacheName, int page, int slot) {
        updateSingleItem(player, inventory, menuFile, cacheName, page, slot);
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

    private int calculateSlotFromIndex(int itemIndex, int page, int invSize) {
        FileConfiguration cfg = configManager.loadMenuConfig("chance-menu.yml");
        if (cfg == null) return 0;
        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), invSize);
        if (slots.isEmpty()) return 0;
        int perPage = slots.size();
        int slotIdx = itemIndex % perPage;
        return slotIdx < slots.size() ? slots.get(slotIdx) : 0;
    }
}