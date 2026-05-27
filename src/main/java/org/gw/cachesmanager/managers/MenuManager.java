package org.gw.cachesmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.TextComponent;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.listeners.CacheModeListener;
import org.gw.cachesmanager.menus.CacheMenuHolder;
import org.gw.cachesmanager.menus.MenuActionHandler;
import org.gw.cachesmanager.menus.MenuItemBuilder;
import org.gw.cachesmanager.menus.MenuLootManager;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class MenuManager implements Listener {
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

        this.itemBuilder = new MenuItemBuilder(plugin);
        this.lootManager = new MenuLootManager(plugin);
        this.actionHandler = new MenuActionHandler(plugin);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initSpecialHandlers();
    }

    private void initSpecialHandlers() {
        specialHandlers.put("[close-menu]", (p, i) -> p.closeInventory());
        specialHandlers.put("[selection-mode]", (p, i) -> enableMode(p, cacheModeListener::enableSelectionMode));
        specialHandlers.put("[rename-cache]", (p, i) -> enableMode(p, cacheModeListener::enableRenameMode));
        specialHandlers.put("[replace-block-cache]", (p, i) -> enableMode(p, cacheModeListener::enableReplaceBlockMode));
        specialHandlers.put("[change-hologram-text]", (p, i) -> enableMode(p, cacheModeListener::enableHologramTextMode));
        specialHandlers.put("[toggle-unbreakable]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleUnbreakable(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[update-menu]", this::handleUpdateMenu);
        specialHandlers.put("[next-page]", this::handleNextPage);
        specialHandlers.put("[previous-page]", this::handlePreviousPage);
        specialHandlers.put("[next-animation]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleNextAnimation(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[last-animation]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handlePreviousAnimation(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[increase-chance]", this::handleIncreaseChance);
        specialHandlers.put("[reduce-chance]", this::handleReduceChance);
        specialHandlers.put("[toggle-hologram]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleHologram(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[hologram-offset-x]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetXMode));
        specialHandlers.put("[hologram-offset-y]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetYMode));
        specialHandlers.put("[hologram-offset-z]", (p, i) -> enableMode(p, cacheModeListener::enableHologramOffsetZMode));
        specialHandlers.put("[change-key-material]", this::handleChangeKeyMaterial);
        specialHandlers.put("[change-key-name]", this::handleChangeKeyName);
        specialHandlers.put("[change-key-lore]", this::handleChangeKeyLore);
        specialHandlers.put("[change-key-cmd]", this::handleChangeKeyCMD);
        specialHandlers.put("[toggle-key-glow]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleToggleKeyGlow(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[reset-key-to-default]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                actionHandler.handleResetKeyToDefault(p, holder, () -> invalidateStaticCache(holder.getCacheName()));
            }
        });
        specialHandlers.put("[change-flags]", this::handleChangeKeyFlags);
        specialHandlers.put("[open-stats]", (p, i) -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CacheMenuHolder holder) {
                String cache = holder.getCacheName();
                p.closeInventory();
                openMenu(p, cache, "stats-menu.yml");
            }
        });
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
            lootManager.fillHistoryItems(player, inventory, cacheName, page, slots);
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
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
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
            lootManager.fillHistoryItems(player, inventory, cacheName, page, slots);
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
        List<String> cmds = getClickCommands(settings, e.getClick());
        if (cmds.isEmpty()) return;
        int slotIdx = lootSlots.indexOf(e.getSlot());
        if (slotIdx == -1) return;
        int itemIdx = (page - 1) * lootSlots.size() + slotIdx;
        executeClickCommands(p, cmds, cacheName, e.getInventory(), page, itemIdx, holder);
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
                List<String> commands = getClickCommands(sec, e.getClick());
                if (!commands.isEmpty()) {
                    executeClickCommands(p, commands, cacheName, e.getInventory(), page, e.getSlot(), holder);
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

    private void executeClickCommands(Player p, List<String> commands, String cacheName, Inventory inv, int page, int index, CacheMenuHolder holder) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;
        for (String rawCmd : commands) {
            String processed = rawCmd.replace("{name-cache}", cache.getDisplayName())
                    .replace("{player}", p.getName()).trim();
            processed = PlaceholderAPIHook.parse(p, processed);
            if (processed.startsWith("[")) {
                int closingBracket = processed.indexOf("]");
                if (closingBracket != -1) {
                    String actionTag = processed.substring(0, closingBracket + 1).toLowerCase();
                    String rawValue = processed.substring(closingBracket + 1).trim();
                    switch (actionTag) {
                        case "[increase-chance]" -> {
                            int delta = extractDelta(processed, 1);
                            actionHandler.handleChanceChange(p, index, true, delta, holder, (name, idx) -> initializeCachePageLoot(name), idx -> {
                                int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                                updateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage(), slot);
                            });
                        }
                        case "[reduce-chance]" -> {
                            int delta = extractDelta(processed, 1);
                            actionHandler.handleChanceChange(p, index, false, delta, holder, (name, idx) -> initializeCachePageLoot(name), idx -> {
                                int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                                updateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage(), slot);
                            });
                        }
                        case "[open-menu]" -> openMenu(p, cacheName, rawValue, 1);
                        case "[message]" -> {
                            String message = rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
                            p.sendMessage(HexColors.translate(message));
                        }
                        case "[message-console]" -> plugin.console(HexColors.translate(rawValue));
                        case "[broadcast]" -> Bukkit.broadcastMessage(HexColors.translate(rawValue));
                        case "[actionbar]" -> p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(HexColors.translate(rawValue)));
                        case "[console-command]" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rawValue);
                        case "[player-command]" -> Bukkit.dispatchCommand(p, rawValue);
                        case "[sound]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                try {
                                    Sound sound = Sound.valueOf(parts[0].toUpperCase());
                                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                                    p.playSound(p.getLocation(), sound, volume, pitch);
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[title]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                String title = HexColors.translate(parts[0]);
                                int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                                int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                                int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                                p.sendTitle(title, "", fadeIn, stay, fadeOut);
                            }
                        }
                        case "[subtitle]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                String subtitle = HexColors.translate(parts[0]);
                                int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                                int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                                int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                                p.sendTitle("", subtitle, fadeIn, stay, fadeOut);
                            }
                        }
                        case "[effect]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                try {
                                    PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                                    int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
                                    int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
                                    p.addPotionEffect(new PotionEffect(type, duration, amplifier));
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[teleport]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 4) {
                                try {
                                    double x = Double.parseDouble(parts[0]);
                                    double y = Double.parseDouble(parts[1]);
                                    double z = Double.parseDouble(parts[2]);
                                    World world = Bukkit.getWorld(parts[3]);
                                    if (world != null) p.teleport(new Location(world, x, y, z));
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[give-item]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 2) {
                                try {
                                    Material material = Material.valueOf(parts[0].toUpperCase());
                                    int amount = Integer.parseInt(parts[1]);
                                    p.getInventory().addItem(new ItemStack(material, amount));
                                } catch (Exception ignored) {}
                            }
                        }
                        default -> {
                            BiConsumer<Player, Integer> handler = specialHandlers.get(actionTag);
                            if (handler != null) {
                                handler.accept(p, index);
                            } else {
                                Bukkit.dispatchCommand(p, processed);
                            }
                        }
                    }
                    continue;
                }
            }
            BiConsumer<Player, Integer> handler = specialHandlers.get(processed.toLowerCase());
            if (handler != null) {
                handler.accept(p, index);
            } else {
                Bukkit.dispatchCommand(p, processed);
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
        plugin.log("Системы менюшек успешно перезагружены");
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