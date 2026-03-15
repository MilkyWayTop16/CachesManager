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
    private String downloadLink = null;
    private BukkitRunnable periodicTask;

    public UpdateChecker(CachesManager plugin) {
        this.plugin = plugin;
        startChecker();
    }

    public void reload() {
        if (periodicTask != null) periodicTask.cancel();
        updateAvailable = false;
        latestVersion = null;
        downloadLink = null;
        startChecker();
    }

    private void startChecker() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) return;

        String mode = plugin.getConfigManager().getUpdateNotifyMode();

        if (!"disabled".equals(mode)) {
            checkForUpdate();
        }

        if ("periodic".equals(mode) || "both".equals(mode)) {
            startPeriodicTask();
        }
    }

    private void startPeriodicTask() {
        int hours = plugin.getConfigManager().getUpdatePeriodicIntervalHours();
        long ticks = hours * 60L * 60L * 20L;

        periodicTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdate();
            }
        };
        periodicTask.runTaskTimerAsynchronously(plugin, ticks, ticks);
    }

    private void checkForUpdate() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.github.com/repos/MilkyWayTop16/CachesManager/releases/latest");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "CachesManager-UpdateChecker");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() != 200) return;

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    String json = response.toString();
                    String tag = extractValue(json, "tag_name");
                    String html = extractValue(json, "html_url");

                    if (tag != null && !tag.equals(plugin.getDescription().getVersion())) {
                        updateAvailable = true;
                        latestVersion = tag;
                        downloadLink = html != null ? html : "https://github.com/MilkyWayTop16/CachesManager/releases";

                        String mode = plugin.getConfigManager().getUpdateNotifyMode();
                        if (!"on-join".equals(mode)) {
                            plugin.getConfigManager().executeActions(null, "update.available", createPlaceholders());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskAsynchronously(plugin);
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
        ph.put("latest-version", latestVersion);
        ph.put("current-version", plugin.getDescription().getVersion());
        ph.put("download-link", downloadLink);
        return ph;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String mode = plugin.getConfigManager().getUpdateNotifyMode();
        if (updateAvailable && ("on-join".equals(mode) || "both".equals(mode)) &&
                (p.isOp() || p.hasPermission("cachesmanager.admin"))) {
            plugin.getConfigManager().executeActions(p, "update.available", createPlaceholders());
        }
    }
}