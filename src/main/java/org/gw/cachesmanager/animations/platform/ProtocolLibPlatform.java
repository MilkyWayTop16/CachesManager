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

import java.util.List;
import java.util.Optional;

public class ProtocolLibPlatform {

    public void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item) {
        try {
            WrappedChatComponent component;

            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String legacyName = HexColors.getItemNameLegacy(item);
                component = WrappedChatComponent.fromLegacyText(HexColors.translate(legacyName));
            } else {
                String translationKey = HexColors.getItemTranslationKey(item);
                String json = "{\"translate\":\"" + translationKey + "\",\"color\":\"white\",\"italic\":false}";
                component = WrappedChatComponent.fromJson(json);
            }

            List<WrappedDataValue> dataValues = List.of(
                    new WrappedDataValue(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true), Optional.of(component.getHandle()))
            );

            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, armorStand.getEntityId());
            packet.getDataValueCollectionModifier().write(0, dataValues);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception ignored) {}
    }

}