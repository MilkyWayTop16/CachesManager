package org.gw.cachesmanager.animations.view;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.Animation;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModernAnimationView implements AnimationView {

    private final CachesManager plugin;
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> texts = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> mounts = new ConcurrentHashMap<>();
    private final Map<String, Location> baseLocations = new ConcurrentHashMap<>();

    public ModernAnimationView(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void spawn(String cacheName, Location location, ItemStack item, Animation animation) {
        if (location.getWorld() == null) return;

        Location baseLoc = location.getBlock().getLocation();
        baseLocations.put(cacheName, baseLoc.clone());

        Location mountLoc = baseLoc.clone().add(0.5, 1.0, 0.5);
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
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "ghost"), PersistentDataType.STRING, "true");
        items.put(cacheName, entity);

        mount.addPassenger(entity);

        Location textLoc = baseLoc.clone().add(0.5, 1.75, 0.5);
        TextDisplay text = (TextDisplay) location.getWorld().spawnEntity(textLoc, EntityType.TEXT_DISPLAY);

        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? HexColors.translate(item.getItemMeta().getDisplayName()) : item.getType().name().replace("_", " ");
        if (plugin.getConfigManager().isTrimHologramItemName()) {
            displayName = displayName.replaceAll("^\\s+", "");
        }

        text.setText(displayName);
        text.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        text.setPersistent(false);
        text.getPersistentDataContainer().set(new NamespacedKey(plugin, "ghost"), PersistentDataType.STRING, "true");
        texts.put(cacheName, text);
    }

    @Override
    public void update(String cacheName, int ticks, Animation animation) {
        ArmorStand mount = mounts.get(cacheName);
        Location baseLoc = baseLocations.get(cacheName);
        if (mount == null || mount.isDead() || baseLoc == null) return;

        double height = Math.sin(ticks * 0.1) * 0.15;
        Location loc = baseLoc.clone().add(0.5, 1.0 + height, 0.5);
        loc.setYaw(ticks * (float) animation.getRotationSpeed());

        mount.teleport(loc);
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