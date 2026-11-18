package com.spiresurge.loot;

import com.spiresurge.SurgeConfig;
import com.spiresurge.SurgeManager;
import com.spiresurge.data.EntityFilter;
import com.spiresurge.data.MobPool;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The rules and maths behind the better loot, shared by the loot modifier and the XP hook. */
public final class SurgeLoot {

    private SurgeLoot() {
    }

    /** Does this dying entity qualify for better loot / experience under the current config? */
    public static boolean qualifies(Entity entity, ServerLevel level) {
        if (!SurgeConfig.ENABLED.get() || !SurgeConfig.LOOT_ENABLED.get() || !(entity instanceof Mob mob)) {
            return false;
        }
        if (EntityFilter.lootBlacklist().blocks(mob.getType())) {
            return false;
        }
        boolean surgeSpawned = mob.getTags().contains(SurgeManager.TAG_SPAWNED);
        switch (SurgeConfig.LOOT_SCOPE.get()) {
            case SURGE_SPAWNED:
                return surgeSpawned;
            case INSIDE_SURGE:
                return surgeSpawned || (isMonster(mob) && SurgeManager.isInsideSurge(level, mob.position()));
            case EVERYWHERE:
            default:
                return surgeSpawned || isMonster(mob);
        }
    }

    /** Hostile mobs, plus anything that is in the surge mob pool (which may include modded creatures). */
    private static boolean isMonster(Mob mob) {
        return mob instanceof Enemy || MobPool.get().find(mob.getType()) != null;
    }

    /** Multiplies stack sizes, rolling the fractional part. Single-item stacks (gear, discs) are left alone. */
    public static ObjectArrayList<ItemStack> multiply(List<ItemStack> loot, double multiplier, RandomSource random) {
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>();
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            if (multiplier <= 1.0 || stack.getMaxStackSize() <= 1) {
                out.add(stack);
                continue;
            }
            double scaled = stack.getCount() * multiplier;
            int whole = (int) Math.floor(scaled);
            if (random.nextDouble() < scaled - whole) {
                whole++;
            }
            addSplit(out, stack, whole);
        }
        return out;
    }

    /** Rolls the config pool and appends whatever drops. */
    public static void rollPool(List<ItemStack> into, RandomSource random) {
        List<LootPoolConfig.Entry> entries = LootPoolConfig.entries();
        if (entries.isEmpty()) {
            return;
        }
        int rolls = SurgeConfig.POOL_ROLLS.get();
        for (int r = 0; r < rolls; r++) {
            for (LootPoolConfig.Entry entry : entries) {
                if (random.nextDouble() < entry.chance()) {
                    int count = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
                    addSplit(into, new ItemStack(entry.item()), count);
                }
            }
        }
    }

    /** Adds {@code count} of the stack, split into legal stack sizes. */
    private static void addSplit(List<ItemStack> into, ItemStack template, int count) {
        int max = Math.max(1, template.getMaxStackSize());
        while (count > 0) {
            int n = Math.min(count, max);
            into.add(template.copyWithCount(n));
            count -= n;
        }
    }
}
