package org.gw.cachesmanager.animations.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.utils.CacheKeys;
import org.gw.cachesmanager.utils.HexColors;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModernAnimationView implements AnimationView {

    private static final double ITEM_BASE_Y = 0.7;
    private static final double NAME_BASE_Y = 1.3;

    private final CachesManager plugin;
    private final Map<String, ItemDisplay> itemDisplays = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> texts = new ConcurrentHashMap<>();

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

        if (!hologramsEnabled) return;

        Location baseLoc = location.getBlock().getLocation();

        Location displayLoc = baseLoc.clone().add(0.5 + offsetX, ITEM_BASE_Y + offsetY, 0.5 + offsetZ);
        ItemDisplay display = location.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(item.clone());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        });
        itemDisplays.put(cacheName, display);

        Location textLoc = baseLoc.clone().add(0.5 + offsetX, NAME_BASE_Y + offsetY, 0.5 + offsetZ);
        TextDisplay text = location.getWorld().spawn(textLoc, TextDisplay.class, t -> {
            Component displayName = HexColors.getItemNameComponent(item);
            boolean hasCustomName = item.hasItemMeta() && item.getItemMeta().hasDisplayName();
            if (plugin.getConfigManager().isTrimHologramItemName() && hasCustomName && displayName instanceof TextComponent) {
                String legacy = LegacyComponentSerializer.legacySection().serialize(displayName).replaceAll("^\\s+", "");
                displayName = LegacyComponentSerializer.legacySection().deserialize(legacy);
            }
            t.text(displayName);
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.getPersistentDataContainer().set(CacheKeys.GHOST.getNamespacedKey(), PersistentDataType.STRING, "true");
        });
        texts.put(cacheName, text);
    }

    @Override
    public void update(String cacheName, int ticks, Animation animation) {
        int interval = keyframeInterval(animation);
        if (ticks % interval != 0) return;

        ItemDisplay display = itemDisplays.get(cacheName);
        if (display == null || display.isDead()) return;

        int target = ticks + interval;
        float yawRad = (float) Math.toRadians(target * animation.getRotationSpeed());
        float bob = (float) ((Math.sin(target * 0.1) + 1.0) * 0.5 * 0.08);

        display.setInterpolationDelay(0);
        display.setInterpolationDuration(interval);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, bob, 0.0f),
                new Quaternionf().rotationY(yawRad),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
        ));
    }

    private int keyframeInterval(Animation animation) {
        double speed = Math.max(1.0, animation.getRotationSpeed());
        return Math.max(1, Math.min(4, (int) (90.0 / speed)));
    }

    @Override
    public void remove(String cacheName) {
        ItemDisplay display = itemDisplays.remove(cacheName);
        if (display != null) display.remove();

        TextDisplay text = texts.remove(cacheName);
        if (text != null) text.remove();
    }
}
