package org.gw.cachesmanager.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.listeners.modes.ChatEditSession;
import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSessionManager implements Listener {

    private final CachesManager plugin;
    private final Map<UUID, ChatEditSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMenuFile = new ConcurrentHashMap<>();

    public ChatSessionManager(CachesManager plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void startSession(Player player, String cacheName, PlayerMode mode, Map<String, String> extraData, Runnable onTimeout) {
        cancelSession(player);

        ChatEditSession session = new ChatEditSession(cacheName, mode, () -> cancelSession(player));
        session.scheduleTimeout(plugin.getConfigManager().getModeTimeoutSeconds(), plugin);

        if (extraData != null) {
            extraData.forEach(session::putExtra);
        }

        activeSessions.put(player.getUniqueId(), session);
    }

    public boolean cancelSession(Player player) {
        ChatEditSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return false;

        session.cancelTimeout();
        return true;
    }

    public ChatEditSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public void completeSession(Player player) {
        ChatEditSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cancelTimeout();
        }
    }

    public void rememberLastMenu(Player player, String menuFile) {
        if (menuFile != null) {
            lastMenuFile.put(player.getUniqueId(), menuFile);
        }
    }

    public String getAndClearLastMenu(Player player) {
        return lastMenuFile.remove(player.getUniqueId());
    }

    public void discardLastMenu(Player player) {
        lastMenuFile.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        activeSessions.remove(player.getUniqueId());
        lastMenuFile.remove(player.getUniqueId());
    }
}