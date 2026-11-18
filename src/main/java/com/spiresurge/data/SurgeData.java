package com.spiresurge.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds everything loaded from datapacks (data/&lt;namespace&gt;/spiresurge/{mobs,gear,enchants}/...).
 * Any mod or datapack can add files; they are all merged.
 */
public final class SurgeData {

    /** One line of a mob pool file. {@code tag} entries expand to every member of that entity type tag. */
    public record MobEntry(ResourceLocation id, boolean tag, int weight, boolean equip,
                           double gearMultiplier, EnumSet<EquipmentSlot> slots) {
    }

    /** A matching set of gear. Higher {@code tier} = better. {@code items} maps a slot to the possible items. */
    public record GearSet(ResourceLocation source, int tier, int weight,
                          Map<EquipmentSlot, List<ResourceLocation>> items) {
    }

    /** One allowed enchantment. */
    public record EnchantEntry(ResourceLocation enchantment, int weight, int minLevel, int maxLevel, int minTier) {
    }

    public static final EnumSet<EquipmentSlot> DEFAULT_SLOTS = EnumSet.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND);

    private static volatile List<MobEntry> mobEntries = List.of();
    private static volatile List<GearSet> gearSets = List.of();
    private static volatile List<EnchantEntry> enchantEntries = List.of();
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private SurgeData() {
    }

    public static List<MobEntry> mobEntries() {
        return mobEntries;
    }

    public static List<GearSet> gearSets() {
        return gearSets;
    }

    public static List<EnchantEntry> enchantEntries() {
        return enchantEntries;
    }

    /** Bumped whenever data or tags change so cached pools know to rebuild. */
    public static int generation() {
        return GENERATION.get();
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
    }

    static void setMobEntries(List<MobEntry> list) {
        mobEntries = List.copyOf(list);
        invalidate();
    }

    static void setGearSets(List<GearSet> list) {
        gearSets = List.copyOf(list);
        invalidate();
    }

    static void setEnchantEntries(List<EnchantEntry> list) {
        enchantEntries = List.copyOf(list);
        invalidate();
    }
}
