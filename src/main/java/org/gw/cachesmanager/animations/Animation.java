package org.gw.cachesmanager.animations;

import lombok.Getter;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.List;

@Getter
public class Animation {
    private final String key;
    private final String name;
    private final List<ParticleEntry> delayParticles;
    private final List<SoundEntry> delaySounds;
    private final int delayDuration;
    private final List<ParticleEntry> itemParticles;
    private final List<SoundEntry> itemSounds;
    private final int itemDuration;
    private final double itemHeight;
    private final double rotationSpeed;
    private final List<ParticleEntry> finalParticles;
    private final List<SoundEntry> finalSounds;
    private final Particle ambientParticleType;
    private final int ambientParticleAmount;
    private final double ambientOffsetX, ambientOffsetY, ambientOffsetZ;
    private final double ambientParticleSpeed;
    private final int ambientInterval;
    private final Color ambientColor;
    private final float ambientSize;
    private final String ambientShape;
    private final double ambientRadius;
    private final FireworkEffect.Type fireworkType;
    private final List<Color> fireworkColors;
    private final List<Color> fireworkFadeColors;
    private final boolean fireworkTrail;
    private final boolean fireworkFlicker;
    private final double fireworkHeight;
    private final int fireworkPower;
    private final int fireworkFlightDuration;
    private final int postExplosionDelay;

    public Animation(String key, String name,
                     List<ParticleEntry> delayParticles, List<SoundEntry> delaySounds, int delayDuration,
                     List<ParticleEntry> itemParticles, List<SoundEntry> itemSounds, int itemDuration, double itemHeight, double rotationSpeed,
                     List<ParticleEntry> finalParticles, List<SoundEntry> finalSounds,
                     Particle ambientParticleType, int ambientParticleAmount,
                     double ambientOffsetX, double ambientOffsetY, double ambientOffsetZ,
                     double ambientParticleSpeed, int ambientInterval,
                     Color ambientColor, float ambientSize, String ambientShape, double ambientRadius,
                     FireworkEffect.Type fireworkType, List<Color> fireworkColors, List<Color> fireworkFadeColors,
                     boolean fireworkTrail, boolean fireworkFlicker,
                     double fireworkHeight, int fireworkPower, int fireworkFlightDuration, int postExplosionDelay) {
        this.key = key;
        this.name = name;
        this.delayParticles = delayParticles;
        this.delaySounds = delaySounds;
        this.delayDuration = delayDuration;
        this.itemParticles = itemParticles;
        this.itemSounds = itemSounds;
        this.itemDuration = itemDuration;
        this.itemHeight = itemHeight;
        this.rotationSpeed = rotationSpeed;
        this.finalParticles = finalParticles;
        this.finalSounds = finalSounds;
        this.ambientParticleType = ambientParticleType;
        this.ambientParticleAmount = ambientParticleAmount;
        this.ambientOffsetX = ambientOffsetX;
        this.ambientOffsetY = ambientOffsetY;
        this.ambientOffsetZ = ambientOffsetZ;
        this.ambientParticleSpeed = ambientParticleSpeed;
        this.ambientInterval = ambientInterval;
        this.ambientColor = ambientColor;
        this.ambientSize = ambientSize;
        this.ambientShape = ambientShape;
        this.ambientRadius = ambientRadius;
        this.fireworkType = fireworkType;
        this.fireworkColors = fireworkColors;
        this.fireworkFadeColors = fireworkFadeColors;
        this.fireworkTrail = fireworkTrail;
        this.fireworkFlicker = fireworkFlicker;
        this.fireworkHeight = fireworkHeight;
        this.fireworkPower = fireworkPower;
        this.fireworkFlightDuration = fireworkFlightDuration;
        this.postExplosionDelay = postExplosionDelay;
    }

    public record SoundEntry(Sound sound, float volume, float pitch) {}
    public record ParticleEntry(Particle type, int amount, double offsetX, double offsetY, double offsetZ, double speed) {}
}