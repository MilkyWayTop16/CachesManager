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
import org.gw.cachesmanager.utils.UpdateChecker;

public class CachesManager extends JavaPlugin {

    @Getter private ConfigManager configManager;
    @Getter private CacheManager cacheManager;
    @Getter private HologramManager hologramManager;
    @Getter private ItemManager itemManager;
    @Getter private MenuManager menuManager;
    @Getter private CacheModeListener cacheModeListener;
    @Getter private AnimationsManager animationsManager;
    @Getter private LootHistoryManager lootHistoryManager;
    @Getter private UpdateChecker updateChecker;
    @Getter private ConfirmDeleteListener confirmDeleteListener;


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
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            console("&#FF5D00◆ CachesManager &f| Ошибочка! Нужный плагин &#FF5D00ProtocolLib &fне был найден, а он &#FF5D00нужен &fдля работы, плагин выключен...");
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            console("&#FF5D00◆ CachesManager &f| Ошибочка! Нужный плагин &#FF5D00DecentHolograms &fне был найден, а он &#FF5D00нужен &fдля работы, плагин выключен...");
            return false;
        }
        console("&#00FF5A◆ CachesManager &f| Зависимости &#00FF5Aуспешно &fподключены!");

        console("&#00FF5A◆ CachesManager &f| Чтение файлов конфигурации и загрузка ресурсов...");
        configManager = new ConfigManager(this);
        itemManager = new ItemManager(this);

        console("&#00FF5A◆ CachesManager &f| Инициализация &#00FF5Aсистемы проверки &fобновлений...");
        updateChecker = new UpdateChecker(this);

        console("&#00FF5A◆ CachesManager &f| Инициализация &#00FF5Aсистем голограмм &fи &#00FF5Aдвижка &fанимаций...");
        hologramManager = new HologramManager(this);
        animationsManager = new AnimationsManager(this, hologramManager);

        console("&#00FF5A◆ CachesManager &f| Подготовка &#00FF5Aядра &fи &#00FF5Aстатистики &fтайников...");
        StatsManager statsManager = new StatsManager(this, configManager);

        console("&#00FF5A◆ CachesManager &f| Инициализация менеджера &#00FF5Aистории &fлута...");
        lootHistoryManager = new LootHistoryManager(this);

        this.cacheManager = new CacheManager(this, configManager, hologramManager, itemManager,
                animationsManager, statsManager, lootHistoryManager);

        console("&#00FF5A◆ CachesManager &f| Сборка всех &#00FF5Aменюшек &fтайников...");
        cacheModeListener = new CacheModeListener(this, cacheManager, configManager);

        menuManager = new MenuManager(this, configManager, cacheManager, itemManager,
                cacheModeListener, animationsManager, statsManager, lootHistoryManager);

        CacheBlockListener cacheBlockListener = new CacheBlockListener(this, cacheManager, itemManager, menuManager, configManager);
        this.confirmDeleteListener = new ConfirmDeleteListener(this);
        getServer().getPluginManager().registerEvents(this.confirmDeleteListener, this);

        cacheModeListener.setMenuManager(menuManager);
        getServer().getPluginManager().registerEvents(animationsManager, this);
        getServer().getPluginManager().registerEvents(cacheModeListener, this);
        getServer().getPluginManager().registerEvents(cacheBlockListener, this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        getCommand("cachesmanager").setExecutor(new CommandsHandler(this));
        getCommand("cachesmanager").setTabCompleter(new CommandsTabCompleter(this));

        Bukkit.getScheduler().runTaskLater(this, cacheManager::loadCaches, 5L);

        return true;
    }

    public void reloadPlugin() {
        String error = configManager.reloadConfig();
        if (error != null) return;

        if (lootHistoryManager != null) {
            lootHistoryManager.saveAll();
        }

        console("&#ffff00◆ CachesManager &f| Перезагрузка анимаций...");
        animationsManager.reloadAnimations();

        console("&#ffff00◆ CachesManager &f| Перезагрузка меню...");
        if (menuManager != null) menuManager.reload();

        if (lootHistoryManager != null) lootHistoryManager.reload();

        console("&#ffff00◆ CachesManager &f| Перезагрузка тайников...");
        cacheManager.removeAllHolograms();
        cacheManager.loadCaches();

        if (updateChecker != null) updateChecker.reload();
    }

    private void logStartupInfo(long loadTime) {
        console("&#ffff00 ");
        console("&#ffff00  █▀▀ ▄▀█ █▀▀ █░█ █▀▀ █▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#ffff00  █▄▄ █▀█ █▄▄ █▀█ ██▄ ▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
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

        console("&#FF5D00◆ CachesManager &f| Начало &#FF5D00выгрузки &fплагина...");

        if (lootHistoryManager != null) {
            console("&#FF5D00◆ CachesManager &f| Сохранение всей &#FF5D00истории &fлута...");
            lootHistoryManager.saveAll();
        }

        if (cacheManager != null) {
            console("&#FF5D00◆ CachesManager &f| Сохранение всех &#FF5D00данных &fтайников...");
            configManager.forceSaveAllCacheConfigs();

            if (hologramManager != null) hologramManager.clearAllHolograms();
            if (animationsManager != null) animationsManager.clearAllAnimations();
        }

        long unloadTime = System.currentTimeMillis() - startTime;

        console("&#ffff00 ");
        console("&#ffff00  █▀▀ ▄▀█ █▀▀ █░█ █▀▀ █▀ █▀▄▀█ ▄▀█ █▄░█ ▄▀█ █▀▀ █▀▀ █▀█");
        console("&#ffff00  █▄▄ █▀█ █▄▄ █▀█ ██▄ ▄█ █░▀░█ █▀█ █░▀█ █▀█ █▄█ ██▄ █▀▄");
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
        Bukkit.getConsoleSender().sendMessage(HexColors.translate(message));
    }

    public void log(String message) {
        if (configManager != null && configManager.isLogsInConsoleEnabled()) {
            console("&#ffff00◆ CachesManager &f| " + message);
        }
    }
}