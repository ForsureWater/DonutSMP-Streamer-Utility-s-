package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Customisation > Guards > Pay Guard / Auction Guard: an ON/OFF button and the amount it kicks in at. */
public class GuardConfigScreen extends NeonScreen {

    enum Kind {
        PAY("Pay Guard"),
        AUCTION("Auction Guard");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private final Kind kind;
    private EditBox amountBox;
    private int infoY;
    private String status = "";

    GuardConfigScreen(Screen parent, Kind kind) {
        super(Component.literal(kind.label));
        this.parent = parent;
        this.kind = kind;
    }

    private boolean enabled() {
        ModConfig c = DonutQueueClient.config;
        return kind == Kind.PAY ? c.guardPayEnabled : c.guardAuctionEnabled;
    }

    private double amount() {
        ModConfig c = DonutQueueClient.config;
        return kind == Kind.PAY ? c.guardPayAmount : c.guardAuctionAmount;
    }

    private boolean fullNumber() {
        ModConfig c = DonutQueueClient.config;
        return kind == Kind.PAY ? c.guardPayFullNumber : c.guardAuctionFullNumber;
    }

    private String formatLabel() {
        return "Warning shows: " + (fullNumber() ? "Full number (175,000,000)" : "Short (175M)");
    }

    private String toggleLabel() {
        return kind.label + ": " + (enabled() ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;

        this.addRenderableWidget(Button.builder(Component.literal(toggleLabel()), b -> {
            if (kind == Kind.PAY) c.guardPayEnabled = !c.guardPayEnabled;
            else c.guardAuctionEnabled = !c.guardAuctionEnabled;
            c.save();
            b.setMessage(Component.literal(toggleLabel()));
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal(formatLabel()), b -> {
            if (kind == Kind.PAY) c.guardPayFullNumber = !c.guardPayFullNumber;
            else c.guardAuctionFullNumber = !c.guardAuctionFullNumber;
            c.save();
            b.setMessage(Component.literal(formatLabel()));
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24 + 14;

        // amount (label drawn above in render)
        amountBox = new EditBox(this.font, cxm - 100, y, 150, 20, Component.literal("Amount"));
        amountBox.setMaxLength(16);
        amountBox.setValue(Guards.money(amount()));
        this.addRenderableWidget(amountBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            double v = DonutQueueClient.parseAmount(amountBox.getValue().trim().replace("$", ""));
            if (v <= 0) {
                status = "Enter an amount above 0, e.g. 1, 50k or 2.5m.";
                return;
            }
            if (kind == Kind.PAY) c.guardPayAmount = v;
            else c.guardAuctionAmount = v;
            c.save();
            amountBox.setValue(Guards.money(v));
            status = kind.label + " amount set to $" + Guards.money(v) + ".";
        }).bounds(cxm + 55, y, 45, 20).build());
        y += 24;

        infoY = y + 4;
        y += 4 + 3 * 11 + 8;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());

        infoY += centerContent();
    }

    @Override
    protected String headerText() {
        return kind.label.toUpperCase();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        int cxm = this.width / 2;
        g.text(this.font, kind == Kind.PAY ? "Ask first when paying this much or more ($)"
                : "Ask first when buying for this much or more ($)", cxm - 100, amountBox.getY() - 12, C_NEON_HI, true);

        String l1;
        String l2;
        if (kind == Kind.PAY) {
            l1 = "/pay <player> <amount> at $" + Guards.money(amount());
            l2 = "or more opens a confirm menu first.";
        } else {
            l1 = "Clicking an auction listing priced at $" + Guards.money(amount());
            l2 = "or more opens a confirm menu first.";
        }
        g.text(this.font, l1, cxm - 100, infoY, C_MUTED, false);
        g.text(this.font, l2, cxm - 100, infoY + 11, C_MUTED, false);
        g.text(this.font, "Amounts can use k, m and b (50k, 2.5m).", cxm - 100, infoY + 22, C_MUTED, false);

        if (!status.isEmpty()) {
            g.text(this.font, status, cxm - this.font.width(status) / 2, this.height - 14, C_WARN, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
