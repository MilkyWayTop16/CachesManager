package org.gw.cachesmanager.menus;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class SpecialActionRegistry {

    private final Map<String, BiConsumer<Player, Integer>> actions = new HashMap<>();

    public void register(String tag, BiConsumer<Player, Integer> action) {
        if (tag == null || action == null) return;
        actions.put(tag.toLowerCase(), action);
    }

    public void clear() {
        actions.clear();
    }
}