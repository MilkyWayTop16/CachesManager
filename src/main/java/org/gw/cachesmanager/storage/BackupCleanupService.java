package org.gw.cachesmanager.storage;

import org.gw.cachesmanager.CachesManager;

import java.io.File;

public final class BackupCleanupService {

    private final CachesManager plugin;

    public BackupCleanupService(CachesManager plugin) {
        this.plugin = plugin;
    }

    public void cleanupOldBackups() {
        try {
            if (plugin.getConfigManager() == null || !plugin.getConfigManager().isCleanupBackups()) {
                return;
            }

            int days = plugin.getConfigManager().getDeleteBackupsAfterDays();
            boolean deleteHistoryFolder = plugin.getConfigManager().isDeleteEmptyHistoryFolder();

            long cutoffTime = (days > 0)
                    ? System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
                    : Long.MAX_VALUE;

            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null) {
                return;
            }

            cleanupBakFilesInFolder(new File(dataFolder, "caches"), cutoffTime);

            File historyFolder = new File(dataFolder, "caches/history");
            cleanupBakFilesInFolder(historyFolder, cutoffTime);

            if (deleteHistoryFolder && historyFolder.exists() && historyFolder.isDirectory()) {
                File[] files = historyFolder.listFiles();
                if (files != null && files.length == 0) {
                    try {
                        if (historyFolder.delete()) {
                            plugin.log("Пустая папка caches/history была автоматически удалена");
                        }
                    } catch (SecurityException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            plugin.log("Очистка .bak-файлов пропущена: " + t.getMessage());
        }
    }

    private void cleanupBakFilesInFolder(File folder, long cutoffTime) {
        try {
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                return;
            }

            File[] bakFiles;
            try {
                bakFiles = folder.listFiles((dir, name) -> name != null && name.endsWith(".yml.bak"));
            } catch (SecurityException e) {
                return;
            }
            if (bakFiles == null || bakFiles.length == 0) {
                return;
            }

            int deletedCount = 0;
            for (File bakFile : bakFiles) {
                if (bakFile == null || !bakFile.isFile()) {
                    continue;
                }
                try {
                    if (bakFile.lastModified() < cutoffTime) {
                        if (bakFile.delete()) {
                            deletedCount++;
                        }
                    }
                } catch (SecurityException ignored) {
                }
            }

            if (deletedCount > 0) {
                plugin.log("Автоматически удалено " + deletedCount + " старых .bak файлов из папки " + folder.getName());
            }
        } catch (Throwable ignored) {
        }
    }
}
