package org.gw.cachesmanager.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.managers.ItemManager;

import java.util.stream.Stream;

public class KeyIntegrityListener implements Listener {
    private final ItemManager itemManager;

    public KeyIntegrityListener(CachesManager plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onKeyPlace(BlockPlaceEvent event) {
        if (itemManager.isAnyKey(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKeyUse(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !itemManager.isAnyKey(item)) {
            return;
        }

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && isInteractable(clickedBlock) && !player.isSneaking()) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKeyAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);
        if ((first != null && itemManager.isAnyKey(first)) || (second != null && itemManager.isAnyKey(second))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onKeyCraft(CraftItemEvent event) {
        if (recipeContainsKey(event.getRecipe())) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        for (ItemStack matrixItem : inventory.getMatrix()) {
            if (matrixItem != null && itemManager.isAnyKey(matrixItem)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKeyConsume(PlayerItemConsumeEvent event) {
        if (itemManager.isAnyKey(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKeyBucketEmpty(PlayerBucketEmptyEvent event) {
        if (itemManager.isAnyKey(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKeyDispense(BlockDispenseEvent event) {
        if (itemManager.isAnyKey(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean recipeContainsKey(Recipe recipe) {
        Stream<RecipeChoice> choices;
        if (recipe instanceof ShapedRecipe shaped) {
            choices = shaped.getChoiceMap().values().stream();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            choices = shapeless.getChoiceList().stream();
        } else {
            return false;
        }
        return choices
                .filter(RecipeChoice.ExactChoice.class::isInstance)
                .map(RecipeChoice.ExactChoice.class::cast)
                .flatMap(choice -> choice.getChoices().stream())
                .anyMatch(itemManager::isAnyKey);
    }

    private boolean isInteractable(Block block) {
        try {
            return block.getType().isInteractable();
        } catch (NoSuchMethodError ignored) {
            return false;
        }
    }
}
