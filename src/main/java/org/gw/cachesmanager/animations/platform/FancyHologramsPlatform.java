package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FancyHologramsPlatform implements HologramPlatform {

    private final CachesManager plugin;
    private static final double FANCY_Y_OFFSET_CORRECTION = -0.20;
    private volatile boolean warnedUnavailable = false;

    public FancyHologramsPlatform(CachesManager plugin) {
        this.plugin = plugin;
        if (!FancyHologramsReflectiveBridge.isAvailable()) {
            warnUnavailableOnce();
        }
    }

    private void warnUnavailableOnce() {
        if (warnedUnavailable) return;
        warnedUnavailable = true;
        if (plugin != null) {
            plugin.error("Установленная версия плагина FancyHolograms несовместима с движком голограмм (" +
                    FancyHologramsReflectiveBridge.getFailureReason() + "). Голограммы через FancyHolograms работать не будут...");
        }
    }

    private Object getManager() {
        if (!FancyHologramsReflectiveBridge.isAvailable()) {
            warnUnavailableOnce();
            return null;
        }

        try {
            if (!FancyHologramsReflectiveBridge.isPluginEnabled()) {
                return null;
            }
            return FancyHologramsReflectiveBridge.getManager();
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Не удалось получить функцию голограмм от плагина FancyHolograms: " + t.getMessage() + "...");
            }
            return null;
        }
    }

    @Override
    public void createHologram(String id, Location location, String text) {
        List<String> lines = (text == null || text.isEmpty()) ? new ArrayList<>() : Arrays.asList(text.split("\n"));
        createHologram(id, location, lines);
    }

    @Override
    public void createHologram(String id, Location location, List<String> lines) {
        Object manager = getManager();
        if (manager == null) return;

        try {
            deleteHologram(id);

            Location adjusted = location.clone();
            adjusted.setY(adjusted.getY() + FANCY_Y_OFFSET_CORRECTION);

            Object data = FancyHologramsReflectiveBridge.createTextHologramData(id, adjusted);
            List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
            FancyHologramsReflectiveBridge.setText(data, safeLines.stream()
                    .map(HexColors::translate)
                    .collect(Collectors.toList()));

            Object hologram = FancyHologramsReflectiveBridge.create(manager, data);
            FancyHologramsReflectiveBridge.setNotPersistent(data, hologram);
            FancyHologramsReflectiveBridge.addHologram(manager, hologram);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка создания голограммы через плагин FancyHolograms (айди: " + id + ")");
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
        Object manager = getManager();
        if (manager == null) return;

        try {
            Optional<Object> hologramOpt = FancyHologramsReflectiveBridge.getHologram(manager, id);
            if (hologramOpt.isEmpty()) return;
            Object hologram = hologramOpt.get();

            Object data = FancyHologramsReflectiveBridge.getData(hologram);
            if (FancyHologramsReflectiveBridge.isTextHologramData(data)) {
                List<String> safeLines = (lines == null) ? new ArrayList<>() : lines;
                FancyHologramsReflectiveBridge.setText(data, safeLines.stream()
                        .map(HexColors::translate)
                        .collect(Collectors.toList()));
                FancyHologramsReflectiveBridge.queueUpdate(hologram);
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.error("Ошибка обновления голограммы через плагин FancyHolograms (айди: " + id + ")");
            }
        }
    }

    @Override
    public void deleteHologram(String id) {
        Object manager = getManager();
        if (manager == null) return;

        try {
            Optional<Object> hologramOpt = FancyHologramsReflectiveBridge.getHologram(manager, id);
            if (hologramOpt.isPresent()) {
                FancyHologramsReflectiveBridge.removeHologram(manager, hologramOpt.get());
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.log("Ошибка удаления голограммы через плагин FancyHolograms (айди: " + id + "): " + t.getMessage());
            }
        }
    }
}
