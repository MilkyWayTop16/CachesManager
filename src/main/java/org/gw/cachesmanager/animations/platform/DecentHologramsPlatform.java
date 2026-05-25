package org.gw.cachesmanager.animations.platform;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.Arrays;

public class DecentHologramsPlatform implements HologramPlatform {

    @Override
    public void createHologram(String id, Location location, String text) {
        deleteHologram(id);
        DHAPI.createHologram(id, location, Arrays.asList(text.split("\n")));
    }

    @Override
    public void updateHologram(String id, String text) {
        Hologram hologram = DHAPI.getHologram(id);
        if (hologram != null) {
            DHAPI.setHologramLines(hologram, Arrays.asList(text.split("\n")));
        }
    }

    @Override
    public void deleteHologram(String id) {
        Hologram hologram = DHAPI.getHologram(id);
        if (hologram != null) {
            hologram.delete();
        }
    }
}