package org.gw.cachesmanager.animations.view;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.animations.AnimationEngineConfigurator.PacketItemMetadataSender;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyAnimationView implements AnimationView {

    private final CachesManager plugin;
    private final PacketItemMetadataSender packetSender;
    @Getter
    private final Map<String, Item> phantomItems = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, ArmorStand> itemHolograms = new ConcurrentHashMap<>();
    private final Map<String, Location> currentLocations = new ConcurrentHashMap<>();
    private final Map<String, Float> currentRotation = new ConcurrentHashMap<>();

    public LegacyAnimationView(CachesManager plugin, PacketItemMetadataSender packetSender) {
        this.plugin = plugin;
        this.packetSender = packetSender;
    }

    @Override
    public void spawn(String cacheName, Location location, ItemStack item, Animation animation) {
        if (location.getWorld() == null) return;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        var cache = plugin.getCacheManager().getCache(cacheName);
        boolean hologramsEnabled = cache == null || cache.isHologramEnabled();
        double offsetX = cache != null ? cache.getHologramOffsetX() : 0.0;
        double offsetY = cache != null ? cache.getHologramOffsetY() : 0.0;
        double offsetZ = cache != null ? cache.getHologramOffsetZ() : 0.0;

        if (!hologramsEnabled) {
            return;
        }

        double itemBaseY = 0.55;
        Location spawnLoc = location.clone().add(0.5 + offsetX, itemBaseY + offsetY, 0.5 + offsetZ);

        ArmorStand hologram = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setVisible(false);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);
        hologram.setPersistent(false);
        hologram.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");

        if (hologram.getEquipment() != null) {
            hologram.getEquipment().setHelmet(item.clone());
        }

        String displayName = HexColors.translate(HexColors.getItemNameLegacy(item));
        if (plugin.getConfigManager().isTrimHologramItemName()) {
            displayName = displayName.replaceAll("^\\s+", "");
        }
        hologram.setCustomName(displayName);
        hologram.setCustomNameVisible(true);

        itemHolograms.put(cacheName, hologram);
        currentLocations.put(cacheName, spawnLoc.clone());
        currentRotation.put(cacheName, 0.0f);

        if (packetSender != null) {
            Collection<? extends Player> nearbyPlayers = spawnLoc.getWorld().getNearbyPlayers(spawnLoc, 48);
            for (Player p : nearbyPlayers) {
                packetSender.sendItemHologramMetadata(hologram, p, item);
            }
        }
    }

    @Override
    public void update(String cacheName, int ticks, Animation animation) {
        ArmorStand hologram = itemHolograms.get(cacheName);
        Location baseLoc = currentLocations.get(cacheName);
        Float yaw = currentRotation.get(cacheName);

        if (hologram == null || baseLoc == null || yaw == null) return;

        float newYaw = yaw + (float) animation.getRotationSpeed();
        currentRotation.put(cacheName, newYaw);

        double speed = animation.getRotationSpeed() * 0.05;
        double heightOffset = Math.sin(ticks * speed) * 0.15;

        Location updatedLoc = baseLoc.clone().add(0, heightOffset, 0);
        updatedLoc.setYaw(newYaw);

        hologram.teleport(updatedLoc);
        hologram.setHeadPose(new EulerAngle(0, Math.toRadians(newYaw), 0));
    }

    @Override
    public void remove(String cacheName) {
        ArmorStand hologram = itemHolograms.remove(cacheName);
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }

        phantomItems.remove(cacheName);
        currentLocations.remove(cacheName);
        currentRotation.remove(cacheName);
    }
}