package com.spiresurge.client;

import com.spiresurge.SurgeConfig;
import com.spiresurge.net.SurgeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Client side of the addon: keeps every surging Spire Heart red, and hooks the red particles and red
 * Seeping Grimstone (see {@link SurgeParticles} and {@link SurgeGrimstone}).
 *
 * Runs once per client tick, after the block entities have ticked, so our colour always wins over
 * Spawn Spires' own (it only writes the colour when the block entity first loads, but we re-apply it
 * every tick anyway so chunk reloads and the pulse effect are handled for free).
 */
public final class SurgeClient {

    /** Hearts whose colour we have changed and must restore later. */
    private static final Set<Long> TINTED = new HashSet<>();

    private SurgeClient() {
    }

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(SurgeClient::onClientTick);
        // Lowest priority so our handlers replace Spawn Spires' own registrations for the same things.
        modEventBus.addListener(EventPriority.LOWEST, SurgeParticles::register);
        modEventBus.addListener(EventPriority.LOWEST, SurgeGrimstone::register);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TINTED.clear();
            SurgeClientState.clear();
            SurgeGrimstone.reset();
            return;
        }

        // Settings for the particle / block hooks (they run on other threads, so they read plain fields).
        SurgeParticles.enabled = SurgeConfig.RED_PARTICLES.get();
        SurgeParticles.setColor(SurgeConfig.particleColor());
        SurgeGrimstone.enabled = SurgeConfig.RED_GRIMSTONE.get();
        SurgeGrimstone.color = SurgeConfig.grimstoneColor();
        SurgeGrimstone.refresh(level);

        // The glow on the Heart itself is optional; with it off we only restore anything we changed earlier.
        Set<Long> active = SurgeConfig.TINT_CORE.get() ? SurgeClientState.cores().keySet() : Set.of();

        // Hearts that stopped surging: give them their normal colour back.
        if (!TINTED.isEmpty()) {
            Iterator<Long> it = TINTED.iterator();
            while (it.hasNext()) {
                long key = it.next();
                if (!active.contains(key)) {
                    BlockPos pos = BlockPos.of(key);
                    if (level.hasChunkAt(pos)) {
                        BlockEntity entity = level.getBlockEntity(pos);
                        if (entity != null) {
                            CoreTint.restore(entity, pos);
                        }
                    }
                    it.remove();
                }
            }
        }

        if (active.isEmpty()) {
            return;
        }

        long time = level.getGameTime();
        int color = currentColor(time);
        for (long key : active) {
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunkAt(pos)) {
                TINTED.remove(key); // chunk unloaded; a fresh block entity gets re-tinted when it loads again
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null && CoreTint.apply(entity, color)) {
                TINTED.add(key);
            }
            if (time % 4L == 0L) {
                emberParticles(level, pos);
            }
        }
    }

    /** The configured glow colour, gently pulsing if enabled. */
    private static int currentColor(long time) {
        int base = SurgeConfig.tintColor();
        if (!SurgeConfig.PULSE.get()) {
            return base;
        }
        double wave = 0.8 + 0.2 * Math.sin(time * 0.18);
        int r = Mth.clamp((int) (((base >> 16) & 0xFF) * wave), 0, 255);
        int g = Mth.clamp((int) (((base >> 8) & 0xFF) * wave), 0, 255);
        int b = Mth.clamp((int) ((base & 0xFF) * wave), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A little red dust around the Heart. Also the visual cue if the colour hook is ever unavailable. */
    private static void emberParticles(ClientLevel level, BlockPos pos) {
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.2;
            double y = pos.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 1.2;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.2;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
