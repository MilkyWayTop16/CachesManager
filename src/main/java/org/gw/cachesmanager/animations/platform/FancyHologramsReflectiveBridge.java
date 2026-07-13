package org.gw.cachesmanager.animations.platform;

import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public final class FancyHologramsReflectiveBridge {

    private static volatile boolean resolved = false;
    private static volatile boolean available = false;
    private static volatile String failureReason = null;

    private static Method pluginIsEnabled;
    private static Method pluginGet;
    private static Method getHologramManager;

    private static Class<?> textHologramDataClass;
    private static Constructor<?> textHologramDataConstructor;
    private static Method dataSetText;
    private static Method dataSetPersistent;
    private static Method hologramSetPersistent;

    private static Method managerCreate;
    private static Method managerAddHologram;
    private static Method managerRemoveHologram;
    private static Method managerGetHologram;

    private static Method hologramGetData;
    private static Method hologramQueueUpdate;
    private static Method hologramForceUpdate;

    private FancyHologramsReflectiveBridge() {}

    public static boolean isAvailable() {
        ensureResolved();
        return available;
    }

    public static String getFailureReason() {
        ensureResolved();
        return failureReason;
    }

    private static synchronized void ensureResolved() {
        if (resolved) return;
        resolved = true;

        try {
            Class<?> pluginClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
            Class<?> managerClass = Class.forName("de.oliver.fancyholograms.api.HologramManager");
            Class<?> hologramClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
            Class<?> hologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData");
            textHologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");

            pluginIsEnabled = pluginClass.getMethod("isEnabled");
            pluginGet = pluginClass.getMethod("get");
            getHologramManager = pluginClass.getMethod("getHologramManager");

            textHologramDataConstructor = textHologramDataClass.getConstructor(String.class, Location.class);
            dataSetText = findMethod(textHologramDataClass, "setText", List.class);
            dataSetPersistent = findMethod(textHologramDataClass, "setPersistent", boolean.class);
            hologramSetPersistent = findMethod(hologramClass, "setPersistent", boolean.class);

            managerCreate = findMethod(managerClass, "create", hologramDataClass);
            managerAddHologram = findMethod(managerClass, "addHologram", hologramClass);
            managerRemoveHologram = findMethod(managerClass, "removeHologram", hologramClass);
            managerGetHologram = findMethod(managerClass, "getHologram", String.class);

            hologramGetData = findMethod(hologramClass, "getData");
            hologramQueueUpdate = findMethod(hologramClass, "queueUpdate");
            hologramForceUpdate = findMethod(hologramClass, "forceUpdate");

            List<String> missing = new java.util.ArrayList<>();
            if (pluginIsEnabled == null) missing.add("FancyHologramsPlugin#isEnabled");
            if (pluginGet == null) missing.add("FancyHologramsPlugin#get");
            if (getHologramManager == null) missing.add("FancyHologramsPlugin#getHologramManager");
            if (textHologramDataConstructor == null) missing.add("TextHologramData(String, Location)");
            if (dataSetText == null) missing.add("TextHologramData#setText");
            if (managerCreate == null) missing.add("HologramManager#create");
            if (managerAddHologram == null) missing.add("HologramManager#addHologram");
            if (managerRemoveHologram == null) missing.add("HologramManager#removeHologram");
            if (managerGetHologram == null) missing.add("HologramManager#getHologram");
            if (hologramGetData == null) missing.add("Hologram#getData");
            if (hologramQueueUpdate == null && hologramForceUpdate == null) missing.add("Hologram#queueUpdate/forceUpdate");

            if (!missing.isEmpty()) {
                failureReason = "не найдены методы API: " + String.join(", ", missing);
                available = false;
                return;
            }

            available = true;
        } catch (Throwable t) {
            failureReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            available = false;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static boolean isPluginEnabled() {
        try {
            return (boolean) pluginIsEnabled.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object getManager() throws Exception {
        Object pluginInstance = pluginGet.invoke(null);
        return getHologramManager.invoke(pluginInstance);
    }

    public static Object createTextHologramData(String id, Location location) throws Exception {
        return textHologramDataConstructor.newInstance(id, location);
    }

    public static void setText(Object data, List<String> lines) throws Exception {
        dataSetText.invoke(data, lines);
    }

    public static void setNotPersistent(Object data, Object hologram) {
        try {
            if (dataSetPersistent != null) {
                dataSetPersistent.invoke(data, false);
            }
        } catch (Throwable ignored) {}

        try {
            if (hologramSetPersistent != null && hologram != null) {
                hologramSetPersistent.invoke(hologram, false);
            }
        } catch (Throwable ignored) {}
    }

    public static Object create(Object manager, Object data) throws Exception {
        return managerCreate.invoke(manager, data);
    }

    public static void addHologram(Object manager, Object hologram) throws Exception {
        managerAddHologram.invoke(manager, hologram);
    }

    public static void removeHologram(Object manager, Object hologram) throws Exception {
        managerRemoveHologram.invoke(manager, hologram);
    }

    @SuppressWarnings("unchecked")
    public static Optional<Object> getHologram(Object manager, String id) throws Exception {
        Object result = managerGetHologram.invoke(manager, id);
        if (result instanceof Optional) {
            return (Optional<Object>) result;
        }
        return Optional.ofNullable(result);
    }

    public static Object getData(Object hologram) throws Exception {
        return hologramGetData.invoke(hologram);
    }

    public static void queueUpdate(Object hologram) throws Exception {
        if (hologramQueueUpdate != null) {
            hologramQueueUpdate.invoke(hologram);
        } else if (hologramForceUpdate != null) {
            hologramForceUpdate.invoke(hologram);
        }
    }

    public static boolean isTextHologramData(Object data) {
        return textHologramDataClass != null && textHologramDataClass.isInstance(data);
    }
}
