package org.gw.cachesmanager.animations;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.platform.*;
import org.gw.cachesmanager.animations.view.*;

public class AnimationEngineConfigurator {

    @FunctionalInterface
    public interface PacketItemMetadataSender {
        void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item);
    }

    public static PacketItemMetadataSender selectPacketPlatform() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("packetevents")
                    || Bukkit.getPluginManager().isPluginEnabled("PacketEvents")) {
                PacketEventsPlatform impl = new PacketEventsPlatform();
                return impl::sendItemHologramMetadata;
            }
        } catch (Exception ignored) {}

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                ProtocolLibPlatform impl = new ProtocolLibPlatform();
                return impl::sendItemHologramMetadata;
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static AnimationView selectAnimationView(CachesManager plugin, PacketItemMetadataSender sender) {
        try {
            Class.forName("org.bukkit.entity.ItemDisplay");
            return new ModernAnimationView(plugin);
        } catch (ClassNotFoundException e) {
            return new LegacyAnimationView(plugin, sender);
        }
    }
}