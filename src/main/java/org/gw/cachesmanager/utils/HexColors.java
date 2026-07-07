package org.gw.cachesmanager.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class HexColors {

    private static final MiniMessage MINI_MESSAGE = createMiniMessage();

    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final com.google.common.cache.Cache<String, String> TRANSLATE_CACHE =
            com.google.common.cache.CacheBuilder.newBuilder().maximumSize(2000).build();

    private HexColors() {}

    public static String translateForConsole(String text) {
        if (text == null || text.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection().serialize(translateToComponent(text));
    }

    private static MiniMessage createMiniMessage() {
        try {
            return MiniMessage.miniMessage();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isMiniMessageAvailable() {
        return MINI_MESSAGE != null;
    }

    public static String translate(String text) {
        if (text == null || text.isEmpty()) return "";
        String cached = TRANSLATE_CACHE.getIfPresent(text);
        if (cached != null) return cached;
        String result = LEGACY_HEX.serialize(translateToComponent(text));
        TRANSLATE_CACHE.put(text, result);
        return result;
    }

    public static List<String> translate(List<String> text) {
        if (text == null) return new ArrayList<>();
        List<String> translated = new ArrayList<>(text.size());
        for (String line : text) {
            translated.add(translate(line));
        }
        return translated;
    }

    public static Component translateToComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();

        if (MINI_MESSAGE != null) {
            try {
                return MINI_MESSAGE.deserialize(convertLegacyToMiniMessage(message));
            } catch (Throwable ignored) {
            }
        }

        return LEGACY_HEX.deserialize(convertLegacyToSection(message));
    }

    private static String convertLegacyToMiniMessage(String input) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#') {
                    if (i + 7 < input.length()) {
                        String hex = input.substring(i + 2, i + 8);
                        if (hex.matches("[0-9a-fA-F]{6}")) {
                            result.append("<#").append(hex).append(">");
                            i += 8;
                            continue;
                        }
                    }
                } else if (next == 'x' && i + 13 < input.length()) {
                    StringBuilder hex = new StringBuilder();
                    boolean valid = true;
                    for (int j = 0; j < 6; j++) {
                        int pos = i + 2 + j * 2;
                        if (input.charAt(pos) != '&' || !isHexChar(input.charAt(pos + 1))) {
                            valid = false;
                            break;
                        }
                        hex.append(input.charAt(pos + 1));
                    }
                    if (valid) {
                        result.append("<#").append(hex).append(">");
                        i += 14;
                        continue;
                    }
                } else if (isLegacyColorChar(next)) {
                    result.append(getMiniMessageTag(next));
                    i += 2;
                    continue;
                }
            } else if (c == '#' && i + 6 < input.length()) {
                String hex = input.substring(i + 1, i + 7);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    result.append("<#").append(hex).append(">");
                    i += 7;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static String convertLegacyToSection(String input) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        result.append(sectionHex(hex));
                        i += 8;
                        continue;
                    }
                } else if (next == 'x' && i + 13 < input.length()) {
                    StringBuilder hex = new StringBuilder();
                    boolean valid = true;
                    for (int j = 0; j < 6; j++) {
                        int pos = i + 2 + j * 2;
                        if (input.charAt(pos) != '&' || !isHexChar(input.charAt(pos + 1))) {
                            valid = false;
                            break;
                        }
                        hex.append(input.charAt(pos + 1));
                    }
                    if (valid) {
                        result.append(sectionHex(hex.toString()));
                        i += 14;
                        continue;
                    }
                } else if (isLegacyColorChar(next)) {
                    result.append('§').append(Character.toLowerCase(next));
                    i += 2;
                    continue;
                }
            } else if (c == '#' && i + 6 < input.length()) {
                String hex = input.substring(i + 1, i + 7);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    result.append(sectionHex(hex));
                    i += 7;
                    continue;
                }
            } else if (c == '<') {
                int close = input.indexOf('>', i);
                if (close != -1 && close - i <= 40) {
                    i = close + 1;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static String sectionHex(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char h : hex.toCharArray()) {
            sb.append('§').append(Character.toLowerCase(h));
        }
        return sb.toString();
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isLegacyColorChar(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) != -1;
    }

    private static String getMiniMessageTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "&" + code;
        };
    }

    public static String toGsonJsonFromComponent(Component component) {
        if (component == null) {
            return "{\"text\":\"\"}";
        }
        return GsonComponentSerializer.gson().serialize(component);
    }

    public static String getItemNameLegacy(ItemStack item) {
        if (item == null) return "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return prettifyMaterial(item);
    }

    public static Component getItemNameComponent(ItemStack item) {
        if (item == null) return Component.empty();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        return Component.translatable(getItemTranslationKey(item));
    }

    public static String prettifyMaterial(ItemStack item) {
        if (item == null || item.getType() == null) return "";
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean capitalize = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                capitalize = true;
                sb.append(c);
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String getItemTranslationKey(ItemStack item) {
        if (item == null) return "";
        try {
            return item.getType().translationKey();
        } catch (Throwable t) {
            String key = item.getType().getKey().getKey();
            String namespace = item.getType().getKey().getNamespace();
            return item.getType().isBlock() ? "block." + namespace + "." + key : "item." + namespace + "." + key;
        }
    }
}