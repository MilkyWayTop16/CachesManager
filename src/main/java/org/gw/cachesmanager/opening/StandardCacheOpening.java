package org.gw.cachesmanager.opening;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.caches.Cache;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class StandardCacheOpening implements CacheOpening {

    private final CachesManager plugin;
    private final Cache cache;
    private Player player;
    private ItemStack chosenItem;
    private boolean running;
    private boolean finished;

    public StandardCacheOpening(CachesManager plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public boolean start(Player player, Cache cache) {
        if (this.running) return false;
        this.player = player;

        if (!cache.getInUse().compareAndSet(false, true)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());
            plugin.getConfigManager().executeActions(player, "cache.in-use", ph);
            return false;
        }

        this.running = true;

        try {
            Map<String, String> ph = new HashMap<>();
            ph.put("name-cache", cache.getDisplayName());
            List<Map.Entry<ItemStack, Integer>> lootWithChances = cache.getLootWithChances();

            if (lootWithChances.isEmpty()) {
                plugin.getConfigManager().executeActions(player, "cache.no-loot", ph);
                Cache live = plugin.getCacheManager().getCache(cache.getName());
                (live != null ? live : cache).setInUse(false);
                this.running = false;
                return false;
            }

            this.chosenItem = selectRandomItem(lootWithChances);
            if (this.chosenItem == null) {
                plugin.getConfigManager().executeActions(player, "cache.zero-chance", ph);
                Cache live = plugin.getCacheManager().getCache(cache.getName());
                (live != null ? live : cache).setInUse(false);
                this.running = false;
                return false;
            }

            updateStatistics(cache, player);

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().saveCacheStatsAsync(cache);
                plugin.getDatabaseManager().addLootHistoryEntryAsync(cache.getName(), player.getName(), chosenItem);
                plugin.getDatabaseManager().forceFlushPendingHistory();
            }

            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().removeHologram(cache.getName());
            }

            org.gw.cachesmanager.animations.Animation anim = plugin.getAnimationsManager().getAnimations().get(cache.getAnimation());

            if (anim == null) {
                finishWithInstantGive(ph);
                return true;
            }

            plugin.getAnimationsManager().playAnimation(player, cache.getAnimation(), cache.getLocation(), chosenItem, cache.getName(), this);
            return true;

        } catch (Exception e) {
            Cache live = plugin.getCacheManager().getCache(cache.getName());
            (live != null ? live : cache).setInUse(false);
            this.running = false;
            return false;
        }
    }

    private void updateStatistics(Cache cache, Player player) {
        long now = System.currentTimeMillis();
        cache.setOpenCount(cache.getOpenCount() + 1);
        cache.setLastOpenedTime(now);
        if (cache.getFirstOpenedTime() == 0) {
            cache.setFirstOpenedTime(now);
        }
        if (cache.getLastOpenTimestamp() > 0) {
            long interval = now - cache.getLastOpenTimestamp();
            cache.setTotalIntervalSum(cache.getTotalIntervalSum() + interval);
            cache.setIntervalCount(cache.getIntervalCount() + 1);
            if (interval < cache.getMinInterval()) {
                cache.setMinInterval(interval);
            }
        }
        cache.setLastOpenTimestamp(now);

        LocalDate today = LocalDate.now();
        int count = cache.getDailyOpens().computeIfAbsent(today, k -> new AtomicInteger(0)).incrementAndGet();
        if (count > cache.getMaxDailyOpens()) {
            cache.setMaxDailyOpens(count);
        }

        cache.getRawTopPlayers().merge(player.getName(), 1, Integer::sum);
        cache.setTotalLootGiven(cache.getTotalLootGiven() + 1);
    }

    private void finishWithInstantGive(Map<String, String> ph) {
        giveLootToPlayer(ph);
        Cache live = plugin.getCacheManager().getCache(cache.getName());
        (live != null ? live : cache).setInUse(false);
        restoreHologramIfNeeded();
        this.running = false;
        this.finished = true;
    }

    private void giveLootToPlayer(Map<String, String> ph) {
        if (player == null || !player.isOnline() || chosenItem == null) {
            if (cache.getLocation() != null && cache.getLocation().getWorld() != null && chosenItem != null) {
                cache.getLocation().getWorld().dropItemNaturally(cache.getLocation().clone().add(0.5, 0.5, 0.5), chosenItem);
            }
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(chosenItem);
        if (!leftover.isEmpty()) {
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            plugin.getConfigManager().executeActions(player, "cache.inventory-full", ph);
        }
    }

    private void restoreHologramIfNeeded() {
        Cache live = plugin.getCacheManager().getCache(cache.getName());
        Cache target = (live != null) ? live : cache;

        if (target.isHologramEnabled() && target.getLocation() != null && plugin.getHologramManager() != null) {
            plugin.getHologramManager().createHologram(target.getName(), target.getLocation(), target.getHologramText());
        }
    }

    @Override
    public void finishVisual() {
        if (!running || finished) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("name-cache", cache.getDisplayName());

        giveLootToPlayer(ph);

        Cache live = plugin.getCacheManager().getCache(cache.getName());
        if (live != null) {
            live.setInUse(false);
        } else {
            cache.setInUse(false);
        }
        restoreHologramIfNeeded();

        this.running = false;
        this.finished = true;
    }

    @Override
    public void cancel() {
        if (!running) return;
        Cache live = plugin.getCacheManager().getCache(cache.getName());
        if (live != null) {
            live.setInUse(false);
        } else {
            cache.setInUse(false);
        }
        restoreHologramIfNeeded();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Cache getCache() {
        return cache;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public ItemStack getChosenItem() {
        return chosenItem;
    }

    private ItemStack selectRandomItem(List<Map.Entry<ItemStack, Integer>> lootWithChances) {
        int totalChance = 0;
        for (var entry : lootWithChances) {
            totalChance += entry.getValue();
        }
        if (totalChance <= 0) return null;

        int random = ThreadLocalRandom.current().nextInt(totalChance);
        int current = 0;

        for (var entry : lootWithChances) {
            current += entry.getValue();
            if (random < current) {
                return entry.getKey().clone();
            }
        }
        return null;
    }
}