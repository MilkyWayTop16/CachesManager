package org.gw.cachesmanager;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.cachesmanager.utils.BStats;
import org.gw.cachesmanager.utils.HexColors;
import org.gw.cachesmanager.commands.CommandsHandler;
import org.gw.cachesmanager.commands.CommandsTabCompleter;
import org.gw.cachesmanager.listeners.*;
import org.gw.cachesmanager.managers.*;
import org.gw.cachesmanager.storage.DatabaseManager;
import org.gw.cachesmanager.utils.UpdateChecker;
import org.gw.cachesmanager.utils.PlaceholderAPIHook;

public class CachesManager extends JavaPlugin {

    @Getter private ConfigManager configManager;
    @Getter private DatabaseManager databaseManager;
    @Getter private CacheManager cacheManager;
    @Getter private HologramManager hologramManager;
    @Getter private ItemManager itemManager;
    @Getter private MenuManager menuManager;
    @Getter private CacheModeListener cacheModeListener;
    @Getter private AnimationsManager animationsManager;
    @Getter private LootHistoryManager lootHistoryManager;
    @Getter private UpdateChecker updateChecker;
    @Getter private ConfirmDeleteListener confirmDeleteListener;
    @Getter private StatsManager statsManager;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        if (!initializePlugin()) {
            setEnabled(false);
            return;
        }

        if (configManager.isBStatsEnabled()) {
            new BStats(this);
        }

