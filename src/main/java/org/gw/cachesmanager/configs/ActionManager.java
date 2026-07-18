package org.gw.cachesmanager.configs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

        Object rawActions = config.get("actions." + path);

        if (rawActions == null) return;

        if (rawActions instanceof List) {
            List<?> list = (List<?>) rawActions;
            if (list.isEmpty()) return;
        } else if (rawActions instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawActions;
            if (map.isEmpty()) return;
        } else if (rawActions instanceof String && ((String) rawActions).trim().isEmpty()) {
            return;
        }

        List<String> actions = config.getStringList("actions." + path);
        if (actions.isEmpty()) return;

        Map<String, String> ph = placeholders != null ? new HashMap<>(placeholders) : new HashMap<>();
        if (player != null) {
            ph.putIfAbsent("player", player.getName());
            ph.putIfAbsent("player_display", player.getDisplayName());
        }

        for (String action : actions) {
            String processed = action;
            for (Entry<String, String> entry : ph.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue())
                        .replace("<" + entry.getKey() + ">", entry.getValue());
            }

            processed = PlaceholderAPIHook.parse(player, processed);

            if (!processed.startsWith("[")) {
                plugin.log("Обнаружено невалидное действие без квадратных скобок: &#FB8808" + processed + " &f(Путь: &#FB8808" + path + "&f)...");
                continue;
            }

            int closingBracket = processed.indexOf("]");
            if (closingBracket == -1) {
                plugin.log("Обнаружен незакрытый тег действия в строке: &#FB8808" + processed + " &f(Путь: &#FB8808" + path + "&f)...");
                continue;
            }

            String actionTag = processed.substring(0, closingBracket + 1).toLowerCase();
            String rawValue = processed.substring(closingBracket + 1);

            switch (actionTag) {
                case "[message]" -> {
                    String cacheName = ph.get("name-cache");
                    if (player != null && cacheName != null && (action.contains("{confirm-button}") || action.contains("{cancel-button}"))) {
                        sendConfirmationMessage(player, rawValue, cacheName);
                        continue;
                    }
                    String message = rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
                    if (player != null) {
                        player.sendMessage(HexColors.translateToComponent(message));
                    } else {
                        plugin.console(message);
                    }
                }
                case "[message-console]" -> plugin.console(HexColors.translate(rawValue.trim()));
                case "[broadcast]" -> Bukkit.broadcast(HexColors.translateToComponent(rawValue.trim()));
                case "[actionbar]" -> {
                    if (player != null) {
                        player.sendActionBar(HexColors.translateToComponent(rawValue.trim()));
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
                        } catch (Exception e) {
                            plugin.log("Не удалось воспроизвести звук &#FB8808" + parts[0] + " &fв триггер-действиях (Путь: &#FB8808" + path + "&f)...");
                        }
                    }
                }
                case "[title]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        try {
                            String title = HexColors.translate(parts[0]);
                            int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                            int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                            int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                            player.sendTitle(title, "", fadeIn, stay, fadeOut);
                        } catch (Exception e) {
                            plugin.log("Некорректный формат параметров действия [title] (Путь: &#FB8808" + path + "&f)...");
                        }
                    }
                }
                case "[subtitle]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 1 && player != null) {
                        try {
                            String subtitle = HexColors.translate(parts[0]);
                            int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                            int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                            int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                            player.sendTitle("", subtitle, fadeIn, stay, fadeOut);
                        } catch (Exception e) {
                            plugin.log("Некорректный формат параметров действия [subtitle] (Путь: &#FB8808" + path + "&f)...");
                        }
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
                        } catch (Exception e) {
                            plugin.log("Ошибка добавления эффекта зелья &#FB8808" + parts[0] + " &fв действиях (Путь: &#FB8808" + path + "&f)...");
                        }
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
                        } catch (Exception e) {
                            plugin.log("Ошибка парсинга локации для телепортации в действиях (Путь: &#FB8808" + path + "&f)...");
                        }
                    }
                }
                case "[give-item]" -> {
                    String[] parts = rawValue.trim().split(" ");
                    if (parts.length >= 2 && player != null) {
                        try {
                            Material material = org.gw.cachesmanager.utils.MaterialCompat.match(parts[0], null);
                            if (material == null) {
                                throw new IllegalArgumentException("unknown material");
                            }
                            int amount = Integer.parseInt(parts[1]);
                            player.getInventory().addItem(new ItemStack(material, amount));
                        } catch (Exception e) {
                            plugin.log("Не удалось выдать предмет по материалу &#FB8808" + parts[0] + " &f(Путь: &#FB8808" + path + "&f)...");
                        }
                    }
                }
                default -> plugin.log("Обнаружен неизвестный тип кастомного действия &#FB8808" + actionTag + " &f(Путь: &#FB8808" + path + "&f)...");
            }
        }
    }

    private void sendConfirmationMessage(Player player, String line, String cacheName) {
        if (player == null) return;
        Component message = Component.empty();
        String remaining = line;
        while (true) {
            int confirmIdx = remaining.indexOf("{confirm-button}");
            int cancelIdx = remaining.indexOf("{cancel-button}");
            if (confirmIdx == -1 && cancelIdx == -1) {
                if (!remaining.isEmpty()) {
                    message = message.append(HexColors.translateToComponent(remaining));
                }
                break;
            }
            int nextIdx = (confirmIdx != -1 && (cancelIdx == -1 || confirmIdx < cancelIdx)) ? confirmIdx : cancelIdx;
            String buttonType = (nextIdx == confirmIdx) ? "confirm" : "cancel";
            if (nextIdx > 0) {
                String before = remaining.substring(0, nextIdx);
                message = message.append(HexColors.translateToComponent(before));
            }
            Component button = createConfirmationButton(buttonType, cacheName);
            message = message.append(button);
            int buttonLength = buttonType.equals("confirm") ? 16 : 15;
            remaining = remaining.substring(nextIdx + buttonLength);
        }
        player.sendMessage(message);
    }

    private Component createConfirmationButton(String type, String cacheName) {
        FileConfiguration config = mainConfig.getConfig();
        String textPath = "actions.cache.delete.confirm-buttons." + type + ".text";
        String hoverPath = "actions.cache.delete.confirm-buttons." + type + ".hover";
        String buttonText = config.getString(textPath, "");
        String hoverText = config.getString(hoverPath, "");
        buttonText = buttonText.replace("{name-cache}", cacheName);
        hoverText = hoverText.replace("{name-cache}", cacheName);
        Component button = HexColors.translateToComponent(buttonText);
        if (!hoverText.isEmpty()) {
            Component hoverComponent = HexColors.translateToComponent(hoverText);
            button = button.hoverEvent(HoverEvent.showText(hoverComponent));
        }
        String command = type.equals("confirm") ? "/cm deletecache \"" + cacheName + "\" confirm" : "/cm deletecache \"" + cacheName + "\" cancel";
        button = button.clickEvent(ClickEvent.runCommand(command));
        return button;
    }
}