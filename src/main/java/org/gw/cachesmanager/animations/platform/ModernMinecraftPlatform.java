package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModernMinecraftPlatform implements HologramPlatform {

    private final CachesManager plugin;
    private final Map<String, TextDisplay> activeDisplays = new ConcurrentHashMap<>();

    public ModernMinecraftPlatform(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        if (location.getWorld() == null) return;

        deleteHologram(id);

        TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.setText(HexColors.translate(text));
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.getPersistentDataContainer().set(new NamespacedKey(plugin, "hologram_id"), PersistentDataType.STRING, id);

        activeDisplays.put(id, display);
    }

    @Override
    public void updateHologram(String id, String text) {
        TextDisplay display = activeDisplays.get(id);
        if (display != null && !display.isDead()) {
            display.setText(HexColors.translate(text));
        }
    }

    @Override
    public void deleteHologram(String id) {
        TextDisplay display = activeDisplays.remove(id);
        if (display != null && !display.isDead()) {
            display.remove();
        }

        if (activeDisplays.isEmpty()) {
            plugin.getServer().getWorlds().forEach(world ->
                    world.getEntitiesByClass(TextDisplay.class).forEach(entity -> {
                        String storedId = entity.getPersistentDataContainer().get(
                                new NamespacedKey(plugin, "hologram_id"),
                                PersistentDataType.STRING
                        );
                        if (id.equals(storedId)) {
                            entity.remove();
                        }
                    })
            );
        }
    }
}