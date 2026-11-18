package com.spiresurge.loot;

import com.spiresurge.SpireSurge;
import com.spiresurge.SurgeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses the custom loot pool from the config.
 *
 * Entry format:  item_id=chance   or   item_id=chancexMin-Max   (chance is a percentage, 0 - 100).
 * Parsed entries are cached and only rebuilt when the config text changes.
 */
public final class LootPoolConfig {

    /** One possible drop. {@code chance} is 0.0 - 1.0. */
    public record Entry(Item item, double chance, int min, int max) {
    }

    private static volatile String cachedRaw;
    private static volatile List<Entry> cachedEntries = List.of();

    private LootPoolConfig() {
    }

    public static List<Entry> entries() {
        String raw = SurgeConfig.POOL_ENTRIES.get();
        if (!raw.equals(cachedRaw)) {
            cachedEntries = parse(raw);
            cachedRaw = raw;
        }
        return cachedEntries;
    }

    static List<Entry> parse(String raw) {
        List<Entry> out = new ArrayList<>();
        for (String part : SurgeConfig.csv(raw)) {
            try {
                Entry entry = parseOne(part);
                if (entry != null) {
                    out.add(entry);
                }
            } catch (RuntimeException e) {
                SpireSurge.LOGGER.warn("[Spire Surge] Ignoring bad lootPool entry '{}' ({})", part, e.getMessage());
            }
        }
        return out;
    }

    /** Returns null (silently) when the item does not exist, e.g. it belongs to a mod that is not installed. */
    private static Entry parseOne(String part) {
        String idPart = part;
        String rest = "100";
        int eq = part.indexOf('=');
        if (eq >= 0) {
            idPart = part.substring(0, eq).trim();
            rest = part.substring(eq + 1).trim().toLowerCase(Locale.ROOT).replace("%", "").replace(" ", "");
        }

        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            throw new IllegalArgumentException("not a valid item id");
        }

        String chanceText = rest;
        int min = 1;
        int max = 1;
        int x = rest.indexOf('x');
        if (x >= 0) {
            chanceText = rest.substring(0, x);
            String count = rest.substring(x + 1);
            int dash = count.indexOf('-');
            if (dash > 0) {
                min = Integer.parseInt(count.substring(0, dash));
                max = Integer.parseInt(count.substring(dash + 1));
            } else {
                min = Integer.parseInt(count);
                max = min;
            }
        }
        double chance = Double.parseDouble(chanceText) / 100.0;
        if (chance <= 0.0) {
            return null; // 0% is a valid way to switch an entry off
        }
        chance = Math.min(chance, 1.0);
        min = Math.max(1, min);
        max = Math.max(min, max);

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || item.get() == Items.AIR) {
            return null;
        }
        return new Entry(item.get(), chance, min, max);
    }
}
