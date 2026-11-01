package com.example.donutqueue;

/**
 * Small public surface for addon mods. Everything else in this mod is internal.
 */
public final class DonutQueueApi {

    private DonutQueueApi() { }

    /**
     * A snapshot of the menu's current colours (all ARGB). They follow the player's choice in
     * Customisation &gt; Menu customisation, so an addon that paints with them always matches the menu.
     */
    public record Palette(
            int neon, int neonHi, int edge, int text, int muted,
            int btn, int btnAct, int btnHov, int btnEdge, int btnOff,
            int dim, int panel, int header, int sidebar, int tile, int plot,
            boolean glow) { }

    /** The menu's colours right now. Cheap enough to call every frame. */
    public static Palette palette() {
        if (DonutQueueClient.config != null) {
            Theme.apply(DonutQueueClient.config);
        }
        return new Palette(
                Theme.neon, Theme.neonHi, Theme.edge, Theme.text, Theme.muted,
                Theme.btn, Theme.btnAct, Theme.btnHov, Theme.btnEdge, Theme.btnOff,
                Theme.dim, Theme.panel, Theme.header, Theme.sidebar, Theme.tile, Theme.plot,
                Theme.glow);
    }
}
