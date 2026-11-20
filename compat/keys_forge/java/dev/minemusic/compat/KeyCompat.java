package dev.minemusic.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/** Forge (Minecraft 26.x): key mappings are handed to Forge when it asks for them. */
public final class KeyCompat {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minemusic", "main"));
    private static final List<KeyMapping> MAPPINGS = new ArrayList<>();

    static {
        RegisterKeyMappingsEvent.BUS.addListener(event -> MAPPINGS.forEach(event::register));
    }

    private KeyCompat() {
    }

    public static KeyMapping register(String name, int key) {
        KeyMapping mapping = new KeyMapping(name, key, CATEGORY);
        MAPPINGS.add(mapping);
        return mapping;
    }
}
