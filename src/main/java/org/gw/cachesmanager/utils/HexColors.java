package org.gw.cachesmanager.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HexColors {

    private static final Pattern COLOR_PATTERN = Pattern.compile("&(?:#([A-Fa-f0-9]{6})|([0-9a-fk-orA-FK-OR]))");
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

        Matcher matcher = COLOR_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder(message.length() + 16);

        while (matcher.find()) {
            String hex = matcher.group(1);
            if (hex != null) {
                matcher.appendReplacement(sb, "<#" + hex + ">");
            } else {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                String tag = switch (code) {
                    case '0' -> "<black>"; case '1' -> "<dark_blue>"; case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>"; case '4' -> "<dark_red>"; case '5' -> "<dark_purple>";
                    case '6' -> "<gold>"; case '7' -> "<gray>"; case '8' -> "<dark_gray>";
                    case '9' -> "<blue>"; case 'a' -> "<green>"; case 'b' -> "<aqua>";
                    case 'c' -> "<red>"; case 'd' -> "<light_purple>"; case 'e' -> "<yellow>";
                    case 'f' -> "<white>"; case 'k' -> "<obfuscated>"; case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>"; case 'n' -> "<underlined>"; case 'o' -> "<italic>";
                    case 'r' -> "<reset>"; default -> matcher.group();
                };
                matcher.appendReplacement(sb, tag);
            }
        }
        matcher.appendTail(sb);

        try {
            return MINI_MESSAGE.deserialize(sb.toString());
        } catch (Exception e) {
            return Component.text(message);
        }
    }
}