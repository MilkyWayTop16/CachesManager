package org.gw.cachesmanager.animations.platform;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProtocolLibPlatform implements PacketPlatform {
    @Override
    public void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item) {
        try {
            WrappedChatComponent component;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                component = WrappedChatComponent.fromLegacyText(HexColors.translate(item.getItemMeta().getDisplayName().trim()));
            } else {
                String key = item.getType().getKey().getKey();
                String namespace = item.getType().getKey().getNamespace();
                String translationKey = item.getType().isBlock() ? "block." + namespace + "." + key : "item." + namespace + "." + key;
                String json = "{\"translate\":\"" + translationKey + "\",\"italic\":false}";
                component = WrappedChatComponent.fromJson(json);
            }
            List<WrappedDataValue> dataValues = new ArrayList<>();
            dataValues.add(new WrappedDataValue(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true), Optional.of(component.getHandle())));
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, armorStand.getEntityId());
            packet.getDataValueCollectionModifier().write(0, dataValues);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception ignored) {}
    }

    @Override
    public void sendEntityRelativeMove(int entityId, Player player, double dx, double dy, double dz, float yaw, float pitch) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_MOVE_LOOK);
            packet.getIntegers().write(0, entityId);
            packet.getShorts().write(0, (short) (dx * 4096));
            packet.getShorts().write(1, (short) (dy * 4096));
            packet.getShorts().write(2, (short) (dz * 4096));
            packet.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
            packet.getBytes().write(1, (byte) (pitch * 256.0F / 360.0F));
            packet.getBooleans().write(0, true);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception ignored) {}
    }
}