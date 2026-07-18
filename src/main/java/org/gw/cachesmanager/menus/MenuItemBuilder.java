package org.gw.cachesmanager.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.MaterialCompat;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class MenuItemBuilder {
    private final CachesManager plugin;

    public MenuItemBuilder(CachesManager plugin) {
        this.plugin = plugin;
    }

    public ItemStack createCustomHead(String base64Value) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null || base64Value == null || base64Value.isEmpty()) return head;
        try {
            Class<?> playerProfileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> playerTexturesClass = Class.forName("org.bukkit.profile.PlayerTextures");
            Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class).invoke(null, UUID.randomUUID());
            Object textures = playerProfileClass.getMethod("getTextures").invoke(profile);
            java.net.URL url = new java.net.URL("https://textures.minecraft.net/texture/" + base64Value);
            if (base64Value.startsWith("http")) {
                url = new java.net.URL(base64Value);
            }
            playerTexturesClass.getMethod("setSkin", java.net.URL.class).invoke(textures, url);
            playerProfileClass.getMethod("setTextures", playerTexturesClass).invoke(profile, textures);
            SkullMeta.class.getMethod("setOwnerProfile", playerProfileClass).invoke(meta, profile);
        } catch (Exception e) {
            try {
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                Object profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), null);
                Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64Value);
                Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
                properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
            } catch (Exception ex) {
                try {
                    Field profileField = meta.getClass().getDeclaredField("resolvableProfile");
                    profileField.setAccessible(true);
                    Class<?> resolvableProfileClass = Class.forName("net.minecraft.world.item.component.ResolvableProfile");
                    Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    Object profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), null);
                    Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64Value);
                    Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
                    properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);
                    Object resolvableProfile = resolvableProfileClass.getConstructor(gameProfileClass).newInstance(profile);
                    profileField.set(meta, resolvableProfile);
                } catch (Exception ex2) {
                    plugin.error("Ошибка создания кастомной головы...");
                }
            }
        }
        head.setItemMeta(meta);
        return head;
    }

    public ItemStack createMenuItem(Player player, ConfigurationSection section, String cacheName,
                                    Map<String, ItemStack> staticItemCache,
                                    Map<String, String> translatedNameCache,
                                    Map<String, List<String>> translatedLoreCache,
                                    Map<String, String> finalColoredNameCache,
                                    Map<String, List<String>> finalColoredLoreCache) {
        String cacheKey = section.getName() + "|" + cacheName;
        if (staticItemCache.containsKey(cacheKey)) {
            return staticItemCache.get(cacheKey).clone();
        }
        String matStr = section.getString("material", "STONE");
        Material mat = MaterialCompat.match(matStr, Material.STONE);
        ItemStack item = new ItemStack(mat, section.getInt("amount", 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            staticItemCache.put(cacheKey, item);
            return item;
        }
        if (mat == Material.PLAYER_HEAD && section.contains("value")) {
            String value = section.getString("value");
            if (value != null && !value.isEmpty()) {
                item = createCustomHead(value);
                meta = item.getItemMeta();
            }
        }
        Cache cache = plugin.getCacheManager().getCache(cacheName);

        boolean nameHasPapi = false;
        boolean loreHasPapi = false;
        if (section.contains("display-name")) {
            String nameKey = cacheKey + "|name";
            String cacheApplied = translatedNameCache.computeIfAbsent(nameKey, k -> {
                String raw = section.getString("display-name");
                return cache != null ? applyCachePlaceholders(raw, cache) : raw;
            });
            nameHasPapi = cacheApplied.contains("%");
            String resolved;
            if (nameHasPapi) {
                resolved = PlaceholderAPIHook.parse(player, cacheApplied);
            } else {
                String finalKey = nameKey + "|final";
                resolved = finalColoredNameCache.computeIfAbsent(finalKey, k -> PlaceholderAPIHook.parse(player, cacheApplied));
            }
            meta.setDisplayName(HexColors.translate(resolved));
        }

        if (section.contains("lore")) {
            String loreKey = cacheKey + "|lore";
            List<String> cacheAppliedLore = translatedLoreCache.computeIfAbsent(loreKey, k -> {
                List<String> raw = new ArrayList<>(section.getStringList("lore"));
                return cache != null ? applyCachePlaceholdersToLore(raw, cache) : raw;
            });
            loreHasPapi = cacheAppliedLore.stream().anyMatch(l -> l.contains("%"));
            List<String> resolvedLore;
            if (loreHasPapi) {
                resolvedLore = cacheAppliedLore.stream()
                        .map(line -> PlaceholderAPIHook.parse(player, line))
                        .collect(Collectors.toList());
            } else {
                String finalLoreKey = loreKey + "|final";
                resolvedLore = finalColoredLoreCache.computeIfAbsent(finalLoreKey, k ->
                        cacheAppliedLore.stream()
                                .map(line -> PlaceholderAPIHook.parse(player, line))
                                .collect(Collectors.toList())
                );
            }
            meta.setLore(HexColors.translate(resolvedLore));
        }
        if (section.contains("enchantments")) {
            for (String ench : section.getStringList("enchantments")) {
                String[] parts = ench.split(";");
                if (parts.length == 2) {
                    Enchantment e = Enchantment.getByName(parts[0].trim().toUpperCase());
                    if (e != null) meta.addEnchant(e, Integer.parseInt(parts[1].trim()), true);
                }
            }
        }
        if (section.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        if (section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (section.contains("custom-model-data")) meta.setCustomModelData(section.getInt("custom-model-data"));
        if (meta.hasAttributeModifiers()) meta.getAttributeModifiers().keySet().forEach(meta::removeAttributeModifier);
        if (section.contains("flags")) {
            for (String f : section.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(f.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        item.setItemMeta(meta);

        if (!nameHasPapi && !loreHasPapi) {
            staticItemCache.put(cacheKey, item.clone());
        }
        return item;
    }

    private String applyCachePlaceholders(String text, Cache cache) {
        String anim = plugin.getAnimationsManager().getAnimations().containsKey(cache.getAnimation())
                ? plugin.getAnimationsManager().getAnimations().get(cache.getAnimation()).getName() : "Неизвестная анимация";
        return text
                .replace("{name-cache}", cache.getName())
                .replace("{unbreakable-status}", cache.isUnbreakable() ? "Включена" : "Выключена")
                .replace("{animation-name}", anim)
                .replace("{hologram-status}", cache.isHologramEnabled() ? "Включена" : "Выключена")
                .replace("{hologram-offset-x}", String.valueOf(cache.getHologramOffsetX()))
                .replace("{hologram-offset-y}", String.valueOf(cache.getHologramOffsetY()))
                .replace("{hologram-offset-z}", String.valueOf(cache.getHologramOffsetZ()))
                .replace("{key-name}", cache.getKeyName())
                .replace("{key-material}", cache.getKeyMaterial())
                .replace("{key-glow-status}", cache.isKeyGlowEnabled() ? "Включено" : "Выключено")
                .replace("{key-cmd}", String.valueOf(cache.getKeyCustomModelData()))
                .replace("{key-flags}", cache.getKeyFlagsString());
    }

    private List<String> applyCachePlaceholdersToLore(List<String> lore, Cache cache) {
        List<String> result = new ArrayList<>();
        String anim = plugin.getAnimationsManager().getAnimations().containsKey(cache.getAnimation())
                ? plugin.getAnimationsManager().getAnimations().get(cache.getAnimation()).getName() : "Неизвестная анимация";
        for (String line : lore) {
            String processed = line
                    .replace("{name-cache}", cache.getName())
                    .replace("{unbreakable-status}", cache.isUnbreakable() ? "Включена" : "Выключена")
                    .replace("{animation-name}", anim)
                    .replace("{hologram-status}", cache.isHologramEnabled() ? "Включена" : "Выключена")
                    .replace("{hologram-offset-x}", String.valueOf(cache.getHologramOffsetX()))
                    .replace("{hologram-offset-y}", String.valueOf(cache.getHologramOffsetY()))
                    .replace("{hologram-offset-z}", String.valueOf(cache.getHologramOffsetZ()))
                    .replace("{key-name}", cache.getKeyName())
                    .replace("{key-material}", cache.getKeyMaterial())
                    .replace("{key-glow-status}", cache.isKeyGlowEnabled() ? "Включено" : "Выключено")
                    .replace("{key-cmd}", String.valueOf(cache.getKeyCustomModelData()))
                    .replace("{key-flags}", cache.getKeyFlagsString());

            if (processed.contains("{key-lore}")) {
                String indent = processed.substring(0, processed.indexOf("{key-lore}"));
                List<String> keyLore = cache.getKeyLore();
                if (keyLore.isEmpty()) {
                    result.add(indent + "&#FFFF00Отсутствует");
                } else {
                    for (String loreLine : keyLore) {
                        result.add(indent + loreLine);
                    }
                }
            } else {
                result.add(processed);
            }
        }
        return result;
    }
}