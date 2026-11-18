package com.spiresurge;

import com.spiresurge.data.MobPool;
import com.spiresurge.data.SurgeData;
import com.spiresurge.data.SurgeDataListeners;
import com.spiresurge.loot.SurgeLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** All game-bus (server side) event wiring for the addon. */
public final class SurgeEvents {

    private SurgeEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(SurgeEvents::onRightClickBlock);
        bus.addListener(SurgeEvents::onLevelTick);
        bus.addListener(SurgeEvents::onAddReloadListeners);
        bus.addListener(SurgeEvents::onTagsUpdated);
        bus.addListener(SurgeEvents::onEntityJoin);
        bus.addListener(SurgeEvents::onExperienceDrop);
        bus.addListener(SurgeEvents::onPlayerLoggedIn);
        bus.addListener(SurgeEvents::onPlayerChangedDimension);
        bus.addListener(SurgeEvents::onPlayerRespawn);
    }

    /** Ghast tear (or whatever is configured) on the Spire Heart. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return; // the server decides; the client just sends the click
        }
        if (!SurgeConfig.ENABLED.get()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!SurgeConfig.isCoreBlock(state.getBlock())) {
            return;
        }
        InteractionHand hand = SurgeManager.findTriggerHand(player);
        if (hand == null) {
            return; // normal click: the Spire Heart toggles as usual
        }
        // From here on we own the click, so the Spire Heart does not also toggle on/off.
        event.setCanceled(true);
        event.setCancellationResult(SurgeManager.tryActivate(level, pos, state, player, hand));
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && SurgeConfig.ENABLED.get()) {
            SurgeManager.tick(level);
        }
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SurgeDataListeners.Mobs());
        event.addListener(new SurgeDataListeners.Gear());
        event.addListener(new SurgeDataListeners.Enchants());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        SurgeData.invalidate(); // entity tags may have changed, rebuild the pool
    }

    /** Gives surge gear to ordinary monsters that spawn naturally inside an active surge. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !SurgeConfig.ENABLED.get() || !SurgeConfig.GEAR_NATURAL_SPAWNS.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob.getTags().contains(SurgeManager.TAG_GEARED) || !SurgeManager.isInsideSurge(level, mob.position())) {
            return;
        }
        MobPool.Resolved entry = MobPool.get().find(mob.getType());
        if (entry == null || !entry.equip()) {
            return;
        }
        mob.addTag(SurgeManager.TAG_GEARED);
        GearEquipper.equip(mob, entry, level);
    }

    /** More experience from qualifying monsters (same rules as the loot). */
    private static void onExperienceDrop(LivingExperienceDropEvent event) {
        double multiplier = SurgeConfig.LOOT_XP_MULTIPLIER.get();
        if (multiplier <= 1.0 || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (SurgeLoot.qualifies(event.getEntity(), level)) {
            event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * multiplier));
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SurgeManager.syncTo(player);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SurgeManager.syncTo(player);
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SurgeManager.syncTo(player);
        }
    }
}
