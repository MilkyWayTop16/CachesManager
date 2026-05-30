package org.gw.cachesmanager.animations.platform;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FancyHologramsPlatform implements HologramPlatform {

    private final CachesManager plugin;

    private static final double FANCY_Y_OFFSET_CORRECTION = -0.20;

    public FancyHologramsPlatform(CachesManager plugin) {
        this.plugin = plugin;
    }

    private HologramManager getManager() {
        try {
            if (!FancyHologramsPlugin.isEnabled()) {
                return null;
            }
            return FancyHologramsPlugin.get().getHologramManager();
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Не удалось получить HologramManager от FancyHolograms: " + t.getMessage());
            }
            return null;
        }
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        HologramManager manager = getManager();
        if (manager == null) {
            return;
        }

        try {
            deleteHologram(id);

            Location adjusted = location.clone();
            adjusted.setY(adjusted.getY() + FANCY_Y_OFFSET_CORRECTION);

            TextHologramData data = new TextHologramData(id, adjusted);
            data.setText(Arrays.stream(text.split("\n"))
                    .map(HexColors::translate)
                    .collect(Collectors.toList()));

            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка создания голограммы через FancyHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void updateHologram(String id, String text) {
        HologramManager manager = getManager();
        if (manager == null) {
            return;
        }

        try {
            Hologram hologram = manager.getHologram(id).orElse(null);
            if (hologram != null && hologram.getData() instanceof TextHologramData data) {
                data.setText(Arrays.stream(text.split("\n"))
                        .map(HexColors::translate)
                        .collect(Collectors.toList()));
                hologram.queueUpdate();
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка обновления голограммы через FancyHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void deleteHologram(String id) {
        HologramManager manager = getManager();
        if (manager == null) {
            return;
        }

        try {
            manager.getHologram(id).ifPresent(manager::removeHologram);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.log("Ошибка удаления голограммы через FancyHolograms (айди: " + id + "): " + t.getMessage());
            }
        }
    }
}