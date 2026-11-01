package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Customisation > Screen features: On Screen KBM (choose how much of the keyboard shows), a CPS counter
 * (default spot: under the mouse buttons) and an FPS counter. One Move button opens a screen where each box is dragged on its own,
 * one colour picker colours the boxes.
 */
public class ScreenFeaturesScreen extends NeonScreen {

    private static final float[] SIZES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private final Screen parent;
    private EditBox colourBox;
    private String status = "";

    public ScreenFeaturesScreen(Screen parent) {
        super(Component.literal("Screen features"));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int left = cxm - 100;
        int y = 46;

        this.addRenderableWidget(Button.builder(Component.literal("On Screen KBM: " + onOff(c.kbmEnabled)), b -> {
            c.kbmEnabled = !c.kbmEnabled;
            c.save();
            b.setMessage(Component.literal("On Screen KBM: " + onOff(c.kbmEnabled)));
        }).bounds(left, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal(kbmModeLabel(c)), b -> {
            c.kbmMode = (c.kbmMode + 1) % ScreenFeatures.KBM_MODES.length;
            c.save();
            b.setMessage(Component.literal(kbmModeLabel(c)));
        }).bounds(left, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("CPS Counter: " + onOff(c.cpsEnabled)), b -> {
            c.cpsEnabled = !c.cpsEnabled;
            c.save();
            b.setMessage(Component.literal("CPS Counter: " + onOff(c.cpsEnabled)));
        }).bounds(left, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("FPS Counter: " + onOff(c.fpsEnabled)), b -> {
            c.fpsEnabled = !c.fpsEnabled;
            c.save();
            b.setMessage(Component.literal("FPS Counter: " + onOff(c.fpsEnabled)));
        }).bounds(left, y, 200, 20).build());
        y += 24 + 4;

        // one Move button for everything
        this.addRenderableWidget(Button.builder(Component.literal("Move"), b -> openMover())
                .bounds(left, y, 200, 20).build());
        y += 24 + 14;

        // box colour: [hex] [Set] [Reset]
        colourBox = new EditBox(this.font, left, y, 78, 20, Component.literal("Box colour hex"));
        colourBox.setMaxLength(8);
        colourBox.setValue(String.format("%06X", ScreenFeatures.accent() & 0xFFFFFF));
        this.addRenderableWidget(colourBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colourBox.getValue());
            if (parsed == null) {
                status = "Enter a 6-digit hex colour, e.g. 5599FF.";
                return;
            }
            c.screenFeaturesColor = parsed & 0xFFFFFF;
            c.save();
            status = "Box colour set to #" + String.format("%06X", parsed & 0xFFFFFF);
        }).bounds(cxm + 5, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            c.screenFeaturesColor = 0;
            c.save();
            colourBox.setValue(String.format("%06X", ScreenFeatures.accent() & 0xFFFFFF));
            status = "Box colour reset to the menu colour.";
        }).bounds(cxm + 55, y, 45, 20).build());
        y += 24 + 4;

        this.addRenderableWidget(Button.builder(Component.literal(sizeLabel(c)), b -> {
            int idx = 0;
            for (int i = 0; i < SIZES.length; i++) {
                if (Math.abs(SIZES[i] - c.screenFeaturesScale) < 0.01f) idx = i;
            }
            c.screenFeaturesScale = SIZES[(idx + 1) % SIZES.length];
            c.save();
            b.setMessage(Component.literal(sizeLabel(c)));
        }).bounds(left, y, 200, 20).build());
        y += 24 + 4;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left, y, 200, 20).build());
        centerContent();
    }

    /** One Move screen with all three boxes on it; each box is dragged on its own. */
    private void openMover() {
        this.minecraft.gui.setScreen(new ScreenFeaturesMoveScreen(this));
    }

    private static String kbmModeLabel(ModConfig c) {
        int m = Math.max(0, Math.min(ScreenFeatures.KBM_MODES.length - 1, c.kbmMode));
        return "Keys shown: " + ScreenFeatures.KBM_MODES[m];
    }

    private static String sizeLabel(ModConfig c) {
        return String.format("Size: %.2fx", ModConfig.clampScale(c.screenFeaturesScale));
    }

    @Override
    protected String headerText() {
        return "SCREEN FEATURES";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        int cxm = this.width / 2;
        g.text(this.font, "Box colour", cxm - 100, colourBox.getY() - 11, C_NEON_HI, true);
        Integer typed = DonutQueueConfigScreen.parseHexColor(colourBox.getValue());
        int col = typed != null ? typed : (ScreenFeatures.accent() | 0xFF000000);
        g.fill(cxm - 18, colourBox.getY(), cxm - 2, colourBox.getY() + 20, col);
        Gfx.outline(g, cxm - 18, colourBox.getY(), 16, 20, C_NEON);

        if (!status.isEmpty()) {
            g.text(this.font, status, cxm - this.font.width(status) / 2, this.height - 14, C_WARN, true);
        }
    }

    @Override
    public void onClose() {
        DonutQueueClient.config.save();
        this.minecraft.gui.setScreen(parent);
    }
}
