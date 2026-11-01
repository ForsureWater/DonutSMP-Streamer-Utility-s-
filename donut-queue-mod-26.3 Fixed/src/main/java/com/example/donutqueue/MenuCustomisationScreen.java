package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Customisation > Menu customisation. A small hub:
 *   Menu colour       - pick a preset or type a hex colour; the whole menu is re-shaded from it
 *   Main menu items   - opens the real menu in edit mode: grab and drop cards to rearrange, show / hide them
 *   Sidebar tabs      - opens the real menu faded with only the sidebar live: click a tab to hide / show it
 *   Animations        - on/off, style (Fade / Slide / Wipe) and speed for switching pages, with a live preview
 *   GUI style         - Neon / Flat / Glass, backdrop dim, panel opacity
 * Every change is saved straight away and shows up immediately (the menu reads its colours from Theme).
 */
public class MenuCustomisationScreen extends NeonScreen {

    enum Section {
        HUB("Menu customisation"),
        COLOUR("Menu colour"),
        ANIM("Animations"),
        GUI("GUI style");

        final String title;

        Section(String title) {
            this.title = title;
        }
    }

    private static final String[] PRESET_NAMES = {"Blue", "Purple", "Pink", "Red", "Orange", "Gold", "Green", "Cyan"};
    private static final int[] PRESET_COLORS = {
            0xFF2F6BFF, 0xFF9B5CFF, 0xFFFF4FA3, 0xFFFF4D4D, 0xFFFF8A2F, 0xFFFFC857, 0xFF2FD97A, 0xFF2FD6FF
    };
    private static final String[] STYLE_NAMES = {"Neon", "Flat", "Glass"};

    private final Screen parent;
    private final Section section;
    private String status = "";

    // Menu colour
    private EditBox colorBox;
    private final List<Button> swatches = new ArrayList<>();

    // Animations preview
    private long previewStart = 0;
    private int previewY = 0;

    public MenuCustomisationScreen(Screen parent) {
        this(parent, Section.HUB);
    }

    private MenuCustomisationScreen(Screen parent, Section section) {
        super(Component.literal(section.title));
        this.parent = parent;
        this.section = section;
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        swatches.clear();
        previewStart = System.currentTimeMillis();
        switch (section) {
            case COLOUR -> buildColour();
            case ANIM -> buildAnim();
            case GUI -> buildGui();
            default -> buildHub();
        }
        int dy = centerContent();
        previewY += dy;
    }

