package com.spiresurge.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the addon's single server -> client packet. Hooked up on the mod event bus. */
public final class SurgeNetwork {

    private SurgeNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SurgeSyncPayload.TYPE,
                SurgeSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SurgeClientState.set(payload.cores())));
    }
}
