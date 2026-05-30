package org.gw.cachesmanager.listeners.modes;

import org.bukkit.entity.Player;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;

import java.util.*;

public class KeyFlagsModeHandler implements ChatModeHandler {

    private static final Set<String> VALID_FLAGS = Set.of(
            "HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_UNBREAKABLE", "HIDE_DESTROYS",
            "HIDE_PLACED_ON", "HIDE_POTION_EFFECTS", "HIDE_DYE", "HIDE_ARMOR_TRIM"
    );

    private final CacheManager cacheManager;
    private final ConfigManager configManager;

    public KeyFlagsModeHandler(CacheManager cacheManager, ConfigManager configManager) {
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    @Override
    public PlayerMode getMode() {
        return PlayerMode.KEY_FLAGS;
    }

    @Override
    public boolean handleChat(Player player, Cache cache, ChatEditSession session, String message) {
        List<String> inputFlags = Arrays.asList(message.split(","));
        inputFlags = inputFlags.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> currentFlags = new ArrayList<>(cache.getKeyFlags());

        boolean anyChange = false;

        for (String raw : inputFlags) {
            boolean isRemove = raw.startsWith("-");
            String flag = isRemove ? raw.substring(1).toUpperCase() : raw.toUpperCase();

            if (!VALID_FLAGS.contains(flag)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("name-cache", cache.getDisplayName());
                ph.put("flag", flag);
                configManager.executeActions(player, "key.change-flags.invalid", ph);
                continue;
            }

            if (isRemove) {
                if (currentFlags.remove(flag)) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("name-cache", cache.getDisplayName());
                    ph.put("flag", flag);
                    configManager.executeActions(player, "key.change-flags.removed", ph);
                    anyChange = true;
                } else {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("name-cache", cache.getDisplayName());
                    ph.put("flag", flag);
                    configManager.executeActions(player, "key.change-flags.not-found", ph);
                }
            } else {
                if (!currentFlags.contains(flag)) {
                    currentFlags.add(flag);
                    Map<String, String> ph = new HashMap<>();
                    ph.put("name-cache", cache.getDisplayName());
                    ph.put("flag", flag);
                    configManager.executeActions(player, "key.change-flags.added", ph);
                    anyChange = true;
                } else {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("name-cache", cache.getDisplayName());
                    ph.put("flag", flag);
                    configManager.executeActions(player, "key.change-flags.already-exists", ph);
                }
            }
        }

        if (anyChange) {
            cacheManager.setCacheKeyFlags(cache, currentFlags);
        }

        return true;
    }

    @Override
    public String getCancelMessagePath() {
        return "key.change-flags.cancelled";
    }
}
