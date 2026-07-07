package org.gw.cachesmanager.animations.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.utils.HexColors;

import java.util.List;
import java.util.Optional;

public class PacketEventsPlatform {

    public void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item) {
        try {
            Component nameComponent = HexColors.getItemNameComponent(item);

            List<EntityData> dataList = List.of(new EntityData(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(nameComponent)));
            var packet = new WrapperPlayServerEntityMetadata(armorStand.getEntityId(), dataList);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception ignored) {}
    }
}