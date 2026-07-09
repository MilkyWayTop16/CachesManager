package org.gw.cachesmanager.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ItemManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class KeyIntegrityListener implements Listener {
    private final CachesManager plugin;
    private final ItemManager itemManager;
    private final Set<UUID> pendingRepair = new HashSet<>();

    public KeyIntegrityListener(CachesManager plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleRepair(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleRepair(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemManager.repairKeyStack(dropped)) {
            event.getItemDrop().setItemStack(dropped);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (itemManager.repairKeyStack(stack)) {
            event.getItem().setItemStack(stack);
        }
        scheduleRepair(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRepair(event.getPlayer());
    }

    private void scheduleRepair(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!pendingRepair.add(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRepair.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                return;
            }

            int repaired = itemManager.repairKeysInInventory(online);
            if (repaired > 0) {
                plugin.log("Восстановлены Pdc-метки ключей у игрока &#ffff00" + online.getName()
                        + " &f(предметов: &#ffff00" + repaired + "&f, режим игры: &#ffff00"
                        + online.getGameMode() + "&f)");
            }
        });
    }
}
