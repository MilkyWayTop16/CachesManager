package org.gw.cachesmanager.managers;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerQuitEvent;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationsManager implements Listener {
    private final CachesManager plugin;
    private final HologramManager hologramManager;
    private final File animationsFile;
    private FileConfiguration animationsConfig;
    @Getter
    private final Map<String, Animation> animations;
    private final List<String> animationOrder;

    private final Map<String, Item> phantomItems = new HashMap<>();
    private final Map<String, ArmorStand> itemHolograms = new HashMap<>();
    private final Map<String, BukkitTask> animationTasks = new HashMap<>();
    private final Map<UUID, ItemStack> pendingLoot = new ConcurrentHashMap<>();

    public AnimationsManager(CachesManager plugin, HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
        this.animationsFile = new File(plugin.getDataFolder(), "animations.yml");
        this.animations = new HashMap<>();
        this.animationOrder = new ArrayList<>();
        loadAnimations();
    }

    public void loadAnimations() {
        animations.clear();
        animationOrder.clear();
        if (!animationsFile.exists()) {
            plugin.saveResource("animations.yml", false);
            plugin.log("Создан файл animations.yml по умолчанию");
        }
        animationsConfig = YamlConfiguration.loadConfiguration(animationsFile);

        ConfigurationSection animationsSection = animationsConfig.getConfigurationSection("animations");
        if (animationsSection == null) {
            plugin.log("Раздел animations не найден в конфиге");
            return;
        }

        int count = 0;
        for (String key : animationsSection.getKeys(false)) {
            List<SoundEntry> delaySounds = parseSounds("animations." + key + ".delay.sounds");
            List<SoundEntry> itemSounds = parseSounds("animations." + key + ".item.sounds");
            List<SoundEntry> finalSounds = parseSounds("animations." + key + ".final.sounds");

            List<ParticleEntry> delayParticles = parseParticles("animations." + key + ".delay.particles");
            List<ParticleEntry> itemParticles = parseParticles("animations." + key + ".item.particles");
            List<ParticleEntry> finalParticles = parseParticles("animations." + key + ".final.particles");

            String ambientColorStr = animationsConfig.getString("animations." + key + ".ambient.particles.color", "#FFFFFF");
            Color ambientColor = Color.fromRGB(
                    Integer.parseInt(ambientColorStr.substring(1, 3), 16),
                    Integer.parseInt(ambientColorStr.substring(3, 5), 16),
                    Integer.parseInt(ambientColorStr.substring(5, 7), 16)
            );
            float ambientSize = (float) animationsConfig.getDouble("animations." + key + ".ambient.particles.size", 1.0);
            String ambientShape = animationsConfig.getString("animations." + key + ".ambient.particles.shape", "box").toLowerCase();
            double ambientRadius = animationsConfig.getDouble("animations." + key + ".ambient.particles.radius", 0.6);

            FireworkEffect.Type fwType = FireworkEffect.Type.BALL_LARGE;
            List<Color> fwColors = new ArrayList<>();
            List<Color> fwFadeColors = new ArrayList<>();
            boolean fwTrail = true;
            boolean fwFlicker = true;

            if ("firework".equals(key)) {
                String typeStr = animationsConfig.getString("animations." + key + ".explosion.type", "BALL_LARGE").toUpperCase();
                try { fwType = FireworkEffect.Type.valueOf(typeStr); } catch (Exception ignored) {}
                List<String> colorList = animationsConfig.getStringList("animations." + key + ".explosion.colors");
                for (String c : colorList) {
                    try { fwColors.add(Color.fromRGB(Integer.parseInt(c.substring(1,3),16), Integer.parseInt(c.substring(3,5),16), Integer.parseInt(c.substring(5,7),16))); } catch (Exception ignored) {}
                }
                List<String> fadeList = animationsConfig.getStringList("animations." + key + ".explosion.fade-colors");
                for (String c : fadeList) {
                    try { fwFadeColors.add(Color.fromRGB(Integer.parseInt(c.substring(1,3),16), Integer.parseInt(c.substring(3,5),16), Integer.parseInt(c.substring(5,7),16))); } catch (Exception ignored) {}
                }
                fwTrail = animationsConfig.getBoolean("animations." + key + ".explosion.trail", true);
                fwFlicker = animationsConfig.getBoolean("animations." + key + ".explosion.flicker", true);
            }

            Animation animation = new Animation(
                    key,
                    animationsConfig.getString("animations." + key + ".name", key),
                    delayParticles, delaySounds, animationsConfig.getInt("animations." + key + ".delay.duration", 60),
                    itemParticles, itemSounds, animationsConfig.getInt("animations." + key + ".item.duration", 90),
                    animationsConfig.getDouble("animations." + key + ".item.height", 1.3),
                    animationsConfig.getDouble("animations." + key + ".item.rotation-speed", 5.0),
                    finalParticles, finalSounds,
                    Particle.REDSTONE,
                    animationsConfig.getInt("animations." + key + ".ambient.particles.amount", 10),
                    animationsConfig.getDouble("animations." + key + ".ambient.particles.offset-x", 0.5),
                    animationsConfig.getDouble("animations." + key + ".ambient.particles.offset-y", 0.1),
                    animationsConfig.getDouble("animations." + key + ".ambient.particles.offset-z", 0.5),
                    animationsConfig.getDouble("animations." + key + ".ambient.particles.speed", 0.0),
                    animationsConfig.getInt("animations." + key + ".ambient.interval", 4),
                    ambientColor, ambientSize, ambientShape, ambientRadius,
                    fwType, fwColors, fwFadeColors, fwTrail, fwFlicker,
                    0.0, 2, 45, 20
            );
            animations.put(key, animation);
            animationOrder.add(key);
            count++;
        }

        plugin.log("Загружено " + count + " анимаций");
    }

    private List<ParticleEntry> parseParticles(String path) {
        List<ParticleEntry> list = new ArrayList<>();
        if (animationsConfig.isList(path)) {
            for (Map<?, ?> map : animationsConfig.getMapList(path)) {
                try {
                    list.add(new ParticleEntry(
                            Particle.valueOf((String) map.get("type")),
                            map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 25,
                            map.containsKey("offset-x") ? ((Number) map.get("offset-x")).doubleValue() : 0.3,
                            map.containsKey("offset-y") ? ((Number) map.get("offset-y")).doubleValue() : 0.3,
                            map.containsKey("offset-z") ? ((Number) map.get("offset-z")).doubleValue() : 0.3,
                            map.containsKey("speed") ? ((Number) map.get("speed")).doubleValue() : 0.1
                    ));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private List<SoundEntry> parseSounds(String path) {
        List<SoundEntry> list = new ArrayList<>();
        if (animationsConfig.isList(path)) {
            for (Map<?, ?> map : animationsConfig.getMapList(path)) {
                String type = (String) map.get("type");
                double volume = map.containsKey("volume") ? (Double) map.get("volume") : 1.0;
                double pitch = map.containsKey("pitch") ? (Double) map.get("pitch") : 1.0;
                try {
                    list.add(new SoundEntry(Sound.valueOf(type), (float) volume, (float) pitch));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private void spawnParticles(Location loc, List<ParticleEntry> particles, ItemStack itemData) {
        if (particles == null || loc == null || loc.getWorld() == null) return;
        for (ParticleEntry p : particles) {
            spawnParticleSafely(loc.getWorld(), p.type, loc, p.amount, p.offsetX, p.offsetY, p.offsetZ, p.speed, itemData, null, 0);
        }
    }

    private void playSounds(Location loc, List<SoundEntry> sounds) {
        if (sounds == null || loc == null || loc.getWorld() == null) return;
        for (SoundEntry entry : sounds) {
            loc.getWorld().getNearbyPlayers(loc, 10).forEach(p ->
                    p.playSound(loc, entry.sound, entry.volume, entry.pitch)
            );
        }
    }

    public void startAnimationTask(String cacheName, Player player, ItemStack item, Location baseLocation, Animation animation) {
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;

        if ("firework".equals(animation.getKey())) {
            startFireworkDelayPhase(cacheName, player, item, baseLocation, animation, cache);
        } else {
            Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
            Location itemLoc = baseLocation.clone().add(0.5, animation.getItemHeight() + 0.1, 0.5);
            startDelayPhase(cacheName, player, item, delayLoc, itemLoc, baseLocation, animation, cache);
        }
    }

    private void startDelayPhase(String cacheName, Player player, ItemStack item, Location delayLoc,
                                 Location itemLoc, Location baseLocation, Animation animation, CacheManager.Cache cache) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getDelayDuration()) {
                    cancel();
                    startItemPhase(cacheName, player, item, baseLocation, itemLoc, animation, cache);
                    return;
                }
                if (ticks % 8 == 0) {
                    playSounds(delayLoc, animation.getDelaySounds());
                    spawnParticles(delayLoc, animation.getDelayParticles(), null);
                }
                if (ticks % animation.getAmbientInterval() == 0) {
                    spawnAmbientParticles(baseLocation, animation);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startItemPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                Location itemLoc, Animation animation, CacheManager.Cache cache) {
        spawnPhantomItem(cacheName, itemLoc, item);
        Item phantom = phantomItems.get(cacheName);
        if (phantom == null || phantom.isDead()) {
            finishAnimation(cacheName, player, item, baseLocation, itemLoc, animation, cache);
            return;
        }

        createItemHologram(cacheName, itemLoc.clone().add(0, 0.5, 0), item);

        playSounds(itemLoc, animation.getItemSounds());
        spawnParticles(itemLoc, animation.getItemParticles(), item);

        phantom.setGravity(false);
        phantom.setVelocity(new Vector(0, 0, 0));

        final Location reusableLoc = itemLoc.clone();
        final float[] yaw = {itemLoc.getYaw()};

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getItemDuration()) {
                    cancel();
                    finishAnimation(cacheName, player, item, baseLocation, reusableLoc, animation, cache);
                    return;
                }
                if (ticks % animation.getAmbientInterval() == 0) {
                    spawnAmbientParticles(baseLocation, animation);
                }
                yaw[0] += (float) animation.getRotationSpeed();
                reusableLoc.setYaw(yaw[0]);

                if (ticks % 3 == 0) {
                    phantom.teleport(reusableLoc);
                    ArmorStand h = itemHolograms.get(cacheName);
                    if (h != null && !h.isDead()) {
                        h.teleport(reusableLoc.clone().add(0, 0.5, 0));
                    }
                }
                if (ticks % 3 == 0 && item.getType() != Material.AIR) {
                    spawnParticles(reusableLoc, animation.getItemParticles(), item);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        animationTasks.put(cacheName, task);
    }

    private void finishAnimation(String cacheName, Player player, ItemStack item, Location baseLocation,
                                 Location itemLoc, Animation animation, CacheManager.Cache cache) {
        removePhantomItem(cacheName);
        removeItemHologram(cacheName);

        playSounds(itemLoc, animation.getFinalSounds());
        spawnParticles(itemLoc, animation.getFinalParticles(), null);

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());

        if (player != null && player.isOnline()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                plugin.getConfigManager().executeActions(player, "cache.inventory-full", ph);
            } else {
                player.getInventory().addItem(item);
            }
        } else if (player != null) {
            pendingLoot.put(player.getUniqueId(), item);
        } else if (baseLocation != null && baseLocation.getWorld() != null) {
            baseLocation.getWorld().dropItemNaturally(baseLocation, item);
        }

        cache.setInUse(false);
        if (cache.isHologramEnabled() && cache.getLocation() != null) {
            hologramManager.createHologram(cacheName, cache.getLocation(), cache.getHologramText());
        }
    }

    public void playAnimation(Player player, String animationName, Location location, ItemStack item, String cacheName) {
        Animation animation = animations.get(animationName);
        if (animation == null) {
            plugin.log("Анимация " + animationName + " не найдена!");
            return;
        }
        animation.play(player, location, item, cacheName);
    }

    public String getNextAnimation(String currentAnimation) {
        if (animationOrder.isEmpty() || animationOrder.size() == 1) return null;
        int currentIndex = animationOrder.indexOf(currentAnimation);
        if (currentIndex == -1 || currentIndex == animationOrder.size() - 1) {
            return animationOrder.get(0);
        }
        return animationOrder.get(currentIndex + 1);
    }

    public String getPreviousAnimation(String currentAnimation) {
        if (animationOrder.isEmpty() || animationOrder.size() == 1) return null;
        int currentIndex = animationOrder.indexOf(currentAnimation);
        if (currentIndex <= 0) {
            return animationOrder.get(animationOrder.size() - 1);
        }
        return animationOrder.get(currentIndex - 1);
    }

    private void startFireworkDelayPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, CacheManager.Cache cache) {
        Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getDelayDuration()) {
                    cancel();
                    startFireworkFlight(cacheName, player, item, baseLocation, animation, cache);
                    return;
                }
                if (ticks % 6 == 0) {
                    playSounds(delayLoc, animation.getDelaySounds());
                    spawnParticles(delayLoc, animation.getDelayParticles(), null);
                }
                if (ticks % animation.getAmbientInterval() == 0) {
                    spawnAmbientParticles(baseLocation, animation);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startFireworkFlight(String cacheName, Player player, ItemStack item, Location baseLocation,
                                     Animation animation, CacheManager.Cache cache) {
        Location launchLoc = baseLocation.clone().add(0.5, 1.1, 0.5);

        Firework fw = launchLoc.getWorld().spawn(launchLoc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(0);

        FireworkEffect.Builder builder = FireworkEffect.builder().with(animation.getFireworkType());
        for (Color c : animation.getFireworkColors()) builder.withColor(c);
        for (Color c : animation.getFireworkFadeColors()) builder.withFade(c);
        if (animation.isFireworkTrail()) builder.withTrail();
        if (animation.isFireworkFlicker()) builder.withFlicker();

        meta.addEffect(builder.build());
        fw.setFireworkMeta(meta);
        fw.setVelocity(new Vector(0, 0.42, 0));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getFireworkFlightDuration() + 15) {
                    cancel();
                    return;
                }
                if (ticks % animation.getAmbientInterval() == 0) {
                    spawnAmbientParticles(baseLocation, animation);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fw.isDead()) fw.detonate();
                startPostExplosionDelay(cacheName, player, item, baseLocation, animation, cache);
            }
        }.runTaskLater(plugin, animation.getFireworkFlightDuration() + 1);
    }

    private void startPostExplosionDelay(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, CacheManager.Cache cache) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Location itemLoc = baseLocation.clone().add(0.5, 1.1, 0.5);
                startItemPhase(cacheName, player, item, baseLocation, itemLoc, animation, cache);
            }
        }.runTaskLater(plugin, animation.getPostExplosionDelay());
    }

    public void spawnPhantomItem(String cacheName, Location location, ItemStack item) {
        removePhantomItem(cacheName);
        if (location.getWorld() == null) return;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        Item phantomItem = location.getWorld().dropItem(location, item.clone());
        phantomItem.setPickupDelay(Integer.MAX_VALUE);
        phantomItem.setGravity(false);
        phantomItem.setCanMobPickup(false);
        phantomItem.setInvulnerable(true);
        phantomItems.put(cacheName, phantomItem);
    }

    public void createItemHologram(String cacheName, Location location, ItemStack item) {
        removeItemHologram(cacheName);
        if (location.getWorld() == null) return;

        ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.setVisible(false);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);
        itemHolograms.put(cacheName, hologram);

        try {
            WrappedChatComponent component;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String legacy = HexColors.translate(item.getItemMeta().getDisplayName().trim());
                component = WrappedChatComponent.fromLegacyText(legacy);
            } else {
                String translationKey = getTranslationKey(item);
                ChatColor rarityColor = getRarityColor(item.getType());
                String json = "{\"translate\":\"" + translationKey + "\",\"color\":\"" + rarityColor.name().toLowerCase() + "\",\"italic\":false}";
                component = WrappedChatComponent.fromJson(json);
            }

            List<WrappedDataValue> dataValues = new ArrayList<>();
            dataValues.add(new WrappedDataValue(
                    2,
                    WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                    Optional.of(component.getHandle())
            ));

            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.ENTITY_METADATA);

            packet.getIntegers().write(0, hologram.getEntityId());
            packet.getDataValueCollectionModifier().write(0, dataValues);

            for (Player p : location.getWorld().getPlayers()) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet);
            }
        } catch (Exception e) {
            String fallback;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                fallback = HexColors.translate(item.getItemMeta().getDisplayName().trim());
            } else {
                String translationKey = getTranslationKey(item);
                ChatColor rarityColor = getRarityColor(item.getType());
                fallback = rarityColor + translationKey.replace("item.minecraft.", "")
                        .replace("block.minecraft.", "")
                        .replace("_", " ");
            }
            hologram.setCustomName(fallback);
        }
    }

    private String getTranslationKey(ItemStack item) {
        String key = item.getType().getKey().getKey();
        String namespace = item.getType().getKey().getNamespace();
        return item.getType().isBlock() ? "block." + namespace + "." + key : "item." + namespace + "." + key;
    }

    private ChatColor getRarityColor(Material material) {
        if (material.name().startsWith("NETHERITE") ||
                material == Material.DRAGON_EGG ||
                material == Material.ENCHANTED_GOLDEN_APPLE ||
                material == Material.NETHER_STAR ||
                material == Material.END_CRYSTAL ||
                material == Material.BEACON) {
            return ChatColor.LIGHT_PURPLE;
        }
        if (material == Material.ELYTRA ||
                material == Material.HEART_OF_THE_SEA ||
                material == Material.TOTEM_OF_UNDYING ||
                material == Material.EXPERIENCE_BOTTLE ||
                material == Material.DRAGON_HEAD ||
                material == Material.CONDUIT ||
                material == Material.SHULKER_SHELL ||
                material == Material.NAUTILUS_SHELL) {
            return ChatColor.AQUA;
        }
        if (material == Material.GOLDEN_APPLE ||
                material == Material.GOLDEN_CARROT ||
                material == Material.ENCHANTED_BOOK ||
                material == Material.CROSSBOW ||
                material == Material.SHIELD ||
                material == Material.TRIDENT ||
                material == Material.BOW ||
                material == Material.FISHING_ROD ||
                material == Material.CARROT_ON_A_STICK ||
                material == Material.WARPED_FUNGUS_ON_A_STICK ||
                material == Material.SPECTRAL_ARROW ||
                material == Material.TIPPED_ARROW) {
            return ChatColor.YELLOW;
        }
        return ChatColor.WHITE;
    }

    public void removePhantomItem(String cacheName) {
        BukkitTask task = animationTasks.remove(cacheName);
        if (task != null) task.cancel();

        Item phantomItem = phantomItems.remove(cacheName);
        if (phantomItem != null && !phantomItem.isDead() && phantomItem.isValid() && phantomItem.getWorld() != null) {
            phantomItem.remove();
        }

        removeItemHologram(cacheName);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (phantomItem != null && !phantomItem.isDead() && phantomItem.isValid() && phantomItem.getWorld() != null) {
                    phantomItem.remove();
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    public void removeItemHologram(String cacheName) {
        ArmorStand hologram = itemHolograms.remove(cacheName);
        if (hologram != null && !hologram.isDead() && hologram.isValid() && hologram.getWorld() != null) {
            hologram.setCustomName(null);
            hologram.setCustomNameVisible(false);
            hologram.remove();
        }
    }

    public void forceFinishAnimationForPlayer(Player player) {
        Set<String> activeCaches = new HashSet<>();
        activeCaches.addAll(phantomItems.keySet());
        activeCaches.addAll(itemHolograms.keySet());
        activeCaches.addAll(animationTasks.keySet());

        for (String cacheName : new ArrayList<>(activeCaches)) {
            BukkitTask task = animationTasks.remove(cacheName);
            if (task != null) task.cancel();

            CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);
            }

            ItemStack item = pendingLoot.remove(player.getUniqueId());
            if (item != null) {
                if (player.isOnline() && player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item.clone());
                } else if (cache != null && cache.getLocation() != null) {
                    cache.getLocation().getWorld().dropItemNaturally(cache.getLocation().clone().add(0.5, 0.5, 0.5), item.clone());
                } else if (player.isOnline()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                }
            }

            removePhantomItem(cacheName);
            removeItemHologram(cacheName);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupNearbyGhosts(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    public void givePendingLootToPlayer(Player player) {
        ItemStack item = pendingLoot.remove(player.getUniqueId());
        if (item == null) return;

        ItemStack safeItem = item.clone();

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), safeItem);
        } else {
            player.getInventory().addItem(safeItem);
        }
    }

    public void removeAnimationArtifacts(String cacheName) {
        removePhantomItem(cacheName);
        removeItemHologram(cacheName);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();

        for (Map.Entry<String, Item> entry : new HashMap<>(phantomItems).entrySet()) {
            String cacheName = entry.getKey();
            Item phantomItem = entry.getValue();

            if (phantomItem == null || phantomItem.isDead() || phantomItem.getWorld() == null) continue;
            if (!phantomItem.getWorld().equals(world)) continue;

            try {
                Location loc = phantomItem.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        removePhantomItem(cacheName);
                    }
                }
            } catch (Exception ignored) {
                removePhantomItem(cacheName);
            }
        }

        for (Map.Entry<String, ArmorStand> entry : new HashMap<>(itemHolograms).entrySet()) {
            String cacheName = entry.getKey();
            ArmorStand hologram = entry.getValue();

            if (hologram == null || hologram.isDead() || hologram.getWorld() == null) continue;
            if (!hologram.getWorld().equals(world)) continue;

            try {
                Location loc = hologram.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        removeItemHologram(cacheName);
                    }
                }
            } catch (Exception ignored) {
                removeItemHologram(cacheName);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        forceFinishAnimationForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        givePendingLootToPlayer(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupNearbyGhosts(player);
            }
        }.runTaskLater(plugin, 10L);
    }

    private void spawnParticleSafely(World world, Particle particle, Location loc, int amount,
                                     double offsetX, double offsetY, double offsetZ, double speed,
                                     ItemStack data, Color color, float size) {
        if (world == null || loc == null) return;
        if (particle == Particle.REDSTONE && color != null) {
            world.spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, speed,
                    new Particle.DustOptions(color, size));
        } else if (particle.getDataType() == ItemStack.class && data != null) {
            world.spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, speed, data);
        } else {
            world.spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, speed);
        }
    }

    private void spawnAmbientParticles(Location baseLocation, Animation animation) {
        if (baseLocation.getWorld() == null) return;
        World w = baseLocation.getWorld();
        Location center = baseLocation.clone().add(0.5, 0.1, 0.5);

        if ("circle".equals(animation.getAmbientShape())) {
            double r = animation.getAmbientRadius();
            int amt = animation.getAmbientParticleAmount();
            double speed = animation.getAmbientParticleSpeed();
            Color col = animation.getAmbientColor();
            float sz = animation.getAmbientSize();

            for (int i = 0; i < amt; i++) {
                double a = 2 * Math.PI * i / amt;
                w.spawnParticle(Particle.REDSTONE,
                        center.getX() + Math.cos(a) * r,
                        center.getY(),
                        center.getZ() + Math.sin(a) * r,
                        1, 0, 0, 0, speed, new Particle.DustOptions(col, sz));
            }
        } else {
            spawnParticleSafely(w, Particle.REDSTONE, center,
                    animation.getAmbientParticleAmount(),
                    animation.getAmbientOffsetX(), animation.getAmbientOffsetY(), animation.getAmbientOffsetZ(),
                    animation.getAmbientParticleSpeed(), null, animation.getAmbientColor(), animation.getAmbientSize());
        }
    }

    public void clearAllAnimations() {
        for (String cacheName : new ArrayList<>(animationTasks.keySet())) {
            BukkitTask task = animationTasks.remove(cacheName);
            if (task != null) task.cancel();
        }
        animationTasks.clear();

        for (String cacheName : new ArrayList<>(phantomItems.keySet())) {
            removePhantomItem(cacheName);
        }
        for (String cacheName : new ArrayList<>(itemHolograms.keySet())) {
            removeItemHologram(cacheName);
        }

        phantomItems.clear();
        itemHolograms.clear();
    }

    public void reloadAnimations() {
        clearAllAnimations();
        loadAnimations();
    }

    private void cleanupNearbyGhosts(Player player) {
        if (player == null || player.getWorld() == null) return;
        Location center = player.getLocation();
        for (Entity entity : center.getWorld().getNearbyEntities(center, 50, 50, 50)) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                if (!stand.isVisible() || stand.getCustomName() == null) {
                    stand.remove();
                }
            } else if (entity instanceof Item) {
                Item item = (Item) entity;
                if (item.getPickupDelay() > 1000) {
                    item.remove();
                }
            }
        }
    }

    private static class SoundEntry {
        final Sound sound;
        final float volume;
        final float pitch;

        SoundEntry(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static class ParticleEntry {
        final Particle type;
        final int amount;
        final double offsetX, offsetY, offsetZ, speed;
        ParticleEntry(Particle type, int amount, double offsetX, double offsetY, double offsetZ, double speed) {
            this.type = type; this.amount = amount;
            this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ; this.speed = speed;
        }
    }

    @Getter
    public class Animation {
        private final String key;
        private final String name;
        private final List<ParticleEntry> delayParticles;
        private final List<SoundEntry> delaySounds;
        private final int delayDuration;
        private final List<ParticleEntry> itemParticles;
        private final List<SoundEntry> itemSounds;
        private final int itemDuration;
        private final double itemHeight;
        private final double rotationSpeed;
        private final List<ParticleEntry> finalParticles;
        private final List<SoundEntry> finalSounds;
        private final Particle ambientParticleType;
        private final int ambientParticleAmount;
        private final double ambientOffsetX, ambientOffsetY, ambientOffsetZ;
        private final double ambientParticleSpeed;
        private final int ambientInterval;
        private final Color ambientColor;
        private final float ambientSize;
        private final String ambientShape;
        private final double ambientRadius;
        private final FireworkEffect.Type fireworkType;
        private final List<Color> fireworkColors;
        private final List<Color> fireworkFadeColors;
        private final boolean fireworkTrail;
        private final boolean fireworkFlicker;
        private final double fireworkHeight;
        private final int fireworkPower;
        private final int fireworkFlightDuration;
        private final int postExplosionDelay;

        public Animation(String key, String name,
                         List<ParticleEntry> delayParticles, List<SoundEntry> delaySounds, int delayDuration,
                         List<ParticleEntry> itemParticles, List<SoundEntry> itemSounds, int itemDuration, double itemHeight, double rotationSpeed,
                         List<ParticleEntry> finalParticles, List<SoundEntry> finalSounds,
                         Particle ambientParticleType, int ambientParticleAmount,
                         double ambientOffsetX, double ambientOffsetY, double ambientOffsetZ,
                         double ambientParticleSpeed, int ambientInterval,
                         Color ambientColor, float ambientSize, String ambientShape, double ambientRadius,
                         FireworkEffect.Type fireworkType, List<Color> fireworkColors, List<Color> fireworkFadeColors,
                         boolean fireworkTrail, boolean fireworkFlicker,
                         double fireworkHeight, int fireworkPower, int fireworkFlightDuration, int postExplosionDelay) {
            this.key = key;
            this.name = name;
            this.delayParticles = delayParticles != null ? delayParticles : new ArrayList<>();
            this.delaySounds = delaySounds != null ? delaySounds : new ArrayList<>();
            this.delayDuration = delayDuration;
            this.itemParticles = itemParticles != null ? itemParticles : new ArrayList<>();
            this.itemSounds = itemSounds != null ? itemSounds : new ArrayList<>();
            this.itemDuration = itemDuration;
            this.itemHeight = itemHeight;
            this.rotationSpeed = rotationSpeed;
            this.finalParticles = finalParticles != null ? finalParticles : new ArrayList<>();
            this.finalSounds = finalSounds != null ? finalSounds : new ArrayList<>();
            this.ambientParticleType = ambientParticleType;
            this.ambientParticleAmount = ambientParticleAmount;
            this.ambientOffsetX = ambientOffsetX;
            this.ambientOffsetY = ambientOffsetY;
            this.ambientOffsetZ = ambientOffsetZ;
            this.ambientParticleSpeed = ambientParticleSpeed;
            this.ambientInterval = ambientInterval;
            this.ambientColor = ambientColor;
            this.ambientSize = ambientSize;
            this.ambientShape = ambientShape;
            this.ambientRadius = ambientRadius;
            this.fireworkType = fireworkType;
            this.fireworkColors = fireworkColors != null ? fireworkColors : new ArrayList<>();
            this.fireworkFadeColors = fireworkFadeColors != null ? fireworkFadeColors : new ArrayList<>();
            this.fireworkTrail = fireworkTrail;
            this.fireworkFlicker = fireworkFlicker;
            this.fireworkHeight = fireworkHeight;
            this.fireworkPower = fireworkPower;
            this.fireworkFlightDuration = fireworkFlightDuration;
            this.postExplosionDelay = postExplosionDelay;
        }

        public void play(Player player, Location location, ItemStack item, String cacheName) {
            plugin.getAnimationsManager().startAnimationTask(cacheName, player, item, location, this);
        }
    }
}