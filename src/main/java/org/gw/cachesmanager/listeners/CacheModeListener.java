package org.gw.cachesmanager.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.managers.MenuManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CacheModeListener implements Listener {
    private final CachesManager plugin;
    private final CacheManager cacheManager;
    private final ConfigManager configManager;
    private MenuManager menuManager;

    private final Map<UUID, ModeData> activeModes = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMenuFile = new HashMap<>();

    private enum PlayerMode {
        SELECTION,
        REPLACE_BLOCK,
        RENAME,
        HOLOGRAM_TEXT,
        HOLOGRAM_OFFSET_X,
        HOLOGRAM_OFFSET_Y,
        HOLOGRAM_OFFSET_Z,
        KEY_MATERIAL,
        KEY_NAME,
        KEY_LORE,
        KEY_CMD,
        KEY_FLAGS
    }

    private static class ModeData {
        final String cacheName;
        final PlayerMode mode;
        final BukkitRunnable timeoutTask;

        ModeData(String cacheName, PlayerMode mode, BukkitRunnable timeoutTask) {
            this.cacheName = cacheName;
            this.mode = mode;
            this.timeoutTask = timeoutTask;
        }
    }

    public CacheModeListener(CachesManager plugin, CacheManager cacheManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    public void setMenuManager(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public void enableSelectionMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.SELECTION, "interaction.select-block.mode-enabled");
    }

    public void enableReplaceBlockMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.REPLACE_BLOCK, "interaction.replace-block.mode-enabled");
    }

    public void enableRenameMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.RENAME, "interaction.rename.mode-enabled");
    }

    public void enableHologramTextMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_TEXT, "interaction.hologram.change-text.mode-enabled");
    }

    public void enableHologramOffsetXMode(Player player, String cacheName) {
        enableOffsetMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_X, "interaction.hologram.offset-x.mode-enabled", "current-x");
    }

    public void enableHologramOffsetYMode(Player player, String cacheName) {
        enableOffsetMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Y, "interaction.hologram.offset-y.mode-enabled", "current-y");
    }

    public void enableHologramOffsetZMode(Player player, String cacheName) {
        enableOffsetMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Z, "interaction.hologram.offset-z.mode-enabled", "current-z");
    }

    public void enableKeyMaterialMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_MATERIAL, "key.change-material.mode-enabled");
    }

    public void enableKeyNameMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_NAME, "key.change-name.mode-enabled");
    }

    public void enableKeyLoreMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_LORE, "key.change-lore.mode-enabled");
    }

    public void enableKeyCMDMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_CMD, "key.change-cmd.mode-enabled");
    }

    public void enableKeyFlagsMode(Player player, String cacheName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        ph.put("key-flags", cache != null ? cache.getKeyFlagsString() : "Отсутствуют");
        enableMode(player, cacheName, PlayerMode.KEY_FLAGS, "key.change-flags.mode-enabled", ph);
    }

    private void enableMode(Player player, String cacheName, PlayerMode mode, String messagePath) {
        enableMode(player, cacheName, mode, messagePath, null);
    }

    private void enableMode(Player player, String cacheName, PlayerMode mode, String messagePath, Map<String, String> extraPh) {
        cancelSelectionMode(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        if (extraPh != null) ph.putAll(extraPh);

        configManager.executeActions(player, messagePath, ph);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                cancelSelectionMode(player);
            }
        };
        task.runTaskLater(plugin, configManager.getModeTimeoutSeconds() * 20L);

        activeModes.put(player.getUniqueId(), new ModeData(cacheName, mode, task));

        String currentMenu = menuManager != null ? menuManager.getCurrentMenu(player) : null;
        if (currentMenu != null) {
            lastMenuFile.put(player.getUniqueId(), currentMenu);
        }
    }

    private void enableOffsetMode(Player player, String cacheName, PlayerMode mode, String messagePath, String currentKey) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        double value = 0.0;
        if (mode == PlayerMode.HOLOGRAM_OFFSET_X) value = cache != null ? cache.getHologramOffsetX() : 0.0;
        else if (mode == PlayerMode.HOLOGRAM_OFFSET_Y) value = cache != null ? cache.getHologramOffsetY() : 0.5;
        else if (mode == PlayerMode.HOLOGRAM_OFFSET_Z) value = cache != null ? cache.getHologramOffsetZ() : 0.0;
        ph.put(currentKey, String.valueOf(value));

        enableMode(player, cacheName, mode, messagePath, ph);
    }

    public boolean cancelSelectionMode(Player player) {
        ModeData data = activeModes.remove(player.getUniqueId());
        if (data == null) return false;

        if (data.timeoutTask != null) data.timeoutTask.cancel();

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", data.cacheName != null ? data.cacheName : "");

        String path;
        switch (data.mode) {
            case SELECTION:
                path = "interaction.select-block.cancelled";
                break;
            case REPLACE_BLOCK:
                path = "interaction.replace-block.cancelled";
                break;
            case RENAME:
                path = "interaction.rename.cancelled";
                break;
            case HOLOGRAM_TEXT:
                path = "interaction.hologram.change-text.cancelled";
                break;
            case HOLOGRAM_OFFSET_X:
                path = "interaction.hologram.offset-x.cancelled";
                break;
            case HOLOGRAM_OFFSET_Y:
                path = "interaction.hologram.offset-y.cancelled";
                break;
            case HOLOGRAM_OFFSET_Z:
                path = "interaction.hologram.offset-z.cancelled";
                break;
            case KEY_MATERIAL:
                path = "key.change-material.cancelled";
                break;
            case KEY_NAME:
                path = "key.change-name.cancelled";
                break;
            case KEY_LORE:
                path = "key.change-lore.cancelled";
                break;
            case KEY_CMD:
                path = "key.change-cmd.cancelled";
                break;
            case KEY_FLAGS:
                path = "key.change-flags.cancelled";
                break;
            default:
                path = "interaction.select-block.cancelled";
        }

        configManager.executeActions(player, path, ph);
        reopenLastMenu(player, data.cacheName);
        return true;
    }

    public void removeSelectionMode(Player player) {
        activeModes.remove(player.getUniqueId());
    }

    public String getSelectionMode(Player player) {
        ModeData data = activeModes.get(player.getUniqueId());
        return data != null && data.mode == PlayerMode.SELECTION ? data.cacheName : null;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ModeData data = activeModes.get(player.getUniqueId());
        if (data == null) return;

        event.setCancelled(true);
        event.getRecipients().clear();

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            cancelSelectionMode(player);
            return;
        }

        if (data.timeoutTask != null) data.timeoutTask.cancel();

        Bukkit.getScheduler().runTask(plugin, () -> handleChat(player, data, message));
    }

    private void handleChat(Player player, ModeData data, String message) {
        switch (data.mode) {
            case SELECTION:
                handleSelectionModeChat(player, data.cacheName, message);
                break;
            case REPLACE_BLOCK:
                handleReplaceBlockModeChat(player, data.cacheName, message);
                break;
            case RENAME:
                handleRenameModeChat(player, data.cacheName, message);
                break;
            case HOLOGRAM_TEXT:
                handleHologramTextModeChat(player, data.cacheName, message);
                break;
            case HOLOGRAM_OFFSET_X:
                handleOffsetChat(player, data.cacheName, message, "X");
                break;
            case HOLOGRAM_OFFSET_Y:
                handleOffsetChat(player, data.cacheName, message, "Y");
                break;
            case HOLOGRAM_OFFSET_Z:
                handleOffsetChat(player, data.cacheName, message, "Z");
                break;
            case KEY_MATERIAL:
                handleKeyMaterialChat(player, data.cacheName, message);
                break;
            case KEY_NAME:
                handleKeyNameChat(player, data.cacheName, message);
                break;
            case KEY_LORE:
                handleKeyLoreChat(player, data.cacheName, message);
                break;
            case KEY_CMD:
                handleKeyCMDChat(player, data.cacheName, message);
                break;
            case KEY_FLAGS:
                handleKeyFlagsChat(player, data.cacheName, message);
                break;
        }
    }

    private void handleSelectionModeChat(Player player, String cacheName, String message) {
        String[] parts = message.split(" ");
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (parts.length != 5) {
            configManager.executeActions(player, "cache.invalid-coordinates-format", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(parts[0]);
            y = Double.parseDouble(parts[1]);
            z = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            configManager.executeActions(player, "cache.invalid-coordinates", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        String worldName = parts[3];
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            ph.put("world", worldName);
            configManager.executeActions(player, "cache.invalid-world", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        String blockId = parts[4];
        Material blockType;
        try {
            blockType = Material.valueOf(blockId.toUpperCase());
            if (!blockType.isBlock()) {
                throw new IllegalArgumentException("Not a block");
            }
        } catch (IllegalArgumentException e) {
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.invalid", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        org.bukkit.Location location = new org.bukkit.Location(world, x, y, z);
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        CacheManager.Cache existingCache = cacheManager.getCacheByLocation(location);
        if (existingCache != null && !existingCache.name.equals(cacheName)) {
            ph.put("name-cache", existingCache.getDisplayName());
            configManager.executeActions(player, "cache.already-exists", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());
        cache.setLocation(location);
        cache.setBlockType(blockType);

        ph.put("x", String.valueOf(location.getBlockX()));
        ph.put("y", String.valueOf(location.getBlockY()));
        ph.put("z", String.valueOf(location.getBlockZ()));
        ph.put("world", location.getWorld().getName());
        configManager.executeActions(player, "interaction.select-block.set-location", ph);
        reopenLastMenu(player, cacheName);
    }

    private void handleReplaceBlockModeChat(Player player, String cacheName, String blockId) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        org.bukkit.Location location = cache.getLocation();
        if (location == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        Material newBlockType;
        try {
            newBlockType = Material.valueOf(blockId.toUpperCase());
            if (!newBlockType.isBlock()) {
                throw new IllegalArgumentException("Not a block");
            }
        } catch (IllegalArgumentException e) {
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.invalid", ph);
            reopenLastMenu(player, cacheName);
            return;
        }

        if (cache.getBlockType() == newBlockType) {
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.same", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());
        cache.setBlockType(newBlockType);
        ph.put("block-id", newBlockType.name().toLowerCase());
        configManager.executeActions(player, "interaction.replace-block.completed", ph);
        reopenLastMenu(player, cacheName);
    }

    private void handleRenameModeChat(Player player, String cacheName, String newNameRaw) {
        String newName = newNameRaw.trim();

        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        if (newName.isEmpty()) {
            configManager.executeActions(player, "interaction.rename.empty-name", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        ph.put("new-name", newName);

        if (cacheManager.getCache(newName) != null) {
            configManager.executeActions(player, "interaction.rename.already-exists", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());

        if (cacheManager.renameCache(cacheName, newName)) {
            ph.put("old-name", cacheName);
            ph.put("new-name", newName);
            configManager.executeActions(player, "interaction.rename.completed", ph);

            reopenLastMenu(player, newName);
        } else {
            configManager.executeActions(player, "interaction.rename.failed", ph);
            reopenLastMenu(player, cacheName);
        }
    }

    private void handleHologramTextModeChat(Player player, String cacheName, String newText) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            return;
        }

        newText = newText.replace("\\n", "\n");
        cache.setHologramText(newText);

        plugin.getHologramManager().updateHologram(cacheName, newText);

        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(player, "interaction.hologram.change-text.completed", ph);
        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleOffsetChat(Player player, String cacheName, String value, String axis) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            return;
        }

        try {
            double newOffset = Double.parseDouble(value);

            String actionPath;
            if ("X".equals(axis)) {
                cache.setHologramOffsetX(newOffset);
                actionPath = "interaction.hologram.offset-x-changed";
            } else if ("Y".equals(axis)) {
                cache.setHologramOffsetY(newOffset);
                actionPath = "interaction.hologram.offset-y-changed";
            } else {
                cache.setHologramOffsetZ(newOffset);
                actionPath = "interaction.hologram.offset-z-changed";
            }

            ph.put("offset", String.valueOf(newOffset));
            configManager.executeActions(player, actionPath, ph);
            plugin.getHologramManager().updateHologram(cacheName, cache.getHologramText());
        } catch (NumberFormatException e) {
            configManager.executeActions(player, "interaction.hologram.offset-invalid-number");
        }
        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyMaterialChat(Player player, String cacheName, String materialStr) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        Material mat = Material.matchMaterial(materialStr.toUpperCase());
        if (mat == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("material", materialStr);
            configManager.executeActions(player, "key.change-material.invalid", ph);
            return;
        }

        cache.setKeyMaterial(mat.name());
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("key-material", mat.name());
        configManager.executeActions(player, "key.material-changed", ph);

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyNameChat(Player player, String cacheName, String newName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        if (newName.trim().equalsIgnoreCase("none")) {
            cache.setKeyName(null);
        } else {
            cache.setKeyName(newName);
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(player, "key.name-changed", ph);

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyLoreChat(Player player, String cacheName, String input) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            activeModes.remove(player.getUniqueId());
            return;
        }

        List<String> newLore;
        String trimmed = input.trim();

        if (trimmed.equalsIgnoreCase("none")) {
            newLore = new ArrayList<>();
        } else {
            String processed = input.replace("\\n", "\n");
            newLore = Arrays.stream(processed.split("\n"))
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (newLore.stream().allMatch(String::isEmpty)) {
                newLore = new ArrayList<>();
            }
        }

        cache.setKeyLore(newLore);
        configManager.saveCacheConfig(cacheName);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(player, "key.lore-changed", ph);

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyCMDChat(Player player, String cacheName, String cmdStr) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        try {
            int cmd = Integer.parseInt(cmdStr);
            cache.setKeyCustomModelData(cmd);
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());
            ph.put("key-cmd", String.valueOf(cmd));
            configManager.executeActions(player, "key.cmd-changed", ph);

            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
        } catch (NumberFormatException e) {
            configManager.executeActions(player, "key.change-cmd.invalid");
        }
    }

    private void handleKeyFlagsChat(Player player, String cacheName, String input) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            activeModes.remove(player.getUniqueId());
            return;
        }

        String flagInput = input.trim().toUpperCase();
        boolean isRemove = flagInput.startsWith("-");
        String flagName = isRemove ? flagInput.substring(1) : flagInput;

        ItemFlag itemFlag;
        try {
            itemFlag = ItemFlag.valueOf(flagName);
        } catch (IllegalArgumentException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("flag", flagName);
            configManager.executeActions(player, "key.change-flags.invalid", ph);
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("flag", flagName);

        if (isRemove) {
            if (cache.removeKeyFlag(flagName)) {
                configManager.executeActions(player, "key.change-flags.removed", ph);
            } else {
                configManager.executeActions(player, "key.change-flags.not-found", ph);
                return;
            }
        } else {
            if (cache.addKeyFlag(flagName)) {
                configManager.executeActions(player, "key.change-flags.added", ph);
            } else {
                configManager.executeActions(player, "key.change-flags.already-exists", ph);
                return;
            }
        }

        configManager.saveCacheConfig(cacheName);
        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void reopenLastMenu(Player player, String cacheName) {
        String menu = lastMenuFile.remove(player.getUniqueId());
        if (menu == null) menu = "key-menu.yml";
        player.closeInventory();
        String finalMenu = menu;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (menuManager != null) menuManager.openMenu(player, cacheName, finalMenu);
        }, 3L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelSelectionMode(event.getPlayer());
        lastMenuFile.remove(event.getPlayer().getUniqueId());
    }
}