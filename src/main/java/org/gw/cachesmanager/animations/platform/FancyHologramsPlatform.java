package org.gw.cachesmanager.animations.platform;

import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FancyHologramsPlatform implements HologramPlatform {

    private HologramManager getManager() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("FancyHolograms");
            if (plugin != null) {
                return (HologramManager) plugin.getClass().getMethod("getHologramManager").invoke(plugin);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        try {
            HologramManager manager = getManager();
            if (manager == null) return;

            deleteHologram(id);

            Location calibratedLoc = location.clone().subtract(0, 0.3, 0);
            TextHologramData data = new TextHologramData(id, calibratedLoc);
            data.setText(Arrays.stream(text.split("\n"))
                    .map(HexColors::translate)
                    .collect(Collectors.toList()));

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
        } catch (Exception ignored) {}
    }

    @Override
    public void updateHologram(String id, String text) {
        try {
            HologramManager manager = getManager();
            if (manager == null) return;

            Hologram hologram = manager.getHologram(id).orElse(null);
            if (hologram != null && hologram.getData() instanceof TextHologramData data) {
                data.setText(Arrays.stream(text.split("\n"))
                        .map(HexColors::translate)
                        .collect(Collectors.toList()));
                hologram.queueUpdate();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void deleteHologram(String id) {
        try {
            HologramManager manager = getManager();
            if (manager == null) return;

            manager.getHologram(id).ifPresent(manager::removeHologram);
        } catch (Exception ignored) {}
    }
}