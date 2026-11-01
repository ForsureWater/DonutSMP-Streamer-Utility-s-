package com.example.donutqueue;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Only ever loaded by Mod Menu itself, and only if Mod Menu is installed
 * (Fabric Loader won't touch this class otherwise). This is what puts the
 * gear/settings icon on the mod's card in Mod Menu's mod list.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FuturisticMenuScreen::new;
    }
}
