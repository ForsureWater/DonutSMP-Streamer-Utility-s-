package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor for ONE preset slot: choose the item, the amount and the time, then CONFIRM.
 * It never looks at what you hold or have selected - everything is chosen here.
 */
public class PresetEditorScreen extends NeonScreen {

    private static final int W = 282;
    private static final int H = 268;
    private static final int COLS = 11;
    private static final int ROWS = 4;
    private static final int CELL = 22;
    private static final int PER_PAGE = COLS * ROWS;

    record Entry(String id, String name, String lower, ItemStack stack) {}

    private static List<Entry> allItems;

    static List<Entry> all() {
        if (allItems == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ItemStack st = new ItemStack(item);
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = st.getHoverName().getString();
                list.add(new Entry(id, name, (name + " " + id).toLowerCase(Locale.ROOT), st));
            }
            allItems = list;
        }
        return allItems;
    }

    private final net.minecraft.client.gui.screens.Screen parent;
    private final int slot;

    private int left;
    private int top;
    private String selectedId = "";
    private String countText = "1";
    private String timeText = "60";
    private String minText = "";
    private String searchText = "";
    private String status = "";
    private int page = 0;
    private List<Entry> filtered = new ArrayList<>();

    private EditBox searchBox;
    private EditBox countBox;
    private EditBox timeBox;
    private EditBox minBox;
    private Button[] grid;

    public PresetEditorScreen(net.minecraft.client.gui.screens.Screen parent, int slot) {
        super(Component.literal(slot < 0 ? "Quick auction" : "Preset " + (slot + 1)));
        this.parent = parent;
        this.slot = slot; // -1 = quick auction (choose an item and start right away, nothing is saved)
        if (slot >= 0) {
            ModConfig.BidPreset p = DonutQueueClient.config.biddingPresets.get(slot);
            if (!p.isEmpty()) {
                selectedId = p.itemId;
                countText = String.valueOf(p.count);
                timeText = Auction.formatDuration(p.seconds);
                minText = p.minimum > 0 ? DonutQueueClient.formatAmount(p.minimum) : "";
            }
            Tutorial.event("PRESET_EDITOR_OPENED");
        }
        refilter();
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);
        int x = left + 20;

        searchBox = new EditBox(this.font, x, top + 56, 242, 18, Component.literal("Search"));
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
                .bounds(x + 212, top + 168, 30, 18).build());

        countBox = new EditBox(this.font, x, top + 200, 56, 18, Component.literal("Amount"));
        countBox.setMaxLength(6);
        countBox.setValue(countText);
        countBox.setResponder(s -> {
            // EditBox#setFilter is gone in 26.1: keep the box digits-only here instead.
            String clean = s.replaceAll("\\D", "");
            if (!clean.equals(s)) { countBox.setValue(clean); return; }
        });
        this.addRenderableWidget(countBox);

        timeBox = new EditBox(this.font, x + 60, top + 200, 80, 18, Component.literal("Duration"));
        timeBox.setMaxLength(10);
        timeBox.setValue(timeText);
        this.addRenderableWidget(timeBox);

        minBox = new EditBox(this.font, x + 144, top + 200, 98, 18, Component.literal("Minimum bid"));
        minBox.setMaxLength(14);
        minBox.setValue(minText);
        this.addRenderableWidget(minBox);

        this.addRenderableWidget(Button.builder(Component.literal(slot < 0 ? "START AUCTION" : "CONFIRM"), b -> confirm())
                .bounds(x, top + 228, 118, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> onClose())
                .bounds(x + 124, top + 228, 118, 20).build());
    }

    private void refilter() {
        String q = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
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

    private int parseCount() {
        try {
            return Integer.parseInt(countBox.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void confirm() {
        if (Auction.stackFor(selectedId).isEmpty()) {
            status = "Pick an item first.";
            return;
        }
        int count = parseCount();
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
        if (slot < 0) {
            Auction.start(selectedId, count, seconds, minimum);
            this.minecraft.gui.setScreen(null);
            return;
        }
        ModConfig c = DonutQueueClient.config;
        ModConfig.BidPreset p = new ModConfig.BidPreset();
        p.itemId = selectedId;
        p.count = count;
        p.seconds = seconds;
        p.minimum = minimum;
        c.biddingPresets.set(slot, p);
        c.save();
        Tutorial.event("PRESET_SAVED");
        onClose();
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
        String head = slot < 0 ? "QUICK AUCTION" : "PRESET " + (slot + 1);
        g.text(this.font, head, left + (W - this.font.width(head)) / 2, top + 8, C_NEON_HI, true);

        for (int i = 0; i < PER_PAGE; i++) {
            grid[i].active = page * PER_PAGE + i < filtered.size();
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        neonFaces(g, mouseX, mouseY);

        int x = left + 20;

        // chosen item
        g.text(this.font, "ITEM", x, top + 30, C_NEON_HI, true);
        g.fill(x + 34, top + 28, x + 242, top + 48, C_TILE);
        Gfx.outline(g, x + 34, top + 28, 208, 20, C_EDGE);
        ItemStack sel = Auction.stackFor(selectedId);
        if (sel.isEmpty()) {
            g.text(this.font, "Click an item below", x + 40, top + 34, C_MUTED, true);
        } else {
            g.item(sel, x + 36, top + 30);
            g.text(this.font, fitText(sel.getHoverName().getString(), 176), x + 56, top + 34, C_TEXT, true);
        }
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.text(this.font, "Search items...", x + 4, top + 61, C_MUTED, false);
        }

        String hoverName = null;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
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
        mid = fitText(mid, 170);
        g.text(this.font, mid, x + 121 - this.font.width(mid) / 2, top + 173, hoverName != null ? C_TEXT : C_MUTED, true);

        g.text(this.font, "AMOUNT", x, top + 189, C_NEON_HI, true);
        g.text(this.font, "TIME (5m, 1h)", x + 60, top + 189, C_NEON_HI, true);
        g.text(this.font, "MIN BID (50k, 1m)", x + 144, top + 189, C_NEON_HI, true);

        if (!status.isEmpty()) {
            g.text(this.font, fitText(status, 242), x, top + 254, C_WARN, true);
        }
        Tutorial.renderOverlay(g, this.minecraft);
    }
}
