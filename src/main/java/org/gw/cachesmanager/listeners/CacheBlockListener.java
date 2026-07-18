package org.gw.cachesmanager.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.managers.ItemManager;
import org.gw.cachesmanager.managers.MenuManager;

import java.util.HashMap;
import java.util.Map;

public class CacheBlockListener implements Listener {
    private final CachesManager plugin;
    private final CacheManager cacheManager;
    private final ItemManager itemManager;
    private final MenuManager menuManager;
    private final ConfigManager configManager;

    public CacheBlockListener(CachesManager plugin, CacheManager cacheManager, ItemManager itemManager, MenuManager menuManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.itemManager = itemManager;
        this.menuManager = menuManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainItem = player.getInventory().getItemInMainHand();
        ItemStack offItem = player.getInventory().getItemInOffHand();

        Location location = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
        Cache cache = location != null ? cacheManager.getCacheByLocation(location) : null;

        if (cache != null) {
            if (event.getHand() != EquipmentSlot.HAND) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);

            FileConfiguration menuConfig = configManager.loadMenuConfig("global-menu.yml");
            if (menuConfig == null) {
                plugin.log("Не удалось загрузить файл конфигурации &#FB8808global-menu.yml&f...");
                return;
            }

            String clickTypeStr = menuConfig.getString("menu.interaction.click-type", "shift-right").toLowerCase();
            String permission = menuConfig.getString("menu.interaction.permission", "cachesmanager.openmenu.global-menu");

            boolean isShift = player.isSneaking();
            boolean isRightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK;
            boolean isLeftClick = event.getAction() == Action.LEFT_CLICK_BLOCK;

            boolean clickMatches = false;
            switch (clickTypeStr) {
                case "shift-right":
                    clickMatches = isShift && isRightClick;
                    break;
                case "shift-left":
                    clickMatches = isShift && isLeftClick;
                    break;
                case "right":
                    clickMatches = !isShift && isRightClick;
                    break;
                case "left":
                    clickMatches = !isShift && isLeftClick;
                    break;
                default:
                    plugin.log("Некорректный тип клика &#FB8808click-type &fв global-menu.yml: &#FB8808" + clickTypeStr + "&f...");
                    clickMatches = isShift && isRightClick;
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getName());

            if (clickMatches && player.hasPermission(permission)) {
                plugin.log("Игрок &#ffff00" + player.getName() + " &fоткрыл главное меню управления для тайника &#ffff00" + cache.getName());
                menuManager.openMenu(player, cache.getName(), "global-menu.yml");
                return;
            }

            if (isRightClick) {
                if (!player.hasPermission("cachesmanager.opencache")) {
                    configManager.executeActions(player, "errors.no-permission");
                    return;
                }

                EquipmentSlot keyHand = null;
                if (itemManager.isKey(mainItem, cache.getName())) {
                    keyHand = EquipmentSlot.HAND;
                } else if (itemManager.isKey(offItem, cache.getName())) {
                    keyHand = EquipmentSlot.OFF_HAND;
                }

                if (keyHand == null) {
                    boolean hasPluginKey = itemManager.isAnyKey(mainItem) || itemManager.isAnyKey(offItem);
                    if (hasPluginKey) {
                        configManager.executeActions(player, "key.wrong-key", ph);
                    } else {
                        configManager.executeActions(player, "key.no-key-in-hand", ph);
                    }
                    return;
                }

                if (cache.isInUse()) {
                    boolean actuallyRunning = plugin.getAnimationsManager() != null
                            && plugin.getAnimationsManager().hasActiveAnimation(cache.getName());

                    if (!actuallyRunning) {
                        cache.setInUse(false);
                        plugin.log("Автоматически сброшено застрявшее состояние isInUse у тайника &#ffff00" + cache.getName());

                        if (cache.isHologramEnabled() && cache.getLocation() != null && plugin.getHologramManager() != null) {
                            plugin.getHologramManager().createHologram(cache.getName(), cache.getLocation(), cache.getHologramLines());
                        }
                    } else {
                        configManager.executeActions(player, "cache.in-use", ph);
                        return;
                    }
                }

                if (!itemManager.consumeKey(player, keyHand, cache.getName())) {
                    configManager.executeActions(player, "key.no-key-in-hand", ph);
                    return;
                }

                if (cacheManager.openCache(cache, player)) {
                    plugin.log("Игрок &#ffff00" + player.getName() + " &fуспешно использовал ключ для открытия тайника &#ffff00" + cache.getName());
                } else {
                    itemManager.giveKey(player, cache.getName(), 1);
                }
                return;
            }

            if (clickMatches) {
                configManager.executeActions(player, "errors.no-permission");
                return;
            }

            if (isLeftClick) {
                if (cache.isUnbreakable()) {
                    event.setCancelled(true);
                } else {
                    event.setCancelled(false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String cacheName = plugin.getCacheModeListener().getSelectionMode(player);
        Location location = event.getBlock().getLocation();
        Cache existingCache = cacheManager.getCacheByLocation(location);

        Map<String, String> ph = new HashMap<>();

        if (existingCache != null) {
            if (cacheName != null) {
                event.setCancelled(true);
                ph.put("name-cache", existingCache.getName());
                if (existingCache.getName().equals(cacheName)) {
                    configManager.executeActions(player, "interaction.select-block.same-cache", ph);
                } else {
                    configManager.executeActions(player, "cache.already-exists", ph);
                }
                return;
            }

            if (existingCache.isUnbreakable()) {
                event.setCancelled(true);
                event.setDropItems(false);
                ph.put("name-cache", existingCache.getName());
                configManager.executeActions(player, "cache.break-forbidden", ph);
                plugin.log("Игрок &#ffff00" + player.getName() + " &fпопытался сломать неразрушимый тайник &#ffff00" + existingCache.getName());
                return;
            } else {
                event.setCancelled(false);
                ph.put("name-cache", existingCache.getName());
                configManager.executeActions(player, "cache.block-removed", ph);
                plugin.log("Блок тайника &#ffff00" + existingCache.getName() + " &fбыл разрушен игроком &#ffff00" + player.getName());
                cacheManager.setCacheLocation(existingCache, null);
                cacheManager.setCacheBlockType(existingCache, null);
                configManager.saveCacheConfig(existingCache.getName());
                return;
            }
        }

        if (cacheName != null) {
            event.setCancelled(true);
            Block block = event.getBlock();
            Material blockType = block.getType();

            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                ph.put("name-cache", cacheName);
                configManager.executeActions(player, "cache.not-found", ph);
                plugin.getCacheModeListener().cancelSession(player);
                plugin.getCacheModeListener().discardLastMenu(player);
                return;
            }

            plugin.getCacheModeListener().removeSelectionMode(player);
            cacheManager.setCacheLocation(cache, location);
            cacheManager.setCacheBlockType(cache, blockType);
            plugin.log("Игрок &#ffff00" + player.getName() + " &fустановил физический блок тайника &#ffff00" + cacheName + " &fчерез разрушение блока");
            ph.put("name-cache", cacheName);
            ph.put("x", String.valueOf(location.getBlockX()));
            ph.put("y", String.valueOf(location.getBlockY()));
            ph.put("z", String.valueOf(location.getBlockZ()));
            ph.put("world", location.getWorld().getName());
            configManager.executeActions(player, "interaction.select-block.set-location", ph);
        }
    }
}