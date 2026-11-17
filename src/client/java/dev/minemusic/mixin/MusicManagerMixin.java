package dev.minemusic.mixin;

import dev.minemusic.audio.MusicPlayer;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Minecraft's own background music quiet while a MineMusic song plays. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Unique
    private boolean minemusic$quiet;

    @Shadow
    public abstract void stopPlaying();

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void minemusic$stayQuiet(CallbackInfo ci) {
        if (MusicPlayer.get().shouldMuteGameMusic()) {
            // stopPlaying() also pushes the next vanilla song back, so call it once, not every tick.
            if (!minemusic$quiet) {
                stopPlaying();
                minemusic$quiet = true;
            }
            ci.cancel();
        } else {
            minemusic$quiet = false;
        }
    }
}
