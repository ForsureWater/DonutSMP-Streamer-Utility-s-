package com.example.donutqueue.mixin;

import com.example.donutqueue.GuardsBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auction Guard: lets the guard hold back a click on a pricey auction listing until the player confirms. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void donutqueue$auctionGuard(Slot slot, int slotId, MouseButtonEvent event, ContainerInput type, CallbackInfo ci) {
        if (GuardsBridge.auctionClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, event.button(), type)) {
            ci.cancel();
        }
    }
}
