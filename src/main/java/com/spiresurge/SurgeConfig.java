package com.spiresurge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Common config: config/spiresurge-common.toml
 *
 * Lists are written as comma separated strings on purpose: it works on every
 * NeoForge 21.1.x build and is easy to edit by hand.
 */
public final class SurgeConfig {

    public enum RepeatMode {
        /** Using another tear on an active core resets the timer to the full duration. */
        REFRESH,
        /** Using another tear adds the duration on top of the remaining time (capped by maxTotalSeconds). */
        EXTEND,
        /** Using another tear on an active core does nothing (the tear is not consumed). */
        IGNORE
    }

    public enum LootScope {
        /** Only mobs that this addon spawned. */
        SURGE_SPAWNED,
        /** Surge-spawned mobs, plus any monster that dies inside an active surge. */
        INSIDE_SURGE,
        /** Every monster, everywhere, surge or not. */
        EVERYWHERE
    }

    public enum PoolMode {
        /** The custom loot pool is not used. */
        OFF,
        /** Custom pool drops are added on top of the normal drops. */
        ADD,
        /** Custom pool drops replace the normal drops (equipment drops are not affected). */
        REPLACE
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---- [trigger] --------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> CORE_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> TRIGGER_ITEM;
    public static final ModConfigSpec.BooleanValue CONSUME_ITEM;
    public static final ModConfigSpec.BooleanValue REQUIRE_LIT_CORE;
    public static final ModConfigSpec.BooleanValue END_WHEN_CORE_UNLIT;
    public static final ModConfigSpec.EnumValue<RepeatMode> REPEAT_MODE;
    public static final ModConfigSpec.IntValue MAX_TOTAL_SECONDS;

    // ---- [surge] ----------------------------------------------------------
    public static final ModConfigSpec.IntValue RADIUS;
    public static final ModConfigSpec.IntValue DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue ANNOUNCE;

    // ---- [spawning] -------------------------------------------------------
    public static final ModConfigSpec.IntValue INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ATTEMPTS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_MOBS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ModConfigSpec.BooleanValue IGNORE_LIGHT_LEVEL;
    public static final ModConfigSpec.BooleanValue USE_TOWER_SUMMONED_TAG;
    public static final ModConfigSpec.IntValue TOWER_SUMMONED_TAG_WEIGHT;
    public static final ModConfigSpec.ConfigValue<String> MOB_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<String> EXTRA_MOBS;

    // ---- [gear] -----------------------------------------------------------
    public static final ModConfigSpec.BooleanValue GEAR_NATURAL_SPAWNS;
    public static final ModConfigSpec.DoubleValue TIER_MULTIPLIER;
    public static final ModConfigSpec.IntValue MIN_TIER;
    public static final ModConfigSpec.IntValue MAX_TIER;
    public static final ModConfigSpec.DoubleValue SLOT_EQUIP_CHANCE;
    public static final ModConfigSpec.DoubleValue DROP_CHANCE;

    // ---- [enchanting] -----------------------------------------------------
    public static final ModConfigSpec.DoubleValue ENCHANT_CHANCE;
    public static final ModConfigSpec.IntValue MAX_ENCHANTS_PER_ITEM;
    public static final ModConfigSpec.DoubleValue ENCHANT_LEVEL_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ALLOW_OVER_MAX_LEVELS;
    public static final ModConfigSpec.ConfigValue<String> ENCHANT_WHITELIST;
    public static final ModConfigSpec.ConfigValue<String> ENCHANT_BLACKLIST;

    // ---- [loot] -----------------------------------------------------------
    public static final ModConfigSpec.BooleanValue LOOT_ENABLED;
    public static final ModConfigSpec.EnumValue<LootScope> LOOT_SCOPE;
    public static final ModConfigSpec.BooleanValue LOOT_REQUIRE_PLAYER_KILL;
    public static final ModConfigSpec.DoubleValue LOOT_DROP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LOOT_XP_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> LOOT_ENTITY_BLACKLIST;

    // ---- [lootPool] -------------------------------------------------------
    public static final ModConfigSpec.EnumValue<PoolMode> POOL_MODE;
    public static final ModConfigSpec.IntValue POOL_ROLLS;
    public static final ModConfigSpec.ConfigValue<String> POOL_ENTRIES;

    // ---- [visuals] --------------------------------------------------------
    public static final ModConfigSpec.BooleanValue TINT_CORE;
    public static final ModConfigSpec.ConfigValue<String> CORE_TINT_COLOR;
    public static final ModConfigSpec.BooleanValue PULSE;
    public static final ModConfigSpec.BooleanValue RED_PARTICLES;
    public static final ModConfigSpec.ConfigValue<String> PARTICLE_TINT_COLOR;
    public static final ModConfigSpec.BooleanValue RED_GRIMSTONE;
    public static final ModConfigSpec.ConfigValue<String> GRIMSTONE_TINT_COLOR;

