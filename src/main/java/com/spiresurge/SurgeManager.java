package com.spiresurge;

import com.spiresurge.data.MobPool;
import com.spiresurge.net.SurgeSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts, ticks and ends surges.
 *
 * A surge is a sphere around a Spire Heart. While it is active, every player inside it gets extra
 * monsters from the mob pool spawned around them (the spawn "rate"), and every pool mob that spawns
 * inside gets better, possibly enchanted gear.
 */
public final class SurgeManager {

    /** Scoreboard tags stored on the mobs themselves (survive saves). */
    public static final String TAG_SPAWNED = "spiresurge_spawned";
    public static final String TAG_GEARED = "spiresurge_geared";

    private SurgeManager() {
    }

    // ------------------------------------------------------------ activation

    /** The hand holding the trigger item, main hand first, or null. */
    public static InteractionHand findTriggerHand(ServerPlayer player) {
        if (SurgeConfig.isTriggerItem(player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return InteractionHand.MAIN_HAND;
        }
        if (SurgeConfig.isTriggerItem(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static boolean isLit(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    public static InteractionResult tryActivate(ServerLevel level, BlockPos pos, BlockState state,
                                                ServerPlayer player, InteractionHand hand) {
        if (SurgeConfig.REQUIRE_LIT_CORE.get() && !isLit(state)) {
            player.displayClientMessage(Component.translatable("message.spiresurge.not_lit"), true);
            return InteractionResult.FAIL;
        }

        SurgeSavedData data = SurgeSavedData.get(level);
        long now = level.getGameTime();
        long duration = SurgeConfig.DURATION_SECONDS.get() * 20L;
        long expiresAt = now + duration;

        SurgeSavedData.Surge existing = data.get(pos);
        boolean wasActive = existing != null && existing.expiresAt() > now;
        if (wasActive) {
            switch (SurgeConfig.REPEAT_MODE.get()) {
                case IGNORE:
                    player.displayClientMessage(Component.translatable("message.spiresurge.already_active"), true);
                    return InteractionResult.FAIL;
                case EXTEND:
                    expiresAt = Math.min(existing.expiresAt() + duration, now + SurgeConfig.MAX_TOTAL_SECONDS.get() * 20L);
                    break;
                case REFRESH:
                default:
                    break;
            }
        }

        int radius = SurgeConfig.RADIUS.get();
        data.put(pos, new SurgeSavedData.Surge(expiresAt, radius));

        if (!player.getAbilities().instabuild && SurgeConfig.CONSUME_ITEM.get()) {
            ItemStack stack = player.getItemInHand(hand);
            stack.shrink(1);
        }

        level.playSound(null, pos, SoundEvents.GHAST_SCREAM, SoundSource.BLOCKS, 3.0F, 0.6F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                40, 0.5, 0.5, 0.5, 0.08);

        if (SurgeConfig.ANNOUNCE.get()) {
            long secondsLeft = (expiresAt - now) / 20L;
            announce(level, pos, radius, Component.translatable(
                    wasActive ? "message.spiresurge.extended" : "message.spiresurge.started",
                    radius, formatDuration(secondsLeft)));
        }

        sync(level);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(ServerLevel level) {
        SurgeSavedData data = SurgeSavedData.get(level);
        if (data.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean changed = false;

        for (Map.Entry<Long, SurgeSavedData.Surge> entry : data.snapshot()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            SurgeSavedData.Surge surge = entry.getValue();

            if (now >= surge.expiresAt()) {
                end(level, data, pos, surge, true);
                changed = true;
                continue;
            }

            // once a second, make sure the core is still there (and still lit, if configured)
            if (now % 20L == 0L && level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                boolean gone = !SurgeConfig.isCoreBlock(state.getBlock());
                boolean unlit = SurgeConfig.END_WHEN_CORE_UNLIT.get() && !isLit(state);
                if (gone || unlit) {
                    end(level, data, pos, surge, !gone);
                    changed = true;
                    continue;
                }
            }

            if (now % 10L == 0L && level.hasChunkAt(pos)) {
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        4, 0.35, 0.35, 0.35, 0.02);
            }

            if (now % SurgeConfig.INTERVAL_TICKS.get() == 0L) {
                spawnWave(level, pos, surge);
            }
        }

        if (changed) {
            sync(level);
        }
    }

    private static void end(ServerLevel level, SurgeSavedData data, BlockPos pos, SurgeSavedData.Surge surge, boolean tell) {
        data.remove(pos);
        if (level.hasChunkAt(pos)) {
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.7F);
        }
        if (tell && SurgeConfig.ANNOUNCE.get()) {
            announce(level, pos, surge.radius(), Component.translatable("message.spiresurge.ended"));
        }
    }

    /** True if the position lies inside any active surge in this level. */
    public static boolean isInsideSurge(ServerLevel level, Vec3 position) {
        SurgeSavedData data = SurgeSavedData.get(level);
        if (data.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        for (Map.Entry<Long, SurgeSavedData.Surge> e : data.snapshot()) {
            SurgeSavedData.Surge s = e.getValue();
            if (now >= s.expiresAt()) {
                continue;
            }
            BlockPos pos = BlockPos.of(e.getKey());
            double r = s.radius();
            if (position.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r * r) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- spawning

    private static void spawnWave(ServerLevel level, BlockPos corePos, SurgeSavedData.Surge surge) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return; // monsters would be removed instantly anyway
        }
        MobPool.Snapshot pool = MobPool.get();
        if (pool.isEmpty()) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(corePos);
        double radiusSqr = (double) surge.radius() * surge.radius();
        int min = SurgeConfig.MIN_SPAWN_DISTANCE.get();
        int max = Math.max(min + 1, SurgeConfig.MAX_SPAWN_DISTANCE.get());
        int attempts = SurgeConfig.ATTEMPTS_PER_PLAYER.get();
        int cap = SurgeConfig.MAX_MOBS_PER_PLAYER.get();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(center) > radiusSqr) {
                continue;
            }
            AABB area = player.getBoundingBox().inflate(max + 8.0);
            int existing = level.getEntitiesOfClass(Mob.class, area, mob -> mob.getTags().contains(TAG_SPAWNED)).size();
            int budget = cap - existing;
            for (int i = 0; i < attempts && budget > 0; i++) {
                if (trySpawn(level, player, center, radiusSqr, pool, min, max)) {
                    budget--;
                }
            }
        }
    }

    private static boolean trySpawn(ServerLevel level, ServerPlayer player, Vec3 center, double radiusSqr,
                                    MobPool.Snapshot pool, int min, int max) {
        RandomSource random = level.getRandom();
        MobPool.Resolved pick = pool.pick(random);
        if (pick == null) {
            return false;
        }

        // random spot in a ring around the player
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = min + random.nextDouble() * (max - min);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
            return false;
        }

        // look for solid ground, starting at a random height near the player and scanning downward
        // (this finds both surface spots and cave floors)
        int bottom = Math.max(level.getMinBuildHeight() + 1, player.getBlockY() - 20);
        int top = Math.min(level.getMaxBuildHeight() - 3, player.getBlockY() + 10);
        if (top < bottom) {
            return false;
        }
        int startY = bottom + random.nextInt(top - bottom + 1);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        int foundY = Integer.MIN_VALUE;
        for (int y = startY; y >= bottom; y--) {
            feet.set(x, y, z);
            if (canStand(level, feet)) {
                foundY = y;
                break;
            }
        }
        if (foundY == Integer.MIN_VALUE) {
            return false;
        }

        // must be inside the surge sphere
        double dx = x + 0.5 - center.x;
        double dy = foundY - center.y;
        double dz = z + 0.5 - center.z;
        if (dx * dx + dy * dy + dz * dz > radiusSqr) {
            return false;
        }

        Entity entity = pick.type().create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }
        mob.moveTo(x + 0.5, foundY, z + 0.5, random.nextFloat() * 360.0F, 0.0F);

        BlockPos spawnPos = mob.blockPosition();
        if (!mob.checkSpawnObstruction(level) || !mob.checkSpawnRules(level, MobSpawnType.EVENT)) {
            mob.discard();
            return false;
        }
        if (!SurgeConfig.IGNORE_LIGHT_LEVEL.get() && mob instanceof Monster
                && !Monster.isDarkEnoughToSpawn(level, spawnPos, random)) {
            mob.discard();
            return false;
        }

        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
        mob.addTag(TAG_SPAWNED);
        mob.addTag(TAG_GEARED);
        if (pick.equip()) {
            GearEquipper.equip(mob, pick, level);
        }
        level.addFreshEntityWithPassengers(mob);
        level.sendParticles(ParticleTypes.FLAME, x + 0.5, foundY + 0.5, z + 0.5, 8, 0.25, 0.5, 0.25, 0.02);
        return true;
    }

    private static boolean canStand(ServerLevel level, BlockPos.MutableBlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.above();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    // ------------------------------------------------------------------ sync

    /** Tells every player in the level which cores are surging (the client turns them red). */
    public static void sync(ServerLevel level) {
        SurgeSyncPayload payload = buildPayload(level);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, buildPayload(player.serverLevel()));
    }

    private static SurgeSyncPayload buildPayload(ServerLevel level) {
        // Always sent: the client decides (from its own config) which visual effects to show.
        List<SurgeSyncPayload.Entry> cores = new ArrayList<>();
        long now = level.getGameTime();
        for (Map.Entry<Long, SurgeSavedData.Surge> e : SurgeSavedData.get(level).snapshot()) {
            if (e.getValue().expiresAt() > now) {
                cores.add(new SurgeSyncPayload.Entry(e.getKey(), e.getValue().radius()));
            }
        }
        return new SurgeSyncPayload(cores);
    }

    // --------------------------------------------------------------- helpers

    private static void announce(ServerLevel level, BlockPos pos, int radius, Component message) {
        Vec3 center = Vec3.atCenterOf(pos);
        double rangeSqr = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= rangeSqr) {
                player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        if (seconds == 0L) {
            return minutes + (minutes == 1L ? " minute" : " minutes");
        }
        return minutes + "m " + seconds + "s";
    }
}
