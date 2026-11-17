package dev.minemusic.hud;

import dev.minemusic.audio.Message;
import dev.minemusic.audio.MusicPlayer;
import dev.minemusic.compat.Compat;
import dev.minemusic.mixin.BossHealthOverlayAccess;
import dev.minemusic.screen.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** The small "♪ song name" line in the top-left corner while MineMusic plays or loads. */
public final class NowPlayingHud {
    private static final int MAX_WIDTH = 220;

    /** Drawing differs between Minecraft versions, so each version's compat code supplies this. */
    public interface Painter {
        void fill(int x1, int y1, int x2, int y2, int argb);

        void text(String text, int x, int y, int argb);
    }

    private NowPlayingHud() {
    }

    public static void render(Painter painter) {
        MusicPlayer player = MusicPlayer.get();
        Message hud = player.getHudMessage();
        if (!player.isShowHud() || hud == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String text = fitLine(font, hud);
        // White while a song plays; yellow while it is paused, loading or just turned off.
        int color = player.getState() == MusicPlayer.State.PLAYING ? 0xFFFFFFFF : 0xFFFFFF55;

        // Boss bars (Ender Dragon, Wither, raids) are drawn at the top, 19 pixels apart starting at
        // y 12; the line moves below them so it does not cover the boss name.
        int bars = ((BossHealthOverlayAccess) Compat.bossOverlay(minecraft)).minemusic$events().size();
        int x = 4;
        int y = bars > 0 ? 19 * bars + 2 : 4;
        painter.fill(x - 2, y - 2, x + font.width(text) + 2, y + font.lineHeight + 1, 0x90000000);
        painter.text(text, x, y, color);
    }

    /**
     * Shortens a long line by cutting the song title (the first value), not the end, so the time
     * and "(paused)" stay visible.
     */
    private static String fitLine(Font font, Message hud) {
        String text = Texts.of(hud).getString();
        int over = font.width(text) - MAX_WIDTH;
        if (over > 0 && hud.args().length > 1 && hud.args()[0] instanceof String title && !title.isEmpty()) {
            String shorter = Texts.fit(font, title, Math.max(40, font.width(title) - over));
            Object[] args = hud.args().clone();
            args[0] = shorter;
            text = Texts.of(new Message(hud.key(), args)).getString();
        }
        return Texts.fit(font, text, MAX_WIDTH);
    }
}
