package com.spiresurge;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.spiresurge.client.SurgeClient;
import com.spiresurge.loot.SurgeLootModifier;
import com.spiresurge.net.SurgeNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Spire Surge: an addon for Spawn Spires (mobtowers).
 * Use a ghast tear on a lit Spire Heart to trigger a timed monster surge around it.
 */
@Mod(SpireSurge.MOD_ID)
public class SpireSurge {
    public static final String MOD_ID = "spiresurge";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The loot modifier type. It is switched on by data/neoforge/loot_modifiers/global_loot_modifiers.json. */
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);
    public static final Supplier<MapCodec<SurgeLootModifier>> MOB_LOOT =
            LOOT_MODIFIERS.register("mob_loot", () -> SurgeLootModifier.CODEC);

    public SpireSurge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SurgeConfig.SPEC);
        LOOT_MODIFIERS.register(modEventBus);
        modEventBus.addListener(SurgeNetwork::registerPayloads);
        SurgeEvents.register(NeoForge.EVENT_BUS);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            SurgeClient.init(modEventBus);
        }
    }
}
