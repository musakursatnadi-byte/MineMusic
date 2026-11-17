package dev.minemusic.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the song-name line see how many boss bars are on screen, so it can sit below them. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccess {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> minemusic$events();
}
