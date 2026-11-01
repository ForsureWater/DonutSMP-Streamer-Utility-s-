package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Customisation > Chat > Chat Settings: Chat saves and Payment sounds. */
public class ChatSettingsScreen extends NeonScreen {

    private final Screen parent;

    public ChatSettingsScreen(Screen parent) {
        super(Component.literal("Chat Settings"));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;
        this.addRenderableWidget(Button.builder(Component.literal("Chat saves: " + onOff(c.chatSaveEnabled)), b -> {
            c.chatSaveEnabled = !c.chatSaveEnabled;
            c.save();
            b.setMessage(Component.literal("Chat saves: " + onOff(c.chatSaveEnabled)));
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Payment sounds: " + onOff(c.paySoundsEnabled)), b -> {
            c.paySoundsEnabled = !c.paySoundsEnabled;
            c.save();
            b.setMessage(Component.literal("Payment sounds: " + onOff(c.paySoundsEnabled)));
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24 + 8;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());
        centerContent();
    }

    @Override
    protected String headerText() {
        return "CHAT SETTINGS";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        DonutQueueClient.config.save();
        this.minecraft.gui.setScreen(parent);
    }
}
