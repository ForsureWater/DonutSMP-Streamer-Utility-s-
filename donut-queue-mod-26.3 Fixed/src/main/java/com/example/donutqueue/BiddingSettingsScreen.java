package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Gear menu of the Bidding hotkey menu: the Bidding options of the neon menu that are not already in the hotkey menu
 * (round time, minimum bid and stop are on the hotkey menu itself).
 */
public class BiddingSettingsScreen extends NeonScreen {

    private static final int W = 300;
    private static final int H = 150;

    private final Screen parent;
    private int left;
    private int top;

    public BiddingSettingsScreen(Screen parent) {
        super(Component.literal("Bidding settings"));
        this.parent = parent;
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    private String boxLabel() {
        return "Bid Box: " + (DonutQueueClient.config.biddingBoxEnabled ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);
        int x = left + 14;
        int w = W - 28;

        this.addRenderableWidget(Button.builder(Component.literal(boxLabel()), b -> {
            c.biddingBoxEnabled = !c.biddingBoxEnabled;
            c.save();
            b.setMessage(Component.literal(boxLabel()));
        }).bounds(x, top + 34, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Move Box"), b ->
                this.minecraft.gui.setScreen(new MovableBoxScreen(this, "Move Bidding Box",
                        DonutQueueClient.scaledSize(270, c.biddingBoxScale), DonutQueueClient.scaledSize(70, c.biddingBoxScale),
                        "Bidding", c.biddingBoxColor,
                        () -> c.biddingBoxX, () -> c.biddingBoxY, v -> c.biddingBoxX = v, v -> c.biddingBoxY = v)))
                .bounds(x, top + 60, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Size, Color & Blacklist"), b ->
                this.minecraft.gui.setScreen(new BiddingConfigScreen(this)))
                .bounds(x, top + 86, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x, top + H - 30, w, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        if (Theme.glow) {
            Gfx.outline(g, left - 2, top - 2, W + 4, H + 4, Theme.neonA(0x22));
            Gfx.outline(g, left - 1, top - 1, W + 2, H + 2, Theme.neonA(0x55));
        }
        g.fill(left, top, left + W, top + H, C_PANEL);
        g.fill(left + 1, top + 1, left + W - 1, top + 23, C_HEADER);
        g.fill(left + 1, top + 23, left + W - 1, top + 24, C_NEON);
        String head = "BIDDING SETTINGS";
        g.text(this.font, head, left + (W - this.font.width(head)) / 2, top + 8, C_NEON_HI, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);
        Tutorial.renderOverlay(g, this.minecraft);
    }
}
