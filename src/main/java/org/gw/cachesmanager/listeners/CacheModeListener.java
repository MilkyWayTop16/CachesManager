package org.gw.cachesmanager.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.listeners.modes.*;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.managers.MenuManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CacheModeListener implements Listener {
    private final CachesManager plugin;
    private final CacheManager cacheManager;
    private final ConfigManager configManager;
    private MenuManager menuManager;

    private final ChatModeRegistry modeRegistry = new ChatModeRegistry();
    private final ChatSessionManager sessionManager;
    private final Set<UUID> chatInputClaimed = ConcurrentHashMap.newKeySet();

    public CacheModeListener(CachesManager plugin, CacheManager cacheManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.configManager = configManager;
        this.sessionManager = new ChatSessionManager(plugin);

        modeRegistry.register(new RenameModeHandler(cacheManager, configManager));
        modeRegistry.register(new KeyMaterialModeHandler(cacheManager, configManager));
        modeRegistry.register(new KeyNameModeHandler(cacheManager, configManager));
        modeRegistry.register(new KeyLoreModeHandler(cacheManager, configManager));
        modeRegistry.register(new KeyCommandModeHandler(cacheManager, configManager));
        modeRegistry.register(new KeyFlagsModeHandler(cacheManager, configManager));
        modeRegistry.register(new HologramTextModeHandler(cacheManager, configManager));
        modeRegistry.register(new HologramOffsetModeHandler(cacheManager, configManager, "X"));
        modeRegistry.register(new HologramOffsetModeHandler(cacheManager, configManager, "Y"));
        modeRegistry.register(new HologramOffsetModeHandler(cacheManager, configManager, "Z"));
        modeRegistry.register(new SelectionModeHandler(cacheManager, configManager));
        modeRegistry.register(new ReplaceBlockModeHandler(cacheManager, configManager));

        if (isModernChatEventAvailable()) {
            plugin.getServer().getPluginManager().registerEvents(new ModernChatListener(), plugin);
        }
    }

    private static boolean isModernChatEventAvailable() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isInEditSession(Player player) {
        return sessionManager.getSession(player) != null;
    }

    private boolean claimChatInput(Player player) {
        UUID uuid = player.getUniqueId();
        if (!chatInputClaimed.add(uuid)) {
            return false;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> chatInputClaimed.remove(uuid));
        return true;
    }

    public void setMenuManager(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public String getSelectionMode(Player player) {
        ChatEditSession session = sessionManager.getSession(player);
        if (session != null && session.getMode() == PlayerMode.SELECTION) {
            return session.getCacheName();
        }
        return null;
    }

    public void removeSelectionMode(Player player) {
        sessionManager.cancelSession(player);
        sessionManager.discardLastMenu(player);
    }

    public void discardLastMenu(Player player) {
        sessionManager.discardLastMenu(player);
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
        Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> extra = new HashMap<>();
        if (cache != null) {
            extra.put("current-x", String.valueOf(cache.getHologramOffsetX()));
            extra.put("current-y", String.valueOf(cache.getHologramOffsetY()));
            extra.put("current-z", String.valueOf(cache.getHologramOffsetZ()));
        }
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_X, "interaction.hologram.offset-x.mode-enabled", extra);
    }

    public void enableHologramOffsetYMode(Player player, String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> extra = new HashMap<>();
        if (cache != null) {
            extra.put("current-x", String.valueOf(cache.getHologramOffsetX()));
            extra.put("current-y", String.valueOf(cache.getHologramOffsetY()));
            extra.put("current-z", String.valueOf(cache.getHologramOffsetZ()));
        }
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Y, "interaction.hologram.offset-y.mode-enabled", extra);
    }

    public void enableHologramOffsetZMode(Player player, String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> extra = new HashMap<>();
        if (cache != null) {
            extra.put("current-x", String.valueOf(cache.getHologramOffsetX()));
            extra.put("current-y", String.valueOf(cache.getHologramOffsetY()));
            extra.put("current-z", String.valueOf(cache.getHologramOffsetZ()));
        }
        enableMode(player, cacheName, PlayerMode.HOLOGRAM_OFFSET_Z, "interaction.hologram.offset-z.mode-enabled", extra);
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
        Cache cache = cacheManager.getCache(cacheName);
        Map<String, String> extra = new HashMap<>();
        extra.put("key-flags", cache != null ? cache.getKeyFlagsString() : "");
        enableMode(player, cacheName, PlayerMode.KEY_FLAGS, "key.change-flags.mode-enabled", extra);
    }

    private void enableMode(Player player, String cacheName, PlayerMode mode, String messagePath, Map<String, String> extraPh) {
        sessionManager.cancelSession(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName);
        if (extraPh != null) ph.putAll(extraPh);

        configManager.executeActions(player, messagePath, ph);

        sessionManager.startSession(player, cacheName, mode, extraPh, () -> sessionManager.cancelSession(player));

        ChatEditSession session = sessionManager.getSession(player);
        if (session != null && extraPh != null) {
            extraPh.forEach(session::putExtra);
        }

        ChatModeHandler handler = modeRegistry.getHandler(mode);
        if (handler != null) {
            Cache cache = cacheManager.getCache(cacheName);
            handler.onSessionStart(player, cache, session);
        }

        plugin.log("Для игрока &#ffff00" + player.getName() + " &fактивирован режим настройки &#ffff00" + mode.name() + " &fдля тайника &#ffff00" + cacheName);

        String currentMenu = menuManager != null ? menuManager.getCurrentMenu(player) : null;
        if (currentMenu != null) {
            sessionManager.rememberLastMenu(player, currentMenu);
        }
    }

    public boolean cancelSession(Player player) {
        ChatEditSession session = sessionManager.getSession(player);
        if (session == null) return false;

        String cacheName = session.getCacheName();
        PlayerMode mode = session.getMode();

        sessionManager.cancelSession(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cacheName != null ? cacheName : "");

        ChatModeHandler handler = modeRegistry.getHandler(mode);
        String path = (handler != null) ? handler.getCancelMessagePath() : "interaction.select-block.cancelled";

        Cache cache = cacheManager.getCache(cacheName);
        if (handler != null) {
            handler.onSessionEnd(player, cache, session, false);
        }

        configManager.executeActions(player, path, ph);
        plugin.log("Игрок &#ffff00" + player.getName() + " &fвышел из режима ввода параметров тайника или истекло время ожидания...");
        reopenLastMenu(player, cacheName);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChatLowest(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatEditSession session = sessionManager.getSession(player);
        if (session == null) return;

        String message = event.getMessage();
        suppressLegacyChat(event);
        if (claimChatInput(player)) {
            handleChatInput(player, session, message);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerChatHighest(AsyncPlayerChatEvent event) {
        if (!isInEditSession(event.getPlayer())) return;
        suppressLegacyChat(event);
    }

    private void suppressLegacyChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        try {
            event.getRecipients().clear();
        } catch (Throwable ignored) {
        }
    }

    private final class ModernChatListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
        public void onPlayerChatLowest(io.papermc.paper.event.player.AsyncChatEvent event) {
            Player player = event.getPlayer();
            ChatEditSession session = sessionManager.getSession(player);
            if (session == null) return;

            String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message());
            suppressModernChat(event);
            if (claimChatInput(player)) {
                handleChatInput(player, session, message);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onPlayerChatHighest(io.papermc.paper.event.player.AsyncChatEvent event) {
            if (!isInEditSession(event.getPlayer())) return;
            suppressModernChat(event);
        }

        private void suppressModernChat(io.papermc.paper.event.player.AsyncChatEvent event) {
            event.setCancelled(true);
            try {
                event.viewers().clear();
            } catch (Throwable ignored) {
            }
            try {
                event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty());
            } catch (Throwable ignored) {
            }
        }
    }

    private void handleChatInput(Player player, ChatEditSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    cancelSession(player);
                }
            }.runTask(plugin);
            return;
        }

        ChatModeHandler handler = modeRegistry.getHandler(session.getMode());
        if (handler != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Cache cache = cacheManager.getCache(session.getCacheName());
                    boolean handled = handler.handleChat(player, cache, session, message);
                    if (handled) {
                        Cache currentCache = cacheManager.getCache(session.getCacheName());
                        if (handler != null) {
                            handler.onSessionEnd(player, currentCache, session, true);
                        }
                        sessionManager.completeSession(player);
                        reopenLastMenu(player, session.getCacheName());
                    }
                }
            }.runTask(plugin);
        } else {
            cancelSession(player);
        }
    }

    private void reopenLastMenu(Player player, String cacheName) {
        String menuFile = sessionManager.getAndClearLastMenu(player);
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