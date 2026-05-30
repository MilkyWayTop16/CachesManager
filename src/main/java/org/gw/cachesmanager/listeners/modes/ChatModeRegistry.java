package org.gw.cachesmanager.listeners.modes;

import org.gw.cachesmanager.listeners.modes.PlayerMode;

import java.util.EnumMap;
import java.util.Map;

public class ChatModeRegistry {

    private final Map<PlayerMode, ChatModeHandler> handlers = new EnumMap<>(PlayerMode.class);

    public void register(ChatModeHandler handler) {
        handlers.put(handler.getMode(), handler);
    }

    public ChatModeHandler getHandler(PlayerMode mode) {
        return handlers.get(mode);
    }
}
