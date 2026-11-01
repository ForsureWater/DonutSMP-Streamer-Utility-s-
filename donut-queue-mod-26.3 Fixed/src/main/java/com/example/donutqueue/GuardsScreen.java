package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Customisation > Guards: pick Pay Guard or Auction Guard. */
public class GuardsScreen extends NeonScreen {

    private final Screen parent;
    private Button first;

    public GuardsScreen(Screen parent) {
        super(Component.literal("Guards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cxm = this.width / 2;
        int y = 46;
        first = this.addRenderableWidget(Button.builder(Component.literal("Pay Guard"), b ->
                this.minecraft.gui.setScreen(new GuardConfigScreen(this, GuardConfigScreen.Kind.PAY)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Auction Guard"), b ->
                this.minecraft.gui.setScreen(new GuardConfigScreen(this, GuardConfigScreen.Kind.AUCTION)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24 + 40;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());
        centerContent();
    }

    @Override
    protected String headerText() {
        return "GUARDS";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int top = first.getY();
        g.text(this.font, "Ask \"are you sure?\" first", cxm - 100, top - 12, C_NEON_HI, true);

        int y = top + 24 * 2 + 4;
        g.text(this.font, "Pay Guard: " + (c.guardPayEnabled ? "ON, from $" + Guards.money(c.guardPayAmount) : "OFF"),
                cxm - 100, y, c.guardPayEnabled ? C_GOOD : C_MUTED, false);
        g.text(this.font, "Auction Guard: " + (c.guardAuctionEnabled ? "ON, from $" + Guards.money(c.guardAuctionAmount) : "OFF"),
                cxm - 100, y + 14, c.guardAuctionEnabled ? C_GOOD : C_MUTED, false);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