    public static final ModConfigSpec SPEC;

    static {
        B.push("trigger");
        ENABLED = B.comment("Master switch. If false the addon does nothing.")
                .define("enabled", true);
        CORE_BLOCK = B.comment("Block that starts a surge when right clicked with the trigger item.",
                        "Default is the Spire Heart from Spawn Spires.")
                .define("coreBlock", "mobtowers:monster_flame");
        TRIGGER_ITEM = B.comment("Item that triggers the surge. Any registry id works, including modded items.")
                .define("triggerItem", "minecraft:ghast_tear");
        CONSUME_ITEM = B.comment("Use up one trigger item per activation (never consumed in creative).")
                .define("consumeItem", true);
        REQUIRE_LIT_CORE = B.comment("The Spire Heart must be lit (active) to accept the item.")
                .define("requireLitCore", true);
        END_WHEN_CORE_UNLIT = B.comment("If the Spire Heart is extinguished during a surge, end the surge immediately.")
                .define("endWhenCoreUnlit", true);
        REPEAT_MODE = B.comment("What happens when the item is used on a core that is already surging.",
                        "REFRESH = reset the timer, EXTEND = add time on top, IGNORE = do nothing.")
                .defineEnum("repeatMode", RepeatMode.REFRESH);
        MAX_TOTAL_SECONDS = B.comment("Upper limit for the remaining surge time when repeatMode = EXTEND.")
                .defineInRange("maxTotalSeconds", 3600, 10, 86400);
        B.pop();

        B.push("surge");
        RADIUS = B.comment("Radius in blocks around the core where the surge is active (spherical).")
                .defineInRange("radius", 100, 8, 1024);
        DURATION_SECONDS = B.comment("How long a surge lasts, in seconds. 600 = 10 minutes.")
                .defineInRange("durationSeconds", 600, 10, 86400);
        ANNOUNCE = B.comment("Show an action-bar message to nearby players when a surge starts or ends.")
                .define("announce", true);
        B.pop();

        B.push("spawning");
        INTERVAL_TICKS = B.comment("How often (in ticks) the surge tries to spawn mobs. 20 ticks = 1 second.")
                .defineInRange("intervalTicks", 20, 2, 1200);
        ATTEMPTS_PER_PLAYER = B.comment("Spawn attempts per interval for every player inside the surge radius.",
                        "This is the main 'spawn rate' dial.")
                .defineInRange("attemptsPerPlayer", 2, 1, 64);
        MAX_MOBS_PER_PLAYER = B.comment("Cap on surge-spawned mobs alive around each player. Stops the area from being flooded.")
                .defineInRange("maxMobsPerPlayer", 24, 1, 512);
        MIN_SPAWN_DISTANCE = B.comment("Closest distance (blocks) to a player that a surge mob can appear.")
                .defineInRange("minSpawnDistance", 12, 1, 64);
        MAX_SPAWN_DISTANCE = B.comment("Farthest distance (blocks) from a player that a surge mob can appear.")
                .defineInRange("maxSpawnDistance", 40, 2, 128);
        IGNORE_LIGHT_LEVEL = B.comment("Surge mobs spawn regardless of light level (day or night).")
                .define("ignoreLightLevel", true);
        USE_TOWER_SUMMONED_TAG = B.comment("Also add every entity in the Spawn Spires tag #mobtowers:tower_summoned to the pool.",
                        "That tag is where other mods already register their spire mobs, so modded monsters join automatically.")
                .define("useTowerSummonedTag", true);
        TOWER_SUMMONED_TAG_WEIGHT = B.comment("Spawn weight given to mobs that come in through the tower_summoned tag.")
                .defineInRange("towerSummonedTagWeight", 10, 1, 1000);
        MOB_BLACKLIST = B.comment("Comma separated entity ids or #tags that must never be spawned or geared by a surge.",
                        "Example: minecraft:creeper, #minecraft:raiders, somemod:boss")
                .define("mobBlacklist", "");
        EXTRA_MOBS = B.comment("Comma separated extra mobs to add to the pool, as id=weight (weight optional, default 10).",
                        "Tags are allowed with a leading #. Example: minecraft:pillager=5, #somemod:undead=8",
                        "For bigger changes use datapacks: data/<namespace>/spiresurge/mobs/*.json")
                .define("extraMobs", "");
        B.pop();

        B.push("gear");
        GEAR_NATURAL_SPAWNS = B.comment("Also give surge gear to ordinary monsters that spawn naturally inside an active surge.",
                        "Only mobs that are in the pool (weight 0 entries count) are affected.")
                .define("applyToNaturalSpawns", true);
        TIER_MULTIPLIER = B.comment("Gear tier multiplier. A gear set's chance is: weight * multiplier ^ tier.",
                        "1.0 = use the weights from the datapack as they are.",
                        "above 1.0 = higher tier gear is more common. below 1.0 = lower tier gear is more common.")
                .defineInRange("tierMultiplier", 1.5, 0.1, 10.0);
        MIN_TIER = B.comment("Lowest gear tier that can be handed out.")
                .defineInRange("minTier", 0, 0, 1000);
        MAX_TIER = B.comment("Highest gear tier that can be handed out.")
                .defineInRange("maxTier", 1000, 0, 1000);
        SLOT_EQUIP_CHANCE = B.comment("Chance that each allowed equipment slot actually receives an item.")
                .defineInRange("slotEquipChance", 0.9, 0.0, 1.0);
        DROP_CHANCE = B.comment("Drop chance for surge gear when the mob dies (0.0 - 1.0).",
                        "-1 keeps the vanilla default (about 8.5%).")
                .defineInRange("dropChance", -1.0, -1.0, 1.0);
        B.pop();

        B.push("enchanting");
        ENCHANT_CHANCE = B.comment("Chance that each piece of gear gets enchanted.")
                .defineInRange("enchantChance", 0.65, 0.0, 1.0);
        MAX_ENCHANTS_PER_ITEM = B.comment("Maximum number of different enchantments on one item. 0 disables enchanting.")
                .defineInRange("maxEnchantsPerItem", 2, 0, 8);
        ENCHANT_LEVEL_MULTIPLIER = B.comment("Multiplier applied to the enchantment level that was rolled from the datapack range.")
                .defineInRange("levelMultiplier", 1.0, 0.1, 10.0);
        ALLOW_OVER_MAX_LEVELS = B.comment("Allow levels above the enchantment's normal maximum (for example Sharpness VII).")
                .define("allowOverMaxLevels", false);
        ENCHANT_WHITELIST = B.comment("Comma separated enchantment ids. If not empty, ONLY these can be used.")
                .define("enchantWhitelist", "");
        ENCHANT_BLACKLIST = B.comment("Comma separated enchantment ids that can never be used.",
                        "Example: minecraft:thorns, minecraft:fire_aspect")
                .define("enchantBlacklist", "");
        B.pop();

        B.push("loot");
        LOOT_ENABLED = B.comment("Master switch for everything in the [loot] and [lootPool] sections.")
                .define("enabled", true);
        LOOT_SCOPE = B.comment("Which monsters get the better loot.",
                        "SURGE_SPAWNED = only mobs this addon spawned.",
                        "INSIDE_SURGE  = surge-spawned mobs plus any monster that dies inside an active surge.",
                        "EVERYWHERE    = every monster in the world, surge or not.")
                .defineEnum("scope", LootScope.INSIDE_SURGE);
        LOOT_REQUIRE_PLAYER_KILL = B.comment("Only give the better loot when a player (or their pet) landed the killing blow,",
                        "like vanilla rare drops and experience.")
                .define("requirePlayerKill", true);
        LOOT_DROP_MULTIPLIER = B.comment("Multiplies the amount of every normal (stackable) drop. 2.0 = double drops, 1.5 = 50% more.",
                        "Fractions are rolled randomly, so 1.5 on a single item gives 1 or 2. Gear is never duplicated.",
                        "Not used when lootPool.mode = REPLACE (there are no normal drops to multiply).")
                .defineInRange("dropMultiplier", 2.0, 1.0, 64.0);
        LOOT_XP_MULTIPLIER = B.comment("Multiplies the experience dropped by those monsters.")
                .defineInRange("xpMultiplier", 2.0, 1.0, 64.0);
        LOOT_ENTITY_BLACKLIST = B.comment("Comma separated entity ids or #tags that never get the loot changes (bosses by default).",
                        "Example: minecraft:ender_dragon, minecraft:wither, #somemod:bosses")
                .define("entityBlacklist", "minecraft:ender_dragon, minecraft:wither, minecraft:warden, minecraft:elder_guardian");
        B.pop();

        B.push("lootPool");
        POOL_MODE = B.comment("What the custom loot pool below does.",
                        "OFF     = not used.",
                        "ADD     = pool drops are added to the normal drops.",
                        "REPLACE = pool drops replace the normal drops (armour and weapons the mob was carrying still drop as usual).")
                .defineEnum("mode", PoolMode.REPLACE);
        POOL_ROLLS = B.comment("How many times the whole pool is rolled per kill. Every entry is rolled separately each time.")
                .defineInRange("rolls", 1, 1, 32);
        POOL_ENTRIES = B.comment("The custom loot pool: comma separated entries of   item_id=chance   or   item_id=chancexMin-Max",
                        "  chance is a percentage from 0 to 100 (decimals allowed, e.g. 0.5).",
                        "  xMin-Max is an optional stack size range; leave it out for exactly 1. A single number like x3 means exactly 3.",
                        "Examples:   minecraft:diamond=2   (2% for 1 diamond)",
                        "            minecraft:emerald=10x1-2   (10% for 1-2 emeralds)",
                        "Items from mods work the same way (somemod:cool_item=5x1-3). Items from mods that are not installed are skipped.")
                .define("entries", "minecraft:bone=30x1-2, minecraft:rotten_flesh=30x1-2, minecraft:gunpowder=25x1-2, "
                        + "minecraft:string=20x1-2, minecraft:arrow=20x1-3, minecraft:iron_nugget=25x1-4, "
                        + "minecraft:gold_nugget=20x1-4, minecraft:emerald=10x1-2, minecraft:ender_pearl=5, "
                        + "minecraft:experience_bottle=8x1-2, minecraft:iron_ingot=6x1-2, minecraft:gold_ingot=4, "
                        + "minecraft:diamond=2, minecraft:golden_apple=1.5, minecraft:ghast_tear=1.5, "
                        + "minecraft:netherite_scrap=0.4");
        B.pop();

        B.push("visuals");
        TINT_CORE = B.comment("Turn the Spire Heart's flame red while a surge is active.")
                .define("tintCore", true);
        CORE_TINT_COLOR = B.comment("Hex colour (RRGGBB) for the surge glow. Read on the client.")
                .define("coreTintColor", "FF2A2A");
        PULSE = B.comment("Make the red glow gently pulse. Read on the client.")
                .define("pulse", true);
        RED_PARTICLES = B.comment("Turn Spawn Spires' flame particles red inside a surge. Read on the client.")
                .define("redParticles", true);
        PARTICLE_TINT_COLOR = B.comment("Hex colour (RRGGBB) for those particles. Read on the client.")
                .define("particleTintColor", "FF2A2A");
        RED_GRIMSTONE = B.comment("Turn Seeping Grimstone red inside a surge. Read on the client.",
                        "The block texture is dark, so the result is a deep red; brighter colours look better.")
                .define("redGrimstone", true);
        GRIMSTONE_TINT_COLOR = B.comment("Hex colour (RRGGBB) for Seeping Grimstone. Read on the client.")
                .define("grimstoneTintColor", "FF3A3A");
        B.pop();

        SPEC = B.build();
    }

