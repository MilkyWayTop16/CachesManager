package org.gw.cachesmanager.animations.view;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.animations.platform.PacketPlatform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyAnimationView implements AnimationView {

    private final CachesManager plugin;
    private final PacketPlatform packetPlatform;
    private final Map<String, Item> phantomItems = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> itemHolograms = new ConcurrentHashMap<>();
    private final Map<String, Location> currentLocations = new ConcurrentHashMap<>();
    private final Map<String, Float> currentRotation = new ConcurrentHashMap<>();

    public LegacyAnimationView(CachesManager plugin, PacketPlatform packetPlatform) {
        this.plugin = plugin;
        this.packetPlatform = packetPlatform;
    }

    @Override
    public void spawn(String cacheName, Location location, ItemStack item, Animation animation) {
        if (location.getWorld() == null) return;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        Location spawnLoc = location.clone().add(0.5, 0.0, 0.5);

        ArmorStand hologram = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc.clone().add(0, 0.5, 0), EntityType.ARMOR_STAND);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setVisible(false);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);
        hologram.setPersistent(false);
        hologram.getPersistentDataContainer().set(new NamespacedKey(plugin, "ghost"), PersistentDataType.STRING, "true");

        if (hologram.getEquipment() != null) {
            hologram.getEquipment().setHelmet(item.clone());
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = org.gw.cachesmanager.utils.HexColors.translate(item.getItemMeta().getDisplayName());
            if (plugin.getConfigManager().isTrimHologramItemName()) {
                displayName = displayName.replaceAll("^\\s+", "");
            }
            hologram.setCustomName(displayName);
            hologram.setCustomNameVisible(true);
        }

        itemHolograms.put(cacheName, hologram);
        currentLocations.put(cacheName, spawnLoc.clone());
        currentRotation.put(cacheName, 0.0f);

        if (packetPlatform != null) {
            for (Player p : spawnLoc.getWorld().getPlayers()) {
                packetPlatform.sendItemHologramMetadata(hologram, p, item);
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

        Location updatedLoc = baseLoc.clone().add(0, 0.5 + heightOffset, 0);
        updatedLoc.setYaw(newYaw);

        hologram.teleport(updatedLoc);
        hologram.setHeadPose(new EulerAngle(0, Math.toRadians(newYaw), 0));
    }

    @Override
    public void remove(String cacheName) {
        Item phantom = phantomItems.remove(cacheName);
        if (phantom != null && !phantom.isDead()) phantom.remove();

        ArmorStand hologram = itemHolograms.remove(cacheName);
        if (hologram != null && !hologram.isDead()) hologram.remove();

        currentLocations.remove(cacheName);
        currentRotation.remove(cacheName);
    }

    public Map<String, Item> getPhantomItems() {
        return phantomItems;
    }

    public Map<String, ArmorStand> getItemHolograms() {
        return itemHolograms;
    }
}