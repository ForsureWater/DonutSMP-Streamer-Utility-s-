package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Customisation > Chat: Chat Colours (the pay / whisper colours) and Chat Settings (chat saves, payment sounds). */
public class ChatHubScreen extends NeonScreen {

    private final Screen parent;

    public ChatHubScreen(Screen parent) {
        super(Component.literal("Chat"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cxm = this.width / 2;
        int y = 46;
        this.addRenderableWidget(Button.builder(Component.literal("Chat Colours"), b ->
                this.minecraft.gui.setScreen(new ChatConfigScreen(this))).bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Chat Settings"), b ->
                this.minecraft.gui.setScreen(new ChatSettingsScreen(this))).bounds(cxm - 100, y, 200, 20).build());
        y += 24 + 8;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());
        centerContent();
    }

    @Override
    protected String headerText() {
        return "CHAT";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
