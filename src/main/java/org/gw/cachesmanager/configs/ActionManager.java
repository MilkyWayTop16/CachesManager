package org.gw.cachesmanager.configs;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ActionManager {
    private final CachesManager plugin;
    private final MainConfig mainConfig;

    public ActionManager(CachesManager plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    public void executeActions(Player player, String path) {
        executeActions(player, path, null);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        FileConfiguration config = mainConfig.getConfig();
        List<String> actions = config.getStringList("actions." + path);
        if (actions.isEmpty()) return;

        Map<String, String> ph = placeholders != null ? new HashMap<>(placeholders) : new HashMap<>();
        if (player != null) {
            ph.put("player", player.getName());
            ph.put("player_display", player.getDisplayName());
        }

        for (String action : actions) {
            String processed = action;
            for (Entry<String, String> entry : ph.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue())
                        .replace("<" + entry.getKey() + ">", entry.getValue());
            }

            processed = PlaceholderAPIHook.parse(player, processed);

            if (!processed.startsWith("[")) continue;

            int closingBracket = processed.indexOf("]");
            if (closingBracket == -1) continue;

            String actionTag = processed.substring(0, closingBracket + 1).toLowerCase();
            String rawValue = processed.substring(closingBracket + 1);

            switch (actionTag) {
                case "[message]" -> {
                    String cacheName = ph.get("name-cache");
                    if (player != null && cacheName != null && (action.contains("{confirm-button}") || action.contains("{cancel-button}"))) {
                        sendConfirmationMessage(player, rawValue, cacheName);
                        continue;
                    }
                    if (player != null) {
                        String message = rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
                        player.sendMessage(HexColors.translate(message));
                    }
                }
                case "[message-console]" -> plugin.console(HexColors.translate(rawValue.trim()));
                case "[broadcast]" -> Bukkit.broadcastMessage(HexColors.translate(rawValue.trim()));
                case "[actionbar]" -> {
                    if (player != null) {
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(HexColors.translate(rawValue.trim())));
                    }
                }
                case "[console-command]" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rawValue.trim());
                case "[player-command]" -> {
                    if (player != null) Bukkit.dispatchCommand(player, rawValue.trim());
                }
                case "[sound]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        try {
                            Sound sound = Sound.valueOf(parts[0].toUpperCase());
                            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                            player.playSound(player.getLocation(), sound, volume, pitch);
                        } catch (Exception ignored) {}
                    }
                }
                case "[title]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        String title = HexColors.translate(parts[0]);
                        int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                        int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                        int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                        player.sendTitle(title, "", fadeIn, stay, fadeOut);
                    }
                }
                case "[subtitle]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        String subtitle = HexColors.translate(parts[0]);
                        int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                        int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                        int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                        player.sendTitle("", subtitle, fadeIn, stay, fadeOut);
                    }
                }
                case "[effect]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        try {
                            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
                            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
                            player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                        } catch (Exception ignored) {}
                    }
                }
                case "[teleport]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 4 && player != null) {
                        try {
                            double x = Double.parseDouble(parts[0]);
                            double y = Double.parseDouble(parts[1]);
                            double z = Double.parseDouble(parts[2]);
                            World world = Bukkit.getWorld(parts[3]);
                            if (world != null) player.teleport(new Location(world, x, y, z));
                        } catch (Exception ignored) {}
                    }
                }
                case "[give-item]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 2 && player != null) {
                        try {
                            Material material = Material.valueOf(parts[0].toUpperCase());
                            int amount = Integer.parseInt(parts[1]);
                            player.getInventory().addItem(new ItemStack(material, amount));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private void sendConfirmationMessage(Player player, String line, String cacheName) {
        if (player == null) return;
        ComponentBuilder builder = new ComponentBuilder();
        String remaining = line;
        while (true) {
            int confirmIdx = remaining.indexOf("{confirm-button}");
            int cancelIdx = remaining.indexOf("{cancel-button}");
            if (confirmIdx == -1 && cancelIdx == -1) {
                if (!remaining.isEmpty()) {
                    builder.append(TextComponent.fromLegacyText(HexColors.translate(remaining)));
                }
                break;
            }
            int nextIdx = (confirmIdx != -1 && (cancelIdx == -1 || confirmIdx < cancelIdx)) ? confirmIdx : cancelIdx;
            String buttonType = (nextIdx == confirmIdx) ? "confirm" : "cancel";
            if (nextIdx > 0) {
                String before = remaining.substring(0, nextIdx);
                builder.append(TextComponent.fromLegacyText(HexColors.translate(before)));
            }
            TextComponent button = createConfirmationButton(buttonType, cacheName);
            builder.append(button);
            int buttonLength = buttonType.equals("confirm") ? 16 : 15;
            remaining = remaining.substring(nextIdx + buttonLength);
        }
        player.spigot().sendMessage(builder.create());
    }

    private TextComponent createConfirmationButton(String type, String cacheName) {
        FileConfiguration config = mainConfig.getConfig();
        String textPath = "actions.cache.delete.confirm-buttons." + type + ".text";
        String hoverPath = "actions.cache.delete.confirm-buttons." + type + ".hover";
        String buttonText = config.getString(textPath, "");
        String hoverText = config.getString(hoverPath, "");
        buttonText = buttonText.replace("{name-cache}", cacheName);
        hoverText = hoverText.replace("{name-cache}", cacheName);
        String translatedText = HexColors.translate(buttonText);
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(translatedText));
        if (!hoverText.isEmpty()) {
            String translatedHover = HexColors.translate(hoverText);
            button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(translatedHover)));
        }
        String command = type.equals("confirm") ? "/cm deletecache \"" + cacheName + "\" confirm" : "/cm deletecache \"" + cacheName + "\" cancel";
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        return button;
    }
}