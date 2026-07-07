package org.gw.cachesmanager.animations.platform;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.gw.cachesmanager.CachesManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DecentHologramsPlatform implements HologramPlatform {

    private final CachesManager plugin;

    public DecentHologramsPlatform(CachesManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : Arrays.asList(text.split("\n"));
        createHologram(id, location, lines);
    }

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        try {
            deleteHologram(id);
            List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
            DHAPI.createHologram(id, location, safeLines);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка создания голограммы через плагин DecentHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void updateHologram(String id, String text) {
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : Arrays.asList(text.split("\n"));
        updateHologram(id, lines);
    }

    @Override
    public void updateHologram(String id, List<String> lines) {
        try {
            Hologram hologram = DHAPI.getHologram(id);
            if (hologram != null) {
                List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
                DHAPI.setHologramLines(hologram, safeLines);
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка обновления голограммы через плагин DecentHolograms (айди: " + id + ")");
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
                plugin.log("Ошибка удаления голограммы через плагин DecentHolograms (айди: " + id + "): " + t.getMessage());
            }
        }
    }
}