package org.gw.cachesmanager.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexColors {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#[A-Fa-f0-9]{6}");

    public static String translate(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group().substring(1);
            matcher.appendReplacement(buffer, ChatColor.of(hex).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> translate(List<String> text) {
        List<String> translated = new ArrayList<>();
        for (String line : text) {
            translated.add(translate(line));
        }
        return translated;
    }
}