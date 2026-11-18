package com.spiresurge.client;

import com.spiresurge.SpireSurge;
import com.spiresurge.net.SurgeClientState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.lang.reflect.Constructor;
import java.util.Optional;

/**
 * Turns Spawn Spires' flame particles red while they appear inside a surge.
 *
 * Spawn Spires colours these particles from the region colour in their constructors. Rather than poking
 * at live particles, we wrap Spawn Spires' own particle providers (found by reflection, so there is no
 * compile-time dependency): the wrapper asks the original provider for the particle and, if it is inside
 * a surge, calls the public {@code Particle.setColor}. The textures are light and tintable, so no new
 * texture is needed. Registered at the lowest event priority so our wrapper replaces theirs.
 */
final class SurgeParticles {

    private static final String PACKAGE = "net.sandwich.mobtowers.particle.custom.";
    private static final String[][] TARGETS = {
            {"tower_flame", "TowerFlameParticle$Provider"},
            {"monster_spawn_flame", "MonsterSpawnFlameParticle$Provider"},
            {"vanilla_spawner_flame", "VanillaSpawnerFlameParticle$Provider"},
    };

    static volatile boolean enabled = true;
    static volatile float red = 1.0F;
    static volatile float green = 0.16F;
    static volatile float blue = 0.16F;

    private SurgeParticles() {
    }

    static void setColor(int argb) {
        red = ((argb >> 16) & 0xFF) / 255.0F;
        green = ((argb >> 8) & 0xFF) / 255.0F;
        blue = (argb & 0xFF) / 255.0F;
    }

    @SuppressWarnings("unchecked")
    static void register(RegisterParticleProvidersEvent event) {
        for (String[] target : TARGETS) {
            Optional<ParticleType<?>> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(
                    ResourceLocation.fromNamespaceAndPath("mobtowers", target[0]));
            if (type.isEmpty() || !(type.get() instanceof SimpleParticleType simple)) {
                continue;
            }
            Constructor<?> constructor;
            try {
                constructor = Class.forName(PACKAGE + target[1], true, SurgeParticles.class.getClassLoader())
                        .getConstructor(SpriteSet.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                SpireSurge.LOGGER.warn("[Spire Surge] Could not hook the '{}' particle ({}); it will not turn red.",
                        target[0], e.toString());
                continue;
            }
            event.registerSpriteSet(simple, sprites -> {
                final ParticleProvider<SimpleParticleType> original;
                try {
                    original = (ParticleProvider<SimpleParticleType>) constructor.newInstance(sprites);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("Spire Surge could not create the " + target[0] + " particle provider", e);
                }
                return (options, level, x, y, z, vx, vy, vz) -> {
                    Particle particle = original.createParticle(options, level, x, y, z, vx, vy, vz);
                    if (particle != null && enabled && !SurgeClientState.isEmpty() && SurgeClientState.isInside(x, y, z)) {
                        particle.setColor(red, green, blue);
                    }
                    return particle;
                };
            });
        }
    }
}
