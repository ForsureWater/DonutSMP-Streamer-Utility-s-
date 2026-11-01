package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Page-switch animations for the neon menu (Customisation > Menu customisation > Animations).
 * Three styles: Fade (new page fades in), Slide (new page glides in 8px while fading) and Wipe
 * (a curtain sweeps left to right and reveals the new page).
 */
final class MenuAnim {

    static final String[] STYLES = {"Fade", "Slide", "Wipe"};
    static final String[] SPEEDS = {"Fast", "Normal", "Slow"};
    /** Small enough to stay inside the margin around the page content. */
    static final int SLIDE_PX = 8;

    private MenuAnim() { }

    static long durationMs(ModConfig c) {
        return switch (c.menuAnimSpeed) {
            case 0 -> 130L;
            case 2 -> 340L;
            default -> 220L;
        };
    }

    /** Eased progress 0..1 of a transition that began at startMillis (1 = finished / animations off). */
    static float progress(long startMillis, ModConfig c) {
        if (!c.menuAnimEnabled || startMillis <= 0) return 1f;
        float t = (System.currentTimeMillis() - startMillis) / (float) durationMs(c);
        if (t >= 1f) return 1f;
        if (t < 0f) t = 0f;
        float inv = 1f - t;
        return 1f - inv * inv * inv; // ease-out cubic
    }

    /** Vertical offset (pixels) to draw the page at. dir = +1 when moving down the sidebar, -1 when moving up. */
    static int slideOffset(float p, int dir, ModConfig c) {
        if (!c.menuAnimEnabled || c.menuAnimStyle != 1 || p >= 1f) return 0;
        return Math.round((1f - p) * SLIDE_PX * dir);
    }

    /** Paints the fade / wipe cover over the page area. Call after the page has been drawn. */
    static void drawCover(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float p, ModConfig c) {
        if (!c.menuAnimEnabled || p >= 1f) return;
        int rgb = Theme.panel & 0xFFFFFF;
        if (c.menuAnimStyle == 2) {
            int x = x0 + Math.round((x1 - x0) * p);
            if (x < x1) {
                g.fill(x, y0, x1, y1, 0xFF000000 | rgb);
                g.fill(x - 1, y0, x, y1, Theme.neon);
            }
        } else {
            int a = Math.round((1f - p) * 255f);
            if (a > 0) g.fill(x0, y0, x1, y1, (a << 24) | rgb);
        }
    }
}
