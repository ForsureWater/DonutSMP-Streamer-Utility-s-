package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bidding menu (hotkey). Left: choose an item, amount and time and press START for a one-off auction.
 * Right: 8 presets. Click an empty slot to make a preset (item, time, CONFIRM in its own editor), click a filled
 * one to start it. E edits a slot, X clears it. "/bidding <number>" starts a preset too.
 */
public class BiddingMenuScreen extends NeonScreen {

    private static final int W = 450;
    private static final int H = 272;
    private static final int COLS = 11;
    private static final int ROWS = 4;
    private static final int CELL = 22;
    private static final int PER_PAGE = COLS * ROWS;

    // kept between openings (while the game runs)
    private static String selectedId = "";
    private static String countText = "1";
    private static String timeText = "60";
    private static String searchText = "";
    private String minText = "";

    private int left;
    private int top;
    private String status = "";
    private int page = 0;
    private List<PresetEditorScreen.Entry> filtered = new ArrayList<>();

    private EditBox searchBox;
    private EditBox countBox;
    private EditBox timeBox;
    private EditBox minBox;
    private Button[] grid;

    public BiddingMenuScreen() {
        super(Component.literal("Bidding"));
        DonutQueueClient.config.ensureBidPresets();
        double m = DonutQueueClient.config.biddingMinimum;
        minText = m > 0 ? DonutQueueClient.formatAmount(m) : "";
        refilter();
        Tutorial.event("BID_MENU_OPENED");
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    @Override
    protected void init() {
        ModConfig c = DonutQueueClient.config;
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);
        int x = left + 14;

        // header: settings (gear) + tutorial
        this.addRenderableWidget(Button.builder(Component.literal("\u2699"), b ->
                this.minecraft.gui.setScreen(new BiddingSettingsScreen(this)))
                .bounds(left + W - 96, top + 2, 20, 19).build());
        this.addRenderableWidget(Button.builder(Component.literal("Tutorial"), b -> {
            Tutorial.start("AUCTION");
            this.minecraft.gui.setScreen(new TutorialScreen());
        }).bounds(left + W - 72, top + 2, 66, 19).build());

        // chosen item row
        this.addRenderableWidget(Button.builder(Component.literal("Held"), b -> useHeldItem())
                .bounds(x + 196, top + 30, 50, 20).build());

        searchBox = new EditBox(this.font, x, top + 56, 246, 18, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setValue(searchText);
        searchBox.setResponder(s -> {
            searchText = s;
            page = 0;
            refilter();
        });
        this.addRenderableWidget(searchBox);

        grid = new Button[PER_PAGE];
        for (int i = 0; i < PER_PAGE; i++) {
            final int cell = i;
            int cx = x + (i % COLS) * CELL;
            int cy = top + 78 + (i / COLS) * CELL;
            grid[i] = this.addRenderableWidget(Button.builder(Component.empty(), b -> pick(page * PER_PAGE + cell))
                    .bounds(cx, cy, CELL - 1, CELL - 1).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> setPage(page - 1))
                .bounds(x, top + 168, 30, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> setPage(page + 1))
                .bounds(x + 216, top + 168, 30, 18).build());

        countBox = new EditBox(this.font, x, top + 202, 56, 18, Component.literal("Amount"));
        countBox.setMaxLength(6);
        countBox.setValue(countText);
        countBox.setResponder(s -> {
            // EditBox#setFilter is gone in 26.1: keep the box digits-only here instead.
            String clean = s.replaceAll("\\D", "");
            if (!clean.equals(s)) { countBox.setValue(clean); return; }
            countText = s;
        });
        this.addRenderableWidget(countBox);

        timeBox = new EditBox(this.font, x + 60, top + 202, 80, 18, Component.literal("Duration"));
        timeBox.setMaxLength(10);
        timeBox.setValue(timeText);
        timeBox.setResponder(s -> timeText = s);
        this.addRenderableWidget(timeBox);

        minBox = new EditBox(this.font, x + 144, top + 202, 102, 18, Component.literal("Minimum bid"));
        minBox.setMaxLength(14);
        minBox.setValue(minText);
        minBox.setResponder(s -> minText = s);
        this.addRenderableWidget(minBox);

        this.addRenderableWidget(Button.builder(Component.literal("START"), b -> startNow())
                .bounds(x, top + 228, 121, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("STOP"), b -> {
            Auction.stop();
            status = "Bidding stopped and reset.";
        }).bounds(x + 125, top + 228, 121, 20).build());

        // presets on the side
        int px = left + 274;
        for (int i = 0; i < ModConfig.BID_PRESET_SLOTS; i++) {
            final int slot = i;
            int ry = top + 42 + i * 26;
            this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                if (c.biddingPresets.get(slot).isEmpty()) {
                    this.minecraft.gui.setScreen(new PresetEditorScreen(this, slot));
                } else if (Auction.startPreset(slot + 1)) {
                    this.minecraft.gui.setScreen(null);
                }
            }).bounds(px, ry, 118, 22).build());
            this.addRenderableWidget(Button.builder(Component.literal("E"), b ->
                    this.minecraft.gui.setScreen(new PresetEditorScreen(this, slot)))
                    .bounds(px + 120, ry, 20, 22).build());
            this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                c.biddingPresets.set(slot, new ModConfig.BidPreset());
                c.save();
                status = "Preset " + (slot + 1) + " cleared.";
            }).bounds(px + 142, ry, 20, 22).build());
        }
    }

    // ---------------- actions ----------------

    private void refilter() {
        String q = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        List<PresetEditorScreen.Entry> out = new ArrayList<>();
        for (PresetEditorScreen.Entry e : PresetEditorScreen.all()) {
            if (q.isEmpty() || e.lower().contains(q)) out.add(e);
        }
        filtered = out;
        setPage(page);
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void setPage(int p) {
        page = Math.max(0, Math.min(p, pageCount() - 1));
    }

    private void pick(int index) {
        if (index < 0 || index >= filtered.size()) return;
        selectedId = filtered.get(index).id();
        status = "";
    }

    private void useHeldItem() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ItemStack held = this.minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            status = "Your main hand is empty.";
            return;
        }
        selectedId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        countText = String.valueOf(held.getCount());
        countBox.setValue(countText);
        status = "";
    }

    private void startNow() {
        if (Auction.stackFor(selectedId).isEmpty()) {
            status = "Pick an item first.";
            return;
        }
        int count;
        try {
            count = Integer.parseInt(countBox.getValue().trim());
        } catch (NumberFormatException e) {
            count = 0;
        }
        if (count <= 0) {
            status = "Amount must be at least 1.";
            return;
        }
        long seconds = DonutQueueClient.parseDurationSeconds(timeBox.getValue());
        if (seconds <= 0) {
            status = "Give a valid time, e.g. 60, 5m or 1h.";
            return;
        }
        String rawMin = minBox.getValue().trim();
        double minimum = rawMin.isEmpty() ? 0 : DonutQueueClient.parseAmount(rawMin);
        if (minimum < 0) minimum = 0;
        Auction.start(selectedId, count, seconds, minimum);
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            setPage(page - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    // ---------------- drawing ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ModConfig c = DonutQueueClient.config;
        neonBackdrop(g);

        if (Theme.glow) {
            Gfx.outline(g, left - 2, top - 2, W + 4, H + 4, Theme.neonA(0x22));
            Gfx.outline(g, left - 1, top - 1, W + 2, H + 2, Theme.neonA(0x55));
        }
        g.fill(left, top, left + W, top + H, C_PANEL);
        g.fill(left + 1, top + 1, left + W - 1, top + 23, C_HEADER);
        g.fill(left + 1, top + 23, left + W - 1, top + 24, C_NEON);
        String head = "BIDDING - AUCTION";
        g.text(this.font, head, left + (W - this.font.width(head)) / 2, top + 8, C_NEON_HI, true);
        g.fill(left + 266, top + 32, left + 267, top + H - 12, C_EDGE);

        for (int i = 0; i < PER_PAGE; i++) {
            grid[i].active = page * PER_PAGE + i < filtered.size();
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        int x = left + 14;

        // chosen item
        g.fill(x, top + 30, x + 190, top + 50, C_TILE);
        Gfx.outline(g, x, top + 30, 190, 20, C_EDGE);
        ItemStack sel = Auction.stackFor(selectedId);
        if (sel.isEmpty()) {
            g.text(this.font, "No item selected", x + 6, top + 36, C_MUTED, true);
        } else {
            g.item(sel, x + 2, top + 32);
            g.text(this.font, fitText(sel.getHoverName().getString(), 160), x + 22, top + 36, C_TEXT, true);
        }
        g.text(this.font, "ITEM", x, top + 22 + 0, C_NEON_HI, false);
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.text(this.font, "Search items...", x + 4, top + 61, C_MUTED, false);
        }

        String hoverName = null;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= filtered.size()) break;
            PresetEditorScreen.Entry e = filtered.get(idx);
            int cx = x + (i % COLS) * CELL;
            int cy = top + 78 + (i / COLS) * CELL;
            g.item(e.stack(), cx + 2, cy + 2);
            if (e.id().equals(selectedId)) {
                Gfx.outline(g, cx, cy, CELL - 1, CELL - 1, C_GOOD);
            }
            if (mouseX >= cx && mouseX < cx + CELL - 1 && mouseY >= cy && mouseY < cy + CELL - 1) {
                hoverName = e.name();
            }
        }
        String mid = hoverName != null ? hoverName : ("Page " + (page + 1) + " / " + pageCount());
        mid = fitText(mid, 176);
        g.text(this.font, mid, x + 123 - this.font.width(mid) / 2, top + 173, hoverName != null ? C_TEXT : C_MUTED, true);

        g.text(this.font, "AMOUNT", x, top + 191, C_NEON_HI, true);
        g.text(this.font, "TIME (5m, 1h)", x + 60, top + 191, C_NEON_HI, true);
        g.text(this.font, "MIN BID (50k, 1m)", x + 144, top + 191, C_NEON_HI, true);

        // status line
        String line;
        int lineColor;
        if (!status.isEmpty()) {
            line = status;
            lineColor = C_WARN;
        } else if (c.biddingActive) {
            long secs = Math.max(0, (c.biddingEndTimeMillis - System.currentTimeMillis()) / 1000L);
            String item = (c.biddingItemId != null && !c.biddingItemId.isEmpty() && c.biddingItemCount > 0)
                    ? c.biddingItemCount + "x " + Auction.itemName(c.biddingItemId) + " - " : "";
            line = "Running: " + item + Auction.formatDuration(secs) + " left";
            lineColor = C_GOOD;
        } else {
            line = "Start a preset with /bidding <number>";
            lineColor = C_MUTED;
        }
        g.text(this.font, fitText(line, 246), x, top + 254, lineColor, true);

        // presets
        int px = left + 274;
        g.text(this.font, "PRESETS", px, top + 22 + 0, C_NEON_HI, false);
        g.text(this.font, fitText("empty = make, filled = start", 162 - 46), px + 46, top + 30, C_MUTED, false);
        for (int i = 0; i < ModConfig.BID_PRESET_SLOTS; i++) {
            int ry = top + 42 + i * 26;
            ModConfig.BidPreset p = c.biddingPresets.get(i);
            g.text(this.font, String.valueOf(i + 1), px + 6, ry + 7, C_NEON_HI, true);
            if (p.isEmpty()) {
                g.text(this.font, "empty", px + 20, ry + 7, C_MUTED, true);
            } else {
                ItemStack st = Auction.stackFor(p.itemId);
                if (!st.isEmpty()) g.item(st, px + 17, ry + 3);
                String label = p.count + "x " + Auction.itemName(p.itemId) + " " + Auction.formatDuration(p.seconds);
                g.text(this.font, fitText(label, 118 - 38), px + 36, ry + 7, C_TEXT, true);
            }
        }
        Tutorial.renderOverlay(g, this.minecraft);
    }
}
