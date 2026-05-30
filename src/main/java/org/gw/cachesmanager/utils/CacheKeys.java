package org.gw.cachesmanager.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.gw.cachesmanager.CachesManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("rawtypes")
public enum CacheKeys {

    CACHE_NAME("cache-name", PersistentDataType.STRING),
    KEY_UUID("key-uuid", PersistentDataType.STRING),
    GHOST("ghost", PersistentDataType.STRING),
    HOLOGRAM_ID("hologram_id", PersistentDataType.STRING);

    private final CachesManager plugin = CachesManager.getPlugin(CachesManager.class);

    private final String keyName;
    private final PersistentDataType type;

    CacheKeys(@NotNull final String keyName, @NotNull final PersistentDataType type) {
        this.keyName = keyName;
        this.type = type;
    }

    public @NotNull final NamespacedKey getNamespacedKey() {
        return new NamespacedKey(this.plugin, this.plugin.getName().toLowerCase() + "_" + this.keyName);
    }

    public @NotNull final PersistentDataType getType() {
        return this.type;
    }
}
