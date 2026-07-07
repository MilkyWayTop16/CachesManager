package org.gw.cachesmanager.animations.view;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.animations.AnimationEngineConfigurator.PacketItemMetadataSender;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyAnimationView implements AnimationView {

    private static final double ITEM_BASE_Y = 0.55;
    private static final double NAME_OFFSET_Y = 1.05;

    private final CachesManager plugin;
    private final PacketItemMetadataSender packetSender;
    @Getter
    private final Map<String, Item> phantomItems = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, ArmorStand> itemHolograms = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> mounts = new ConcurrentHashMap<>();
    private final Map<String, Location> baseLocations = new ConcurrentHashMap<>();

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

        if (!hologramsEnabled) return;

        Location baseLoc = location.getBlock().getLocation();
        baseLocations.put(cacheName, baseLoc.clone());

        Location mountLoc = baseLoc.clone().add(0.5 + offsetX, ITEM_BASE_Y + offsetY, 0.5 + offsetZ);
        ArmorStand mount = (ArmorStand) baseLoc.getWorld().spawnEntity(mountLoc, EntityType.ARMOR_STAND);
        mount.setVisible(false);
        mount.setGravity(false);
        mount.setMarker(true);
        mount.setInvulnerable(true);
        mount.setPersistent(false);
        mount.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        mounts.put(cacheName, mount);

        Item phantom = (Item) baseLoc.getWorld().spawnEntity(mountLoc, EntityType.DROPPED_ITEM);
        phantom.setItemStack(item.clone());
        phantom.setGravity(false);
        phantom.setPickupDelay(Integer.MAX_VALUE);
        phantom.setInvulnerable(true);
        phantom.setPersistent(false);
        phantom.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        phantomItems.put(cacheName, phantom);

        mount.addPassenger(phantom);

        Location nameLoc = baseLoc.clone().add(0.5 + offsetX, NAME_OFFSET_Y + offsetY, 0.5 + offsetZ);
        ArmorStand nameStand = (ArmorStand) baseLoc.getWorld().spawnEntity(nameLoc, EntityType.ARMOR_STAND);
        nameStand.setVisible(false);
        nameStand.setGravity(false);
        nameStand.setMarker(true);
        nameStand.setInvulnerable(true);
        nameStand.setCanPickupItems(false);
        nameStand.setPersistent(false);
        nameStand.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");

        Component displayName = HexColors.getItemNameComponent(item);
        boolean hasCustomName = item.hasItemMeta() && item.getItemMeta().hasDisplayName();
        if (plugin.getConfigManager().isTrimHologramItemName() && hasCustomName && displayName instanceof TextComponent) {
            String legacy = LegacyComponentSerializer.legacySection().serialize(displayName).replaceAll("^\\s+", "");
            displayName = LegacyComponentSerializer.legacySection().deserialize(legacy);
        }
        try {
            nameStand.customName(displayName);
        } catch (Throwable t) {
            nameStand.setCustomName(LegacyComponentSerializer.legacySection().serialize(displayName));
        }
        nameStand.setCustomNameVisible(true);
        itemHolograms.put(cacheName, nameStand);

        if (packetSender != null) {
            Collection<? extends Player> nearbyPlayers = nameLoc.getWorld().getNearbyPlayers(nameLoc, 48);
            for (Player p : nearbyPlayers) {
                packetSender.sendItemHologramMetadata(nameStand, p, item);
            }
        }
    }

    @Override
    public void update(String cacheName, int ticks, Animation animation) {
        ArmorStand mount = mounts.get(cacheName);
        Location baseLoc = baseLocations.get(cacheName);
        if (mount == null || mount.isDead() || baseLoc == null) return;

        var cache = plugin.getCacheManager().getCache(cacheName);
        double offsetX = cache != null ? cache.getHologramOffsetX() : 0.0;
        double offsetY = cache != null ? cache.getHologramOffsetY() : 0.0;
        double offsetZ = cache != null ? cache.getHologramOffsetZ() : 0.0;

        double height = Math.sin(ticks * 0.1) * 0.15;
        Location loc = baseLoc.clone().add(0.5 + offsetX, ITEM_BASE_Y + offsetY + height, 0.5 + offsetZ);
        loc.setYaw(ticks * (float) animation.getRotationSpeed());
        mount.teleport(loc);
    }

    @Override
    public void remove(String cacheName) {
        Item phantom = phantomItems.remove(cacheName);
        if (phantom != null && !phantom.isDead()) {
            phantom.remove();
        }

        ArmorStand mount = mounts.remove(cacheName);
        if (mount != null && !mount.isDead()) {
            mount.remove();
        }

        ArmorStand nameStand = itemHolograms.remove(cacheName);
        if (nameStand != null && !nameStand.isDead()) {
            nameStand.remove();
        }

        baseLocations.remove(cacheName);
    }
}
