package org.gw.cachesmanager.animations;

import lombok.Getter;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.cachesmanager.CachesManager;

import java.io.File;
import java.util.*;

public class AnimationRegistry {
    private final CachesManager plugin;
    private final File file;
    @Getter
    private final Map<String, Animation> animations = new HashMap<>();
    private final List<String> animationOrder = new ArrayList<>();

    public AnimationRegistry(CachesManager plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "animations.yml");
        load();
    }

    public void load() {
        animations.clear();
        animationOrder.clear();
        if (!file.exists()) {
            plugin.saveResource("animations.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("animations");
        if (section == null) {
            plugin.error("Критическая &#FB8808ошибка &fконфигурации! Раздел &#FB8808animations &fне найден в файле &#FB8808animations.yml&f...");
            return;
        }

        for (String key : section.getKeys(false)) {
            List<Animation.SoundEntry> delaySounds = parseSounds(config, "animations." + key + ".delay.sounds");
            List<Animation.SoundEntry> itemSounds = parseSounds(config, "animations." + key + ".item.sounds");
            List<Animation.SoundEntry> finalSounds = parseSounds(config, "animations." + key + ".final.sounds");

            List<Animation.ParticleEntry> delayParticles = parseParticles(config, "animations." + key + ".delay.particles");
            List<Animation.ParticleEntry> itemParticles = parseParticles(config, "animations." + key + ".item.particles");
            List<Animation.ParticleEntry> finalParticles = parseParticles(config, "animations." + key + ".final.particles");

            String colorStr = config.getString("animations." + key + ".ambient.particles.color", "#FFFFFF");
            Color ambientColor = Color.WHITE;
            try {
                if (colorStr != null && colorStr.length() == 7 && colorStr.startsWith("#")) {
                    ambientColor = Color.fromRGB(
                            Integer.parseInt(colorStr.substring(1, 3), 16),
                            Integer.parseInt(colorStr.substring(3, 5), 16),
                            Integer.parseInt(colorStr.substring(5, 7), 16)
                    );
                }
            } catch (Exception e) {
                plugin.error("Не удалось корректно распознать hex-цвет частицы &#FB8808" + colorStr + " &fв анимации &#FB8808" + key + "...");
            }

            float ambientSize = (float) config.getDouble("animations." + key + ".ambient.particles.size", 1.0);
            String ambientShape = config.getString("animations." + key + ".ambient.particles.shape", "box").toLowerCase();
            double ambientRadius = config.getDouble("animations." + key + ".ambient.particles.radius", 0.6);

            FireworkEffect.Type fwType = FireworkEffect.Type.BALL_LARGE;
            List<Color> fwColors = new ArrayList<>();
            List<Color> fwFadeColors = new ArrayList<>();
            boolean fwTrail = true;
            boolean fwFlicker = true;

            if ("firework".equals(key)) {
                String typeStr = config.getString("animations." + key + ".explosion.type", "BALL_LARGE").toUpperCase();
                try { fwType = FireworkEffect.Type.valueOf(typeStr); } catch (Exception e) {
                    plugin.error("Указан неизвестный тип взрыва фейерверка &#FB8808" + typeStr + " &fв анимации firework...");
                }
                for (String c : config.getStringList("animations." + key + ".explosion.colors")) {
                    try {
                        if (c != null && c.length() == 7 && c.startsWith("#")) {
                            fwColors.add(Color.fromRGB(Integer.parseInt(c.substring(1, 3), 16), Integer.parseInt(c.substring(3, 5), 16), Integer.parseInt(c.substring(5, 7), 16)));
                        }
                    } catch (Exception ignored) {}
                }
                for (String c : config.getStringList("animations." + key + ".explosion.fade-colors")) {
                    try {
                        if (c != null && c.length() == 7 && c.startsWith("#")) {
                            fwFadeColors.add(Color.fromRGB(Integer.parseInt(c.substring(1, 3), 16), Integer.parseInt(c.substring(3, 5), 16), Integer.parseInt(c.substring(5, 7), 16)));
                        }
                    } catch (Exception ignored) {}
                }
                fwTrail = config.getBoolean("animations." + key + ".explosion.trail", true);
                fwFlicker = config.getBoolean("animations." + key + ".explosion.flicker", true);
            }

            Animation animation = new Animation(
                    key, config.getString("animations." + key + ".name", key),
                    delayParticles, delaySounds, config.getInt("animations." + key + ".delay.duration", 60),
                    itemParticles, itemSounds, config.getInt("animations." + key + ".item.duration", 90),
                    config.getDouble("animations." + key + ".item.height", 1.3),
                    config.getDouble("animations." + key + ".item.rotation-speed", 5.0),
                    finalParticles, finalSounds, Particle.REDSTONE,
                    config.getInt("animations." + key + ".ambient.particles.amount", 10),
                    config.getDouble("animations." + key + ".ambient.particles.offset-x", 0.5),
                    config.getDouble("animations." + key + ".ambient.particles.offset-y", 0.1),
                    config.getDouble("animations." + key + ".ambient.particles.offset-z", 0.5),
                    config.getDouble("animations." + key + ".ambient.particles.speed", 0.0),
                    config.getInt("animations." + key + ".ambient.interval", 4),
                    ambientColor, ambientSize, ambientShape, ambientRadius,
                    fwType, fwColors, fwFadeColors, fwTrail, fwFlicker,
                    0.0, 2, 45, 20
            );
            animations.put(key, animation);
            animationOrder.add(key);
        }
        plugin.log("Успшено &#ffff00зарегистрировано &fконфигураций уникальных эффектов и анимаций: &#ffff00" + animations.size());
    }

    private List<Animation.ParticleEntry> parseParticles(FileConfiguration config, String path) {
        List<Animation.ParticleEntry> list = new ArrayList<>();
        if (config.isList(path)) {
            for (Map<?, ?> map : config.getMapList(path)) {
                try {
                    list.add(new Animation.ParticleEntry(
                            Particle.valueOf((String) map.get("type")),
                            map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 25,
                            map.containsKey("offset-x") ? ((Number) map.get("offset-x")).doubleValue() : 0.3,
                            map.containsKey("offset-y") ? ((Number) map.get("offset-y")).doubleValue() : 0.3,
                            map.containsKey("offset-z") ? ((Number) map.get("offset-z")).doubleValue() : 0.3,
                            map.containsKey("speed") ? ((Number) map.get("speed")).doubleValue() : 0.1
                    ));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private List<Animation.SoundEntry> parseSounds(FileConfiguration config, String path) {
        List<Animation.SoundEntry> list = new ArrayList<>();
        if (config.isList(path)) {
            for (Map<?, ?> map : config.getMapList(path)) {
                try {
                    list.add(new Animation.SoundEntry(
                            Sound.valueOf((String) map.get("type")),
                            map.containsKey("volume") ? ((Number) map.get("volume")).floatValue() : 1.0f,
                            map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 1.0f
                    ));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public String getNext(String current) {
        if (animationOrder.size() <= 1) return null;
        int idx = animationOrder.indexOf(current);
        if (idx == -1 || idx == animationOrder.size() - 1) return animationOrder.get(0);
        return animationOrder.get(idx + 1);
    }

    public String getPrevious(String current) {
        if (animationOrder.size() <= 1) return null;
        int idx = animationOrder.indexOf(current);
        if (idx <= 0) return animationOrder.get(animationOrder.size() - 1);
        return animationOrder.get(idx - 1);
    }
}