package org.gw.cachesmanager.animations;

import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.platform.HologramPlatform;
import org.gw.cachesmanager.animations.view.AnimationView;
import org.gw.cachesmanager.managers.CacheManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationExecutor {
    private final CachesManager plugin;
    private final HologramPlatform hologramPlatform;
    private final AnimationRegistry registry;
    private final AnimationView animationView;

    private final Map<String, BukkitTask> animationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> pendingLoot = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> activeAnimationLoot = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerActiveCaches = new ConcurrentHashMap<>();

    private final Map<String, Vector[]> circleMathCache = new ConcurrentHashMap<>();
    private final Map<String, Particle.DustOptions> dustOptionsCache = new ConcurrentHashMap<>();

    public AnimationExecutor(CachesManager plugin, HologramPlatform hologramPlatform, AnimationRegistry registry, AnimationView animationView) {
        this.plugin = plugin;
        this.hologramPlatform = hologramPlatform;
        this.registry = registry;
        this.animationView = animationView;
    }

    private void trackTask(String cacheName, BukkitTask task) {
        BukkitTask oldTask = animationTasks.put(cacheName, task);
        if (oldTask != null) {
            oldTask.cancel();
        }
    }

    public void startAnimation(String cacheName, Player player, ItemStack item, Location baseLocation, Animation animation) {
        CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
        if (cache == null) return;

        activeAnimationLoot.put(cacheName, item);
        playerActiveCaches.put(player.getUniqueId(), cacheName);

        if ("firework".equals(animation.getKey())) {
            startFireworkDelayPhase(cacheName, player, item, baseLocation, animation, cache);
        } else {
            Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
            Location itemLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
            startDelayPhase(cacheName, player, item, delayLoc, itemLoc, baseLocation, animation, cache);
        }
    }

    private void startDelayPhase(String cacheName, Player player, ItemStack item, Location delayLoc,
                                 Location itemLoc, Location baseLocation, Animation animation, CacheManager.Cache cache) {
        BukkitTask task = new BukkitRunnable() {
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
        trackTask(cacheName, task);
    }

    private void startItemPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                Location itemLoc, Animation animation, CacheManager.Cache cache) {
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
                    finishAnimation(cacheName, player, item, baseLocation, reusableLoc, animation, cache);
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
        trackTask(cacheName, task);
    }

    private void finishAnimation(String cacheName, Player player, ItemStack item, Location baseLocation,
                                 Location itemLoc, Animation animation, CacheManager.Cache cache) {
        activeAnimationLoot.remove(cacheName);
        if (player != null) playerActiveCaches.remove(player.getUniqueId());
        animationTasks.remove(cacheName);

        animationView.remove(cacheName);

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
            plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramText());
        }
    }

    private void startFireworkDelayPhase(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, CacheManager.Cache cache) {
        Location delayLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
        BukkitTask task = new BukkitRunnable() {
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
        trackTask(cacheName, task);
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
        trackTask(cacheName, flightTask);

        BukkitTask explosionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!fw.isDead()) fw.detonate();
                startPostExplosionDelay(cacheName, player, item, baseLocation, animation, cache);
            }
        }.runTaskLater(plugin, animation.getFireworkFlightDuration() + 1);
        trackTask(cacheName, explosionTask);
    }

    private void startPostExplosionDelay(String cacheName, Player player, ItemStack item, Location baseLocation,
                                         Animation animation, CacheManager.Cache cache) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Location itemLoc = baseLocation.clone().add(0.5, 0.5, 0.5);
                startItemPhase(cacheName, player, item, baseLocation, itemLoc, animation, cache);
            }
        }.runTaskLater(plugin, animation.getPostExplosionDelay());
        trackTask(cacheName, task);
    }

    public void forceFinishAnimationForPlayer(Player player) {
        String cacheName = playerActiveCaches.remove(player.getUniqueId());
        if (cacheName != null) {
            BukkitTask task = animationTasks.remove(cacheName);
            if (task != null) task.cancel();

            CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);
                if (cache.isHologramEnabled() && cache.getLocation() != null) {
                    plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramText());
                }
            }

            ItemStack item = activeAnimationLoot.remove(cacheName);
            if (item != null) {
                if (cache != null && cache.getLocation() != null) {
                    cache.getLocation().getWorld().dropItemNaturally(cache.getLocation().clone().add(0.5, 0.5, 0.5), item.clone());
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                }
            }
            animationView.remove(cacheName);
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
    }

    public void clearAllAnimations() {
        for (BukkitTask task : animationTasks.values()) {
            if (task != null) task.cancel();
        }
        animationTasks.clear();

        for (Map.Entry<String, ItemStack> entry : activeAnimationLoot.entrySet()) {
            String cacheName = entry.getKey();
            ItemStack item = entry.getValue();
            CacheManager.Cache cache = plugin.getCacheManager().getCache(cacheName);
            if (cache != null) {
                cache.setInUse(false);
                if (cache.getLocation() != null && cache.getLocation().getWorld() != null) {
                    cache.getLocation().getWorld().dropItemNaturally(cache.getLocation().clone().add(0.5, 0.5, 0.5), item.clone());
                }
                if (cache.isHologramEnabled() && cache.getLocation() != null) {
                    plugin.getHologramManager().createHologram(cacheName, cache.getLocation(), cache.getHologramText());
                }
            }
            animationView.remove(cacheName);
        }

        activeAnimationLoot.clear();
        playerActiveCaches.clear();
        circleMathCache.clear();
        dustOptionsCache.clear();
    }

    private void playSounds(Location loc, List<Animation.SoundEntry> sounds) {
        if (sounds == null || loc == null || loc.getWorld() == null) return;
        for (Animation.SoundEntry entry : sounds) {
            loc.getWorld().getNearbyPlayers(loc, 10).forEach(p -> p.playSound(loc, entry.sound(), entry.volume(), entry.pitch()));
        }
    }

    private void spawnParticles(Location loc, List<Animation.ParticleEntry> particles, ItemStack itemData) {
        if (particles == null || loc == null || loc.getWorld() == null) return;
        for (Animation.ParticleEntry p : particles) {
            spawnParticleSafely(loc.getWorld(), p.type(), loc, p.amount(), p.offsetX(), p.offsetY(), p.offsetZ(), p.speed(), itemData, null, 0);
        }
    }

    private void spawnParticleSafely(World world, Particle particle, Location loc, int amount, double offsetX, double offsetY, double offsetZ, double speed, ItemStack data, Color color, float size) {
        if (world == null || loc == null) return;
        if (particle == Particle.REDSTONE && color != null) {
            world.spawnParticle(particle, loc, amount, offsetX, offsetY, offsetZ, speed, new Particle.DustOptions(color, size));
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

            Particle.DustOptions dust = dustOptionsCache.computeIfAbsent(animation.getKey(), k ->
                    new Particle.DustOptions(animation.getAmbientColor(), animation.getAmbientSize()));

            int amt = animation.getAmbientParticleAmount();
            double speed = animation.getAmbientParticleSpeed();

            for (int i = 0; i < amt; i++) {
                Vector v = offsets[i];
                w.spawnParticle(Particle.REDSTONE, center.getX() + v.getX(), center.getY(), center.getZ() + v.getZ(), 1, 0, 0, 0, speed, dust);
            }
        } else {
            spawnParticleSafely(w, Particle.REDSTONE, center, animation.getAmbientParticleAmount(), animation.getAmbientOffsetX(), animation.getAmbientOffsetY(), animation.getAmbientOffsetZ(), animation.getAmbientParticleSpeed(), null, animation.getAmbientColor(), animation.getAmbientSize());
        }
    }

    public AnimationView getAnimationView() { return animationView; }
}