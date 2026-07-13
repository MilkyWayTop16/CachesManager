package org.gw.cachesmanager.animations;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.view.AnimationView;
import org.gw.cachesmanager.animations.view.LegacyAnimationView;
import org.gw.cachesmanager.caches.Cache;
import org.gw.cachesmanager.opening.CacheOpening;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationExecutor {
    private final CachesManager plugin;
    private final AnimationRegistry registry;
    private final AnimationView animationView;
    private final Particle dustParticle;

    private final Map<UUID, ItemStack> pendingLoot = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> activeAnimationLoot = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerActiveCaches = new ConcurrentHashMap<>();

    private final Map<String, Vector[]> circleMathCache = new ConcurrentHashMap<>();
    private final Map<String, Particle.DustOptions> dustOptionsCache = new ConcurrentHashMap<>();

    private final Map<String, CacheOpening> activeOpenings = new ConcurrentHashMap<>();

    private BukkitTask centralTask;
    private final java.util.Set<String> activeAnimations = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> orphanedAnimations = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, BukkitTask> phaseTasks = new ConcurrentHashMap<>();

    public AnimationExecutor(CachesManager plugin, AnimationRegistry registry, AnimationView animationView) {
        this.plugin = plugin;
        this.registry = registry;
        this.animationView = animationView;
        Particle dp;
        try {
            dp = Particle.valueOf("DUST");
        } catch (Exception e) {
            dp = Particle.valueOf("REDSTONE");
        }
        this.dustParticle = dp;
        startCentralTask();
    }

    private void startCentralTask() {
        if (centralTask != null) return;
        centralTask = new BukkitRunnable() {
            @Override
            public void run() {
                activeAnimations.removeIf(cacheName -> {
                    Cache cache = plugin.getCacheManager().getCache(cacheName);
                    return cache == null || !cache.isInUse();
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void registerActiveAnimation(String cacheName) {
        activeAnimations.add(cacheName);
    }

    private void unregisterActiveAnimation(String cacheName) {
        activeAnimations.remove(cacheName);
    }

    private void trackPhaseTask(String cacheName, BukkitTask task) {
        BukkitTask old = phaseTasks.put(cacheName, task);
        if (old != null) old.cancel();
    }

    public void startAnimation(String cacheName, Player player, ItemStack item, Location baseLocation, Animation animation, CacheOpening opening) {
        var cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) {
            plugin.error("Не удалось запустить анимацию, так как тайник &#FB8808" + cacheName + " &fне найден в базе...");
            return;
        }

        activeAnimationLoot.put(cacheName, item);
        playerActiveCaches.put(player.getUniqueId(), cacheName);

        if (opening != null) {
            activeOpenings.put(cacheName, opening);
        }

        plugin.log("Инициализация запуска анимации &#ffff00" + animation.getKey() + " &fдля тайника &#ffff00" + cacheName);

        if ("firework".equals(animation.getKey())) {
            startFireworkDelayPhase(cacheName, player, item, baseLocation, animation, cache, opening);
        } else {
            Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
            Location itemLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
            startDelayPhase(cacheName, player, item, delayLoc, itemLoc, baseLocation, animation, cache, opening);
        }
    }

    private void startDelayPhase(String cacheName, Player player, ItemStack item, Location delayLoc,
                                 Location itemLoc, Location baseLocation, Animation animation, Cache cache, CacheOpening opening) {
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getDelayDuration()) {
                    cancel();
                    startItemPhase(cacheName, player, item, baseLocation, itemLoc, animation, cache, opening);
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
        trackPhaseTask(cacheName, task);
        registerActiveAnimation(cacheName);
    }

    private void startItemPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                Location itemLoc, Animation animation, Cache cache, CacheOpening opening) {
        animationView.spawn(cacheName, itemLoc, item, animation);

        playSounds(itemLoc, animation.getItemSounds());
        spawnParticles(itemLoc, animation.getItemParticles(), item);

        final Location reusableLoc = itemLoc.clone();

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getItemDuration()) {
                    cancel();
                    finishAnimation(cacheName, player, item, baseLocation, reusableLoc, animation, cache, opening);
                    return;
                }
                if (ticks % animation.getAmbientInterval() == 0) {
                    spawnAmbientParticles(baseLocation, animation);
                }

                animationView.update(cacheName, ticks, animation);

                if (ticks % 3 == 0 && item.getType() != Material.AIR) {
                    spawnParticles(reusableLoc, animation.getItemParticles(), item);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        trackPhaseTask(cacheName, task);
        registerActiveAnimation(cacheName);
    }

    private void finishAnimation(String cacheName, Player player, ItemStack item, Location baseLocation,
                                 Location itemLoc, Animation animation, Cache cache, CacheOpening opening) {
        activeAnimationLoot.remove(cacheName);
        if (player != null) playerActiveCaches.remove(player.getUniqueId());
        phaseTasks.remove(cacheName);
        unregisterActiveAnimation(cacheName);
        CacheOpening tracked = activeOpenings.remove(cacheName);

        animationView.remove(cacheName);

        playSounds(itemLoc, animation.getFinalSounds());
        spawnParticles(itemLoc, animation.getFinalParticles(), null);

        CacheOpening effectiveOpening = opening != null ? opening : tracked;

        if (effectiveOpening != null) {
            Player effPlayer = effectiveOpening.getPlayer();
            if (effPlayer != null && !effPlayer.isOnline()) {
                ItemStack lootItem = effectiveOpening.getChosenItem();
                if (lootItem != null) {
                    pendingLoot.put(effPlayer.getUniqueId(), lootItem);
                }
                effectiveOpening.cancel();
            } else {
                effectiveOpening.finishVisual();
            }
        } else {
            if (player != null && player.isOnline()) {
                Map<String, String> ph = new HashMap<>();
                ph.put("name-cache", cache.getName());
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    plugin.getConfigManager().executeActions(player, "cache.inventory-full", ph);
                    plugin.log("Инвентарь игрока &#ffff00" + player.getName() + " &fзаполнен, награда из тайника &#ffff00" + cacheName + " &fвыброшена на землю...");
                } else {
                    player.getInventory().addItem(item);
                    plugin.log("Предмет из тайника &#ffff00" + cacheName + " &fуспешно выдан в инвентарь игрока &#ffff00" + player.getName());
                }
            } else if (player != null) {
                pendingLoot.put(player.getUniqueId(), item);
                plugin.log("Игрок &#ffff00" + player.getName() + " &fвышел из сети во время анимации тайника &#ffff00" + cacheName + " &f, награда сохранена в кэш ожидания...");
            } else {
                plugin.log("Анимация тайника &#ffff00" + cacheName + " &fзавершена, но игрок, открывший тайник не найден, так что предмет потерян...");
            }

            Cache liveCache = plugin.getCacheManager().getCache(cacheName);
            if (liveCache != null) {
                liveCache.setInUse(false);
                if (liveCache.isHologramEnabled() && liveCache.getLocation() != null && plugin.getHologramManager() != null) {
                    plugin.getHologramManager().createHologram(cacheName, liveCache.getLocation(), liveCache.getHologramLines());
                }
            }
        }
    }

    private void startFireworkDelayPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, Cache cache, CacheOpening opening) {
        Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= animation.getDelayDuration()) {
                    cancel();
                    startFireworkFlight(cacheName, player, item, baseLocation, animation, cache, opening);
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
        trackPhaseTask(cacheName, task);
        registerActiveAnimation(cacheName);
    }

    private void startFireworkFlight(String cacheName, Player player, ItemStack item, Location baseLocation,
                                     Animation animation, Cache cache, CacheOpening opening) {
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

        BukkitTask flightTask = new BukkitRunnable() {
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
        trackPhaseTask(cacheName, flightTask);
        registerActiveAnimation(cacheName);

        BukkitTask explosionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!fw.isDead()) fw.detonate();
                startPostExplosionDelay(cacheName, player, item, baseLocation, animation, cache, opening);
            }
        }.runTaskLater(plugin, animation.getFireworkFlightDuration() + 1);
        trackPhaseTask(cacheName, explosionTask);
        registerActiveAnimation(cacheName);
    }

    private void startPostExplosionDelay(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, Cache cache, CacheOpening opening) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Location itemLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
                startItemPhase(cacheName, player, item, baseLocation, itemLoc, animation, cache, opening);
            }
        }.runTaskLater(plugin, animation.getPostExplosionDelay());
        trackPhaseTask(cacheName, task);
        registerActiveAnimation(cacheName);
    }

    public void forceFinishAnimationForPlayer(Player player) {
        String cacheName = playerActiveCaches.remove(player.getUniqueId());
        if (cacheName != null) {
            orphanedAnimations.remove(cacheName);
            BukkitTask task = phaseTasks.remove(cacheName);
            if (task != null) task.cancel();
            unregisterActiveAnimation(cacheName);

            CacheOpening opening = activeOpenings.remove(cacheName);
            if (opening != null) {
                if (player != null && !player.isOnline()) {
                    ItemStack lootItem = opening.getChosenItem();
                    if (lootItem != null) {
                        pendingLoot.put(player.getUniqueId(), lootItem);
                    }
                    opening.cancel();
                } else {
                    opening.finishVisual();
                }
            } else {
                var cache = plugin.getCacheManager().getCache(cacheName);
                if (cache != null) {
                    cache.setInUse(false);
                }

                ItemStack removedItem = activeAnimationLoot.remove(cacheName);
                if (removedItem != null && player != null) {
                    if (player.isOnline()) {
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(removedItem.clone());
                        } else {
                            player.getWorld().dropItemNaturally(player.getLocation(), removedItem.clone());
                        }
                    } else {
                        pendingLoot.put(player.getUniqueId(), removedItem);
                    }
                }

                if (cache != null && cache.isHologramEnabled() && cache.getLocation() != null && plugin.getHologramManager() != null) {
                    plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramLines());
                }
            }

            animationView.remove(cacheName);
            plugin.log("Анимация тайника &#ffff00" + cacheName + " &fпринудительно остановлена для игрока &#ffff00" + player.getName());
        }
    }

    public void handlePlayerLeftAnimationEvent(Player player) {
        String cacheName = playerActiveCaches.remove(player.getUniqueId());
        if (cacheName == null) return;

        if (!plugin.getConfigManager().getMainConfig().isContinueAnimationIfPlayersNearby()) {
            forceFinishAnimationForPlayerInternal(cacheName, player);
            return;
        }

        org.gw.cachesmanager.caches.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null || cache.getLocation() == null) {
            forceFinishAnimationForPlayerInternal(cacheName, player);
            return;
        }

        boolean hasNearbyPlayers = hasPlayersNearLocation(cache.getLocation());

        if (hasNearbyPlayers) {
            makeAnimationOrphaned(cacheName, player.getUniqueId());
            plugin.log("Анимация тайника &#ffff00" + cacheName + " &fпродолжена для игроков, находящихся рядом");
        } else {
            forceFinishAnimationForPlayerInternal(cacheName, player);
        }
    }

    private boolean hasPlayersNearLocation(org.bukkit.Location location) {
        if (location == null) return false;

        int radius = plugin.getConfigManager().getMainConfig().getOrphanedAnimationRadius();
        double radiusSquared = radius * radius;

        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(location.getWorld()) &&
                p.getLocation().distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void forceFinishAnimationForPlayerInternal(String cacheName, Player player) {
        BukkitTask task = phaseTasks.remove(cacheName);
        if (task != null) task.cancel();
        unregisterActiveAnimation(cacheName);

        CacheOpening opening = activeOpenings.remove(cacheName);
        if (opening != null) {
            if (player != null && !player.isOnline()) {
                ItemStack lootItem = opening.getChosenItem();
                if (lootItem != null) {
                    pendingLoot.put(player.getUniqueId(), lootItem);
                }
                opening.cancel();
            } else {
                opening.finishVisual();
            }
        } else {
            var cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);
            }

            ItemStack removedItem = activeAnimationLoot.remove(cacheName);
            if (removedItem != null && player != null) {
                if (player.isOnline()) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(removedItem.clone());
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), removedItem.clone());
                    }
                } else {
                    pendingLoot.put(player.getUniqueId(), removedItem);
                }
            }

            if (cache != null && cache.isHologramEnabled() && cache.getLocation() != null && plugin.getHologramManager() != null) {
                plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramLines());
            }
        }

        animationView.remove(cacheName);
        if (player != null) {
            plugin.log("Анимация тайника &#ffff00" + cacheName + " &fпринудительно остановлена для игрока &#ffff00" + player.getName());
        }
    }

    public void givePendingLootToPlayer(Player player) {
        ItemStack item = pendingLoot.remove(player.getUniqueId());
        if (item == null) return;
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        } else {
            player.getInventory().addItem(item.clone());
        }
        plugin.log("Отложенная награда тайника успешно выдана вернувшемуся игроку &#ffff00" + player.getName());
    }

    public void removeAnimationArtifacts(String cacheName) {
        animationView.remove(cacheName);
    }

    private void forceRemoveAnimation(String cacheName) {
        BukkitTask task = phaseTasks.remove(cacheName);
        if (task != null) task.cancel();
        unregisterActiveAnimation(cacheName);

        CacheOpening opening = activeOpenings.remove(cacheName);
        if (opening != null) {
            Player p = opening.getPlayer();
            if (p != null && !p.isOnline()) {
                ItemStack it = opening.getChosenItem();
                if (it != null) {
                    pendingLoot.put(p.getUniqueId(), it);
                }
                opening.cancel();
            } else {
                opening.finishVisual();
            }
        } else {
            animationView.remove(cacheName);

            Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);

                ItemStack item = activeAnimationLoot.remove(cacheName);
                if (item != null) {
                    Player owner = null;
                    for (Map.Entry<UUID, String> entry : playerActiveCaches.entrySet()) {
                        if (entry.getValue().equals(cacheName)) {
                            owner = plugin.getServer().getPlayer(entry.getKey());
                            break;
                        }
                    }

                    if (owner != null && owner.isOnline()) {
                        if (owner.getInventory().firstEmpty() != -1) {
                            owner.getInventory().addItem(item.clone());
                        } else {
                            owner.getWorld().dropItemNaturally(owner.getLocation(), item.clone());
                        }
                    } else if (owner != null) {
                        pendingLoot.put(owner.getUniqueId(), item);
                    } else {
                        plugin.error("Предмет из тайника &#ffff00" + cacheName + " &fпотерян, так как владелец не определён...");
                    }
                }

                playerActiveCaches.values().removeIf(name -> name.equals(cacheName));

                if (cache.isHologramEnabled() && cache.getLocation() != null && plugin.getHologramManager() != null) {
                    plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramLines());
                }
            }
        }
    }

    public void handleChunkUnload(World world, int chunkX, int chunkZ) {

        if (animationView instanceof LegacyAnimationView legacyView) {
            handleLegacyChunkUnload(legacyView, world, chunkX, chunkZ);
        }


        for (String cacheName : new ArrayList<>(activeAnimationLoot.keySet())) {
            Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null && cache.getLocation() != null) {
                Location loc = cache.getLocation();
                if (loc.getWorld().equals(world) &&
                    (loc.getBlockX() >> 4) == chunkX &&
                    (loc.getBlockZ() >> 4) == chunkZ) {
                    CacheOpening opening = activeOpenings.remove(cacheName);
                    if (opening != null) {
                        Player p = opening.getPlayer();
                        if (p != null && !p.isOnline()) {
                            ItemStack it = opening.getChosenItem();
                            if (it != null) {
                                pendingLoot.put(p.getUniqueId(), it);
                            }
                            opening.cancel();
                        } else {
                            opening.finishVisual();
                        }
                    } else {
                        forceRemoveAnimation(cacheName);
                    }
                }
            }
        }
    }

    private void handleLegacyChunkUnload(LegacyAnimationView legacyView, World world, int chunkX, int chunkZ) {

        for (Map.Entry<String, Item> entry : new HashMap<>(legacyView.getPhantomItems()).entrySet()) {
            String cacheName = entry.getKey();
            Item phantomItem = entry.getValue();

            if (phantomItem == null || phantomItem.isDead() || phantomItem.getWorld() == null) continue;
            if (!phantomItem.getWorld().equals(world)) continue;

            try {
                Location loc = phantomItem.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        legacyView.remove(cacheName);
                    }
                }
            } catch (Exception ignored) {
                legacyView.remove(cacheName);
            }
        }

        for (Map.Entry<String, ArmorStand> entry : new HashMap<>(legacyView.getItemHolograms()).entrySet()) {
            String cacheName = entry.getKey();
            ArmorStand hologram = entry.getValue();

            if (hologram == null || hologram.isDead() || hologram.getWorld() == null) continue;
            if (!hologram.getWorld().equals(world)) continue;

            try {
                Location loc = hologram.getLocation();
                if (loc == null) continue;

                if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                    Cache cache = plugin.getCacheManager().getCache(cacheName);
                    if (cache == null || !cache.isInUse()) {
                        legacyView.remove(cacheName);
                    }
                }
            } catch (Exception ignored) {
                legacyView.remove(cacheName);
            }
        }
    }

    public void cleanupGhostEntities(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();


        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, 48, 48, 48)) {
            if (entity.getPersistentDataContainer().has(org.gw.cachesmanager.utils.CacheKeys.GHOST.getNamespacedKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                entity.remove();
            }
        }


        if (animationView instanceof LegacyAnimationView legacyView) {
            legacyView.getItemHolograms().values().removeIf(stand -> {
                if (stand != null && !stand.isDead() && stand.getWorld().equals(world) && stand.getLocation().distanceSquared(center) < 2304) {
                    stand.remove();
                    return true;
                }
                return false;
            });
        }
    }

    public boolean hasActiveAnimationForCache(String cacheName) {
        if (cacheName == null) return false;
        return activeOpenings.containsKey(cacheName)
                || activeAnimationLoot.containsKey(cacheName)
                || activeAnimations.contains(cacheName);
    }

    public boolean isPlayerInAnimation(org.bukkit.entity.Player player) {
        return player != null && playerActiveCaches.containsKey(player.getUniqueId());
    }

    public void makeAnimationOrphaned(String cacheName, java.util.UUID ownerId) {
        if (cacheName == null) return;

        orphanedAnimations.add(cacheName);
        if (ownerId != null) {
            playerActiveCaches.remove(ownerId);
        }
    }

    public void tryReattachOrphanedAnimation(org.bukkit.entity.Player player) {
        if (player == null) return;

        for (String cacheName : new java.util.ArrayList<>(orphanedAnimations)) {
            org.gw.cachesmanager.caches.Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache == null) continue;

            if (pendingLoot.containsKey(player.getUniqueId())) {
                orphanedAnimations.remove(cacheName);
                playerActiveCaches.put(player.getUniqueId(), cacheName);
                return;
            }
        }
    }

    public void clearAllAnimations() {
        orphanedAnimations.clear();

        if (centralTask != null) {
            centralTask.cancel();
            centralTask = null;
        }
        for (BukkitTask task : phaseTasks.values()) {
            if (task != null) task.cancel();
        }
        phaseTasks.clear();
        activeAnimations.clear();

        for (String cacheName : new ArrayList<>(activeOpenings.keySet())) {
            CacheOpening opening = activeOpenings.remove(cacheName);
            if (opening != null) {
                Player p = opening.getPlayer();
                if (p != null && !p.isOnline()) {
                    ItemStack it = opening.getChosenItem();
                    if (it != null) {
                        pendingLoot.put(p.getUniqueId(), it);
                    }
                    opening.cancel();
                } else {
                    opening.finishVisual();
                }
            }
            activeAnimationLoot.remove(cacheName);
            playerActiveCaches.values().removeIf(name -> name.equals(cacheName));
        }
        activeOpenings.clear();

        for (Map.Entry<String, ItemStack> entry : new ArrayList<>(activeAnimationLoot.entrySet())) {
            String cacheName = entry.getKey();
            ItemStack item = entry.getValue();

            Player owner = null;
            for (Map.Entry<UUID, String> playerEntry : playerActiveCaches.entrySet()) {
                if (playerEntry.getValue().equals(cacheName)) {
                    owner = plugin.getServer().getPlayer(playerEntry.getKey());
                    break;
                }
            }

            var cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);
            }

            if (item != null) {
                boolean given = false;

                if (owner != null && owner.isOnline()) {
                    if (owner.getInventory().firstEmpty() != -1) {
                        owner.getInventory().addItem(item.clone());
                        plugin.log("Предмет из тайника &#ffff00" + cacheName + " &fвыдан игроку &#ffff00" + owner.getName() + " &fпри принудительной очистке анимаций");
                        given = true;
                    } else {
                        owner.getWorld().dropItemNaturally(owner.getLocation(), item.clone());
                        plugin.log("Инвентарь игрока &#ffff00" + owner.getName() + " &fбыл полон, предмет из тайника &#ffff00" + cacheName + " &fвыброшен на землю при очистке анимаций...");
                        given = true;
                    }
                }

                if (!given) {
                    if (owner != null) {
                        pendingLoot.put(owner.getUniqueId(), item);
                    } else {
                        plugin.log("Предмет из тайника &#ffff00" + cacheName + " &fпотерян при очистке анимаций...");
                    }
                }
            }

            if (cache != null && cache.isHologramEnabled() && cache.getLocation() != null && plugin.getHologramManager() != null) {
                plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramLines());
            }
        }

        activeAnimationLoot.clear();
        playerActiveCaches.clear();
        circleMathCache.clear();
        dustOptionsCache.clear();

        if (plugin.getCacheManager() != null) {
            for (Cache c : plugin.getCacheManager().getCaches().values()) {
                if (c.isInUse()) {
                    c.setInUse(false);
                }
            }
        }

        plugin.log("Все активные анимационные процессы и таски плагина &#ffff00успешно &fочищены!");
    }

    private void playSounds(Location loc, List<Animation.SoundEntry> sounds) {
        if (sounds == null || sounds.isEmpty() || loc == null || loc.getWorld() == null) return;
        var players = loc.getWorld().getNearbyPlayers(loc, 12);
        if (players.isEmpty()) return;
        for (Animation.SoundEntry entry : sounds) {
            for (Player p : players) {
                p.playSound(loc, entry.sound(), entry.volume(), entry.pitch());
            }
        }
    }

    private void spawnParticles(Location loc, List<Animation.ParticleEntry> particles, ItemStack itemData) {
        if (particles == null || loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();

        if (world.getNearbyPlayers(loc, 32).isEmpty()) return;

        for (Animation.ParticleEntry p : particles) {
            spawnParticleSafely(world, p.type(), loc, p.amount(), p.offsetX(), p.offsetY(), p.offsetZ(), p.speed(), itemData, null, 0);
        }
    }

    private void spawnParticleSafely(World world, Particle particle, Location loc, int amount, double offsetX, double offsetY, double offsetZ, double speed, ItemStack data, Color color, float size) {
        if (world == null || loc == null) return;

        Particle effectiveParticle = "REDSTONE".equals(particle.name()) ? dustParticle : particle;
        Class<?> dataType = effectiveParticle.getDataType();

        try {
            if (dataType == Particle.DustOptions.class) {
                Color dustColor = color != null ? color : Color.WHITE;
                float dustSize = size > 0 ? size : 1.0f;
                world.spawnParticle(effectiveParticle, loc, amount, offsetX, offsetY, offsetZ, speed, new Particle.DustOptions(dustColor, dustSize));
            } else if (dataType == ItemStack.class) {
                if (data != null) {
                    world.spawnParticle(effectiveParticle, loc, amount, offsetX, offsetY, offsetZ, speed, data);
                }
            } else if (dataType == Color.class) {
                world.spawnParticle(effectiveParticle, loc, amount, offsetX, offsetY, offsetZ, speed, color != null ? color : Color.WHITE);
            } else if (dataType == Void.class) {
                world.spawnParticle(effectiveParticle, loc, amount, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception ignored) {}
    }

    private void spawnAmbientParticles(Location baseLocation, Animation animation) {
        if (baseLocation.getWorld() == null) return;
        World w = baseLocation.getWorld();
        Location center = baseLocation.clone().add(0.5, 0.1, 0.5);


        if (w.getNearbyPlayers(center, 28).isEmpty()) return;

        if ("circle".equals(animation.getAmbientShape())) {
            Vector[] offsets = circleMathCache.computeIfAbsent(animation.getKey(), k -> {
                int amt = animation.getAmbientParticleAmount();
                double r = animation.getAmbientRadius();
                Vector[] arr = new Vector[amt];
                for (int i = 0; i < amt; i++) {
                    double a = 2 * Math.PI * i / amt;
                    arr[i] = new Vector(Math.cos(a) * r, 0, Math.sin(a) * r);
                }
                return arr;
            });

            final Particle.DustOptions dust = dustOptionsCache.computeIfAbsent(animation.getKey(), k ->
                    new Particle.DustOptions(animation.getAmbientColor(), animation.getAmbientSize()));

            int amt = animation.getAmbientParticleAmount();
            double speed = animation.getAmbientParticleSpeed();

            for (int i = 0; i < amt; i++) {
                Vector v = offsets[i];
                w.spawnParticle(dustParticle, center.getX() + v.getX(), center.getY(), center.getZ() + v.getZ(), 1, 0, 0, 0, speed, dust);
            }
        } else {
            spawnParticleSafely(w, dustParticle, center, animation.getAmbientParticleAmount(), animation.getAmbientOffsetX(), animation.getAmbientOffsetY(), animation.getAmbientOffsetZ(), animation.getAmbientParticleSpeed(), null, animation.getAmbientColor(), animation.getAmbientSize());
        }
    }
}