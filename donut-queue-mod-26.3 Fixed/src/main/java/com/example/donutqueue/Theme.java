package com.example.donutqueue;

/**
 * The menu's colour theme. Everything in the neon menu (and every sub-screen that extends NeonScreen)
 * reads its colours from here, so changing the menu colour / GUI style in Customisation > Menu
 * customisation re-colours the whole menu at once.
 *
 * The default blue (2F6BFF) uses the exact original palette, so nothing changes until someone picks
 * a different colour. Any other colour has its dark shades worked out from the chosen colour's hue.
 */
final class Theme {

    static final int DEFAULT_ACCENT = 0xFF2F6BFF;

    // main palette
    static int dim, panel, header, sidebar, tile;
    static int neon, neonHi, edge, text, muted;
    // button faces
    static int btn, btnAct, btnHov, btnEdge, btnOff, bar;
    // graph / chart surfaces (these used to be hard-coded blue)
    static int plot, spark, track, flat, grid, ringOff;
    /** Whether to draw the soft glow outline around panels (Neon and Glass styles). */
    static boolean glow = true;

    static {
        apply(DEFAULT_ACCENT, 0, true, 100);
    }

    private Theme() { }

    /** Re-reads the menu settings from the config. Cheap - safe to call whenever a setting changes. */
    static void apply(ModConfig c) {
        if (c == null) return;
        apply(c.menuColor, c.menuStyle, c.menuBackdropDim, c.menuPanelOpacity);
    }

    /** An alpha-tinted version of the main menu colour (for glows and faint grid lines). */
    static int neonA(int alpha) {
        return (alpha << 24) | (neon & 0xFFFFFF);
    }

    /** An alpha-tinted version of the bright menu colour (crosshairs, hover lines). */
    static int neonHiA(int alpha) {
        return (alpha << 24) | (neonHi & 0xFFFFFF);
    }

    /** True when the menu is still the original blue (the donut logo is then drawn untouched). */
    static boolean isDefaultAccent() {
        return (neon & 0xFFFFFF) == (DEFAULT_ACCENT & 0xFFFFFF);
    }

    static void apply(int accent, int style, boolean dimOn, int opacityPct) {
        float opa = Math.max(0.4f, Math.min(1f, opacityPct / 100f));
        if (style == 2) opa *= 0.6f;                       // Glass
        int panelA = Math.round((style == 1 ? 0xFF : 0xF0) * opa);
        int solidA = Math.round(0xFF * opa);
        int dimA = !dimOn ? 0 : (style == 2 ? 0x80 : 0xC0);
        glow = style != 1;                                  // Flat has no glow

        int rgb = accent & 0xFFFFFF;
        if (rgb == (DEFAULT_ACCENT & 0xFFFFFF)) {
            neon = 0xFF2F6BFF;
            neonHi = 0xFF6FB4FF;
            edge = 0xFF16338F;
            text = 0xFFE6F0FF;
            muted = 0xFF7F95C9;
            btn = 0xFF081146;
            btnAct = 0xFF0E2878;
            btnHov = 0xFF16399E;
            btnEdge = 0xFF1A3FA8;
            btnOff = 0xFF050B2A;
            bar = 0xFF0A1450;
            dim = (dimA << 24) | 0x020612;
            panel = (panelA << 24) | 0x050A24;
            header = (solidA << 24) | 0x071138;
            sidebar = (solidA << 24) | 0x040922;
            tile = (solidA << 24) | 0x07102F;
            plot = 0xFF02050F;
            spark = 0xFF030820;
            track = 0xFF0A1850;
            flat = 0xFF14246A;
            grid = 0xFF3A4F9A;
            ringOff = 0xFF2A3350;
        } else {
            float[] hsb = rgbToHsb(rgb);
            float h = hsb[0];
            float s = hsb[1];
            float v = Math.max(0.35f, hsb[2]);              // very dark picks would vanish on the dark panel
            // dark shades keep the hue; greys stay grey
            float sd = s < 0.12f ? s : Math.max(0.5f, Math.min(0.95f, s * 1.1f));

            neon = 0xFF000000 | hsbToRgb(h, s, v);
            neonHi = 0xFF000000 | hsbToRgb(h, s * 0.7f, 1f);
            edge = 0xFF000000 | hsbToRgb(h, sd, 0.56f);
            text = 0xFF000000 | hsbToRgb(h, s * 0.1f, 1f);
            muted = 0xFF000000 | hsbToRgb(h, s * 0.45f, 0.79f);
            btn = 0xFF000000 | hsbToRgb(h, sd, 0.27f);
            btnAct = 0xFF000000 | hsbToRgb(h, sd, 0.47f);
            btnHov = 0xFF000000 | hsbToRgb(h, sd * 0.92f, 0.62f);
            btnEdge = 0xFF000000 | hsbToRgb(h, sd * 0.88f, 0.66f);
            btnOff = 0xFF000000 | hsbToRgb(h, sd, 0.16f);
            bar = 0xFF000000 | hsbToRgb(h, sd, 0.31f);
            dim = (dimA << 24) | hsbToRgb(h, sd, 0.07f);
            panel = (panelA << 24) | hsbToRgb(h, sd, 0.14f);
            header = (solidA << 24) | hsbToRgb(h, sd, 0.22f);
            sidebar = (solidA << 24) | hsbToRgb(h, sd, 0.13f);
            tile = (solidA << 24) | hsbToRgb(h, sd, 0.18f);
            plot = 0xFF000000 | hsbToRgb(h, sd, 0.06f);
            spark = 0xFF000000 | hsbToRgb(h, sd, 0.125f);
            track = 0xFF000000 | hsbToRgb(h, sd, 0.31f);
            flat = 0xFF000000 | hsbToRgb(h, sd, 0.42f);
            grid = 0xFF000000 | hsbToRgb(h, sd * 0.8f, 0.6f);
            ringOff = 0xFF000000 | hsbToRgb(h, s * 0.3f, 0.32f);
        }

        // NeonScreen keeps its own copies (its sub-screens use the bare names)
        NeonScreen.C_DIM = dim;
        NeonScreen.C_PANEL = panel;
        NeonScreen.C_HEADER = header;
        NeonScreen.C_TILE = tile;
        NeonScreen.C_NEON = neon;
        NeonScreen.C_NEON_HI = neonHi;
        NeonScreen.C_EDGE = edge;
        NeonScreen.C_TEXT = text;
        NeonScreen.C_MUTED = muted;
    }

    // --- tiny HSB helpers (java.awt is avoided on purpose) -------------------------------------

    static float[] rgbToHsb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int hsbToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6f) % 2f - 1));
        float m = v - c;
        float r, g, b;
        int sector = (int) (h * 6f);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((g + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }
}
