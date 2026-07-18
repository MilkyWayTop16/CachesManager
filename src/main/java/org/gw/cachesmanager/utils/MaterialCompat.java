package org.gw.cachesmanager.utils;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MaterialCompat {

    private static final Map<String, String> RENAMES;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("CHAIN", "IRON_CHAIN");
        map.put("IRON_CHAIN", "CHAIN");
        map.put("MINECRAFT:CHAIN", "IRON_CHAIN");
        map.put("MINECRAFT:IRON_CHAIN", "CHAIN");
        RENAMES = Collections.unmodifiableMap(map);
    }

    private MaterialCompat() {
    }

    public static Material match(String raw) {
        return match(raw, Material.STONE);
    }

    public static Material match(String raw, Material fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }

        String name = normalize(raw);
        Material material = tryMatch(name);
        if (material != null) {
            return material;
        }

        String alias = RENAMES.get(name);
        if (alias != null) {
            material = tryMatch(alias);
            if (material != null) {
                return material;
            }
        }

        return fallback;
    }

    private static String normalize(String raw) {
        String name = raw.trim();
        if (name.regionMatches(true, 0, "minecraft:", 0, 10)) {
            name = name.substring(10);
        }
        return name.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static Material tryMatch(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        if (material != null) {
            return material;
        }
        return Material.matchMaterial(name, false);
    }
}
