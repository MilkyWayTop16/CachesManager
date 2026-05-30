package org.gw.cachesmanager.animations.view;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModernAnimationView implements AnimationView {

    private final CachesManager plugin;
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> texts = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> mounts = new ConcurrentHashMap<>();
    private final Map<String, Location> baseLocations = new ConcurrentHashMap<>();
    private final Map<String, Double> itemBaseYOffsets = new ConcurrentHashMap<>();

    public ModernAnimationView(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void spawn(String cacheName, Location location, ItemStack item, Animation animation) {
        if (location.getWorld() == null) return;

        var cache = plugin.getCacheManager().getCache(cacheName);
        boolean hologramsEnabled = cache == null || cache.isHologramEnabled();
        double offsetX = cache != null ? cache.getHologramOffsetX() : 0.0;
        double offsetY = cache != null ? cache.getHologramOffsetY() : 0.0;
        double offsetZ = cache != null ? cache.getHologramOffsetZ() : 0.0;

        if (!hologramsEnabled) {
            baseLocations.put(cacheName, location.getBlock().getLocation().clone());
            return;
        }

        Location baseLoc = location.getBlock().getLocation();
        baseLocations.put(cacheName, baseLoc.clone());

        double itemBaseY = 0.55;
        itemBaseYOffsets.put(cacheName, itemBaseY);

        Location mountLoc = baseLoc.clone().add(0.5 + offsetX, itemBaseY + offsetY, 0.5 + offsetZ);
        ArmorStand mount = (ArmorStand) location.getWorld().spawnEntity(mountLoc, EntityType.ARMOR_STAND);
        mount.setVisible(false);
        mount.setGravity(false);
        mount.setMarker(true);
        mount.setPersistent(false);
        mounts.put(cacheName, mount);

        Item entity = (Item) location.getWorld().spawnEntity(mountLoc, EntityType.DROPPED_ITEM);
        entity.setItemStack(item.clone());
        entity.setGravity(false);
        entity.setPickupDelay(Integer.MAX_VALUE);
        entity.setInvulnerable(true);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        items.put(cacheName, entity);

        mount.addPassenger(entity);

        Location textLoc = baseLoc.clone().add(0.5 + offsetX, itemBaseY + 0.75 + offsetY, 0.5 + offsetZ);
        TextDisplay text = (TextDisplay) location.getWorld().spawnEntity(textLoc, EntityType.TEXT_DISPLAY);

        Component displayName = HexColors.getItemNameComponent(item);
        if (plugin.getConfigManager().isTrimHologramItemName() && displayName instanceof TextComponent) {
            String legacy = LegacyComponentSerializer.legacySection().serialize(displayName);
            legacy = legacy.replaceAll("^\\s+", "");
            displayName = LegacyComponentSerializer.legacySection().deserialize(legacy);
        }

        text.text(displayName);
        text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        text.setPersistent(false);
        text.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        texts.put(cacheName, text);
    }

    @Override
    public void update(String cacheName, int ticks, Animation animation) {
        ArmorStand mount = mounts.get(cacheName);
        Location baseLoc = baseLocations.get(cacheName);
        Double itemBaseY = itemBaseYOffsets.getOrDefault(cacheName, 0.55);
        if (mount == null || mount.isDead() || baseLoc == null) return;

        var cache = plugin.getCacheManager().getCache(cacheName);
        double offsetX = cache != null ? cache.getHologramOffsetX() : 0.0;
        double offsetY = cache != null ? cache.getHologramOffsetY() : 0.0;
        double offsetZ = cache != null ? cache.getHologramOffsetZ() : 0.0;

        double height = Math.sin(ticks * 0.1) * 0.15;
        Location loc = baseLoc.clone().add(0.5 + offsetX, itemBaseY + offsetY + height, 0.5 + offsetZ);
        loc.setYaw(ticks * (float) animation.getRotationSpeed());

        mount.teleport(loc);

        TextDisplay text = texts.get(cacheName);
        if (text != null && !text.isDead()) {
            Location textLoc = baseLoc.clone().add(0.5 + offsetX, itemBaseY + 0.75 + offsetY, 0.5 + offsetZ);
            text.teleport(textLoc);
        }
    }

    @Override
    public void remove(String cacheName) {
        Item item = items.remove(cacheName);
        if (item != null) item.remove();

        ArmorStand mount = mounts.remove(cacheName);
        if (mount != null) mount.remove();

        TextDisplay text = texts.remove(cacheName);
        if (text != null) text.remove();

        baseLocations.remove(cacheName);
    }
}