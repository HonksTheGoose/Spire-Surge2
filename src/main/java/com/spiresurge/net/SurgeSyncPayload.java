package com.spiresurge.net;

import com.spiresurge.SpireSurge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the packed block position and surge radius of every Spire Heart that is currently
 * surging in the player's dimension. The client uses it to turn those Hearts, the nearby Spawn Spires
 * particles and the seeping grimstone red.
 *
 * The codec is written by hand (plain Netty reads/writes) so it does not depend on any
 * StreamCodec helper that changed between Minecraft versions.
 */
public record SurgeSyncPayload(List<Entry> cores) implements CustomPacketPayload {

    /** One surging Heart: its packed block position and the radius of its surge. */
    public record Entry(long pos, int radius) {
    }

    public static final CustomPacketPayload.Type<SurgeSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SpireSurge.MOD_ID, "surge_sync"));

    /** Hard cap so a corrupt packet can never make the client allocate a huge list. */
    private static final int MAX_CORES = 4096;

    public static final StreamCodec<ByteBuf, SurgeSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SurgeSyncPayload decode(ByteBuf buf) {
            int count = Math.min(Math.max(buf.readInt(), 0), MAX_CORES);
            List<Entry> cores = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long pos = buf.readLong();
                int radius = buf.readInt();
                cores.add(new Entry(pos, radius));
            }
            return new SurgeSyncPayload(cores);
        }

        @Override
        public void encode(ByteBuf buf, SurgeSyncPayload payload) {
            int count = Math.min(payload.cores().size(), MAX_CORES);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                Entry e = payload.cores().get(i);
                buf.writeLong(e.pos());
                buf.writeInt(e.radius());
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
