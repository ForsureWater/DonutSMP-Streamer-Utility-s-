package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InfoDisplayConfigScreen extends NeonScreen {

    private final Screen parent;
    private EditBox ignBox;
    private EditBox minBox;
    private EditBox maxBox;
    private EditBox gameBox;
    private EditBox customTextBox;
    private EditBox colorBox;
    private Button scaleValueButton;
    private String statusLine = "";

    public InfoDisplayConfigScreen(Screen parent) {
        super(Component.literal("Info Display"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = DonutQueueClient.config;
        int centerX = this.width / 2;
        int y = 46;
        int rowHeight = 22;

        // --- Text color (label drawn above in render) -------------------------
        colorBox = new EditBox(this.font, centerX - 100, y, 150, 20, Component.literal("Hex color"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.infoBoxColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed != null) {
                config.infoBoxColor = parsed;
                config.save();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += rowHeight + 14;

        // --- Size (label drawn above in render) -------------------------------
        scaleValueButton = addSizeRow(centerX, y, () -> config.infoBoxScale, v -> config.infoBoxScale = v);
        y += rowHeight + 14;

        // --- Info fields ----------------------------------------------------
        ignBox = new EditBox(this.font, centerX - 100, y, 200, 18, Component.literal("IGN"));
        ignBox.setMaxLength(24);
        ignBox.setValue(config.infoIgn);
        this.addRenderableWidget(ignBox);
        y += 20;

        minBox = new EditBox(this.font, centerX - 100, y, 200, 18, Component.literal("Minimum amount"));
        minBox.setMaxLength(16);
        minBox.setValue(config.infoMin);
        this.addRenderableWidget(minBox);
        y += 20;

        maxBox = new EditBox(this.font, centerX - 100, y, 200, 18, Component.literal("Maximum amount"));
        maxBox.setMaxLength(16);
        maxBox.setValue(config.infoMax);
        this.addRenderableWidget(maxBox);
        y += 20;

        gameBox = new EditBox(this.font, centerX - 100, y, 200, 18, Component.literal("Game"));
        gameBox.setMaxLength(24);
        gameBox.setValue(config.infoGame);
        this.addRenderableWidget(gameBox);
        y += 20;

        customTextBox = new EditBox(this.font, centerX - 100, y, 200, 18, Component.literal("Custom text"));
        customTextBox.setMaxLength(25);
        customTextBox.setValue(config.infoCustomText);
        this.addRenderableWidget(customTextBox);
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("Save Info"), b -> saveInfo())
                .bounds(centerX - 100, y, 200, 20).build());
        y += rowHeight + 4;

        // --- Done ---------------------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());

        centerContent();
    }

    private void saveInfo() {
        ModConfig config = DonutQueueClient.config;
        config.infoIgn = ignBox.getValue().trim();
        config.infoMin = minBox.getValue().trim();
        config.infoMax = maxBox.getValue().trim();
        config.infoGame = gameBox.getValue().trim();
        config.infoCustomText = customTextBox.getValue().trim();
        config.save();
        statusLine = "Saved.";
    }

    private Component boxLabel() {
        return Component.literal("Info Box: " + (DonutQueueClient.config.infoBoxEnabled ? "Shown" : "Hidden"));
    }

    private Component scaleLabel() {
        return Component.literal(String.format("Size: %.1fx", DonutQueueClient.config.infoBoxScale));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);


        guiGraphics.text(this.font, "Set text color (hex, e.g. FF66FF)",
                this.width / 2 - 100, colorBox.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Box size",
                this.width / 2 - 100, scaleValueButton.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "IGN / Min / Max / Game / Custom (25)",
                this.width / 2 - 100, ignBox.getY() - 12, C_NEON_HI, true);

        if (!statusLine.isEmpty()) {
            guiGraphics.text(this.font, statusLine, this.width / 2 + 100 - this.font.width(statusLine),
                    scaleValueButton.getY() - 12, C_GOOD, true);
        }
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, String text, int y, int color) {
        int textWidth = this.font.width(text);
        guiGraphics.text(this.font, text, (this.width - textWidth) / 2, y, color, true);
    }

    @Override
    public void onClose() {
        saveInfo();
        this.minecraft.gui.setScreen(parent);
    }
}
