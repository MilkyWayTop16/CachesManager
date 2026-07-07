package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;
import java.util.List;

public interface HologramPlatform {
    void createHologram(String id, Location location, String text);
    void createHologram(String id, Location location, List<String> lines);
    void updateHologram(String id, String text);
    void updateHologram(String id, List<String> lines);
    void deleteHologram(String id);
}