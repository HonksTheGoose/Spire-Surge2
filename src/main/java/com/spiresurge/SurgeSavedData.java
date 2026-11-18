package com.spiresurge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension list of active surges. Expiry is stored as an absolute game time, so a surge
 * keeps counting down across saves and restarts.
 */
public class SurgeSavedData extends SavedData {

    public record Surge(long expiresAt, int radius) {
    }

    private static final String NAME = "spiresurge_surges";
    private static final String KEY = "Surges";

    private final Map<Long, Surge> surges = new LinkedHashMap<>();

    public SurgeSavedData() {
    }

    public static SurgeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SurgeSavedData::new, SurgeSavedData::load), NAME);
    }

    public static SurgeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurgeSavedData data = new SurgeSavedData();
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.surges.put(entry.getLong("Pos"), new Surge(entry.getLong("Expires"), entry.getInt("Radius")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Surge> e : surges.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", e.getKey());
            entry.putLong("Expires", e.getValue().expiresAt());
            entry.putInt("Radius", e.getValue().radius());
            list.add(entry);
        }
        tag.put(KEY, list);
        return tag;
    }

    public boolean isEmpty() {
        return surges.isEmpty();
    }

    public Surge get(BlockPos pos) {
        return surges.get(pos.asLong());
    }

    public void put(BlockPos pos, Surge surge) {
        surges.put(pos.asLong(), surge);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (surges.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    /** A copy that is safe to iterate while surges are added or removed. */
    public List<Map.Entry<Long, Surge>> snapshot() {
        return new ArrayList<>(surges.entrySet());
    }
}
