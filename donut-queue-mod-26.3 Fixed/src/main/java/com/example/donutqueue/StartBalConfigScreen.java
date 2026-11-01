package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Side menu of the Starting Bal page in the neon menu. The main page already has move, show/hide,
 * chat messages, start/reset tracking and the goal, so this only has the box colour and a
 * "Check Now" progress read-out.
 */
public class StartBalConfigScreen extends NeonScreen {

    private final Screen parent;
    private EditBox colorBox;
    private Button sizeButton;
    private int statusY;
    private String statusLine = "";

    public StartBalConfigScreen(Screen parent) {
        super(Component.literal("Starting Bal"));
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
        colorBox.setValue(String.format("%06X", config.startBalBoxColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed != null) {
                config.startBalBoxColor = parsed;
                config.save();
            }
        }).bounds(centerX + 55, y, 45, 20).build());
        y += 34 + 12;

        // --- Box size (label drawn above in render) --------------------------
        sizeButton = addSizeRow(centerX, y, () -> config.startBalBoxScale, v -> config.startBalBoxScale = v);
        y += 34;

        // --- Check progress -----------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Check Now"), b -> checkNow())
                .bounds(centerX - 100, y, 200, 20).build());
        y += 24;
        statusY = y;
        y += 16;

        // --- Done ---------------------------------------------------------
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());

        statusY += centerContent();
    }

    private void checkNow() {
        ModConfig config = DonutQueueClient.config;
        if (config.goalAmount > 0) {
            statusLine = "Earned $" + DonutQueueClient.formatCompact(config.earnedTotal)
                    + " / $" + DonutQueueClient.formatCompact(config.goalAmount);
        } else if (config.awaitingBalCapture) {
            statusLine = "Waiting for your /bal ...";
        } else if (config.trackingEnabled) {
            statusLine = "Tracking: $" + DonutQueueClient.formatCompact(config.earnedTotal)
                    + (config.hasStartingBalance ? " (from $" + DonutQueueClient.formatCompact(config.startingBalance) + ")" : "");
        } else {
            statusLine = "No goal set and not tracking.";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);

        guiGraphics.text(this.font, "Set Starting Bal box color (hex, e.g. 33CC66)",
                this.width / 2 - 100, colorBox.getY() - 12, C_NEON_HI, true);

        guiGraphics.text(this.font, "Starting Bal box size",
                this.width / 2 - 100, sizeButton.getY() - 12, C_NEON_HI, true);

        if (!statusLine.isEmpty()) {
            guiGraphics.text(this.font, fitText(statusLine, 200), this.width / 2 - 100, statusY + 4, C_WARN, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
