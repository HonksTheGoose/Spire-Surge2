package com.spiresurge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import com.spiresurge.net.SurgeClientState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns Seeping Grimstone red inside a surge.
 *
 * Spawn Spires already tints that block with a per-region colour (its model has a tint index), so no new
 * texture is needed: we register a colour handler for the same block (lowest priority, so it replaces
 * theirs) that returns our red inside a surge and falls back to Spawn Spires' own region colour everywhere
 * else. When a surge starts or ends the affected chunk sections are marked dirty so they re-mesh.
 */
final class SurgeGrimstone {

    static volatile boolean enabled = true;
    static volatile int color = 0xFFFF3A3A;

    /** What the world looked like the last time we re-meshed: surge cores -> radius, plus the colour signature. */
    private static Map<Long, Integer> meshedCores = Map.of();
    private static int meshedSignature;

    private SurgeGrimstone() {
    }

    static void register(RegisterColorHandlersEvent.Block event) {
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("mobtowers", "seeping_grimstone"));
        block.ifPresent(b -> event.register(SurgeGrimstone::colorFor, b));
    }

    /** Called from chunk-meshing threads, so it only reads volatile / immutable state. */
    private static int colorFor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (pos == null) {
            return -1; // item form / no position, same as Spawn Spires
        }
        if (enabled && !SurgeClientState.isEmpty()
                && SurgeClientState.isInside(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            return color;
        }
        return CoreTint.regionColor(pos);
    }

    /** Re-meshes the affected chunks when the set of surges (or the colour setting) changes. */
    static void refresh(ClientLevel level) {
        Map<Long, Integer> now = SurgeClientState.cores();
        int signature = enabled ? color : 0;
        if (now.equals(meshedCores) && signature == meshedSignature) {
            return;
        }
        Map<Long, Integer> before = meshedCores;
        boolean everything = signature != meshedSignature;
        meshedCores = now;
        meshedSignature = signature;

        Map<Long, Integer> changed = new HashMap<>();
        for (Map.Entry<Long, Integer> e : now.entrySet()) {
            if (everything || !e.getValue().equals(before.get(e.getKey()))) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<Long, Integer> e : before.entrySet()) {
            if (everything || !e.getValue().equals(now.get(e.getKey()))) {
                changed.put(e.getKey(), Math.max(e.getValue(), now.getOrDefault(e.getKey(), 0)));
            }
        }
        for (Map.Entry<Long, Integer> e : changed.entrySet()) {
            BlockPos pos = BlockPos.of(e.getKey());
            int r = e.getValue() + 1;
            Minecraft.getInstance().levelRenderer.setBlocksDirty(
                    pos.getX() - r, Mth.clamp(pos.getY() - r, level.getMinBuildHeight(), level.getMaxBuildHeight()), pos.getZ() - r,
                    pos.getX() + r, Mth.clamp(pos.getY() + r, level.getMinBuildHeight(), level.getMaxBuildHeight()), pos.getZ() + r);
        }
    }

    static void reset() {
        meshedCores = Map.of();
        meshedSignature = 0;
    }
}
