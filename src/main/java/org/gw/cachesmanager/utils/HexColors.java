package org.gw.cachesmanager.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class HexColors {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private HexColors() {}

    public static String translate(String text) {
        if (text == null || text.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection().serialize(translateToComponent(text));
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

        String processed = convertLegacyToMiniMessage(message);

        try {
            return MINI_MESSAGE.deserialize(processed);
        } catch (Exception e) {
            return Component.text(message);
        }
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
        try {
            String translationKey = item.getType().translationKey();
            Component translatable = Component.translatable(translationKey);
            return LegacyComponentSerializer.legacySection().serialize(translatable);
        } catch (Throwable ignored) {
            String key = item.getType().getKey().getKey();
            String namespace = item.getType().getKey().getNamespace();
            String translationKey = item.getType().isBlock() ? "block." + namespace + "." + key : "item." + namespace + "." + key;
            return translationKey;
        }
    }

    public static Component getItemNameComponent(ItemStack item) {
        if (item == null) return Component.empty();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        try {
            String translationKey = item.getType().translationKey();
            return Component.translatable(translationKey);
        } catch (Throwable ignored) {
            return Component.text(item.getType().name());
        }
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