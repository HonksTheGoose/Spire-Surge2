package com.spiresurge.client;

import com.spiresurge.SpireSurge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Recolours the Spire Heart through reflection, so this addon needs no compile-time dependency on the
 * Spawn Spires jar.
 *
 * How it works: Spawn Spires' renderer draws the Heart's flame and the glowing parts of its jaws using
 * the public int field {@code MonsterFlameEntity.tintColor} (the flame texture is greyscale on purpose,
 * the colour comes entirely from that field). Spawn Spires sets it once, when the block entity first
 * loads, from the region colour. We simply overwrite it while a surge is active and put the original
 * colour back afterwards. No custom sprite is needed.
 */
final class CoreTint {

    private static final String ENTITY_CLASS = "net.sandwich.mobtowers.block.entity.MonsterFlame.MonsterFlameEntity";
    private static final String REGION_CLASS = "net.sandwich.mobtowers.mobregion.MobRegion";

    private static boolean initialised;
    private static Class<?> entityClass;
    private static Field tintField;
    private static Method regionColor;

    private CoreTint() {
    }

    private static synchronized boolean ready() {
        if (initialised) {
            return tintField != null;
        }
        initialised = true;
        try {
            ClassLoader loader = CoreTint.class.getClassLoader();
            entityClass = Class.forName(ENTITY_CLASS, true, loader);
            tintField = entityClass.getField("tintColor");
            if (tintField.getType() != int.class) {
                tintField = null;
                throw new NoSuchFieldException("tintColor is not an int");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            tintField = null;
            SpireSurge.LOGGER.warn("[Spire Surge] Could not hook the Spire Heart's colour ({}). "
                    + "The Heart will not turn red, but surges still work and red particles are still shown.", e.toString());
            return false;
        }
        try {
            Class<?> region = Class.forName(REGION_CLASS, true, CoreTint.class.getClassLoader());
            regionColor = region.getMethod("getMobRegionColor", BlockPos.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            regionColor = null; // restore() falls back to white, which still shows the greyscale sprite
        }
        return true;
    }

    /**
     * Spawn Spires' normal colour for a position (the colour of the region it lies in). Used for blocks
     * that are not surging. Falls back to white, which shows the texture untinted.
     */
    static int regionColor(BlockPos pos) {
        Method method = regionMethod();
        if (method == null) {
            return 0xFFFFFFFF;
        }
        try {
            return (Integer) method.invoke(null, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0xFFFFFFFF;
        }
    }

    private static boolean regionTried;
    private static Method regionLookup;

    private static synchronized Method regionMethod() {
        if (!regionTried) {
            regionTried = true;
            try {
                Class<?> region = Class.forName(REGION_CLASS, true, CoreTint.class.getClassLoader());
                regionLookup = region.getMethod("getMobRegionColor", BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                regionLookup = null;
            }
        }
        return regionLookup;
    }

    /** Sets the tint if this block entity is a Spire Heart. Returns true if it is one. */
    static boolean apply(BlockEntity entity, int argb) {
        if (!ready() || !entityClass.isInstance(entity)) {
            return false;
        }
        try {
            if (tintField.getInt(entity) != argb) {
                tintField.setInt(entity, argb);
            }
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    /** Puts the Heart's original region colour back. */
    static void restore(BlockEntity entity, BlockPos pos) {
        if (!ready() || !entityClass.isInstance(entity)) {
            return;
        }
        int color = 0xFFFFFFFF;
        if (regionColor != null) {
            try {
                color = (Integer) regionColor.invoke(null, pos);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // keep the white fallback
            }
        }
        try {
            tintField.setInt(entity, color);
        } catch (IllegalAccessException | RuntimeException ignored) {
            // nothing more we can do
        }
    }
}
