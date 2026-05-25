package org.gw.cachesmanager.animations.platform;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface PacketPlatform {
    void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item);
    void sendEntityRelativeMove(int entityId, Player player, double dx, double dy, double dz, float yaw, float pitch);
}