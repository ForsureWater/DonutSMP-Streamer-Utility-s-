package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base class that gives every settings sub-menu the same dark-blue neon look as
 * FuturisticMenuScreen.
 *
 * The sub-menus keep using ordinary vanilla Buttons / EditBoxes (so clicking and typing
 * stay 100% reliable). This class:
 *   - paints a dimmed backdrop and a neon panel with a title header behind the widgets
 *     (neonBackdrop), sized automatically around whatever widgets the screen added;
 *   - paints a neon face over every vanilla Button and a neon outline around every
 *     EditBox after the widgets render (neonFaces).
 *
 * Subclasses call neonBackdrop(g) before super.extractRenderState(...) and
 * neonFaces(g, mouseX, mouseY) right after it.
 */
abstract class NeonScreen extends Screen {

    static int C_DIM     = 0xC0020612;
    static int C_PANEL   = 0xF0050A24;
    static int C_HEADER  = 0xFF071138;
    static int C_TILE    = 0xFF07102F;
    static int C_NEON    = 0xFF2F6BFF;
    static int C_NEON_HI = 0xFF6FB4FF;
    static int C_EDGE    = 0xFF16338F;
    static int C_TEXT    = 0xFFE6F0FF;
    static int C_MUTED   = 0xFF7F95C9;
    static final int C_GOOD    = 0xFF33E6A0;
    static final int C_WARN    = 0xFFFFC857;

    /** Space above the first widget for the header band + the first field label. */
    static final int TOP_PAD = 42;

    protected NeonScreen(Component title) {
        super(title);
        Theme.apply(DonutQueueClient.config);
    }

    /**
     * Call at the end of init(): moves every widget down so the whole panel (header + content)
     * sits vertically centred on the screen. Returns how far things moved so a subclass can shift
     * any y value it stored itself.
     */
    protected int centerContent() {
        int minY = Integer.MAX_VALUE;
        int maxY = 0;
        for (Object o : this.children()) {
            if (o instanceof AbstractWidget w) {
                minY = Math.min(minY, w.getY());
                maxY = Math.max(maxY, w.getY() + w.getHeight());
            }
        }
        if (minY == Integer.MAX_VALUE) return 0;
        int panelTop = minY - TOP_PAD;
        int panelH = (maxY + 16) - panelTop;
        int dy = (this.height - panelH) / 2 - panelTop;
        if (dy <= 0) return 0; // already as tall as the screen
        for (Object o : this.children()) {
            if (o instanceof AbstractWidget w) w.setY(w.getY() + dy);
        }
        return dy;
    }

    /**
     * Adds a "-  Size: 1.0x  +" row (0.5x - 3.0x in 0.1 steps) at y and returns the middle button, so the
     * caller can draw a label 12px above it. get/set read and write the box's scale in the config.
     */
    protected Button addSizeRow(int centerX, int y, java.util.function.Supplier<Float> get,
                                java.util.function.Consumer<Float> set) {
        final Button[] mid = new Button[1];
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(Math.max(0.5f, Math.round((get.get() - 0.1f) * 10f) / 10f));
            DonutQueueClient.config.save();
            mid[0].setMessage(Component.literal(String.format("Size: %.1fx", get.get())));
        }).bounds(centerX - 100, y, 20, 20).build());

        mid[0] = this.addRenderableWidget(Button.builder(
                Component.literal(String.format("Size: %.1fx", get.get())), b -> { })
                .bounds(centerX - 75, y, 150, 20).build());
        mid[0].active = false;

        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(Math.min(3.0f, Math.round((get.get() + 0.1f) * 10f) / 10f));
            DonutQueueClient.config.save();
            mid[0].setMessage(Component.literal(String.format("Size: %.1fx", get.get())));
        }).bounds(centerX + 80, y, 20, 20).build());
        return mid[0];
    }

    /** Shortens text with ".." so it fits in maxW pixels. */
    protected String fitText(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "..") > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    /** Whether to draw the framed panel + header (the box mover turns this off so you can see the HUD). */
    protected boolean showPanel() {
        return true;
    }

    /** This screen paints its own backdrop; Screen#render would otherwise draw the vanilla one over it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    protected int dimColor() {
        return C_DIM;
    }

    protected String headerText() {
        return this.title.getString().toUpperCase() + " - SETTINGS";
    }

    protected void neonBackdrop(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, dimColor());
        if (!showPanel()) return;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = 0;
        for (Object o : this.children()) {
            if (o instanceof AbstractWidget w) {
                minX = Math.min(minX, w.getX());
                maxX = Math.max(maxX, w.getX() + w.getWidth());
                minY = Math.min(minY, w.getY());
                maxY = Math.max(maxY, w.getY() + w.getHeight());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            minX = this.width / 2 - 100;
            maxX = this.width / 2 + 100;
            minY = 46;
            maxY = 60;
        }

        int pxl = minX - 14;
        int pw = (maxX - minX) + 28;
        int top = Math.max(2, minY - TOP_PAD);
        int bottom = Math.min(this.height - 3, maxY + 16);
        int ph = bottom - top;

        if (Theme.glow) {
            Gfx.outline(g, pxl - 2, top - 2, pw + 4, ph + 4, Theme.neonA(0x22));
            Gfx.outline(g, pxl - 1, top - 1, pw + 2, ph + 2, Theme.neonA(0x55));
        }
        g.fill(pxl, top, pxl + pw, top + ph, C_PANEL);

        // header band with the title
        g.fill(pxl + 1, top + 1, pxl + pw - 1, top + 23, C_HEADER);
        g.fill(pxl + 1, top + 23, pxl + pw - 1, top + 24, C_NEON);
        String head = headerText();
        g.text(this.font, head, pxl + (pw - this.font.width(head)) / 2, top + 8, C_NEON_HI, true);
    }

    /** Paints the neon look over every Button and EditBox. Call right after super.extractRenderState(...). */
    protected void neonFaces(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Object o : this.children()) {
            if (o instanceof Button b) {
                int x = b.getX();
                int y = b.getY();
                int w = b.getWidth();
                int h = b.getHeight();
                boolean enabled = b.active;
                boolean hov = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
                String s = b.getMessage().getString();
                boolean act = s.contains(": ON") || s.endsWith(": Shown") || s.endsWith(": Enabled");

                int bg = !enabled ? Theme.btnOff : (act ? Theme.btnAct : Theme.btn);
                if (hov) bg = Theme.btnHov;
                g.fill(x, y, x + w, y + h, bg);
                Gfx.outline(g, x, y, w, h, !enabled ? C_EDGE : (hov ? C_NEON_HI : (act ? C_NEON : Theme.btnEdge)));
                if (act && enabled) g.fill(x, y, x + 2, y + h, C_NEON_HI);

                int tw = this.font.width(s);
                int color = !enabled ? C_MUTED : (hov || act ? 0xFFFFFFFF : C_TEXT);
                g.text(this.font, s, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
            } else if (o instanceof EditBox e) {
                Gfx.outline(g, e.getX() - 1, e.getY() - 1, e.getWidth() + 2, e.getHeight() + 2, C_NEON);
            }
        }
    }
}
