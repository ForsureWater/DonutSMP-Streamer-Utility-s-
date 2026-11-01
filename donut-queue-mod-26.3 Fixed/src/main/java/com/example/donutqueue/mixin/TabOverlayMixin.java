package com.example.donutqueue.mixin;

import com.example.donutqueue.rank.RankColors;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recolors names in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void donutqueue$rankcolor(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(RankColors.apply(cir.getReturnValue(), info.getProfile().name()));
    }
}
