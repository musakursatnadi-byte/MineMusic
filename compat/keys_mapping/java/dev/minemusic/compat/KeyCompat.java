package dev.minemusic.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Minecraft 26.x: Fabric API's KeyBindingHelper became KeyMappingHelper. */
public final class KeyCompat {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minemusic", "main"));

    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
    }
}
