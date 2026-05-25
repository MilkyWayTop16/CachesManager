package org.gw.cachesmanager.animations.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.gw.cachesmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PacketEventsPlatform implements PacketPlatform {
    @Override
    public void sendItemHologramMetadata(ArmorStand armorStand, Player player, ItemStack item) {
        try {
            String json;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                Component component = Component.text(HexColors.translate(item.getItemMeta().getDisplayName().trim()));
                json = GsonComponentSerializer.gson().serialize(component);
            } else {
                String key = item.getType().getKey().getKey();
                String namespace = item.getType().getKey().getNamespace();
                String translationKey = item.getType().isBlock() ? "block." + namespace + "." + key : "item." + namespace + "." + key;
                json = "{\"translate\":\"" + translationKey + "\",\"italic\":false}";
            }
            List<EntityData> dataList = new ArrayList<>();
            dataList.add(new EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT, Optional.of(json)));
            WrapperPlayServerEntityMetadata metadataWrapper = new WrapperPlayServerEntityMetadata(armorStand.getEntityId(), dataList);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, metadataWrapper);
        } catch (Exception ignored) {}
    }

    @Override
    public void sendEntityRelativeMove(int entityId, Player player, double dx, double dy, double dz, float yaw, float pitch) {
        try {
            WrapperPlayServerEntityRelativeMoveAndRotation moveWrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(
                    entityId, (short) (dx * 4096), (short) (dy * 4096), (short) (dz * 4096), yaw, pitch, true
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, moveWrapper);
        } catch (Exception ignored) {}
    }
}