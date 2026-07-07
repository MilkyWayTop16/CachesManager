package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyMinecraftPlatform implements HologramPlatform {

    private final CachesManager plugin;
    private final java.util.Map<String, List<Entity>> activeHolograms = new ConcurrentHashMap<>();

    public LegacyMinecraftPlatform(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : java.util.Arrays.asList(text.split("\n"));
        createHologram(id, location, lines);
    }

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        if (location.getWorld() == null) return;

        deleteHologram(id);

        List<Entity> entities = new ArrayList<>();

        try {
            List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
            Location spawnLoc = location.clone();

            for (int i = safeLines.size() - 1; i >= 0; i--) {
                final String currentLine = safeLines.get(i);
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
            java.util.Collections.reverse(entities);

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
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : java.util.Arrays.asList(text.split("\n"));
        updateHologram(id, lines);
    }

    @Override
    public void updateHologram(String id, List<String> lines) {
        List<Entity> entities = activeHolograms.get(id);
        if (entities == null || entities.isEmpty()) return;

        try {
            List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
            if (entities.size() == safeLines.size()) {
                for (int i = 0; i < safeLines.size(); i++) {
                    Entity entity = entities.get(i);
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        entity.setCustomName(HexColors.translate(safeLines.get(i)));
                    }
                }
            } else {
                Location loc = entities.get(0).getLocation();
                if (loc != null) {
                    deleteHologram(id);
                    createHologram(id, loc, safeLines);
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
