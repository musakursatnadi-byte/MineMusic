package dev.minemusic.loader;

import dev.minemusic.MineMusicClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge's entry point. The keys and the song-name line are registered in compat/keys_forge and compat/hud_forge. */
@Mod(MineMusicClient.MOD_ID)
public final class ForgeEntry {
    public ForgeEntry(FMLJavaModLoadingContext context) {
        MineMusicClient.init();
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> MineMusicClient.endTick(Minecraft.getInstance()));
    }
}