    private SurgeConfig() {
    }

    // ---- helpers ----------------------------------------------------------

    private static String cachedCoreRaw;
    private static ResourceLocation cachedCoreId;
    private static String cachedItemRaw;
    private static ResourceLocation cachedItemId;

    public static boolean isCoreBlock(Block block) {
        String raw = CORE_BLOCK.get();
        if (!raw.equals(cachedCoreRaw)) {
            cachedCoreId = ResourceLocation.tryParse(raw.trim());
            cachedCoreRaw = raw;
        }
        return cachedCoreId != null && cachedCoreId.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static boolean isTriggerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String raw = TRIGGER_ITEM.get();
        if (!raw.equals(cachedItemRaw)) {
            cachedItemId = ResourceLocation.tryParse(raw.trim());
            cachedItemRaw = raw;
        }
        Item item = stack.getItem();
        return cachedItemId != null && cachedItemId.equals(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Splits a comma separated config string into trimmed, non-empty entries. */
    public static List<String> csv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** Changes whenever a config value that affects the mob pool changes, so the pool cache can rebuild. */
    public static String poolKey() {
        return MOB_BLACKLIST.get() + "|" + EXTRA_MOBS.get() + "|" + USE_TOWER_SUMMONED_TAG.get() + "|" + TOWER_SUMMONED_TAG_WEIGHT.get();
    }

    /** Parses the RRGGBB config colour into an opaque ARGB int. Falls back to red. */
    public static int tintColor() {
        return parseColor(CORE_TINT_COLOR.get(), 0xFFFF2A2A);
    }

    public static int particleColor() {
        return parseColor(PARTICLE_TINT_COLOR.get(), 0xFFFF2A2A);
    }

    public static int grimstoneColor() {
        return parseColor(GRIMSTONE_TINT_COLOR.get(), 0xFFFF3A3A);
    }

    private static int parseColor(String text, int fallback) {
        try {
            String hex = text.trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            return 0xFF000000 | (Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
