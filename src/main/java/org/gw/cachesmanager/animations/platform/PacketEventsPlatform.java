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
            Component nameComponent;

            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                nameComponent = Component.text(HexColors.getItemNameLegacy(item));
            } else {
                String key = HexColors.getItemTranslationKey(item);
                nameComponent = Component.translatable(key);
            }

            String json = HexColors.toGsonJsonFromComponent(nameComponent);
            List<EntityData> dataList = List.of(new EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT, Optional.of(json)));
            var packet = new WrapperPlayServerEntityMetadata(armorStand.getEntityId(), dataList);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception ignored) {}
    }
}