package dev.minemusic.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Minecraft 1.21.3 – 1.21.8: a key category is just its translation key. */
public final class KeyCompat {
    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, "key.category.minemusic.main"));
    }
}
