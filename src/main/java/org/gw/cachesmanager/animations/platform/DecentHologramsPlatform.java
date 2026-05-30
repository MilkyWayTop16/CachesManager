package org.gw.cachesmanager.animations.platform;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.gw.cachesmanager.CachesManager;

import java.util.Arrays;

public class DecentHologramsPlatform implements HologramPlatform {

    private final CachesManager plugin;

    public DecentHologramsPlatform(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        try {
            deleteHologram(id);
            DHAPI.createHologram(id, location, Arrays.asList(text.split("\n")));
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка создания голограммы через DecentHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void updateHologram(String id, String text) {
        try {
            Hologram hologram = DHAPI.getHologram(id);
            if (hologram != null) {
                DHAPI.setHologramLines(hologram, Arrays.asList(text.split("\n")));
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка обновления голограммы через DecentHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void deleteHologram(String id) {
        try {
            Hologram hologram = DHAPI.getHologram(id);
            if (hologram != null) {
                hologram.delete();
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.log("Ошибка удаления голограммы через DecentHolograms (айди: " + id + "): " + t.getMessage());
            }
        }
    }
}