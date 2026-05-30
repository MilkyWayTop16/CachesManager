package org.gw.cachesmanager.listeners.modes;

import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.gw.cachesmanager.CachesManager;

import java.util.HashMap;
import java.util.Map;

public class ChatEditSession {

    @Getter
    private final String cacheName;
    @Getter
    private final PlayerMode mode;
    private BukkitTask timeoutTask;
    private final Map<String, String> extraData = new HashMap<>();

    private final Runnable onTimeout;

    public ChatEditSession(String cacheName, PlayerMode mode, Runnable onTimeout) {
        this.cacheName = cacheName;
        this.mode = mode;
        this.onTimeout = onTimeout;
    }

    public void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public void scheduleTimeout(int seconds, CachesManager plugin) {
        cancelTimeout();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (onTimeout != null) {
                    onTimeout.run();
                }
            }
        }.runTaskLater(plugin, seconds * 20L);

        this.timeoutTask = task;
    }

    public void putExtra(String key, String value) {
        if (key != null && value != null) {
            extraData.put(key, value);
        }
    }
}
