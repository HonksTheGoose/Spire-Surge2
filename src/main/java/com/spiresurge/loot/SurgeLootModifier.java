package com.spiresurge.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spiresurge.SpireSurge;
import com.spiresurge.SurgeConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier that runs on every entity loot table.
 *
 * Working at the loot table level (instead of on the death event) is what lets REPLACE mode swap only
 * the mob's normal drops while the armour and weapons it was carrying still drop as usual.
 *
 * It is switched on by the two json files in data/neoforge/loot_modifiers and data/spiresurge/loot_modifiers.
 */
public class SurgeLootModifier extends LootModifier {

    public static final MapCodec<SurgeLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, SurgeLootModifier::new));

    private static boolean errorLogged;

    public SurgeLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        try {
            Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (entity == null) {
                return generatedLoot;
            }
            boolean playerKill = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) != null;
            RandomSource random = context.getRandom();
            ObjectArrayList<ItemStack> result = generatedLoot;

            // 1. General looting: every mob a player kills, surge or not.
            if (playerKill && SurgeLoot.qualifiesForLooting(entity)) {
                result = SurgeLoot.applyLooting(result, SurgeConfig.GENERAL_LOOTING_LEVEL.get(), random);
            }

            // 2. Surge loot: multiplied drops and/or the custom pool.
            boolean allowed = playerKill || !SurgeConfig.LOOT_REQUIRE_PLAYER_KILL.get();
            if (allowed && SurgeLoot.qualifies(entity, context.getLevel())) {
                SurgeConfig.PoolMode mode = SurgeConfig.POOL_MODE.get();
                if (mode == SurgeConfig.PoolMode.REPLACE) {
                    result = new ObjectArrayList<>();
                } else {
                    result = SurgeLoot.multiply(result, SurgeConfig.LOOT_DROP_MULTIPLIER.get(), random);
                }
                if (mode != SurgeConfig.PoolMode.OFF) {
                    SurgeLoot.rollPool(result, random);
                }
            }

            // 3. Bonus drops (e.g. heart crystals): always on top, only for surge monsters.
            if (allowed && SurgeLoot.qualifiesForBonus(entity, context.getLevel())) {
                SurgeLoot.rollBonus(result, random);
            }
            return result;
        } catch (RuntimeException e) {
            // Never let a loot problem break a kill: log once and hand back the untouched loot.
            if (!errorLogged) {
                errorLogged = true;
                SpireSurge.LOGGER.error("[Spire Surge] Loot modifier failed, leaving drops unchanged", e);
            }
            return generatedLoot;
        }
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
