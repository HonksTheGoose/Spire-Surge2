package com.spiresurge.data;

import com.spiresurge.SpireSurge;
import com.spiresurge.SurgeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A set of entity ids and {@code #tags}, parsed from a comma separated config string. */
public final class EntityFilter {

    private final Set<ResourceLocation> ids = new HashSet<>();
    private final List<TagKey<EntityType<?>>> tags = new ArrayList<>();

    private EntityFilter() {
    }

    public static EntityFilter parse(String csv, String settingName) {
        EntityFilter filter = new EntityFilter();
        for (String s : SurgeConfig.csv(csv)) {
            boolean tag = s.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? s.substring(1) : s);
            if (id == null) {
                SpireSurge.LOGGER.warn("[Spire Surge] Bad id in {}: '{}'", settingName, s);
            } else if (tag) {
                filter.tags.add(TagKey.create(Registries.ENTITY_TYPE, id));
            } else {
                filter.ids.add(id);
            }
        }
        return filter;
    }

    public boolean blocks(EntityType<?> type) {
        if (ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : tags) {
            if (type.is(tag)) {
                return true;
            }
        }
        return false;
    }

    // --- cached copy of the loot blacklist (rebuilt only when the config text changes) ---

    private static volatile String lootRaw;
    private static volatile EntityFilter lootFilter;

    public static EntityFilter lootBlacklist() {
        String raw = SurgeConfig.LOOT_ENTITY_BLACKLIST.get();
        EntityFilter filter = lootFilter;
        if (filter == null || !raw.equals(lootRaw)) {
            filter = parse(raw, "loot.entityBlacklist");
            lootFilter = filter;
            lootRaw = raw;
        }
        return filter;
    }
}
