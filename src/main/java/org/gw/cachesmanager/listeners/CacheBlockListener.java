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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.managers.ItemManager;
import org.gw.cachesmanager.managers.MenuManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.HashMap;
import java.util.List;
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
        CacheManager.Cache cache = location != null ? cacheManager.getCacheByLocation(location) : null;

        if (cache != null) {
            if (event.getHand() != EquipmentSlot.HAND) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);

            FileConfiguration menuConfig = configManager.loadMenuConfig("global-menu.yml");
            if (menuConfig == null) {
                plugin.log("Не удалось загрузить global-menu.yml");
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
                    plugin.log("Некорректный click-type в global-menu.yml: &#ffff00" + clickTypeStr);
                    clickMatches = isShift && isRightClick;
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());

            if (clickMatches) {
                if (!player.hasPermission(permission)) {
                    configManager.executeActions(player, "errors.no-permission");
                    return;
                }
                menuManager.openMenu(player, cache.name, "global-menu.yml");
                return;
            }

            if (isRightClick) {
                if (!player.hasPermission("cachesmanager.opencache")) {
                    configManager.executeActions(player, "errors.no-permission");
                    return;
                }

                ItemStack keyItem = null;
                EquipmentSlot keyHand = null;

                if (itemManager.isKey(mainItem, cache.name)) {
                    keyItem = mainItem;
                    keyHand = EquipmentSlot.HAND;
                } else if (itemManager.isKey(offItem, cache.name)) {
                    keyItem = offItem;
                    keyHand = EquipmentSlot.OFF_HAND;
                }

                if (keyItem == null) {
                    configManager.executeActions(player, "key.no-key-in-hand", ph);
                    return;
                }

                if (cache.isInUse()) {
                    configManager.executeActions(player, "cache.in-use", ph);
                    return;
                }

                if (cache.open(player)) {
                    if (keyItem.getAmount() > 1) {
                        keyItem.setAmount(keyItem.getAmount() - 1);
                    } else {
                        if (keyHand == EquipmentSlot.HAND) {
                            player.getInventory().setItemInMainHand(null);
                        } else {
                            player.getInventory().setItemInOffHand(null);
                        }
                    }
                }
                return;
            }

            if (isLeftClick) {
                if (cache.isUnbreakable()) {
                    event.setCancelled(true);
                } else {
                    event.setCancelled(false);
                }
                return;
            }
        }

        if (itemManager.isAnyKey(mainItem) || itemManager.isAnyKey(offItem)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String cacheName = plugin.getCacheModeListener().getSelectionMode(player);
        Location location = event.getBlock().getLocation();
        CacheManager.Cache existingCache = cacheManager.getCacheByLocation(location);

        Map<String, String> ph = new HashMap<>();

        if (existingCache != null) {
            if (cacheName != null) {
                event.setCancelled(true);
                ph.put("name-cache", existingCache.getDisplayName());
                if (existingCache.name.equals(cacheName)) {
                    configManager.executeActions(player, "interaction.select-block.same-cache", ph);
                } else {
                    configManager.executeActions(player, "cache.already-exists", ph);
                }
                return;
            }

            if (existingCache.isUnbreakable()) {
                event.setCancelled(true);
                event.setDropItems(false);
                ph.put("name-cache", existingCache.getDisplayName());
                configManager.executeActions(player, "cache.break-forbidden", ph);
                return;
            } else {
                event.setCancelled(false);
                return;
            }
        }

        if (cacheName != null) {
            event.setCancelled(true);
            Block block = event.getBlock();
            Material blockType = block.getType();

            CacheManager.Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                ph.put("name-cache", cacheName);
                configManager.executeActions(player, "cache.not-found", ph);
                plugin.getCacheModeListener().cancelSelectionMode(player);
                return;
            }

            plugin.getCacheModeListener().removeSelectionMode(player);
            cache.setLocation(location);
            cache.setBlockType(blockType);
            ph.put("name-cache", cacheName);
            ph.put("x", String.valueOf(location.getBlockX()));
            ph.put("y", String.valueOf(location.getBlockY()));
            ph.put("z", String.valueOf(location.getBlockZ()));
            ph.put("world", location.getWorld().getName());
            configManager.executeActions(player, "interaction.select-block.set-location", ph);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCacheModeListener().cancelSelectionMode(player);
        plugin.getAnimationsManager().forceFinishAnimationForPlayer(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getAnimationsManager().givePendingLootToPlayer(player);
    }
}