package dev.minemusic.loader;

import dev.minemusic.MineMusicClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Fabric's entry point (named in fabric.mod.json). */
public final class FabricEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MineMusicClient.init();
        ClientTickEvents.END_CLIENT_TICK.register(MineMusicClient::endTick);
    }
}
