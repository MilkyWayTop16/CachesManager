package org.gw.cachesmanager.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.managers.MenuManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CacheModeListener implements Listener {
    private final CachesManager plugin;
    private final CacheManager cacheManager;
    private final ConfigManager configManager;
    private MenuManager menuManager;

    private final Map<UUID, ModeData> activeModes = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMenuFile = new ConcurrentHashMap<>();

    public enum PlayerMode {
        SELECTION, REPLACE_BLOCK, RENAME, HOLOGRAM_TEXT,
        HOLOGRAM_OFFSET_X, HOLOGRAM_OFFSET_Y, HOLOGRAM_OFFSET_Z,
        KEY_MATERIAL, KEY_NAME, KEY_LORE, KEY_CMD, KEY_FLAGS
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

    public String getSelectionMode(Player player) {
        ModeData data = activeModes.get(player.getUniqueId());
        return (data != null && data.mode == PlayerMode.SELECTION) ? data.cacheName : null;
    }

    public void removeSelectionMode(Player player) {
        ModeData data = activeModes.remove(player.getUniqueId());
        if (data != null && data.timeoutTask != null) {
            data.timeoutTask.cancel();
        }
    }

    public void enableSelectionMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.SELECTION, "interaction.select-block.mode-enabled", null);
    }

    public void enableReplaceBlockMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.REPLACE_BLOCK, "interaction.replace-block.mode-enabled", null);
    }

    public void enableRenameMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.RENAME, "interaction.rename.mode-enabled", null);
    }

    public void enableHologramTextMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_TEXT, "interaction.hologram.change-text.mode-enabled", null);
    }

    public void enableHologramOffsetXMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_X, "interaction.hologram.offset-x.mode-enabled", null);
    }

    public void enableHologramOffsetYMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Y, "interaction.hologram.offset-y.mode-enabled", null);
    }

    public void enableHologramOffsetZMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Z, "interaction.hologram.offset-z.mode-enabled", null);
    }

    public void enableKeyMaterialMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_MATERIAL, "key.change-material.mode-enabled", null);
    }

    public void enableKeyNameMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_NAME, "key.change-name.mode-enabled", null);
    }

    public void enableKeyLoreMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_LORE, "key.change-lore.mode-enabled", null);
    }

    public void enableKeyCMDMode(Player player, String cacheName) {
        enableMode(player, cacheName, PlayerMode.KEY_CMD, "key.change-cmd.mode-enabled", null);
    }

    public void enableKeyFlagsMode(Player player, String cacheName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> extra = new HashMap<>();
        extra.put("key-flags", cache != null ? cache.getKeyFlagsString() : "");
        enableMode(player, cacheName, PlayerMode.KEY_FLAGS, "key.change-flags.mode-enabled", extra);
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
        plugin.log("Для игрока &#ffff00" + player.getName() + " &fактивирован режим настройки &#ffff00" + mode.name() + " &fдля тайника &#ffff00" + cacheName);

        String currentMenu = menuManager != null ? menuManager.getCurrentMenu(player) : null;
        if (currentMenu != null) {
            lastMenuFile.put(player.getUniqueId(), currentMenu);
        }
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
        plugin.log("Игрок &#ffff00" + player.getName() + " &fвышел из режима ввода параметров тайника или истекло время ожидания...");
        reopenLastMenu(player, data.cacheName);
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ModeData data = activeModes.get(player.getUniqueId());
        if (data == null) return;

        event.setCancelled(true);
        String message = event.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    cancelSelectionMode(player);
                }
            }.runTask(plugin);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
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
        }.runTask(plugin);
    }

    private void handleSelectionModeChat(Player player, String cacheName, String message) {
        String[] parts = message.split(" ");
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (parts.length != 5) {
            activeModes.remove(player.getUniqueId());
            configManager.executeActions(player, "cache.invalid-coordinates-format", ph);
            plugin.error("Игрок &#FB8808" + player.getName() + " &fввел некорректный формат координат тайника: &#FB8808" + message + "&f...");
            reopenLastMenu(player, cacheName);
            return;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(parts[0]);
            y = Double.parseDouble(parts[1]);
            z = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            activeModes.remove(player.getUniqueId());
            configManager.executeActions(player, "cache.invalid-coordinates", ph);
            plugin.error("Игрок &#FB8808" + player.getName() + " &fуказал нечисловые значения координат тайника...");
            reopenLastMenu(player, cacheName);
            return;
        }

        String worldName = parts[3];
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            activeModes.remove(player.getUniqueId());
            ph.put("world", worldName);
            configManager.executeActions(player, "cache.invalid-world", ph);
            plugin.error("Указанный мир &#FB8808" + worldName + " &fне найден в системе сервера...");
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
            activeModes.remove(player.getUniqueId());
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.invalid", ph);
            plugin.error("Указанный ID материала &#FB8808" + blockId + " &fне является полноценным блоком...");
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
            activeModes.remove(player.getUniqueId());
            ph.put("name-cache", existingCache.getDisplayName());
            configManager.executeActions(player, "cache.already-exists", ph);
            plugin.error("Не удалось привязать локацию, там уже расположен тайник &#FB8808" + existingCache.name + "&f...");
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());
        cache.setLocation(location);
        cache.setBlockType(blockType);
        plugin.log("Игрок &#ffff00" + player.getName() + " &fуспешно настроил физические координаты тайника &#ffff00" + cacheName + " &fчерез текстовый чат");

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

        Material newBlockType;
        try {
            newBlockType = Material.valueOf(blockId.toUpperCase());
            if (!newBlockType.isBlock()) {
                throw new IllegalArgumentException("Not a block");
            }
        } catch (IllegalArgumentException e) {
            activeModes.remove(player.getUniqueId());
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.invalid", ph);
            plugin.error("Введен некорректный ID материала &#FB8808" + blockId + " &fдля плановой замены блока...");
            reopenLastMenu(player, cacheName);
            return;
        }

        if (cache.getBlockType() == newBlockType) {
            ph.put("block-id", blockId);
            configManager.executeActions(player, "interaction.replace-block.same", ph);
            activeModes.remove(player.getUniqueId());
            plugin.error("Новый тип блока тайника полностью совпадает со старым: &#FB8808" + blockId + "&f...");
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());
        cache.setBlockType(newBlockType);
        plugin.log("Физический материал блока тайника &#ffff00" + cacheName + " &fуспешно изменен на &#ffff00" + newBlockType.name());
        ph.put("block-id", newBlockType.name().toLowerCase());
        configManager.executeActions(player, "interaction.replace-block.completed", ph);
        reopenLastMenu(player, cacheName);
    }

    private void handleRenameModeChat(Player player, String cacheName, String newDisplayName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);

        if (cache == null) {
            configManager.executeActions(player, "cache.not-found", ph);
            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
            return;
        }

        activeModes.remove(player.getUniqueId());
        configManager.setCacheDisplayName(cacheName, newDisplayName);
        cache.load();

        plugin.log("Отображаемое имя тайника &#ffff00" + cacheName + " &fизменено на &#ffff00" + newDisplayName);

        ph.put("display-name", newDisplayName);
        configManager.executeActions(player, "interaction.rename.completed", ph);
        reopenLastMenu(player, cacheName);
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
        plugin.log("Текст описания голограммы для тайника &#ffff00" + cacheName + " &fуспешно изменен игроком &#ffff00" + player.getName());

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
            plugin.log("Смещение голограммы тайника &#ffff00" + cacheName + " &fпо оси &#ffff00" + axis + " &fустановлено на &#ffff00" + newOffset);
        } catch (NumberFormatException e) {
            activeModes.remove(player.getUniqueId());
            configManager.executeActions(player, "interaction.hologram.offset-invalid-number");
            plugin.error("Введено некорректное дробное число для смещения по оси &#FB8808" + axis + "&f...");
        }
        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyMaterialChat(Player player, String cacheName, String materialStr) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        Material mat = Material.matchMaterial(materialStr.toUpperCase());
        if (mat == null) {
            activeModes.remove(player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("material", materialStr);
            configManager.executeActions(player, "key.change-material.invalid", ph);
            plugin.error("Указанный тип материала ключа не обнаружен в Bukkit API: &#FB8808" + materialStr + "&f...");
            return;
        }

        cache.setKeyMaterial(mat.name());
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("key-material", mat.name());
        configManager.executeActions(player, "key.material-changed", ph);
        plugin.log("Материал идентификатора ключа для тайника &#ffff00" + cacheName + " &fизменен на &#ffff00" + mat.name());

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyNameChat(Player player, String cacheName, String newKeyName) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        cache.setKeyName(newKeyName);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("key-name", newKeyName);
        configManager.executeActions(player, "key.name-changed", ph);
        plugin.log("Кастомное имя ключа для тайника &#ffff00" + cacheName + " &fизменено на &#ffff00" + newKeyName);

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyLoreChat(Player player, String cacheName, String newKeyLore) {
        CacheManager.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        List<String> lines = Arrays.asList(newKeyLore.split("\\\\n"));
        cache.setKeyLore(lines);
        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        configManager.executeActions(player, "key.lore-changed", ph);
        plugin.log("Кастомное описание (Lore) ключа для тайника &#ffff00" + cacheName + " &fуспешно обновлено");

        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void handleKeyCMDChat(Player player, String cacheName, String cmdStr) {
        CacheManager.Cache actualCache = cacheManager.getCache(cacheName);
        if (actualCache == null) return;

        try {
            int cmd = Integer.parseInt(cmdStr);
            actualCache.setKeyCustomModelData(cmd);
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", actualCache.getDisplayName());
            ph.put("key-cmd", String.valueOf(cmd));
            configManager.executeActions(player, "key.cmd-changed", ph);
            plugin.log("Параметр CustomModelData ключа тайника &#ffff00" + cacheName + " &fизменен на &#ffff00" + cmd);

            activeModes.remove(player.getUniqueId());
            reopenLastMenu(player, cacheName);
        } catch (NumberFormatException e) {
            activeModes.remove(player.getUniqueId());
            configManager.executeActions(player, "key.change-cmd.invalid");
            plugin.error("Введено невалидное целочисленное значение CustomModelData ключа: &#FB8808" + cmdStr + "&f...");
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
            activeModes.remove(player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("flag", flagName);
            configManager.executeActions(player, "key.change-flags.invalid", ph);
            plugin.error("Указанное наименование мета-флага предмета не существует: &#FB8808" + flagName + "&f...");
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());
        ph.put("flag", flagName);

        if (isRemove) {
            if (cache.removeKeyFlag(flagName)) {
                configManager.executeActions(player, "key.change-flags.removed", ph);
                plugin.log("С предмета ключа тайника &#ffff00" + cacheName + " &fуспешно удален флаг &#ffff00" + flagName);
            } else {
                activeModes.remove(player.getUniqueId());
                configManager.executeActions(player, "key.change-flags.not-found", ph);
                return;
            }
        } else {
            if (cache.addKeyFlag(flagName)) {
                configManager.executeActions(player, "key.change-flags.added", ph);
                plugin.log("На предмет ключа тайника &#ffff00" + cacheName + " &fуспешно добавлен флаг &#ffff00" + flagName);
            } else {
                activeModes.remove(player.getUniqueId());
                configManager.executeActions(player, "key.change-flags.already-exists", ph);
                return;
            }
        }

        configManager.saveCacheConfig(cacheName);
        activeModes.remove(player.getUniqueId());
        reopenLastMenu(player, cacheName);
    }

    private void reopenLastMenu(Player player, String cacheName) {
        String menuFile = lastMenuFile.remove(player.getUniqueId());
        if (menuFile != null && menuManager != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    menuManager.openMenu(player, cacheName, menuFile, 1);
                }
            }.runTask(plugin);
        }
    }
}