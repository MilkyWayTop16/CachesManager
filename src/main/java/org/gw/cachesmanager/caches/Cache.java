package org.gw.cachesmanager.caches;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Cache {
    @Getter @Setter private String name;
    @Setter private String displayName;
    private List<String> hologramLines = new ArrayList<>();
    @Getter @Setter private Location location;
    @Getter @Setter private Material blockType;
    private final List<Map.Entry<ItemStack, Integer>> lootWithChances = new CopyOnWriteArrayList<>();
    @Getter private final AtomicBoolean inUse = new AtomicBoolean(false);
    @Getter @Setter private boolean unbreakable;
    @Getter @Setter private String animation;
    @Getter @Setter private boolean hologramEnabled;
    @Getter @Setter private double hologramOffsetX;
    @Getter @Setter private double hologramOffsetY;
    @Getter @Setter private double hologramOffsetZ;
    @Getter @Setter private int openCount;
    @Getter @Setter private int totalLootGiven;
    @Getter @Setter private long createdTime;
    @Getter @Setter private long lastOpenedTime;
    @Getter private final Map<LocalDate, AtomicInteger> dailyOpens = new ConcurrentHashMap<>();
    @Getter @Setter private int maxDailyOpens;
    @Getter @Setter private long firstOpenedTime;
    @Getter @Setter private long lastOpenTimestamp;
    @Getter @Setter private long totalIntervalSum;
    @Getter @Setter private int intervalCount;
    @Getter @Setter private long minInterval = Long.MAX_VALUE;
    @Getter @Setter private String keyMaterial = "TRIPWIRE_HOOK";
    @Setter
    private String keyName;
    private final List<String> keyLore = new CopyOnWriteArrayList<>();
    @Getter @Setter private int keyCustomModelData = 0;
    @Getter @Setter private boolean keyGlow = false;
    private final List<String> keyFlags = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> topPlayers = new ConcurrentHashMap<>();

    public Cache(String name) {
        this.name = name;
        this.unbreakable = true;
        this.hologramEnabled = true;
        this.hologramOffsetX = 0.0;
        this.hologramOffsetY = 0.5;
        this.hologramOffsetZ = 0.0;
        this.createdTime = System.currentTimeMillis();
        this.keyFlags.addAll(Arrays.asList("HIDE_ENCHANTS", "HIDE_ATTRIBUTES"));
    }

    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public List<String> getHologramLines() {
        if (hologramLines == null || hologramLines.isEmpty()) {
            return Collections.singletonList("&eТайник " + getDisplayName());
        }
        return new ArrayList<>(hologramLines);
    }

    public void setHologramLines(List<String> lines) {
        this.hologramLines = (lines != null) ? new ArrayList<>(lines) : new ArrayList<>();
    }

    public String getHologramText() {
        return String.join("\n", getHologramLines());
    }

    public void setHologramText(String text) {
        if (text == null || text.isEmpty()) {
            this.hologramLines = new ArrayList<>();
        } else {
            this.hologramLines = new ArrayList<>(Arrays.asList(text.split("\n")));
        }
    }

    public List<Map.Entry<ItemStack, Integer>> getLootWithChances() {
        return new ArrayList<>(lootWithChances);
    }

    public void setLootWithChances(List<Map.Entry<ItemStack, Integer>> loot) {
        lootWithChances.clear();
        if (loot != null) {
            lootWithChances.addAll(loot);
        }
    }

    public List<ItemStack> getLoot() {
        return lootWithChances.stream()
                .map(Map.Entry::getKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getTopPlayers() {
        return topPlayers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Integer> getRawTopPlayers() {
        return topPlayers;
    }

    public String getKeyName() {
        return keyName != null ? keyName : "&eКлюч от тайника " + name;
    }

    public List<String> getKeyLore() {
        return new ArrayList<>(keyLore);
    }

    public void setKeyLore(List<String> lore) {
        keyLore.clear();
        if (lore != null) {
            keyLore.addAll(lore);
        }
    }

    public boolean isKeyGlowEnabled() {
        return keyGlow;
    }

    public String getKeyFlagsString() {
        return keyFlags.isEmpty() ? "Отсутствуют" : String.join(", ", keyFlags);
    }

    public List<String> getKeyFlags() {
        return new ArrayList<>(keyFlags);
    }

    public void setKeyFlags(List<String> flags) {
        keyFlags.clear();
        if (flags != null) {
            keyFlags.addAll(flags);
        }
    }

    public boolean isInUse() {
        return inUse.get();
    }

    public void setInUse(boolean value) {
        inUse.set(value);
    }
}