package dev.minemusic.screen;

import dev.minemusic.audio.Message;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Turns the player's messages into translated Minecraft text. */
public final class Texts {
    private Texts() {
    }

    public static MutableComponent of(Message message) {
        if (message.key() == null) {
            return Component.literal(String.valueOf(message.args()[0]));
        }
        Object[] args = message.args().clone();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Message inner) {
                args[i] = of(inner);
            }
        }
        return Component.translatable(message.key(), args);
    }

    public static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    /** Cuts text that is wider than {@code width} pixels and ends it with "...". */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
