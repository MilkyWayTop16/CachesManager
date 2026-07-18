package org.gw.cachesmanager.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class HexColors {

    private static final String[] PALETTE = {
            "000000", "0000aa", "00aa00", "00aaaa",
            "aa0000", "aa00aa", "ffaa00", "aaaaaa",
            "555555", "5555ff", "55ff55", "55ffff",
            "ff5555", "ff55ff", "ffff55", "ffffff"
    };

    private static final MiniMessage MINI_MESSAGE = createMiniMessage();

    private static final LegacyComponentSerializer SECTION_HEX = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final com.google.common.cache.Cache<String, String> TRANSLATE_CACHE =
            com.google.common.cache.CacheBuilder.newBuilder().maximumSize(2000).build();

    private HexColors() {}

    private static MiniMessage createMiniMessage() {
        try {
            String ver = Bukkit.getBukkitVersion();
            if (ver.startsWith("1.16")) {
                return null;
            }
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
        String result = convert(text, false);
        TRANSLATE_CACHE.put(text, result);
        return result;
    }

    public static List<String> translate(List<String> text) {
        if (text == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(text.size());
        for (String line : text) {
            out.add(translate(line));
        }
        return out;
    }

    public static Component translateToComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        if (MINI_MESSAGE != null) {
            try {
                return MINI_MESSAGE.deserialize(convert(message, true));
            } catch (Throwable ignored) {
            }
        }
        return SECTION_HEX.deserialize(convert(message, false));
    }

    public static String translateForConsole(String text) {
        if (text == null || text.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection().serialize(translateToComponent(text));
    }

    public static Component fromSection(String sectionText) {
        if (sectionText == null || sectionText.isEmpty()) return Component.empty();
        return SECTION_HEX.deserialize(sectionText);
    }

    private static String convert(String input, boolean mini) {
        StringBuilder out = new StringBuilder(input.length() + 24);
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (c == '<' && mini) {
                int close = input.indexOf('>', i);
                if (close != -1 && close - i <= 64) {
                    out.append(input, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }

            char marker = 0;
            if (c == '&' || c == '§') {
                marker = c;
            }

            if (marker != 0 && i + 1 < n) {
                char next = input.charAt(i + 1);

                if (next == '#' && i + 7 < n) {
                    String hex = input.substring(i + 2, i + 8);
                    if (isHex6(hex)) {
                        appendHex(out, hex, mini);
                        i += 8;
                        continue;
                    }
                }

                if ((next == 'x' || next == 'X') && i + 13 < n) {
                    String hex = readRepeatedHex(input, i + 2, marker);
                    if (hex != null) {
                        appendHex(out, hex, mini);
                        i += 14;
                        continue;
                    }
                }

                if (isLegacyCode(next)) {
                    appendLegacy(out, next, mini);
                    i += 2;
                    continue;
                }
            }

            if (c == '#' && i + 6 < n) {
                String hex = input.substring(i + 1, i + 7);
                if (isHex6(hex) && (i == 0 || !isHexChar(input.charAt(i - 1)))) {
                    appendHex(out, hex, mini);
                    i += 7;
                    continue;
                }
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String readRepeatedHex(String input, int start, char marker) {
        StringBuilder hex = new StringBuilder(6);
        for (int j = 0; j < 6; j++) {
            int pos = start + j * 2;
            if (input.charAt(pos) != marker || !isHexChar(input.charAt(pos + 1))) {
                return null;
            }
            hex.append(input.charAt(pos + 1));
        }
        return hex.toString();
    }

    private static void appendHex(StringBuilder out, String hex, boolean mini) {
        if (mini) {
            out.append("<#").append(hex).append('>');
        } else {
            out.append('§').append('x');
            for (int i = 0; i < 6; i++) {
                out.append('§').append(Character.toLowerCase(hex.charAt(i)));
            }
        }
    }

    private static void appendLegacy(StringBuilder out, char code, boolean mini) {
        char c = Character.toLowerCase(code);
        int idx = "0123456789abcdef".indexOf(c);
        if (idx >= 0) {
            appendHex(out, PALETTE[idx], mini);
            return;
        }
        if (mini) {
            out.append(switch (c) {
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> "&" + code;
            });
        } else {
            out.append('§').append(c);
        }
    }

    private static boolean isLegacyCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) != -1;
    }

    private static boolean isHex6(String s) {
        if (s.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            if (!isHexChar(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public static String toGsonJsonFromComponent(Component component) {
        if (component == null) return "{\"text\":\"\"}";
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
            char ch = raw.charAt(i);
            if (ch == ' ') {
                capitalize = true;
                sb.append(ch);
            } else if (capitalize) {
                sb.append(Character.toUpperCase(ch));
                capitalize = false;
            } else {
                sb.append(ch);
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
            return item.getType().isBlock()
                    ? "block." + namespace + "." + key
                    : "item." + namespace + "." + key;
        }
    }
}
