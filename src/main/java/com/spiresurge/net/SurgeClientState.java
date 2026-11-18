package com.spiresurge.net;

import java.util.Collection;
import java.util.Map;

/**
 * What the client currently knows about surging Spire Hearts: packed {@code BlockPos.asLong()} -> surge radius.
 *
 * Deliberately a plain class with no Minecraft client imports, so the network handler can reference it
 * on a dedicated server without loading any client-only code. Snapshots are immutable, so it is safe to
 * read from the chunk-meshing threads.
 */
public final class SurgeClientState {

    private static volatile Map<Long, Integer> cores = Map.of();

    private SurgeClientState() {
    }

    public static void set(Collection<SurgeSyncPayload.Entry> entries) {
        java.util.HashMap<Long, Integer> map = new java.util.HashMap<>();
        for (SurgeSyncPayload.Entry e : entries) {
            map.put(e.pos(), e.radius());
        }
        cores = Map.copyOf(map);
    }

    /** Packed position -> radius of every surging Heart. */
    public static Map<Long, Integer> cores() {
        return cores;
    }

    public static boolean isEmpty() {
        return cores.isEmpty();
    }

    /** True if the point is inside any surge (same sphere test the server uses). */
    public static boolean isInside(double x, double y, double z) {
        for (Map.Entry<Long, Integer> e : cores.entrySet()) {
            long key = e.getKey();
            double dx = x - (net.minecraft.core.BlockPos.getX(key) + 0.5);
            double dy = y - (net.minecraft.core.BlockPos.getY(key) + 0.5);
            double dz = z - (net.minecraft.core.BlockPos.getZ(key) + 0.5);
            double r = e.getValue();
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        cores = Map.of();
    }
}
