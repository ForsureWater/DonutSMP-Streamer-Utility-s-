package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Lets the player reposition any of this mod's HUD boxes by dragging it anywhere on the screen
 * (same free-move feel as the Spotify HUD editor): press on the box and drag, or click an empty
 * spot to make the box jump there. It snaps to the screen edges and centre lines.
 */
public class MovableBoxScreen extends NeonScreen {

    private final Screen parent;
    private final int boxWidth;
    private final int boxHeight;
    private final String previewLabel;
    private final int previewColor;
    private final IntSupplier getX;
    private final IntSupplier getY;
    private final IntConsumer setX;
    private final IntConsumer setY;

    /** Another box shown (dimmed) while you move this one, so you can see where everything else sits. */
    record Ghost(String label, IntSupplier x, IntSupplier y, int w, int h, boolean on) {}

    private final List<Ghost> ghosts = new ArrayList<>();

    private boolean ghostsOnTop = false;

    MovableBoxScreen withGhosts(List<Ghost> others) {
        ghosts.addAll(others);
        return this;
    }

    /** Draw the ghost boxes over the moving box (used when the moving box is a group that contains them). */
    MovableBoxScreen ghostsOnTop() {
        ghostsOnTop = true;
        return this;
    }

    private void drawGhosts(GuiGraphicsExtractor guiGraphics) {
        for (Ghost gh : ghosts) {
            int gx = gh.x().getAsInt();
            int gy = gh.y().getAsInt();
            int col = gh.on() ? C_NEON : C_MUTED;
            guiGraphics.fill(gx, gy, gx + gh.w(), gy + gh.h(), gh.on() ? 0x80071130 : 0x40071130);
            Gfx.outline(guiGraphics, gx, gy, gh.w(), gh.h(), col);
            String t = gh.label() + (gh.on() ? "" : " (off)");
            guiGraphics.text(this.font, t, gx + 3, gy + 3, col, true);
        }
    }

    private boolean dragging = false;
    private double grabX;
    private double grabY;
    private boolean guideX;
    private boolean guideY;

    public MovableBoxScreen(Screen parent, String title, int boxWidth, int boxHeight,
                             String previewLabel, int previewColor,
                             IntSupplier getX, IntSupplier getY, IntConsumer setX, IntConsumer setY) {
        super(Component.literal(title));
        this.parent = parent;
        this.boxWidth = boxWidth;
        this.boxHeight = boxHeight;
        this.previewLabel = previewLabel;
        this.previewColor = previewColor;
        this.getX = getX;
        this.getY = getY;
        this.setX = setX;
        this.setY = setY;
    }

    @Override
    protected boolean showPanel() {
        return false;
    }

    @Override
    protected int dimColor() {
        return 0x70020612;
    }

    @Override
    protected void init() {
        dragging = false;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 26, 120, 20)
                .build());
    }

    /** 26.3 moved from GLFW to SDL, so the left mouse button is 1 there (it was 0 before); accept both. */
    private static boolean isLeftButton(net.minecraft.client.input.MouseButtonEvent event) {
        int b = event.button();
        return b == 0 || b == 1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (isLeftButton(event) && !overDone(mx, my)) {
            int x = getX.getAsInt();
            int y = getY.getAsInt();
            dragging = true;
            if (mx >= x && mx < x + boxWidth && my >= y && my < y + boxHeight) {
                grabX = mx - x;
                grabY = my - y;
            } else {
                // clicked an empty spot: the box jumps there (centred on the cursor) and follows
                grabX = boxWidth / 2.0;
                grabY = boxHeight / 2.0;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (isLeftButton(event) && dragging) {
            dragging = false;
            DonutQueueClient.config.save();
        }
        return super.mouseReleased(event);
    }

    private boolean overDone(int mx, int my) {
        int bx = this.width / 2 - 60;
        int by = this.height - 26;
        return mx >= bx && mx < bx + 120 && my >= by && my < by + 20;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int freeW = Math.max(0, this.width - boxWidth);
        int freeH = Math.max(0, this.height - boxHeight);
        int x;
        int y;

        guideX = false;
        guideY = false;
        if (dragging) {
            int nx = Math.max(0, Math.min(freeW, (int) Math.round(mouseX - grabX)));
            int ny = Math.max(0, Math.min(freeH, (int) Math.round(mouseY - grabY)));

            // magnetic edges and centre lines (3px)
            if (Math.abs(nx + boxWidth / 2 - this.width / 2) <= 3) {
                nx = Math.max(0, Math.min(freeW, (this.width - boxWidth) / 2));
                guideX = true;
            }
            if (Math.abs(ny + boxHeight / 2 - this.height / 2) <= 3) {
                ny = Math.max(0, Math.min(freeH, (this.height - boxHeight) / 2));
                guideY = true;
            }
            if (nx <= 3) nx = 0;
            if (nx >= freeW - 3) nx = freeW;
            if (ny <= 3) ny = 0;
            if (ny >= freeH - 3) ny = freeH;

            setX.accept(nx);
            setY.accept(ny);
        }

        neonBackdrop(guiGraphics);

        // centre guides
        guiGraphics.fill(this.width / 2, 0, this.width / 2 + 1, this.height, guideX ? C_NEON_HI : 0x22FFFFFF);
        guiGraphics.fill(0, this.height / 2, this.width, this.height / 2 + 1, guideY ? C_NEON_HI : 0x22FFFFFF);

        x = getX.getAsInt();
        y = getY.getAsInt();

        if (!ghostsOnTop) drawGhosts(guiGraphics);

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xE0071130);
        Gfx.outline(guiGraphics, x, y, boxWidth, boxHeight, dragging ? C_GOOD : previewColor);
        guiGraphics.text(this.font, previewLabel, x + 6, y + 6, previewColor, true);
        if (ghostsOnTop) drawGhosts(guiGraphics);

        drawCentered(guiGraphics, "Drag the box anywhere, or click a spot to jump there", 10, C_TEXT);
        drawCentered(guiGraphics, "Position: " + x + ", " + y, 22, C_MUTED);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        neonFaces(guiGraphics, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, String text, int y, int color) {
        int textWidth = this.font.width(text);
        guiGraphics.text(this.font, text, (this.width - textWidth) / 2, y, color, true);
    }

    @Override
    public void onClose() {
        DonutQueueClient.config.save();
        this.minecraft.gui.setScreen(parent);
    }
}
