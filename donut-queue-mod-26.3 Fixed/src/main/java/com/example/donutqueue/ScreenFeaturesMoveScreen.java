package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The one Move screen for Screen features: all three boxes (KBM, CPS, FPS) are on screen together and each one is
 * dragged on its own. Press on a box and drag; click an empty spot and the box you used last jumps there.
 * Snaps to the screen edges and centre lines.
 */
public class ScreenFeaturesMoveScreen extends NeonScreen {

    private static final String[] NAMES = {"KBM", "CPS", "FPS"};

    private final Screen parent;
    /** Draw order (last = on top = the box that gets grabbed first). */
    private final int[] order = {0, 1, 2};
    private int active = 0;
    private boolean dragging = false;
    private double grabX;
    private double grabY;
    private boolean guideX;
    private boolean guideY;

    public ScreenFeaturesMoveScreen(Screen parent) {
        super(Component.literal("Move Screen features"));
        this.parent = parent;
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    @Override
    protected int dimColor() {
        return 0x70020612;
    }

    @Override
    protected void init() {
        dragging = false;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 26, 120, 20)
                .build());
    }

    // ---- box data (by index: 0 = KBM, 1 = CPS, 2 = FPS) ----------------------------------------

    private static int bx(int i) {
        ModConfig c = DonutQueueClient.config;
        return i == 0 ? c.kbmX : i == 1 ? c.cpsX : c.fpsX;
    }

    private static int by(int i) {
        ModConfig c = DonutQueueClient.config;
        return i == 0 ? c.kbmY : i == 1 ? c.cpsY : c.fpsY;
    }

    private static void setPos(int i, int x, int y) {
        ModConfig c = DonutQueueClient.config;
        if (i == 0) { c.kbmX = x; c.kbmY = y; }
        else if (i == 1) { c.cpsX = x; c.cpsY = y; }
        else { c.fpsX = x; c.fpsY = y; }
    }

    private static boolean isOn(int i) {
        ModConfig c = DonutQueueClient.config;
        return i == 0 ? c.kbmEnabled : i == 1 ? c.cpsEnabled : c.fpsEnabled;
    }

    private static int bw(int i) {
        ModConfig c = DonutQueueClient.config;
        int w = i == 0 ? ScreenFeatures.KBM_W : i == 1 ? ScreenFeatures.CPS_W : ScreenFeatures.FPS_W;
        return DonutQueueClient.scaledSize(w, c.screenFeaturesScale);
    }

    private static int bh(int i) {
        ModConfig c = DonutQueueClient.config;
        int h = i == 0 ? ScreenFeatures.kbmHeight(c.kbmMode) : i == 1 ? ScreenFeatures.CPS_H : ScreenFeatures.FPS_H;
        return DonutQueueClient.scaledSize(h, c.screenFeaturesScale);
    }

    private void bringToFront(int i) {
        int pos = 0;
        for (int k = 0; k < order.length; k++) if (order[k] == i) pos = k;
        for (int k = pos; k < order.length - 1; k++) order[k] = order[k + 1];
        order[order.length - 1] = i;
        active = i;
    }

    // ---- mouse -----------------------------------------------------------------------------------

    /** 26.3 moved from GLFW to SDL, so the left mouse button is 1 there (it was 0 before); accept both. */
    private static boolean isLeftButton(net.minecraft.client.input.MouseButtonEvent event) {
        int b = event.button();
        return b == 0 || b == 1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (isLeftButton(event) && !overDone(mouseX, mouseY)) {
            int hit = -1;
            for (int k = order.length - 1; k >= 0; k--) {
                int i = order[k];
                if (mouseX >= bx(i) && mouseX < bx(i) + bw(i) && mouseY >= by(i) && mouseY < by(i) + bh(i)) {
                    hit = i;
                    break;
                }
            }
            if (hit >= 0) {
                bringToFront(hit);
                grabX = mouseX - bx(hit);
                grabY = mouseY - by(hit);
            } else {
                // empty spot: the box used last jumps there (centred on the cursor)
                grabX = bw(active) / 2.0;
                grabY = bh(active) / 2.0;
            }
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (isLeftButton(event) && dragging) {
            dragging = false;
            DonutQueueClient.config.save();
        }
        return super.mouseReleased(event);
    }

    private boolean overDone(int mx, int my) {
        int bx0 = this.width / 2 - 60;
        int by0 = this.height - 26;
        return mx >= bx0 && mx < bx0 + 120 && my >= by0 && my < by0 + 20;
    }

    // ---- render ----------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        guideX = false;
        guideY = false;
        if (dragging) {
            int w = bw(active);
            int h = bh(active);
            int freeW = Math.max(0, this.width - w);
            int freeH = Math.max(0, this.height - h);
            int nx = Math.max(0, Math.min(freeW, (int) Math.round(mouseX - grabX)));
            int ny = Math.max(0, Math.min(freeH, (int) Math.round(mouseY - grabY)));
            if (Math.abs(nx + w / 2 - this.width / 2) <= 3) {
                nx = Math.max(0, Math.min(freeW, (this.width - w) / 2));
                guideX = true;
            }
            if (Math.abs(ny + h / 2 - this.height / 2) <= 3) {
                ny = Math.max(0, Math.min(freeH, (this.height - h) / 2));
                guideY = true;
            }
            if (nx <= 3) nx = 0;
            if (nx >= freeW - 3) nx = freeW;
            if (ny <= 3) ny = 0;
            if (ny >= freeH - 3) ny = freeH;
            setPos(active, nx, ny);
        }

        neonBackdrop(g);
        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, guideX ? C_NEON_HI : 0x22FFFFFF);
        g.fill(0, this.height / 2, this.width, this.height / 2 + 1, guideY ? C_NEON_HI : 0x22FFFFFF);

        for (int k = 0; k < order.length; k++) {
            int i = order[k];
            int x = bx(i);
            int y = by(i);
            int w = bw(i);
            int h = bh(i);
            boolean on = isOn(i);
            boolean sel = i == active;
            g.fill(x, y, x + w, y + h, on ? 0xE0071130 : 0xA0071130);
            int outline = (sel && dragging) ? C_GOOD : (sel ? C_NEON_HI : (on ? ScreenFeatures.accent() : C_MUTED));
            Gfx.outline(g, x, y, w, h, outline);
            String label = NAMES[i] + (on ? "" : " (off)");
            g.text(this.font, label, x + 4, y + 4, on ? ScreenFeatures.accent() : C_MUTED, true);
        }

        drawCentered(g, "Drag each box anywhere - they move separately", 10, C_TEXT);
        drawCentered(g, NAMES[active] + " position: " + bx(active) + ", " + by(active), 22, C_MUTED);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphicsExtractor g, String text, int y, int color) {
        g.text(this.font, text, (this.width - this.font.width(text)) / 2, y, color, true);
    }

    @Override
    public void onClose() {
        DonutQueueClient.config.save();
        this.minecraft.gui.setScreen(parent);
    }
}
