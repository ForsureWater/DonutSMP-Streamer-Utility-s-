package com.example.donutqueue;

import net.minecraft.client.gui.screens.Screen;

/**
 * Implement this in a separate mod to add an entry to the neon menu's sidebar.
 *
 * <p>Register it in your fabric.mod.json under the {@value #ENTRYPOINT} entrypoint:
 *
 * <pre>
 * "entrypoints": {
 *   "donutqueue-addon": ["your.mod.YourAddon"]
 * }
 * </pre>
 *
 * Fabric only instantiates the class when this mod is installed and asks for it, so an addon
 * keeps working (without the menu entry) when this mod is missing.
 */
public interface DonutQueueAddon {

    /** The fabric.mod.json entrypoint key addons register under. */
    String ENTRYPOINT = "donutqueue-addon";

    /** Text of the sidebar button. Keep it short (about 14 characters fit). */
    String menuLabel();

    /**
     * Builds the screen opened when the sidebar button is clicked.
     *
     * @param parent the neon menu; call {@code minecraft.gui.setScreen(parent)} when your screen closes
     *               so the player lands back in the menu
     */
    Screen createScreen(Screen parent);
}