        long loadTime = System.currentTimeMillis() - startTime;
        logStartupInfo(loadTime);
    }

    private boolean initializePlugin() {
        console("&f");
        console("&#00FF5A◆ CachesManager &f| Проверка &#00FF5Aнеобходимых &fзависимостей...");

        if (!checkRequiredDependencies()) {
            return false;
        }

        initCoreSystems();
        initHologramAndAnimationSystems();
        initCacheCore();
        initMenusAndListeners();
        registerCommands();

        Bukkit.getScheduler().runTaskLater(this, cacheManager::loadCaches, 5L);

        return true;
    }

    private boolean checkRequiredDependencies() {
        boolean hasPacketLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")
                || Bukkit.getPluginManager().isPluginEnabled("PacketEvents")
                || Bukkit.getPluginManager().isPluginEnabled("packetevents");

        if (!hasPacketLib) {
            error("Библиотека пакетов (&#FF5D00ProtocolLib &fили &#FF5D00PacketEvents&f) не найдена, так что плагин &#FF5D00продолжит работу&f, но на версиях сервера &#FF5D00ниже 1.19.4 &fназвания предметов над анимацией &#FF5D00не будут переводиться &fна язык клиента...");
        } else {
            console("&#00FF5A◆ CachesManager &f| Зависимости &#00FF5Aуспешно &fподключены!");
        }
        return true;
    }

    private void initCoreSystems() {
        console("&#00FF5A◆ CachesManager &f| Чтение &#00FF5Aфайлов конфигурации &fи &#00FF5Aзагрузка &fресурсов...");
        configManager = new ConfigManager(this);

        try {
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari", org.apache.logging.log4j.Level.WARN);
            org.apache.logging.log4j.core.config.Configurator.setLevel("com.zaxxer.hikari.pool", org.apache.logging.log4j.Level.WARN);
        } catch (Throwable ignored) {}

        databaseManager = new DatabaseManager(this);
        databaseManager.cleanupOldBackups();

        itemManager = new ItemManager(this);
        PlaceholderAPIHook.init();

        console("&#00FF5A◆ CachesManager &f| Инициализация &#00FF5Aсистемы проверки &fобновлений...");
        updateChecker = new UpdateChecker(this);
    }

    private void initHologramAndAnimationSystems() {
        console("&#00FF5A◆ CachesManager &f| Инициализация &#00FF5Aсистем голограмм &fи &#00FF5Aдвижка &fанимаций...");
        hologramManager = new HologramManager(this);
        animationsManager = new AnimationsManager(this, hologramManager);

        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")
                && !Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            log("Сторонние плагины голограмм не найдены, так что активирован встроенный движок ModernMinecraftPlatform для показа голограмм...");
        }
    }

    private void initCacheCore() {
        console("&#00FF5A◆ CachesManager &f| Подготовка &#00FF5Aядра &fи &#00FF5Aстатистики &fтайников...");
        statsManager = new StatsManager(this, configManager);

        console("&#00FF5A◆ CachesManager &f| Инициализация менеджера &#00FF5Aистории &fлута...");
        lootHistoryManager = new LootHistoryManager(this, configManager);

        this.cacheManager = new CacheManager(this, configManager, hologramManager, itemManager,
                animationsManager, statsManager, lootHistoryManager);
    }

    private void initMenusAndListeners() {
        console("&#00FF5A◆ CachesManager &f| Сборка всех &#00FF5Aменюшек &fтайников...");
        cacheModeListener = new CacheModeListener(this, cacheManager, configManager);

        menuManager = new MenuManager(this, configManager, cacheManager, itemManager,
                cacheModeListener, animationsManager, statsManager, lootHistoryManager);

        CacheBlockListener cacheBlockListener = new CacheBlockListener(this, cacheManager, itemManager, menuManager, configManager);
        this.confirmDeleteListener = new ConfirmDeleteListener(this);
        KeyIntegrityListener keyIntegrityListener = new KeyIntegrityListener(this, itemManager);

        getServer().getPluginManager().registerEvents(this.confirmDeleteListener, this);
        cacheModeListener.setMenuManager(menuManager);
        getServer().getPluginManager().registerEvents(animationsManager, this);
        getServer().getPluginManager().registerEvents(cacheModeListener, this);
        getServer().getPluginManager().registerEvents(cacheBlockListener, this);
        getServer().getPluginManager().registerEvents(keyIntegrityListener, this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
    }

    private void registerCommands() {
        getCommand("cachesmanager").setExecutor(new CommandsHandler(this));
        getCommand("cachesmanager").setTabCompleter(new CommandsTabCompleter(this));
    }

    public void reloadPlugin() {
        if (menuManager != null) {
            menuManager.closeAllMenus();
        }

        String error = configManager.reloadConfig();
        if (error != null) return;

        reloadDatabase();
        reloadAnimations(false);
        reloadMenus();
        reloadCaches();

        PlaceholderAPIHook.init();
        if (updateChecker != null) updateChecker.reload();

        if (menuManager != null) {
            menuManager.refreshOpenMenus();
        }
    }

    private void reloadDatabase() {
        if (databaseManager == null) return;

        console("&#ffff00◆ CachesManager &f| Сохранение статистики тайников перед перезагрузкой базы данных...");
        if (cacheManager != null) cacheManager.saveAllStatsSynchronously();

        console("&#ffff00◆ CachesManager &f| Перезагрузка соединения с базой данных...");
        console("&#FFFF00◆ CachesManager &f| База данных SQLite перезагружается...");
        databaseManager.closeConnection();
        databaseManager.initializeDatabase(false);
        databaseManager.cleanupOldBackups();
    }

    private void reloadAnimations() {
        reloadAnimations(false);
    }

    private void reloadAnimations(boolean forceClear) {
        console("&#ffff00◆ CachesManager &f| Перезагрузка анимаций...");
        animationsManager.reloadAnimations(forceClear);
    }

    private void reloadMenus() {
        console("&#ffff00◆ CachesManager &f| Перезагрузка меню...");
        if (menuManager != null) {
            menuManager.reload();
        }
    }

    private void reloadCaches() {
        console("&#ffff00◆ CachesManager &f| Перезагрузка тайников...");
        if (animationsManager != null) {
            cacheManager.removeHologramsExceptActiveAnimations(animationsManager);
        } else {
            cacheManager.removeAllHolograms();
        }
        cacheManager.loadCaches();
    }

    public void forceReloadPlugin() {
        if (menuManager != null) {
            menuManager.closeAllMenus();
        }

        String error = configManager.reloadConfig();
        if (error != null) return;

        reloadDatabase();
        reloadAnimations(true);
        reloadMenus();

        if (cacheManager != null) {
            cacheManager.removeAllHolograms();
        }
        if (cacheManager != null) {
            cacheManager.loadCaches();
        }

        PlaceholderAPIHook.init();
        if (updateChecker != null) updateChecker.reload();

        if (menuManager != null) {
            menuManager.refreshOpenMenus();
        }
    }

    private void logStartupInfo(long loadTime) {
        console("&#ffff00 ");
        console("&#ffff00  █▀▀ ▄▀█ █▀▀ █░█ █▀▀ █▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#ffff00  █▄▄ █▀█ █▄▄ █▀█ ██▄ ▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
        console("&#ffff00 ");
        console("&f            (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#00FF5A          ▶ &fПлагин &#00FF5Aуспешно &fзагружен и включен!");
        console("&#ffff00 ");
        console("&#ffff00              ◆ &fВерсия плагина: &#ffff00" + getDescription().getVersion());
        console("&#ffff00             ◆ &fЗагруженных тайников: &#ffff00" + configManager.getCacheNames().size());
        console("&#ffff00             ◆ &fВремя загрузки: &#ffff00" + loadTime + " мс.");
        console("&#ffff00 ");
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();

        if (databaseManager != null) {
            databaseManager.setShuttingDown(true);
        }

        if (menuManager != null) {
            menuManager.closeAllMenus();
        }

        console("&#FF5D00◆ CachesManager &f| Начало &#FF5D00выгрузки &fплагина...");

        if (updateChecker != null) {
            updateChecker.shutdown();
        }

        if (cacheManager != null) {
            console("&#FF5D00◆ CachesManager &f| Сохранение всех &#FF5D00данных &fтайников...");
            configManager.forceSaveAllCacheConfigs();

            console("&#FF5D00◆ CachesManager &f| Сохранение &#FF5D00статистики тайников &fв базу данных...");

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    cacheManager.saveAllStatsSynchronously();
                } catch (Exception ignored) {}
            })
            .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> null)
            .join();

            if (hologramManager != null) hologramManager.clearAllHolograms();
            if (animationsManager != null) animationsManager.clearAllAnimations();
        }

        if (databaseManager != null) {
            console("&#FF5D00◆ CachesManager &f| Закрытие &#FF5D00соединения &fс базой данных...");
            databaseManager.closeConnection();
        }

        long unloadTime = System.currentTimeMillis() - startTime;

        console("&#ffff00 ");
        console("&#ffff00  █▀▀ ▄▀█ █▀▀ █░█ █▀▀ █▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#ffff00  █▄▄ █▀█ █▄▄ █▀█ ██▄ ▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
        console("&#ffff00 ");
        console("&f            (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#FF5D00        ▶ &fПлагин &#FF5D00успешно &fвыгружен и выключен!");
        console("&#ffff00 ");
        console("&#ffff00               ◆ &fВерсия плагина: &#ffff00" + getDescription().getVersion());
        console("&#ffff00             ◆ &fСохранено тайников: &#ffff00" + (configManager != null ? configManager.getCacheNames().size() : 0));
        console("&#ffff00             ◆ &fВремя выгрузки: &#ffff00" + unloadTime + " мс.");
        console("&#ffff00 ");
    }

    public void console(String message) {
        if (message == null) return;
        Bukkit.getConsoleSender().sendMessage(HexColors.translateForConsole(message));
    }

    public void log(String message) {
        if (configManager != null && configManager.isLogsInConsoleEnabled()) {
            console("&#ffff00◆ CachesManager &f| " + message);
        }
    }

    public void error(String message) {
        Bukkit.getConsoleSender().sendMessage(HexColors.translateForConsole("&#FB8808◆ CachesManager &f| " + message));
    }
}