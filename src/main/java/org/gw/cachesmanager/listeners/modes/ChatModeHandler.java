package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;

import java.util.Map;

public interface ChatModeHandler {

    PlayerMode getMode();

    default void onSessionStart(Player player, Cache cache, ChatEditSession session) {
    }

    boolean handleChat(Player player, Cache cache, ChatEditSession session, String message);

    String getCancelMessagePath();

    default void onSessionEnd(Player player, Cache cache, ChatEditSession session, boolean success) {
    }
}
