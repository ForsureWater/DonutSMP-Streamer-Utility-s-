package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DonutQueueLandingScreen extends NeonScreen {

    private final Screen parent;

    public DonutQueueLandingScreen(Screen parent) {
        super(Component.literal("DonutSMP Streamer Utility's"));
        this.parent = parent;
    }

    @Override
    protected String headerText() {
        return "DONUTSMP STREAMER UTILITYS";
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 102;

        this.addRenderableWidget(Button.builder(Component.literal("Anti Gamble Skip"), b ->
                this.minecraft.gui.setScreen(new DonutQueueConfigScreen(this))
        ).bounds(centerX - 100, y, 200, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Starting Bal"), b ->
                this.minecraft.gui.setScreen(new StartBalConfigScreen(this))
        ).bounds(centerX - 100, y, 200, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Bidding"), b ->
                this.minecraft.gui.setScreen(new BiddingConfigScreen(this))
        ).bounds(centerX - 100, y, 200, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Info Display"), b ->
                this.minecraft.gui.setScreen(new InfoDisplayConfigScreen(this))
        ).bounds(centerX - 100, y, 200, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(Component.literal("Earnings"), b ->
                this.minecraft.gui.setScreen(new EarningsConfigScreen(this))
        ).bounds(centerX - 100, y, 200, 20).build());
        y += 36;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, y, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, String text, int y, int color) {
        int textWidth = this.font.width(text);
        guiGraphics.text(this.font, text, (this.width - textWidth) / 2, y, color, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
