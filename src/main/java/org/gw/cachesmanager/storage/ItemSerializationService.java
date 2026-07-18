package org.gw.cachesmanager.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

public final class ItemSerializationService {

    public String serializeItem(ItemStack item) {
        if (item == null) return "";
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public ItemStack deserializeItem(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }

        ItemStack item = deserializeBukkitObject(data);
        if (item != null) return item;

        item = deserializePaperBytes(data);
        if (item != null) return item;

        item = deserializeConfigurationMap(data);
        return item;
    }

    private ItemStack deserializeBukkitObject(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object read = dataInput.readObject();
            if (read instanceof ItemStack stack) {
                return stack;
            }
        } catch (Throwable ignored) {
        }

        try {
            String compact = data.replaceAll("\\s", "");
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(compact));
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                Object read = dataInput.readObject();
                if (read instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ItemStack deserializePaperBytes(String data) {
        try {
            byte[] bytes = decodeBase64Flexible(data);
            if (bytes == null || bytes.length == 0) return null;
            return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserializeConfigurationMap(String data) {
        try {
            byte[] bytes = decodeBase64Flexible(data);
            if (bytes == null) return null;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                Object read = dataInput.readObject();
                if (read instanceof Map<?, ?> map) {
                    return ItemStack.deserialize((Map<String, Object>) map);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private byte[] decodeBase64Flexible(String data) {
        try {
            return Base64Coder.decodeLines(data);
        } catch (Throwable ignored) {
        }
        try {
            return Base64.getDecoder().decode(data.replaceAll("\\s", ""));
        } catch (Throwable ignored) {
        }
        try {
            return Base64.getMimeDecoder().decode(data);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
