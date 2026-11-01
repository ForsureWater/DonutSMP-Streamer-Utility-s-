package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Side menu of the Earnings page in the neon menu. Everything else (move, show/hide, chat messages,
 * tracking, reset, top-N) is on the main page, so this only has the box colour.
 */
public class EarningsConfigScreen extends NeonScreen {

    private final Screen parent;
    private EditBox colorBox;
    private Button sizeButton;

    public EarningsConfigScreen(Screen parent) {
        super(Component.literal("Earnings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = DonutQueueClient.config;
        int centerX = this.width / 2;
        int y = 46;

        // --- Box color (label drawn above in render) -------------------------
        colorBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Hex color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.earningsBoxColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed != null) {
                config.earningsBoxColor = parsed;
                config.save();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += 34 + 12;

        // --- Box size (label drawn above in render) --------------------------
        sizeButton = addSizeRow(centerX, y, () -> config.earningsBoxScale, v -> config.earningsBoxScale = v);
        y += 34;

        // --- Done ---------------------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());

        centerContent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);

        guiGraphics.text(this.font, "Set Earnings box color (hex, e.g. FFD700)",
                this.width / 2 - 100, colorBox.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Earnings box size",
                this.width / 2 - 100, sizeButton.getY() - 12, C_NEON_HI, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
