package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;

public interface HologramPlatform {
    void createHologram(String id, Location location, String text);
    void updateHologram(String id, String text);
    void deleteHologram(String id);
}