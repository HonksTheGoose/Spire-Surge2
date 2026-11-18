package com.spiresurge.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.spiresurge.SpireSurge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listeners. Formats are documented in the README.
 *
 * Invalid entries are logged and skipped, never fatal, so a typo in one file (or an entry
 * for a mod that is not installed) cannot break the rest.
 */
public final class SurgeDataListeners {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private SurgeDataListeners() {
    }

    // ------------------------------------------------------------------ mobs

    public static class Mobs extends SimpleJsonResourceReloadListener {
        public Mobs() {
            super(GSON, "spiresurge/mobs");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<SurgeData.MobEntry> out = new ArrayList<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
                try {
                    JsonObject root = file.getValue().getAsJsonObject();
                    if (!root.has("entries")) {
                        return;
                    }
                    for (JsonElement el : root.getAsJsonArray("entries")) {
                        try {
                            parseMob(el.getAsJsonObject(), out);
                        } catch (RuntimeException ex) {
                            SpireSurge.LOGGER.warn("[Spire Surge] Bad mob entry in {}: {}", file.getKey(), ex.toString());
                        }
                    }
                } catch (RuntimeException ex) {
                    SpireSurge.LOGGER.warn("[Spire Surge] Could not read mob file {}: {}", file.getKey(), ex.toString());
                }
            });
            SurgeData.setMobEntries(out);
            SpireSurge.LOGGER.info("[Spire Surge] Loaded {} mob pool entries from {} files", out.size(), files.size());
        }

        private static void parseMob(JsonObject o, List<SurgeData.MobEntry> out) {
            String raw = o.get("entity").getAsString().trim();
            boolean tag = raw.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id == null) {
                throw new IllegalArgumentException("invalid entity id '" + raw + "'");
            }
            int weight = o.has("weight") ? o.get("weight").getAsInt() : 10;
            boolean equip = !o.has("equip") || o.get("equip").getAsBoolean();
            double gearMultiplier = o.has("gear_multiplier") ? o.get("gear_multiplier").getAsDouble() : 1.0;
            EnumSet<EquipmentSlot> slots = o.has("slots") ? parseSlots(o.getAsJsonArray("slots")) : EnumSet.copyOf(SurgeData.DEFAULT_SLOTS);
            out.add(new SurgeData.MobEntry(id, tag, Math.max(0, weight), equip, Math.max(0.0, gearMultiplier), slots));
        }

        private static EnumSet<EquipmentSlot> parseSlots(JsonArray arr) {
            EnumSet<EquipmentSlot> set = EnumSet.noneOf(EquipmentSlot.class);
            for (JsonElement el : arr) {
                EquipmentSlot slot = slotByName(el.getAsString());
                if (slot != null) {
                    set.add(slot);
                }
            }
            return set;
        }
    }

    // ------------------------------------------------------------------ gear

    public static class Gear extends SimpleJsonResourceReloadListener {
        public Gear() {
            super(GSON, "spiresurge/gear");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<SurgeData.GearSet> out = new ArrayList<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
                try {
                    JsonObject root = file.getValue().getAsJsonObject();
                    if (root.has("sets")) {
                        for (JsonElement el : root.getAsJsonArray("sets")) {
                            parseSet(file.getKey(), el.getAsJsonObject(), out);
                        }
                    } else {
                        parseSet(file.getKey(), root, out);
                    }
                } catch (RuntimeException ex) {
                    SpireSurge.LOGGER.warn("[Spire Surge] Could not read gear file {}: {}", file.getKey(), ex.toString());
                }
            });
            SurgeData.setGearSets(out);
            SpireSurge.LOGGER.info("[Spire Surge] Loaded {} gear sets from {} files", out.size(), files.size());
        }

        private static void parseSet(ResourceLocation source, JsonObject o, List<SurgeData.GearSet> out) {
            try {
                int tier = o.has("tier") ? o.get("tier").getAsInt() : 0;
                int weight = o.has("weight") ? o.get("weight").getAsInt() : 10;
                Map<EquipmentSlot, List<ResourceLocation>> items = new EnumMap<>(EquipmentSlot.class);
                for (String key : new String[]{"head", "chest", "legs", "feet", "mainhand", "offhand"}) {
                    if (!o.has(key) || o.get(key).isJsonNull()) {
                        continue;
                    }
                    List<ResourceLocation> ids = new ArrayList<>();
                    JsonElement val = o.get(key);
                    if (val.isJsonArray()) {
                        for (JsonElement el : val.getAsJsonArray()) {
                            addItem(ids, el.getAsString());
                        }
                    } else {
                        addItem(ids, val.getAsString());
                    }
                    if (!ids.isEmpty()) {
                        items.put(slotByName(key), ids);
                    }
                }
                if (!items.isEmpty() && weight > 0) {
                    out.add(new SurgeData.GearSet(source, Math.max(0, tier), weight, items));
                }
            } catch (RuntimeException ex) {
                SpireSurge.LOGGER.warn("[Spire Surge] Bad gear set in {}: {}", source, ex.toString());
            }
        }

        private static void addItem(List<ResourceLocation> ids, String raw) {
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id != null) {
                ids.add(id);
            }
        }
    }

    // -------------------------------------------------------------- enchants

    public static class Enchants extends SimpleJsonResourceReloadListener {
        public Enchants() {
            super(GSON, "spiresurge/enchants");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<SurgeData.EnchantEntry> out = new ArrayList<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
                try {
                    JsonObject root = file.getValue().getAsJsonObject();
                    if (!root.has("entries")) {
                        return;
                    }
                    for (JsonElement el : root.getAsJsonArray("entries")) {
                        try {
                            JsonObject o = el.getAsJsonObject();
                            ResourceLocation id = ResourceLocation.tryParse(o.get("enchantment").getAsString().trim());
                            if (id == null) {
                                throw new IllegalArgumentException("invalid enchantment id");
                            }
                            int weight = o.has("weight") ? o.get("weight").getAsInt() : 5;
                            int min = o.has("min_level") ? o.get("min_level").getAsInt() : 1;
                            int max = o.has("max_level") ? o.get("max_level").getAsInt() : min;
                            int minTier = o.has("min_tier") ? o.get("min_tier").getAsInt() : 0;
                            out.add(new SurgeData.EnchantEntry(id, Math.max(0, weight), Math.max(1, min), Math.max(min, max), Math.max(0, minTier)));
                        } catch (RuntimeException ex) {
                            SpireSurge.LOGGER.warn("[Spire Surge] Bad enchant entry in {}: {}", file.getKey(), ex.toString());
                        }
                    }
                } catch (RuntimeException ex) {
                    SpireSurge.LOGGER.warn("[Spire Surge] Could not read enchant file {}: {}", file.getKey(), ex.toString());
                }
            });
            SurgeData.setEnchantEntries(out);
            SpireSurge.LOGGER.info("[Spire Surge] Loaded {} enchantment entries from {} files", out.size(), files.size());
        }
    }

    // ---------------------------------------------------------------- shared

    static EquipmentSlot slotByName(String name) {
        switch (name.trim().toLowerCase()) {
            case "head":
                return EquipmentSlot.HEAD;
            case "chest":
                return EquipmentSlot.CHEST;
            case "legs":
                return EquipmentSlot.LEGS;
            case "feet":
                return EquipmentSlot.FEET;
            case "mainhand":
                return EquipmentSlot.MAINHAND;
            case "offhand":
                return EquipmentSlot.OFFHAND;
            default:
                return null;
        }
    }
}
