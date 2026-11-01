package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Version-proof drawing helpers (GuiGraphicsExtractor#renderOutline is missing/renamed in some Minecraft versions). */
public final class Gfx {
    private Gfx() {}

    /** 1px rectangle outline inside the box (x, y, w, h), same look as vanilla's renderOutline. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
