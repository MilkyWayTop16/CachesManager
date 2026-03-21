package org.gw.cachesmanager.utils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.cachesmanager.CachesManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class UpdateChecker implements Listener {

    private final CachesManager plugin;
    private boolean updateAvailable = false;
    private String latestVersion = null;

    public UpdateChecker(CachesManager plugin) {
        this.plugin = plugin;
        startChecker();
    }

    public void reload() {
        updateAvailable = false;
        latestVersion = null;
        startChecker();
    }

    private void startChecker() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) return;
        checkForUpdate();

        String mode = plugin.getConfigManager().getUpdateNotifyMode();
        if ("periodic".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            startPeriodicTask();
        }
    }

    private void startPeriodicTask() {
        int hours = plugin.getConfigManager().getUpdatePeriodicIntervalHours();
        long ticks = hours * 60L * 60L * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdate();
            }
        }.runTaskTimerAsynchronously(plugin, ticks, ticks);
    }

    private void checkForUpdate() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.github.com/repos/MilkyWayTop16/CachesManager/releases/latest");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "CachesManager-UpdateChecker");

                    if (conn.getResponseCode() != 200) return;

                    String json = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                            .lines().collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();

                    String tag = extractValue(json, "tag_name");

                    if (tag == null) return;

                    String cleanLatest = cleanVersion(tag);
                    String cleanCurrent = cleanVersion(plugin.getDescription().getVersion());

                    if (!cleanLatest.isEmpty() && !cleanLatest.equals(cleanCurrent)) {
                        updateAvailable = true;
                        latestVersion = tag;

                        String mode = plugin.getConfigManager().getUpdateNotifyMode();
                        if (!"on-join".equalsIgnoreCase(mode)) {
                            plugin.getConfigManager().executeActions(null, "update.available", createPlaceholders());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskAsynchronously(plugin);
    }

    private String cleanVersion(String version) {
        if (version == null) return "";
        return version.replaceFirst("^v", "")
                .replace(" ", "")
                .replace("-SNAPSHOT", "")
                .replace("-BETA", "")
                .replace("-DEV", "")
                .replace("-ALPHA", "")
                .replace("plugin", "")
                .trim();
    }

    private String extractValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        idx = json.indexOf(":", idx) + 1;
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == '"')) idx++;
        int end = json.indexOf('"', idx);
        return end == -1 ? null : json.substring(idx, end);
    }

    private Map<String, String> createPlaceholders() {
        Map<String, String> ph = new HashMap<>();
        ph.put("latest-version", latestVersion != null ? latestVersion : "неизвестно");
        ph.put("current-version", plugin.getDescription().getVersion());
        ph.put("download-link", "https://github.com/MilkyWayTop16/CachesManager/releases");
        return ph;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String mode = plugin.getConfigManager().getUpdateNotifyMode();

        if (updateAvailable && ("on-join".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) &&
                (p.isOp() || p.hasPermission("cachesmanager.admin"))) {
            plugin.getConfigManager().executeActions(p, "update.available", createPlaceholders());
        }
    }
}