package com.example.donutqueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;

/**
 * Customisation > Screen features: On Screen KBM (keys + mouse buttons), a CPS counter and an FPS counter.
 * Each one is its own box that can be moved anywhere. All OFF until turned on.
 */
final class ScreenFeatures {
    private ScreenFeatures() {}

    static final String[] KBM_MODES = {"WASD", "WASD + Mouse", "WASD + Space + Mouse", "Full keyboard + Mouse"};

    static final int KBM_W = 58;
    static final int CPS_W = 58;
    static final int CPS_H = 16;
    static final int FPS_W = 58;
    static final int FPS_H = 16;

    private static final int KEY = 18;
    private static final int GAP = 2;

    private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private static final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private static boolean wasLeft;
    private static boolean wasRight;

    /** Colour of the boxes: the one picked in Screen features, or the menu theme colour. */
    static int accent() {
        ModConfig c = DonutQueueClient.config;
        if (c != null && c.screenFeaturesColor != 0) return 0xFF000000 | (c.screenFeaturesColor & 0xFFFFFF);
        return Theme.neon;
    }

    static boolean showsMouse(int mode) {
        return mode >= 1;
    }

    /** Height of the KBM box for a mode (local, unscaled). */
    static int kbmHeight(int mode) {
        int m = clampMode(mode);
        int rows = 2;                       // W row + A S D row
        if (m == 3) rows += 2;              // Ctrl/Shift row + Space row
        else if (m == 2) rows += 1;         // Space row
        int h = rows * KEY + (rows - 1) * GAP;
        if (showsMouse(m)) h += GAP + 22;   // LMB / RMB row
        return h;
    }

    private static int clampMode(int m) {
        return Math.max(0, Math.min(KBM_MODES.length - 1, m));
    }

    // ------------------------------------------------------------------
    // Click counting (polled every rendered frame so fast clicking is not missed)
    // ------------------------------------------------------------------

    /**
     * Is a mouse button held? In the world this uses the game's own Attack / Use key state (vanilla clears it
     * whenever a screen opens, so right-clicking a chest can't leave it stuck). Inside a screen it asks GLFW.
     */
    static final int BTN_LEFT = 0;
    static final int BTN_RIGHT = 1;

    static boolean mouseDown(Minecraft mc, int button) {
        if (mc.gui.screen() == null) {
            try {
                return (button == BTN_LEFT ? mc.options.keyAttack : mc.options.keyUse).isDown();
            } catch (RuntimeException e) {
                return false;
            }
        }
        try {
            // 26.3 replaced GLFW with SDL, so ask the game's own mouse handler instead of polling GLFW.
            return button == BTN_LEFT ? mc.mouseHandler.isLeftPressed() : mc.mouseHandler.isRightPressed();
        } catch (RuntimeException | Error e) {
            return false;
        }
    }

    private static void pollClicks(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mouseDown(mc, BTN_LEFT);
        boolean r = mouseDown(mc, BTN_RIGHT);
        if (l && !wasLeft) leftClicks.addLast(now);
        if (r && !wasRight) rightClicks.addLast(now);
        wasLeft = l;
        wasRight = r;
        trim(leftClicks, now);
        trim(rightClicks, now);
    }

    private static void trim(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.removeFirst();
    }

    static int leftCps() {
        return leftClicks.size();
    }

    static int rightCps() {
        return rightClicks.size();
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g) {
        ModConfig c = DonutQueueClient.config;
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc == null || mc.options == null) return;
        // keep counting even when only the CPS box is on
        pollClicks(mc);
        if (mc.gui.screen() instanceof MovableBoxScreen) return; // the mover draws its own preview
        if (mc.gui.hud.isHidden()) return;

        float scale = ModConfig.clampScale(c.screenFeaturesScale);
        if (c.kbmEnabled) {
            begin(g, c.kbmX, c.kbmY, scale);
            drawKbm(g, mc, c.kbmMode);
            end(g);
        }
        if (c.cpsEnabled) {
            begin(g, c.cpsX, c.cpsY, scale);
            drawCps(g, mc);
            end(g);
        }
        if (c.fpsEnabled) {
            begin(g, c.fpsX, c.fpsY, scale);
            drawFps(g, mc);
            end(g);
        }
    }

    private static void begin(GuiGraphicsExtractor g, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
    }

    private static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    private static void drawKey(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, String label, boolean down) {
        g.fill(x, y, x + w, y + h, down ? ((accent() & 0xFFFFFF) | 0xC0000000) : 0x90000000);
        Gfx.outline(g, x, y, w, h, down ? 0xFFFFFFFF : accent());
        int tw = mc.font.width(label);
        g.text(mc.font, label, x + (w - tw) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : 0xFFCCCCCC, true);
    }

    private static void drawKbm(GuiGraphicsExtractor g, Minecraft mc, int mode) {
        int m = clampMode(mode);
        var o = mc.options;
        int x0 = 0;
        int y = 0;
        // W
        drawKey(g, mc, x0 + KEY + GAP, y, KEY, KEY, "W", o.keyUp.isDown());
        y += KEY + GAP;
        // A S D
        drawKey(g, mc, x0, y, KEY, KEY, "A", o.keyLeft.isDown());
        drawKey(g, mc, x0 + KEY + GAP, y, KEY, KEY, "S", o.keyDown.isDown());
        drawKey(g, mc, x0 + 2 * (KEY + GAP), y, KEY, KEY, "D", o.keyRight.isDown());
        y += KEY + GAP;
        if (m == 3) {
            int half = (KBM_W - GAP) / 2;
            drawKey(g, mc, x0, y, half, KEY, "Ctrl", o.keySprint.isDown());
            drawKey(g, mc, x0 + half + GAP, y, KBM_W - half - GAP, KEY, "Shift", o.keyShift.isDown());
            y += KEY + GAP;
        }
        if (m >= 2) {
            drawKey(g, mc, x0, y, KBM_W, KEY, "Space", o.keyJump.isDown());
            y += KEY + GAP;
        }
        if (showsMouse(m)) {
            int half = (KBM_W - GAP) / 2;
            boolean l = mouseDown(mc, BTN_LEFT);
            boolean r = mouseDown(mc, BTN_RIGHT);
            drawKey(g, mc, x0, y, half, 22, "LMB", l);
            drawKey(g, mc, x0 + half + GAP, y, KBM_W - half - GAP, 22, "RMB", r);
        }
    }

    private static void drawCps(GuiGraphicsExtractor g, Minecraft mc) {
        String text = leftCps() + " | " + rightCps() + " CPS";
        g.fill(0, 0, CPS_W, CPS_H, 0x90000000);
        Gfx.outline(g, 0, 0, CPS_W, CPS_H, accent());
        g.text(mc.font, text, (CPS_W - mc.font.width(text)) / 2, 4, 0xFFFFFFFF, true);
    }

    private static void drawFps(GuiGraphicsExtractor g, Minecraft mc) {
        String text = mc.getFps() + " FPS";
        g.fill(0, 0, FPS_W, FPS_H, 0x90000000);
        Gfx.outline(g, 0, 0, FPS_W, FPS_H, accent());
        g.text(mc.font, text, (FPS_W - mc.font.width(text)) / 2, 4, 0xFFFFFFFF, true);
    }
}