    private void buildHub() {
        int cxm = this.width / 2;
        int y = 46;
        this.addRenderableWidget(Button.builder(Component.literal("Menu colour"), b ->
                this.minecraft.gui.setScreen(new MenuCustomisationScreen(this, Section.COLOUR)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        // these two open the real menu in edit mode
        this.addRenderableWidget(Button.builder(Component.literal("Main menu items"), b ->
                this.minecraft.gui.setScreen(new FuturisticMenuScreen(this, FuturisticMenuScreen.EDIT_MAIN)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Sidebar tabs"), b ->
                this.minecraft.gui.setScreen(new FuturisticMenuScreen(this, FuturisticMenuScreen.EDIT_TABS)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Animations"), b ->
                this.minecraft.gui.setScreen(new MenuCustomisationScreen(this, Section.ANIM)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("GUI style"), b ->
                this.minecraft.gui.setScreen(new MenuCustomisationScreen(this, Section.GUI)))
                .bounds(cxm - 100, y, 200, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Reset menu"), b -> {
            resetAll();
            status = "Menu customisation reset to defaults.";
        }).bounds(cxm - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm + 2, y, 98, 20).build());
    }

    private void buildColour() {
        ModConfig config = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;

        // 8 preset swatches, 2 rows of 4 (painted in render)
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int idx = i;
            int sx = cxm - 100 + (i % 4) * 52;
            int sy = y + (i / 4) * 24;
            swatches.add(this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
                setColour(PRESET_COLORS[idx]);
                colorBox.setValue(String.format("%06X", PRESET_COLORS[idx] & 0xFFFFFF));
                status = PRESET_NAMES[idx] + " menu colour applied.";
            }).bounds(sx, sy, 44, 20).build()));
        }
        y += 24 * 2 + 14;

        // hex colour (label drawn above in render)
        colorBox = new EditBox(this.font, cxm - 100, y, 78, 20, Component.literal("Menu colour hex"));
        colorBox.setMaxLength(8);
        colorBox.setValue(String.format("%06X", config.menuColor & 0xFFFFFF));
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> {
            Integer parsed = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
            if (parsed == null) {
                status = "Enter a 6-digit hex colour, e.g. 9B5CFF.";
                return;
            }
            setColour(parsed);
            status = "Menu colour set to #" + String.format("%06X", parsed & 0xFFFFFF);
        }).bounds(cxm + 5, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            setColour(Theme.DEFAULT_ACCENT);
            colorBox.setValue(String.format("%06X", Theme.DEFAULT_ACCENT & 0xFFFFFF));
            status = "Menu colour reset to default blue.";
        }).bounds(cxm + 55, y, 45, 20).build());
        y += 20 + 14;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());
    }

    private void buildAnim() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;

        this.addRenderableWidget(Button.builder(Component.literal("Animations: " + (c.menuAnimEnabled ? "ON" : "OFF")), b -> {
            c.menuAnimEnabled = !c.menuAnimEnabled;
            c.save();
            b.setMessage(Component.literal("Animations: " + (c.menuAnimEnabled ? "ON" : "OFF")));
            previewStart = System.currentTimeMillis();
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("Style: " + MenuAnim.STYLES[Math.floorMod(c.menuAnimStyle, 3)]), b -> {
            c.menuAnimStyle = (Math.floorMod(c.menuAnimStyle, 3) + 1) % 3;
            c.save();
            b.setMessage(Component.literal("Style: " + MenuAnim.STYLES[c.menuAnimStyle]));
            previewStart = System.currentTimeMillis();
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("Speed: " + MenuAnim.SPEEDS[Math.floorMod(c.menuAnimSpeed, 3)]), b -> {
            c.menuAnimSpeed = (Math.floorMod(c.menuAnimSpeed, 3) + 1) % 3;
            c.save();
            b.setMessage(Component.literal("Speed: " + MenuAnim.SPEEDS[c.menuAnimSpeed]));
            previewStart = System.currentTimeMillis();
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;

        // preview box is drawn here in render (keeps this gap free)
        previewY = y + 12;
        y += 12 + 42 + 8;

        this.addRenderableWidget(Button.builder(Component.literal("Play preview"), b ->
                previewStart = System.currentTimeMillis()).bounds(cxm - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm + 2, y, 98, 20).build());
    }

    private void buildGui() {
        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;
        int y = 46;

        this.addRenderableWidget(Button.builder(Component.literal("GUI style: " + STYLE_NAMES[Math.floorMod(c.menuStyle, 3)]), b -> {
            c.menuStyle = (Math.floorMod(c.menuStyle, 3) + 1) % 3;
            c.save();
            Theme.apply(c);
            b.setMessage(Component.literal("GUI style: " + STYLE_NAMES[c.menuStyle]));
            status = switch (c.menuStyle) {
                case 1 -> "Flat: solid panels, no glow.";
                case 2 -> "Glass: see-through panels.";
                default -> "Neon: glowing outline.";
            };
        }).bounds(cxm - 100, y, 200, 20).build());
        y += 24;

        toggleFull(y, "Backdrop dim", () -> c.menuBackdropDim, v -> c.menuBackdropDim = v);
        y += 24;

        // panel opacity: -  Panel opacity: 100%  +   (40% - 100%)
        final Button[] mid = new Button[1];
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            c.menuPanelOpacity = Math.max(40, c.menuPanelOpacity - 10);
            c.save();
            Theme.apply(c);
            mid[0].setMessage(Component.literal("Panel opacity: " + c.menuPanelOpacity + "%"));
        }).bounds(cxm - 100, y, 20, 20).build());
        mid[0] = this.addRenderableWidget(Button.builder(Component.literal("Panel opacity: " + c.menuPanelOpacity + "%"), b -> { })
                .bounds(cxm - 75, y, 150, 20).build());
        mid[0].active = false;
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            c.menuPanelOpacity = Math.min(100, c.menuPanelOpacity + 10);
            c.save();
            Theme.apply(c);
            mid[0].setMessage(Component.literal("Panel opacity: " + c.menuPanelOpacity + "%"));
        }).bounds(cxm + 80, y, 20, 20).build());
        y += 24;

        y += 4;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cxm - 100, y, 200, 20).build());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void toggleFull(int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addToggle(this.width / 2 - 100, y, 200, name, get, set);
    }

    private void addToggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(Button.builder(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")), b -> {
            set.accept(!get.getAsBoolean());
            DonutQueueClient.config.save();
            Theme.apply(DonutQueueClient.config);
            b.setMessage(Component.literal(name + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).bounds(x, y, w, 20).build());
    }

    private void setColour(int argb) {
        ModConfig c = DonutQueueClient.config;
        c.menuColor = 0xFF000000 | (argb & 0xFFFFFF);
        c.save();
        Theme.apply(c);
    }

    private void resetAll() {
        ModConfig c = DonutQueueClient.config;
        c.menuColor = Theme.DEFAULT_ACCENT;
        c.menuStyle = 0;
        c.menuBackdropDim = true;
        c.menuPanelOpacity = 100;
        c.menuAnimEnabled = true;
        c.menuAnimStyle = 0;
        c.menuAnimSpeed = 1;
        c.mainShowStartBal = true;
        c.mainShowToday = true;
        c.mainShowGraph = true;
        c.mainShowStats = true;
        c.mainShowButtons = true;
        c.menuHiddenTabs.clear();
        c.mainOrder = new ArrayList<>(List.of(FuturisticMenuScreen.MAIN_IDS));
        c.mainFourButtons = false;
        c.mainButtons = new ArrayList<>(List.of(FuturisticMenuScreen.DEFAULT_BUTTONS));
        c.mainGraphRange = 0;
        c.mainGraphGains = false;
        c.save();
        Theme.apply(c);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected String headerText() {
        return this.title.getString().toUpperCase();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        neonBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        ModConfig c = DonutQueueClient.config;
        int cxm = this.width / 2;

        switch (section) {
            case COLOUR -> {
                // swatches over the neon button faces
                int current = c.menuColor & 0xFFFFFF;
                for (int i = 0; i < swatches.size(); i++) {
                    Button s = swatches.get(i);
                    g.fill(s.getX() + 3, s.getY() + 3, s.getX() + s.getWidth() - 3, s.getY() + s.getHeight() - 3, PRESET_COLORS[i]);
                    if ((PRESET_COLORS[i] & 0xFFFFFF) == current) {
                        Gfx.outline(g, s.getX() - 1, s.getY() - 1, s.getWidth() + 2, s.getHeight() + 2, 0xFFFFFFFF);
                    }
                }
                g.text(this.font, "Pick a colour", cxm - 100, swatches.get(0).getY() - 12, C_NEON_HI, true);
                g.text(this.font, "Or type a hex colour (e.g. 9B5CFF)", cxm - 100, colorBox.getY() - 12, C_NEON_HI, true);

                // live preview of the typed colour, between the box and the Set button
                Integer preview = DonutQueueConfigScreen.parseHexColor(colorBox.getValue());
                int col = preview != null ? preview : (c.menuColor | 0xFF000000);
                g.fill(cxm - 18, colorBox.getY(), cxm - 2, colorBox.getY() + 20, col);
                Gfx.outline(g, cxm - 18, colorBox.getY(), 16, 20, C_NEON);
            }
            case ANIM -> drawPreview(g, c, cxm);
            case GUI -> {
                int top = firstButtonY();
                g.text(this.font, "How the menu looks", cxm - 100, top - 12, C_NEON_HI, true);
            }
            default -> {
                int top = firstButtonY();
                g.text(this.font, "Make the menu your own", cxm - 100, top - 12, C_NEON_HI, true);
            }
        }

        if (!status.isEmpty()) {
            g.text(this.font, status, cxm - this.font.width(status) / 2, this.height - 14, C_WARN, true);
        }
    }

    /** Y of the top-most widget (after centring) - labels are drawn 12px above it. */
    private int firstButtonY() {
        int min = Integer.MAX_VALUE;
        for (Object o : this.children()) {
            if (o instanceof net.minecraft.client.gui.components.AbstractWidget w) min = Math.min(min, w.getY());
        }
        return min == Integer.MAX_VALUE ? 46 : min;
    }

    /** A little box that replays the chosen animation, so you can judge style + speed without leaving. */
    private void drawPreview(GuiGraphicsExtractor g, ModConfig c, int cxm) {
        long now = System.currentTimeMillis();
        if (now - previewStart > 2200) previewStart = now; // loop

        int x0 = cxm - 100;
        int w = 200;
        int h = 42;
        int y0 = previewY;
        g.fill(x0, y0, x0 + w, y0 + h, Theme.panel);
        Gfx.outline(g, x0, y0, w, h, Theme.edge);

        float p = MenuAnim.progress(previewStart, c);
        int dy = MenuAnim.slideOffset(p, 1, c);
        g.pose().pushMatrix();
        g.pose().translate(0f, (float) dy);
        g.fill(x0 + 8, y0 + 12, x0 + 8 + 70, y0 + 30, Theme.tile);
        Gfx.outline(g, x0 + 8, y0 + 12, 70, 18, Theme.edge);
        g.text(this.font, "Preview page", x0 + 84, y0 + 12, C_TEXT, false);
        g.text(this.font, c.menuAnimEnabled ? MenuAnim.STYLES[Math.floorMod(c.menuAnimStyle, 3)] + ", "
                + MenuAnim.SPEEDS[Math.floorMod(c.menuAnimSpeed, 3)].toLowerCase() : "animations off",
                x0 + 84, y0 + 22, C_MUTED, false);
        g.pose().popMatrix();
        MenuAnim.drawCover(g, x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, p, c);

        g.text(this.font, "Preview", x0, y0 - 11, C_NEON_HI, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
