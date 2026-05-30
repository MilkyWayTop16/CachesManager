package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModernMinecraftPlatform implements HologramPlatform {

    private final CachesManager plugin;
    private final Map<String, List<Entity>> activeHolograms = new ConcurrentHashMap<>();
    private final boolean useTextDisplay;

    public ModernMinecraftPlatform(CachesManager plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
    }

    private boolean isTextDisplaySupported() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        if (location.getWorld() == null) return;

        deleteHologram(id);

        List<Entity> entities = new ArrayList<>();

        try {
            if (useTextDisplay) {
                TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
                    textDisplay.setText(HexColors.translate(text));
                    textDisplay.setBillboard(Display.Billboard.CENTER);
                    textDisplay.setPersistent(false);
                    textDisplay.getPersistentDataContainer().set(CacheKeys.HOLOGRAM_ID.getNamespacedKey(), PersistentDataType.STRING, id);
                });
                entities.add(display);
            } else {
                String[] lines = text.split("\n");
                Location spawnLoc = location.clone();

                for (int i = lines.length - 1; i >= 0; i--) {
                    final String currentLine = lines[i];
                    ArmorStand stand = location.getWorld().spawn(spawnLoc, ArmorStand.class, armorStand -> {
                        armorStand.setCustomName(HexColors.translate(currentLine));
                        armorStand.setCustomNameVisible(true);
                        armorStand.setVisible(false);
                        armorStand.setGravity(false);
                        armorStand.setMarker(true);
                        armorStand.setPersistent(false);
                        armorStand.getPersistentDataContainer().set(CacheKeys.HOLOGRAM_ID.getNamespacedKey(), PersistentDataType.STRING, id);
                    });
                    entities.add(stand);
                    spawnLoc.add(0, 0.28, 0);
                }
                Collections.reverse(entities);
            }

            activeHolograms.put(id, entities);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка создания встроенной голограммы (айди: " + id + ")");
            }
            for (Entity e : entities) {
                try { if (e != null && !e.isDead()) e.remove(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void updateHologram(String id, String text) {
        List<Entity> entities = activeHolograms.get(id);
        if (entities == null || entities.isEmpty()) return;

        try {
            if (useTextDisplay) {
                Entity entity = entities.get(0);
                if (entity instanceof TextDisplay && !entity.isDead()) {
                    ((TextDisplay) entity).setText(HexColors.translate(text));
                }
            } else {
                String[] lines = text.split("\n");
                if (entities.size() == lines.length) {
                    for (int i = 0; i < lines.length; i++) {
                        Entity entity = entities.get(i);
                        if (entity instanceof ArmorStand && !entity.isDead()) {
                            entity.setCustomName(HexColors.translate(lines[i]));
                        }
                    }
                } else {
                    Location loc = entities.get(0).getLocation();
                    if (loc != null) {
                        deleteHologram(id);
                        createHologram(id, loc, text);
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка обновления встроенной голограммы (айди: " + id + ")");
            }
        }
    }

    @Override
    public void deleteHologram(String id) {
        List<Entity> entities = activeHolograms.remove(id);
        if (entities != null) {
            for (Entity entity : entities) {
                try {
                    if (entity != null && !entity.isDead()) {
                        entity.remove();
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}