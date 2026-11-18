package com.spiresurge.data;

import com.spiresurge.SpireSurge;
import com.spiresurge.SurgeConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The resolved mob pool: datapack entries + config extras + the Spawn Spires tower_summoned tag,
 * minus the blacklist. Tags are expanded lazily here (not at datapack load) because tags are
 * not guaranteed to be bound yet when reload listeners run.
 */
public final class MobPool {

    public record Resolved(EntityType<?> type, int weight, boolean equip, double gearMultiplier,
                           Set<EquipmentSlot> slots) {
    }

    public static final class Snapshot {
        private final List<Resolved> spawnable;
        private final int totalWeight;
        private final Map<EntityType<?>, Resolved> byType;

        Snapshot(List<Resolved> spawnable, int totalWeight, Map<EntityType<?>, Resolved> byType) {
            this.spawnable = spawnable;
            this.totalWeight = totalWeight;
            this.byType = byType;
        }

        /** True if nothing can be spawned (gear-only entries don't count). */
        public boolean isEmpty() {
            return spawnable.isEmpty() || totalWeight <= 0;
        }

        /** Weighted random pick of a mob to spawn, or null if the pool is empty. */
        public Resolved pick(RandomSource random) {
            if (isEmpty()) {
                return null;
            }
            int roll = random.nextInt(totalWeight);
            for (Resolved r : spawnable) {
                roll -= r.weight();
                if (roll < 0) {
                    return r;
                }
            }
            return spawnable.get(spawnable.size() - 1);
        }

        /** Finds the pool entry for an entity type (including gear-only weight 0 entries), or null. */
        public Resolved find(EntityType<?> type) {
            return byType.get(type);
        }
    }

    private static volatile Snapshot cache;
    private static volatile String cacheKey = "";
    private static volatile int cacheGeneration = -1;

    private MobPool() {
    }

    public static Snapshot get() {
        String key = SurgeConfig.poolKey();
        int gen = SurgeData.generation();
        Snapshot snap = cache;
        if (snap == null || gen != cacheGeneration || !key.equals(cacheKey)) {
            snap = build();
            cache = snap;
            cacheKey = key;
            cacheGeneration = gen;
        }
        return snap;
    }

    private static Snapshot build() {
        EntityFilter blacklist = EntityFilter.parse(SurgeConfig.MOB_BLACKLIST.get(), "spawning.mobBlacklist");
        Map<EntityType<?>, Resolved> map = new LinkedHashMap<>();

        // 1. datapack entries (earlier entries win)
        for (SurgeData.MobEntry e : SurgeData.mobEntries()) {
            add(map, blacklist, expand(e.id(), e.tag()), e.weight(), e.equip(), e.gearMultiplier(), e.slots());
        }

        // 2. extras from the config file: "id=weight, #tag=weight"
        for (String part : SurgeConfig.csv(SurgeConfig.EXTRA_MOBS.get())) {
            String idPart = part;
            int weight = 10;
            int eq = part.indexOf('=');
            if (eq >= 0) {
                idPart = part.substring(0, eq).trim();
                try {
                    weight = Math.max(0, Integer.parseInt(part.substring(eq + 1).trim()));
                } catch (NumberFormatException ex) {
                    SpireSurge.LOGGER.warn("[Spire Surge] Bad weight in extraMobs entry '{}'", part);
                    continue;
                }
            }
            boolean tag = idPart.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? idPart.substring(1) : idPart);
            if (id == null) {
                SpireSurge.LOGGER.warn("[Spire Surge] Bad id in extraMobs entry '{}'", part);
                continue;
            }
            add(map, blacklist, expand(id, tag), weight, true, 1.0, EnumSet.copyOf(SurgeData.DEFAULT_SLOTS));
        }

        // 3. Spawn Spires' own tag, so every mod that already adds its mobs there is covered automatically
        if (SurgeConfig.USE_TOWER_SUMMONED_TAG.get()) {
            add(map, blacklist, expand(ResourceLocation.fromNamespaceAndPath("mobtowers", "tower_summoned"), true),
                    SurgeConfig.TOWER_SUMMONED_TAG_WEIGHT.get(), true, 1.0, EnumSet.copyOf(SurgeData.DEFAULT_SLOTS));
        }

        List<Resolved> spawnable = new ArrayList<>();
        int total = 0;
        for (Resolved r : map.values()) {
            if (r.weight() > 0) {
                spawnable.add(r);
                total += r.weight();
            }
        }
        return new Snapshot(spawnable, total, map);
    }

    private static void add(Map<EntityType<?>, Resolved> map, EntityFilter blacklist, List<EntityType<?>> types,
                            int weight, boolean equip, double gearMultiplier, Set<EquipmentSlot> slots) {
        for (EntityType<?> type : types) {
            if (!type.canSummon() || blacklist.blocks(type)) {
                continue;
            }
            map.putIfAbsent(type, new Resolved(type, weight, equip, gearMultiplier, slots));
        }
    }

    private static List<EntityType<?>> expand(ResourceLocation id, boolean tag) {
        List<EntityType<?>> out = new ArrayList<>();
        if (tag) {
            TagKey<EntityType<?>> key = TagKey.create(Registries.ENTITY_TYPE, id);
            Optional<HolderSet.Named<EntityType<?>>> named = BuiltInRegistries.ENTITY_TYPE.getTag(key);
            if (named.isPresent()) {
                for (Holder<EntityType<?>> holder : named.get()) {
                    out.add(holder.value());
                }
            }
        } else {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            // a missing entity just means that mod isn't installed - skip silently
            type.ifPresent(out::add);
        }
        return out;
    }
}
