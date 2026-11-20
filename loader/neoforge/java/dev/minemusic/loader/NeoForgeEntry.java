package dev.minemusic.loader;

import dev.minemusic.MineMusicClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's entry point. The keys and the song-name line are registered in compat/keys_neoforge and compat/hud_neoforge. */
@Mod(value = MineMusicClient.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntry {
    private static IEventBus modBus;

    public NeoForgeEntry(IEventBus modBus) {
        NeoForgeEntry.modBus = modBus;
        MineMusicClient.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> MineMusicClient.endTick(Minecraft.getInstance()));
    }

    /** The mod's own event bus, where key mappings and HUD layers are registered. */
    public static IEventBus modBus() {
        return modBus;
    }
}
