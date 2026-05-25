package org.gw.cachesmanager.animations;

import org.bukkit.Bukkit;
import org.gw.cachesmanager.CachesManager;
import org.gw.cachesmanager.animations.platform.*;
import org.gw.cachesmanager.animations.view.*;

public class AnimationEngineConfigurator {
    public static PacketPlatform selectPacketPlatform() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PacketEvents")) {
                return (PacketPlatform) Class.forName("org.gw.cachesmanager.animations.platform.PacketEventsPlatform")
                        .getDeclaredConstructor().newInstance();
            }
        } catch (Exception ignored) {}

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                return (PacketPlatform) Class.forName("org.gw.cachesmanager.animations.platform.ProtocolLibPlatform")
                        .getDeclaredConstructor().newInstance();
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static HologramPlatform selectHologramPlatform() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
                return (HologramPlatform) Class.forName("org.gw.cachesmanager.animations.platform.FancyHologramsPlatform")
                        .getDeclaredConstructor().newInstance();
            }
        } catch (Exception ignored) {}

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                return (HologramPlatform) Class.forName("org.gw.cachesmanager.animations.platform.DecentHologramsPlatform")
                        .getDeclaredConstructor().newInstance();
            }
        } catch (Exception ignored) {}

        return new ModernMinecraftPlatform(CachesManager.getPlugin(CachesManager.class));
    }

    public static AnimationView selectAnimationView(CachesManager plugin, PacketPlatform packetPlatform) {
        try {
            Class.forName("org.bukkit.entity.ItemDisplay");
            return new ModernAnimationView(plugin);
        } catch (ClassNotFoundException e) {
            return new LegacyAnimationView(plugin, packetPlatform);
        }
    }
}