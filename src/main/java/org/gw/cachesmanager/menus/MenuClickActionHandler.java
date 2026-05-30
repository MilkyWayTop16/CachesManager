package org.gw.cachesmanager.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.managers.CacheManager;
import org.gw.cachesmanager.managers.ConfigManager;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

import java.util.List;
import java.util.function.BiConsumer;

public class MenuClickActionHandler {

    private final CachesManager plugin;
    private final CacheManager cacheManager;
    private final ConfigManager configManager;
    private final MenuActionHandler actionHandler;
    private final MenuClickDelegate delegate;
    private final java.util.Map<String, BiConsumer<Player, Integer>> specialHandlers;

    public MenuClickActionHandler(CachesManager plugin,
                                  CacheManager cacheManager,
                                  ConfigManager configManager,
                                  MenuActionHandler actionHandler,
                                  MenuClickDelegate delegate,
                                  java.util.Map<String, BiConsumer<Player, Integer>> specialHandlers) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.configManager = configManager;
        this.actionHandler = actionHandler;
        this.delegate = delegate;
        this.specialHandlers = specialHandlers;
    }

    public List<String> getClickCommands(ConfigurationSection sec, ClickType type) {
        List<String> cmds;
        switch (type) {
            case LEFT:
                cmds = sec.getStringList("left-click-commands");
                break;
            case RIGHT:
                cmds = sec.getStringList("right-click-commands");
                break;
            case SHIFT_LEFT:
                cmds = sec.getStringList("shift-left-click-commands");
                break;
            case SHIFT_RIGHT:
                cmds = sec.getStringList("shift-right-click-commands");
                break;
            default:
                cmds = sec.getStringList("click-commands");
                break;
        }
        if (cmds.isEmpty()) {
            cmds = sec.getStringList("click-commands");
        }
        return cmds;
    }

    private int extractDelta(String command, int defaultValue) {
        try {
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }

    public void executeClickCommands(Player p, List<String> commands, String cacheName, Inventory inv, int page, int index, CacheMenuHolder holder) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;

        for (String rawCmd : commands) {
            String processed = rawCmd.replace("{name-cache}", cache.getDisplayName())
                    .replace("{player}", p.getName()).trim();
            processed = PlaceholderAPIHook.parse(p, processed);

            if (processed.startsWith("[")) {
                int closingBracket = processed.indexOf("]");
                if (closingBracket != -1) {
                    String actionTag = processed.substring(0, closingBracket + 1).toLowerCase();
                    String rawValue = processed.substring(closingBracket + 1).trim();

                    switch (actionTag) {
                        case "[increase-chance]" -> {
                            int delta = extractDelta(processed, 1);
                            actionHandler.handleChanceChange(p, index, true, delta, holder,
                                    (name, idx) -> delegate.delegateInitializeCachePageLoot(name),
                                    idx -> {
                                        int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                                        delegate.delegateUpdateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage(), slot);
                                    });
                        }
                        case "[reduce-chance]" -> {
                            int delta = extractDelta(processed, 1);
                            actionHandler.handleChanceChange(p, index, false, delta, holder,
                                    (name, idx) -> delegate.delegateInitializeCachePageLoot(name),
                                    idx -> {
                                        int slot = calculateSlotFromIndex(idx, holder.getCurrentPage(), p.getOpenInventory().getTopInventory().getSize());
                                        delegate.delegateUpdateSingleItem(p, p.getOpenInventory().getTopInventory(), holder.getMenuFile(), cacheName, holder.getCurrentPage(), slot);
                                    });
                        }
                        case "[open-menu]" -> {
                            delegate.delegateOpenMenu(p, cacheName, rawValue, 1);
                        }
                        case "[message]" -> {
                            String message = rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
                            p.sendMessage(HexColors.translateToComponent(message));
                        }
                        case "[message-console]" -> plugin.console(HexColors.translate(rawValue));
                        case "[broadcast]" -> Bukkit.broadcast(HexColors.translateToComponent(rawValue));
                        case "[actionbar]" -> p.sendActionBar(HexColors.translateToComponent(rawValue));
                        case "[console-command]" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rawValue);
                        case "[player-command]" -> Bukkit.dispatchCommand(p, rawValue);
                        case "[sound]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                try {
                                    Sound sound = Sound.valueOf(parts[0].toUpperCase());
                                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                                    p.playSound(p.getLocation(), sound, volume, pitch);
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[title]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                String title = HexColors.translate(parts[0]);
                                int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                                int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                                int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                                p.sendTitle(title, "", fadeIn, stay, fadeOut);
                            }
                        }
                        case "[subtitle]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                String subtitle = HexColors.translate(parts[0]);
                                int fadeIn = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                                int stay = parts.length > 2 ? Integer.parseInt(parts[2]) : 70;
                                int fadeOut = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                                p.sendTitle("", subtitle, fadeIn, stay, fadeOut);
                            }
                        }
                        case "[effect]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 1) {
                                try {
                                    PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                                    int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
                                    int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
                                    p.addPotionEffect(new PotionEffect(type, duration, amplifier));
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[teleport]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 4) {
                                try {
                                    double x = Double.parseDouble(parts[0]);
                                    double y = Double.parseDouble(parts[1]);
                                    double z = Double.parseDouble(parts[2]);
                                    World world = Bukkit.getWorld(parts[3]);
                                    if (world != null) p.teleport(new org.bukkit.Location(world, x, y, z));
                                } catch (Exception ignored) {}
                            }
                        }
                        case "[give-item]" -> {
                            String[] parts = rawValue.split(" ");
                            if (parts.length >= 2) {
                                try {
                                    Material material = Material.valueOf(parts[0].toUpperCase());
                                    int amount = Integer.parseInt(parts[1]);
                                    p.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                                } catch (Exception ignored) {}
                            }
                        }
                        default -> {
                            if (specialHandlers != null) {
                                BiConsumer<Player, Integer> handler = specialHandlers.get(actionTag);
                                if (handler != null) {
                                    handler.accept(p, index);
                                } else {
                                    Bukkit.dispatchCommand(p, processed);
                                }
                            }
                        }
                    }
                    continue;
                }
            }

            if (specialHandlers != null) {
                BiConsumer<Player, Integer> handler = specialHandlers.get(processed.toLowerCase());
                if (handler != null) {
                    handler.accept(p, index);
                } else {
                    Bukkit.dispatchCommand(p, processed);
                }
            }
        }
    }

    private int calculateSlotFromIndex(int itemIndex, int page, int invSize) {
        org.bukkit.configuration.file.FileConfiguration cfg = configManager.loadMenuConfig("chance-menu.yml");
        if (cfg == null) return 0;
        List<Integer> slots = parseSlotRange(cfg.getStringList("loot.slots"), invSize);
        if (slots.isEmpty()) return 0;
        int perPage = slots.size();
        int slotIdx = itemIndex % perPage;
        return slotIdx < slots.size() ? slots.get(slotIdx) : 0;
    }

    private List<Integer> parseSlotRange(List<String> ranges, int maxSize) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (String r : ranges) {
            try {
                if (r.contains("-")) {
                    String[] p = r.split("-");
                    int start = Integer.parseInt(p[0].trim());
                    int end = Integer.parseInt(p[1].trim());
                    if (start < 0 || end < 0 || start > end || end >= maxSize) continue;
                    for (int i = start; i <= end; i++) result.add(i);
                } else {
                    int s = Integer.parseInt(r.trim());
                    if (s >= 0 && s < maxSize) result.add(s);
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}